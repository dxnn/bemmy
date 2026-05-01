(ns scittle.emmy
  {:no-doc true}
  (:require [emmy.mafs]
            [emmy.sci]
            [emmy.viewer]
            [emmy.viewer.compile]
            [emmy.viewer.physics]
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
    {'emmy.mafs            (sci/copy-ns emmy.mafs            (sci/create-ns 'emmy.mafs))
     'emmy.viewer          (sci/copy-ns emmy.viewer          (sci/create-ns 'emmy.viewer))
     'emmy.viewer.compile  (sci/copy-ns emmy.viewer.compile  (sci/create-ns 'emmy.viewer.compile))
     'emmy.viewer.physics  (sci/copy-ns emmy.viewer.physics  (sci/create-ns 'emmy.viewer.physics))}}))

(defn init []
  ;; Emmy itself.
  (scittle/register-plugin! ::emmy emmy.sci/config)
  ;; Mafs (registers mafs.core, mafs.plot, etc. + js/Math).
  (mafs.sci/install!)
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

    (defn show [win]
      (let [{:keys [viewBox drawables]} (deref win)]
        (into [mafs.core/Mafs {:viewBox viewBox}
               [mafs.coordinates/Cartesian]]
              drawables)))

    ;; Inline the frame test rather than naming it — emmy.env already
    ;; exports a `frame?` (manifold reference-frame predicate), and a
    ;; defn here collides at SCI analysis time, aborting the rest of
    ;; this eval-string.
    (defn maybe-show [v]
      (if (and (some? v)
               (try (and (instance? cljs.core/Atom v)
                         (let [m (deref v)]
                           (and (map? m) (vector? (:drawables m)))))
                    (catch :default _ false)))
        (show v)
        v))"))
