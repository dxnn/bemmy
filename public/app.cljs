;; No (ns ...) form: stay in `user` so the plugin init's
;; (require '[emmy.env :refer :all]) carries over to user-typed code.
(require '[reagent.core :as r]
         '[reagent.dom :as rdom]
         '[emmy.env :refer :all])

(def default-page
  ";; SICM playground — Emmy in the browser via SCI.
;; emmy.env is pre-referred, so D, square, ->TeX, etc. are in scope.
;; Cmd-Enter (or Ctrl-Enter) to evaluate.

;; symbolic derivative of x²
((D (fn [x] (square x))) 'x)
")

;; --- Pages: named source buffers persisted in localStorage. ----------------

(def storage-key "emmy-playground/v1")

(defn- load-state []
  (or (try (when-let [s (.getItem js/localStorage storage-key)]
             (let [obj (js/JSON.parse s)]
               {:pages   (js->clj (.-pages obj))
                :current (.-current obj)}))
           (catch :default _ nil))
      {:pages {"Default" default-page} :current "Default"}))

(defn- save-state! [{:keys [pages current]}]
  (.setItem js/localStorage storage-key
            (js/JSON.stringify #js {:pages   (clj->js pages)
                                    :current current})))

(defonce !pages (r/atom (load-state)))
(defonce _persist (add-watch !pages :persist
                             (fn [_ _ _ new] (save-state! new))))

(defn- current-page-source []
  (get-in @!pages [:pages (:current @!pages)] ""))

(defn- update-current-source! [src]
  (let [cur (:current @!pages)]
    (when (not= src (get-in @!pages [:pages cur]))
      (swap! !pages assoc-in [:pages cur] src))))

(defonce !view   (atom nil))            ; the CodeMirror EditorView
(defonce !result (r/atom {:status :idle}))

(defn- switch-page! [name]
  (when-let [view @!view]
    (when-let [src (get-in @!pages [:pages name])]
      (swap! !pages assoc :current name)
      (.dispatch view #js {:changes #js {:from   0
                                         :to     (.. view -state -doc -length)
                                         :insert src}}))))

(defn- new-page! []
  (when-let [name (some-> (js/prompt "Page name:")
                          .trim
                          not-empty)]
    (when-not (contains? (:pages @!pages) name)
      (swap! !pages assoc-in [:pages name] ";; New page\n"))
    (switch-page! name)))

(defn- delete-page! [name]
  (when (and (> (count (:pages @!pages)) 1)
             (js/confirm (str "Delete \"" name "\"?")))
    (let [{:keys [pages current]} @!pages
          new-pages   (dissoc pages name)
          new-current (if (= current name)
                        (first (sort (keys new-pages)))
                        current)]
      (reset! !pages {:pages new-pages :current new-current})
      (when (= current name) (switch-page! new-current)))))

(defn- normalize-ws
  "SCI's reader treats non-ASCII whitespace (NBSP, em-space, line-separator
   etc.) as token characters and chokes when they appear in pasted source.
   Replace common offenders with regular spaces; preserve newlines."
  [s]
  (-> s
      (.replace (js/RegExp.
                 "[\\u00A0\\u1680\\u2000-\\u200B\\u202F\\u205F\\u3000\\uFEFF]"
                 "g") " ")
      (.replace (js/RegExp. "[\\u2028\\u2029]" "g") "\n")))

(defn- current-source []
  (when-let [v @!view]
    (.. v -state -doc toString)))

(defn- emmy-fragment?
  "emmy-viewers helpers (parametric, of-x, vector-field, ...) return a
   quoted reagent form tagged with :portal.viewer/reagent? metadata so a
   downstream renderer (Clerk/Portal) knows to eval it. We replicate that
   eval step ourselves."
  [v]
  (when-let [m (try (meta v) (catch :default _ nil))]
    (or (:portal.viewer/reagent? m)
        (contains? m :nextjournal.clerk.viewer/viewer))))

(defn- expand-fragment
  "Re-evaluate a fragment form through SCI so the embedded macros
   (reagent.core/with-let etc.) expand and the symbol references
   (mafs.plot/Parametric etc.) resolve to actual Reagent component fns."
  [v]
  (try
    (js/scittle.core.eval_string (pr-str v))
    (catch :default _ v)))

(defn- eval-with-tex [src]
  ;; maybe-show turns a SICM-style frame atom into Mafs hiccup so the
  ;; user can leave `win2` (or any frame) as the last form and see the
  ;; plot inline; non-frames pass through untouched.
  (let [wrapped (str "(let [v# (do " src ")]\n"
                     "  [(maybe-show v#)\n"
                     "   (try (emmy.expression.render/->TeX v#)\n"
                     "        (catch :default _ nil))])")
        [v tex] (js/scittle.core.eval_string wrapped)]
    (if (emmy-fragment? v)
      {:value (expand-fragment v) :tex nil}
      {:value v :tex tex})))

(defn- ws-char? [c]
  (case c (" " "\n" "\t" "\r" ",") true false))

(defn- split-top-forms
  "Split src into a vector of top-level form strings. Tracks bracket depth,
   strings, line comments, and char-literal escapes (\\( \\) \\\" etc.).
   Naked top-level forms (e.g. a bare symbol) are also captured."
  [src]
  (let [n (count src)]
    (loop [i 0 start nil depth 0
           in-str false in-cmt false esc false
           acc []]
      (if (>= i n)
        (cond-> acc
          start (conj (clojure.string/trim (subs src start n))))
        (let [c (.charAt src i)]
          (cond
            esc      (recur (inc i) start depth in-str in-cmt false acc)
            in-str   (case c
                       "\\" (recur (inc i) start depth true in-cmt true  acc)
                       "\"" (recur (inc i) start depth false in-cmt false acc)
                       (recur (inc i) start depth true in-cmt false acc))
            in-cmt   (if (= c "\n")
                       (recur (inc i) start depth false false false acc)
                       (recur (inc i) start depth false true  false acc))
            (= c "\\") (recur (+ i 2) (or start i) depth false false false acc)
            (= c ";")  (recur (inc i) start depth false true  false acc)
            (= c "\"") (recur (inc i) (or start i) depth true  false false acc)
            (or (= c "(") (= c "[") (= c "{"))
            (recur (inc i) (or start i) (inc depth) false false false acc)
            (or (= c ")") (= c "]") (= c "}"))
            (let [d' (dec depth) end (inc i)]
              (if (and start (zero? d'))
                (recur end nil 0 false false false
                       (conj acc (clojure.string/trim (subs src start end))))
                (recur end start d' false false false acc)))
            (and start (zero? depth) (ws-char? c))
            (recur (inc i) nil 0 false false false
                   (conj acc (clojure.string/trim (subs src start i))))
            (and (nil? start) (not (ws-char? c)))
            (recur (inc i) i depth false false false acc)
            :else (recur (inc i) start depth false false false acc)))))))

(defn- top-forms [src]
  (filterv (complement clojure.string/blank?)
           (split-top-forms (normalize-ws src))))

(defonce !eval-id (atom 0))

(defn eval! []
  (when-let [src (current-source)]
    (let [eval-id (swap! !eval-id inc)
          results (reduce
                   (fn [acc form-src]
                     (try
                       (let [{:keys [value tex]} (eval-with-tex form-src)]
                         (conj acc {:form  form-src
                                    :value value      ; raw, for hiccup detection
                                    :pr    (pr-str value)
                                    :tex   tex}))
                       (catch :default e
                         (reduced
                          (conj acc {:form form-src
                                     :err  (or (.-message e) (str e))})))))
                   []
                   (top-forms src))]
      (reset! !result {:status :ok :results results :eval-id eval-id}))))

(defn- escape-html [s]
  (-> s
      (.replace (js/RegExp. "&" "g") "&amp;")
      (.replace (js/RegExp. "<" "g") "&lt;")
      (.replace (js/RegExp. ">" "g") "&gt;")))

(defn- highlight-clojure
  "Render a Clojure source string as a span of HTML with hljs's tokens.
   Falls back to a safely-escaped plain string if hljs or its Clojure
   language module aren't loaded, or if highlighting throws for any
   other reason — never let a display issue tank evaluation."
  [s]
  (or (when (and (exists? js/hljs)
                 (.getLanguage js/hljs "clojure"))
        (try
          (.-value (.highlight js/hljs s #js {:language       "clojure"
                                              :ignoreIllegals true}))
          (catch :default _ nil)))
      (escape-html s)))

(defn- error-boundary
  "React error boundary so a plot rendering error doesn't tear down the
   whole page. The boundary's state is per-instance; pairing it with a
   :key on the parent forces a fresh boundary each evaluation."
  [_child]
  (let [!err (r/atom nil)]
    (r/create-class
     {:display-name "PlotErrorBoundary"
      :component-did-catch
      (fn [_this err _info] (reset! !err err))
      :reagent-render
      (fn [child]
        (if-let [e @!err]
          [:div.err "Render error: "
           [:pre {:style {:font-size "0.7rem" :white-space "pre-wrap"
                          :margin    "0.25rem 0 0 0"}}
            (str e)]]
          child))})))

(defn- katex-block [tex]
  (let [!node   (atom nil)
        render! (fn []
                  (when-let [el @!node]
                    (when (exists? js/katex)
                      (.render js/katex tex el
                               #js {:throwOnError false :displayMode true}))))]
    (r/create-class
     {:component-did-mount  render!
      :component-did-update render!
      :reagent-render
      (fn [_]
        [:div.tex {:ref #(reset! !node %)}])})))

(defn- mount-cm! [el]
  (when (and el (nil? @!view) (exists? js/CM))
    (let [eval-cmd #js {:key "Mod-Enter" :run (fn [_] (eval!) true)}
          ;; Wrap our Cmd-Enter binding in Prec.highest so vim mode (or any
          ;; other keymap) can't shadow it.
          user-keymap (cond->> (.of js/CM.keymap #js [eval-cmd])
                        js/CM.Prec (.highest js/CM.Prec))
          ;; Persist edits to the current page on every doc change.
          save-listener (.of (.. js/CM -EditorView -updateListener)
                             (fn [update]
                               (when (.-docChanged update)
                                 (update-current-source!
                                  (.. update -state -doc toString)))))
          ;; Compose extensions ourselves; skip anything the ESM didn't deliver.
          ;; Vim is conditionally prepended below so its keymap goes first.
          exts (cond-> [user-keymap save-listener]
                 js/CM.lineNumbers         (conj (js/CM.lineNumbers))
                 js/CM.history             (conj (js/CM.history))
                 js/CM.drawSelection       (conj (js/CM.drawSelection))
                 js/CM.highlightActiveLine (conj (js/CM.highlightActiveLine))
                 js/CM.bracketMatching     (conj (js/CM.bracketMatching))
                 (and js/CM.syntaxHighlighting js/CM.defaultHighlightStyle)
                 (conj (js/CM.syntaxHighlighting
                        js/CM.defaultHighlightStyle))
                 js/CM.defaultExtensions   (conj js/CM.defaultExtensions)
                 js/CM.defaultKeymap       (conj (.of js/CM.keymap
                                                      js/CM.defaultKeymap))
                 js/CM.historyKeymap       (conj (.of js/CM.keymap
                                                      js/CM.historyKeymap))
                 js/CM.completeKeymap      (conj (.of js/CM.keymap
                                                      js/CM.completeKeymap)))
          exts (cond->> exts
                 js/CM.vim (into [(js/CM.vim)]))
          state (.create js/CM.EditorState
                         #js {:doc        (current-page-source)
                              :extensions (clj->js exts)})
          view  (js/CM.EditorView. #js {:parent el :state state})]
      (reset! !view view))))

(defn- cm-editor []
  (r/create-class
   {:component-will-unmount (fn [_]
                              (when-let [v @!view] (.destroy v))
                              (reset! !view nil))
    :reagent-render
    (fn [_] [:div.cm-host {:ref mount-cm!}])}))

;; --- SICM → Emmy translator shelf ------------------------------------------

(defonce !shelf (r/atom {:open? false :input "" :output ""}))

(defn- translate-scheme [src]
  (try
    (if (exists? js/SicmToEmmy)
      (.translate js/SicmToEmmy src)
      ";; SicmToEmmy not loaded — check sicm2emmy.js script tag.")
    (catch :default e
      (str ";; Translation error: " (or (.-message e) (str e))))))

(defn- on-shelf-input [src]
  (swap! !shelf assoc :input src :output (translate-scheme src)))

(defn- insert-at-cursor! []
  (when-let [view @!view]
    (let [output (:output @!shelf)]
      (when-not (clojure.string/blank? output)
        (let [sel  (.. view -state -selection -main)
              from (.-from sel)
              to   (.-to sel)]
          (.dispatch view #js {:changes #js {:from from :to to :insert output}})
          (.focus view)
          (swap! !shelf assoc :open? false))))))

(defn- shelf []
  (let [{:keys [open? input output]} @!shelf]
    (when open?
      [:div.shelf
       [:div.shelf-header
        [:span.shelf-title "SICM (Scheme) → Emmy (Clojure)"]
        [:button.shelf-close
         {:on-click #(swap! !shelf assoc :open? false)
          :title    "Close"} "×"]]
       [:div.shelf-body
        [:div.shelf-pane
         [:div.shelf-sublabel "Paste Scheme"]
         [:textarea.shelf-textarea
          {:value       input
           :spell-check false
           :placeholder ";; Paste SICM / scmutils Scheme here…"
           :on-change   #(on-shelf-input (.. % -target -value))}]]
        [:div.shelf-pane
         [:div.shelf-sublabel "Translated Clojure"]
         [:textarea.shelf-textarea
          {:value     output
           :read-only true
           :spell-check false}]]]
       [:div.shelf-toolbar
        [:button {:on-click insert-at-cursor!
                  :disabled (clojure.string/blank? output)}
         "Insert at cursor"]
        [:span.hint
         "Drops the translated text where the editor caret is."]]])))

(defn- pages-bar []
  (let [{:keys [pages current]} @!pages]
    [:div.pages
     (for [name (sort (keys pages))]
       ^{:key name}
       [:span.page {:class (when (= name current) "active")}
        [:span.page-name {:on-click #(switch-page! name)} name]
        (when (> (count pages) 1)
          [:span.page-x {:on-click #(delete-page! name)
                         :title    (str "Delete " name)} "×"])])
     [:button.page-add {:on-click new-page! :title "New page"} "+"]]))

(defn- hiccup?
  "Heuristic: a vector whose first element is a keyword (:div etc.) or a
   function (a Reagent component reference). Symbols are deliberately
   excluded — quoted forms returned by emmy-viewers helpers shouldn't
   accidentally render as hiccup."
  [v]
  (and (vector? v)
       (pos? (count v))
       (let [h (first v)]
         (or (keyword? h) (fn? h)))))

(defn- result-row [{:keys [form value pr tex err]}]
  [:div.result-row
   [:pre.form-snippet
    {:dangerouslySetInnerHTML #js {:__html (highlight-clojure form)}}]
   (cond
     err          [:div.err err]
     (hiccup? value) [:div.viz [error-boundary value]]
     :else
     [:<>
      (when tex [katex-block ^String tex])
      [:div.pr
       {:dangerouslySetInnerHTML #js {:__html (highlight-clojure pr)}}]])])

(defn- result-pane []
  (let [{:keys [status results eval-id]} @!result]
    [:div.result
     (case status
       :idle [:span "Press " [:kbd "Cmd-Enter"] " or " [:kbd "Ctrl-Enter"]
              " to evaluate."]
       :ok   (if (empty? results)
               [:span {:style {:color "#57606a"}} "(no forms)"]
               [:<>
                ;; Prefix the key with eval-id so each evaluation forces a
                ;; fresh remount — stateful viz components (Leva-driven
                ;; plot-with-params, etc.) get rebuilt cleanly so the new
                ;; code takes effect.
                (for [[i r] (map-indexed vector results)]
                  ^{:key (str eval-id "-" i)} [result-row r])]))]))

(defn- app []
  [:<>
   [:header
    [:h1 "Emmy + SCI · SICM playground"]
    [:span.hint "Cmd-Enter / Ctrl-Enter to evaluate"]]
   [:div.panes
    [:div.pane
     [:div.label "Code"]
     [pages-bar]
     [cm-editor]
     [shelf]
     [:div.toolbar
      [:button {:on-click eval!} "Evaluate"]
      [:button.shelf-toggle
       {:class    (when (:open? @!shelf) "active")
        :on-click #(swap! !shelf update :open? not)
        :title    "Toggle SICM → Emmy translator"}
       "SICM → Emmy"]]]
    [:div.pane
     [:div.label "Result"]
     [result-pane]]]])

;; Wait for the ESM-loaded CodeMirror modules before mounting.
(.then js/window.cm_ready
       (fn [_]
         (rdom/render [app] (.getElementById js/document "app")))
       (fn [err]
         (js/console.error "CodeMirror failed to load" err)
         (set! (.. (.getElementById js/document "app") -innerHTML)
               "Failed to load CodeMirror — check console.")))
