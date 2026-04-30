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

(defn- eval-with-tex [src]
  (let [src     (normalize-ws src)
        wrapped (str "(let [v# (do " src ")]\n"
                     "  [v# (try (emmy.expression.render/->TeX v#)\n"
                     "           (catch :default _ nil))])")
        [v tex] (js/scittle.core.eval_string wrapped)]
    {:value v :tex tex}))

(defn eval! []
  (when-let [src (current-source)]
    (try
      (let [{:keys [value tex]} (eval-with-tex src)]
        (reset! !result {:status :ok :pr (pr-str value) :tex tex}))
      (catch :default e
        (reset! !result {:status :err
                         :err    (or (.-message e) (str e))})))))

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

(defn- result-pane []
  (let [{:keys [status err pr tex]} @!result]
    [:div.result
     (case status
       :idle [:span "Press " [:kbd "Cmd-Enter"] " or " [:kbd "Ctrl-Enter"]
              " to evaluate."]
       :err  [:span.err err]
       :ok   [:<>
              (when tex [katex-block ^String tex])
              [:div {:style {:color "#57606a" :margin-top "0.5rem"}} pr]])]))

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
     [:div.toolbar
      [:button {:on-click eval!} "Evaluate"]]]
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
