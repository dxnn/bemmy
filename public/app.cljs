(ns playground
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [scittle.core]))

(def initial-source
  ";; SICM playground — Emmy in the browser via SCI.
;; Cmd-Enter (or Ctrl-Enter) to evaluate.

(require '[emmy.env :as e :refer :all])

;; symbolic derivative of x²
((D (fn [x] (square x))) 'x)
")

(defonce !source (r/atom initial-source))
(defonce !result (r/atom {:status :idle}))

(defn- eval-with-tex
  "Evaluate user source in SCI; return {:value v :tex t} where t is the TeX
   form of v (or nil if Emmy can't render it). Wrapped so user source runs
   exactly once."
  [src]
  (let [wrapped (str "(let [v# (do " src ")]\n"
                     "  [v# (try (emmy.expression.render/->TeX v#)\n"
                     "           (catch :default _ nil))])")
        [v tex] (scittle.core/eval-string wrapped)]
    {:value v :tex tex}))

(defn eval! []
  (try
    (let [{:keys [value tex]} (eval-with-tex @!source)]
      (reset! !result {:status :ok :pr (pr-str value) :tex tex}))
    (catch :default e
      (reset! !result {:status :err :err (or (.-message e) (str e))}))))

(defn- katex-block [tex]
  (let [!node (atom nil)
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

(defn- editor []
  [:textarea.editor
   {:value         @!source
    :spell-check   false
    :auto-complete "off"
    :on-change     #(reset! !source (.. % -target -value))
    :on-key-down   (fn [e]
                     (when (and (or (.-metaKey e) (.-ctrlKey e))
                                (= "Enter" (.-key e)))
                       (.preventDefault e)
                       (eval!)))}])

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
     [editor]
     [:div.toolbar
      [:button {:on-click eval!} "Evaluate"]]]
    [:div.pane
     [:div.label "Result"]
     [result-pane]]]])

(rdom/render [app] (.getElementById js/document "app"))
