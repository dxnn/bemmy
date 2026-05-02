(ns scittle.emmy
  {:no-doc true}
  (:require [emmy.leva]
            [emmy.mafs]
            [emmy.sci]
            [emmy.viewer]
            [emmy.viewer.compile]
            [emmy.viewer.physics]
            [leva.sci]
            [mafs.sci]
            [sci.core :as sci]
            [sci.ctx-store]
            [scittle.core :as scittle]))

;; emmy-viewers' high-level helpers (emmy.mafs/of-x etc.) and the small
;; emmy.viewer utility namespace need to live in SCI, but emmy-viewers ships
;; only an aggregate install! that drags in mathbox/jsxgraph/leva/mathlive.
;; We re-implement the 2D subset by hand, mirroring the (sci/copy-ns ...)
;; pattern from emmy.viewer.sci.
(defn- install-emmy-viewers-2d! []
  (sci.ctx-store/swap-ctx!
   sci/merge-opts
   {:namespaces
    {'emmy.leva            (sci/copy-ns emmy.leva            (sci/create-ns 'emmy.leva))
     'emmy.mafs            (sci/copy-ns emmy.mafs            (sci/create-ns 'emmy.mafs))
     'emmy.viewer          (sci/copy-ns emmy.viewer          (sci/create-ns 'emmy.viewer))
     'emmy.viewer.compile  (sci/copy-ns emmy.viewer.compile  (sci/create-ns 'emmy.viewer.compile))
     'emmy.viewer.physics  (sci/copy-ns emmy.viewer.physics  (sci/create-ns 'emmy.viewer.physics))}}))

(defn init []
  ;; Emmy itself.
  (scittle/register-plugin! ::emmy emmy.sci/config)
  ;; Mafs (registers mafs.core, mafs.plot, etc. + js/Math).
  (mafs.sci/install!)
  ;; Leva — slider/input panel UI.
  (leva.sci/install!)
  ;; emmy-viewers 2D high-level helpers (skips the 3D/interactive/UI/Clerk bits).
  (install-emmy-viewers-2d!)
  ;; Pre-refer / pre-alias common namespaces so user snippets are short.
  ;; `mafs` aliases mafs.core (the low-level Reagent components) per the
  ;; Mentat Collective convention. emmy-viewers' high-level helpers stay
  ;; reachable via the full `emmy.mafs/...` namespace.
  (scittle/eval-string
   "(require '[emmy.env :refer :all]
             '[mafs.core :as mafs]
             '[mafs.coordinates]
             '[mafs.plot]
             '[mafs.line])")
  ;; SICM-book imperative graphics shim. The book's `frame`,
  ;; `graphics-clear`, `plot-function` etc. mutate a window object; we
  ;; back that with a Reagent-friendly atom and add a `show` that turns
  ;; current state into Mafs hiccup. Auto-show wraps eval so the user
  ;; doesn't need to remember to call (show win) at the end.
  (scittle/eval-string
   "(defn frame [x-min x-max y-min y-max]
      (atom {:viewBox   {:x [(double x-min) (double x-max)]
                         :y [(double y-min) (double y-max)]}
             :drawables []}))

    (defn graphics-clear [win]
      (swap! win assoc :drawables [])
      win)

    (defn plot-function [win f t-min t-max & _step]
      (swap! win update :drawables conj
             [mafs.plot/OfX
              {:y      (fn [x] (double (f x)))
               :domain [(double t-min) (double t-max)]}])
      win)

    (defn plot-point [win [x y]]
      (swap! win update :drawables conj
             [mafs.core/Point {:x (double x) :y (double y)}])
      win)

    (defn plot-path
      \"Clear win, plot path (any IFn-like — Clojure fn, Emmy polynomial,
       etc.) from t0..t1 in one go, return win. The auto-show in the
       playground displays the result.\"
      ([win path t0 t1]
       (plot-path win path t0 t1 (/ (- t1 t0) 100)))
      ([win path t0 t1 step]
       (graphics-clear win)
       (plot-function win path t0 t1 step)
       win))

    (defn show [win]
      (let [{:keys [viewBox drawables]} (deref win)]
        (into [mafs.core/Mafs {:viewBox viewBox}
               [mafs.coordinates/Cartesian]]
              drawables)))

    (defn plot
      \"Render y = f(x) inline. f is anything callable — a Clojure fn, an
       Emmy polynomial, a path returned by find-path, etc. — that returns
       a number for numeric x. Domain defaults to [-5, 5]; y-range to
       [-5, 5]. Override either by passing extra args:

         (plot Math/sin)
         (plot Math/sin [(- Math/PI) Math/PI])
         (plot (fn [x] (* x x x)) [-3 3] [-10 10])

       For a symbolic Emmy expression with free symbols, simplify or
       substitute numeric values for the parameters first — the plot
       needs concrete numbers per x-sample.\"
      ([f] (plot f [-5 5] [-5 5]))
      ([f x-range] (plot f x-range [-5 5]))
      ([f [x-min x-max] [y-min y-max]]
       [mafs.core/Mafs {:viewBox {:x [(double x-min) (double x-max)]
                                  :y [(double y-min) (double y-max)]}}
        [mafs.coordinates/Cartesian]
        [mafs.plot/OfX {:y      (fn [x] (double (f x)))
                        :domain [(double x-min) (double x-max)]}]]))

    (defn ^:private animate-impl [f [x-min x-max] [y-min y-max] speed]
      ;; Form-2 component: outer mounts a 60Hz timer that advances a
      ;; reagent atom; inner derefs it and rebuilds the OfX y-fn so the
      ;; curve morphs over time. Cleans up the timer on unmount.
      (let [!t    (reagent.core/atom 0)
            !last (atom (.now js/Date))
            timer (atom nil)]
        (reagent.core/create-class
          {:component-did-mount
           (fn [_]
             (reset! timer
               (js/setInterval
                 (fn []
                   (let [now (.now js/Date)
                         dt  (/ (- now (deref !last)) 1000)]
                     (reset! !last now)
                     (swap! !t + (* speed dt))))
                 16)))
           :component-will-unmount
           (fn [_] (when (deref timer) (js/clearInterval (deref timer))))
           :reagent-render
           (fn [_]
             (let [t (deref !t)]
               [mafs.core/Mafs
                {:viewBox {:x [(double x-min) (double x-max)]
                           :y [(double y-min) (double y-max)]}}
                [mafs.coordinates/Cartesian]
                [mafs.plot/OfX
                 {:y      (fn [x] (double (f t x)))
                  :domain [(double x-min) (double x-max)]}]]))})))

    (defn animate
      \"Plot y = f(t, x) where t auto-advances. Returns hiccup.

         (animate (fn [t x] (Math/sin (- x t))))
         (animate (fn [t x] (Math/sin (- x t))) [(- Math/PI) Math/PI])
         (animate (fn [t x] (Math/sin (- x t))) [(- Math/PI) Math/PI] [-1 1])
         (animate (fn [t x] (Math/sin (- x t))) [-3 3] [-1 1] 2.0)  ; 2x speed

       The default speed is 1 (real-time seconds). The timer cleans
       up automatically when the result row remounts (e.g. on the
       next evaluation).\"
      ([f]                         (animate f [-5 5] [-5 5] 1.0))
      ([f x-range]                 (animate f x-range [-5 5] 1.0))
      ([f x-range y-range]         (animate f x-range y-range 1.0))
      ([f x-range y-range speed]
       [animate-impl f x-range y-range speed]))

    (defn ^:private plot-with-params-impl
      [f params-spec [x-min x-max] [y-min y-max]]
      ;; Form-2 component: outer fn runs once on mount and creates the
      ;; params atom; inner fn re-runs on each slider change. The
      ;; @!params MUST be dereferenced in the render scope (not deeper
      ;; in the OfX :y callback) so Reagent's reactive context tracks
      ;; the atom as a dependency.
      (let [defaults (into {} (map (fn [[k v]] [k (:value v)])) params-spec)
            !params  (reagent.core/atom defaults)]
        (fn []
          (let [params @!params]
            [:div {:style {:display \"flex\" :flex-direction \"column\" :gap \"0.5rem\"}}
             [leva.core/Controls {:atom !params :schema params-spec}]
             [mafs.core/Mafs {:viewBox {:x [(double x-min) (double x-max)]
                                        :y [(double y-min) (double y-max)]}}
              [mafs.coordinates/Cartesian]
              [mafs.plot/OfX {:y      (fn [x] (double (f params x)))
                              :domain [(double x-min) (double x-max)]}]]]))))

    (defn plot-with-params
      \"Render y = f(params, x) with a Leva control panel for params.

         (plot-with-params
           (fn [{:keys [m k]} t] (* m (Math/sin (* k t))))
           {:m {:value 1 :min 0 :max 5 :step 0.05}
            :k {:value 1 :min 0.1 :max 5 :step 0.05}}
           [0 (* 2 Math/PI)]
           [-5 5])

       params-spec is a map of param-key → Leva control config. Each
       :value seeds the initial slider value; :min, :max, :step shape
       the slider. f receives the current params map as its first arg
       and the x-sample as its second.\"
      ([f params-spec] (plot-with-params f params-spec [-5 5] [-5 5]))
      ([f params-spec x-range] (plot-with-params f params-spec x-range [-5 5]))
      ([f params-spec x-range y-range]
       [plot-with-params-impl f params-spec x-range y-range]))

    ;; Inline the frame test rather than naming it — emmy.env already
    ;; exports a `frame?` (manifold reference-frame predicate), and a
    ;; defn here collides at SCI analysis time, aborting the rest of
    ;; this eval-string. Use try/deref instead of (instance? Atom v)
    ;; because cljs.core/Atom isn't exposed as a SCI-resolvable symbol.
    (defn maybe-show [v]
      (let [m (try (deref v) (catch :default _ ::not-deref))]
        (if (and (not= m ::not-deref)
                 (map? m)
                 (vector? (:drawables m)))
          (show v)
          v)))"))
