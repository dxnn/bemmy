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
  (scittle/eval-string
   "(require '[emmy.env :refer :all]
             '[emmy.mafs :as mafs]
             '[emmy.viewer :as viewer])"))
