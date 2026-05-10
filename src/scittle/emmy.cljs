(ns scittle.emmy
  {:no-doc true}
  (:require [emmy.leva]
            [emmy.mafs]
            [emmy.mathbox]
            [emmy.mathbox.plot]
            [emmy.sci]
            [emmy.viewer]
            [emmy.viewer.compile]
            [emmy.viewer.physics]
            [leva.sci]
            [mafs.sci]
            [mathbox.primitives]
            [mathbox.sci]
            [sci.core :as sci]
            [sci.ctx-store]
            [scittle.core :as scittle]))

;; emmy-viewers' high-level helpers (emmy.mafs/of-x etc.) and the small
;; emmy.viewer utility namespace need to live in SCI, but emmy-viewers ships
;; only an aggregate install! that drags in mathbox/jsxgraph/leva/mathlive.
;; We re-implement the 2D subset by hand, mirroring the (sci/copy-ns ...)
;; pattern from emmy.viewer.sci.
(defn- install-emmy-viewers! []
  (sci.ctx-store/swap-ctx!
   sci/merge-opts
   {:namespaces
    {'emmy.leva            (sci/copy-ns emmy.leva            (sci/create-ns 'emmy.leva))
     'emmy.mafs            (sci/copy-ns emmy.mafs            (sci/create-ns 'emmy.mafs))
     'emmy.mathbox         (sci/copy-ns emmy.mathbox         (sci/create-ns 'emmy.mathbox))
     'emmy.mathbox.plot    (sci/copy-ns emmy.mathbox.plot    (sci/create-ns 'emmy.mathbox.plot))
     'emmy.viewer          (sci/copy-ns emmy.viewer          (sci/create-ns 'emmy.viewer))
     'emmy.viewer.compile  (sci/copy-ns emmy.viewer.compile  (sci/create-ns 'emmy.viewer.compile))
     'emmy.viewer.physics  (sci/copy-ns emmy.viewer.physics  (sci/create-ns 'emmy.viewer.physics))
     'mathbox.primitives   (sci/copy-ns mathbox.primitives   (sci/create-ns 'mathbox.primitives))}}))

(defn init []
  ;; Emmy itself.
  (scittle/register-plugin! ::emmy emmy.sci/config)
  ;; Mafs (registers mafs.core, mafs.plot, etc. + js/Math).
  (mafs.sci/install!)
  ;; Leva — slider/input panel UI.
  (leva.sci/install!)
  ;; MathBox — 3D math viz (Three.js underneath).
  (mathbox.sci/install!)
  ;; emmy-viewers high-level helpers (mafs + mathbox + viewer; we still
  ;; skip jsxgraph / mathlive / Clerk-specific bits).
  (install-emmy-viewers!)
  ;; Pre-refer / pre-alias common namespaces so user snippets are short.
  ;; `mafs` aliases mafs.core (the low-level Reagent components) per the
  ;; Mentat Collective convention. emmy-viewers' high-level helpers stay
  ;; reachable via the full `emmy.mafs/...` namespace.
  (scittle/eval-string
   "(require '[emmy.env :refer :all]
             ;; SICM-book names not pre-referred into emmy.env (e.g.
             ;; make-path used inside parametric-path-action). Pulling
             ;; the entire mechanics.* surface keeps SICM-style code
             ;; working without per-page qualification.
             '[emmy.mechanics.lagrange :refer :all]
             '[emmy.mechanics.hamilton :refer :all]
             '[emmy.mechanics.rotation :refer :all]
             '[emmy.mechanics.rigid :refer :all]
             '[emmy.mechanics.noether :refer :all]
             '[mafs.core :as mafs]
             '[mafs.coordinates]
             '[mafs.plot]
             '[mafs.line]
             '[mathbox.core :as mathbox]
             '[mathbox.primitives :as mb])")
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
      ;; reagent atom from elapsed-since-mount; inner derefs it and
      ;; rebuilds the OfX y-fn so the curve morphs over time. The timer
      ;; uses cljs.core arithmetic explicitly — emmy.env's :refer :all
      ;; shadows +, -, /, *, and emmy's / on integers preserves rationals
      ;; that eventually overflow into BigInt, breaking Math/sin et al.
      (let [!t     (reagent.core/atom 0)
            !start (atom nil)
            timer  (atom nil)]
        (reagent.core/create-class
          {:component-did-mount
           (fn [_]
             (reset! !start (.now js/Date))
             (reset! timer
               (js/setInterval
                 (fn []
                   (let [elapsed (cljs.core//
                                  (cljs.core/- (.now js/Date)
                                               (deref !start))
                                  1000)]
                     (reset! !t (cljs.core/* speed elapsed))))
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
          v)))")
  ;; --- SICM-compat shims ----------------------------------------------
  ;; Mirrors test/sicm/compat.clj (the JVM-side equivalence-test shim).
  ;; Surfaces SICM-book names that ship inside Emmy sub-namespaces but
  ;; aren't pre-referred into emmy.env, plus a 2-arg transpose dispatch
  ;; the canonical-transform machinery needs.
  (scittle/eval-string
   "(require '[emmy.generic :as g]
             '[emmy.matrix :as matrix]
             '[emmy.mechanics.hamilton :as ham]
             '[emmy.mechanics.lagrange :as lag]
             '[emmy.quaternion :as quat])

    ;; H-central-polar lives in emmy.mechanics.hamilton; expose it as a
    ;; bare name in user so SICM ch-3 pages don't have to qualify.
    (def H-central-polar ham/H-central-polar)

    ;; SICM's qp-submatrix / symplectic-transform? reaches g/transpose
    ;; with two structure args (the Jacobian-like matrix + the state
    ;; tuple it was evaluated at), but Emmy only registers the 1-arg
    ;; dispatch. Forward to emmy.matrix's structure-aware s:transpose.
    (defmethod g/transpose [:emmy.structure/down :emmy.structure/up] [ms rs]
      (matrix/s:transpose ms rs))
    (defmethod g/transpose [:emmy.structure/up :emmy.structure/down] [ms rs]
      (matrix/s:transpose ms rs))

    ;; --- scmutils ↔ Emmy name aliases ---
    ;; SICM book uses these names; Emmy ships them under different
    ;; ones. Aliasing here keeps SICM page text running unmodified.

    (def make-quaternion             quat/make)
    (def quaternion->vector          quat/->vector)
    (def quaternion->3vector         (fn [q] (rest (quat/->vector q))))
    (def quaternion->rotation-matrix quat/->rotation-matrix)
    (def rotation-matrix->quaternion quat/from-rotation-matrix)
    (def quaternion-ref              (fn [q i] (nth (quat/->vector q) i)))
    (def quaternion->real-part       quat/get-r)
    (def q:r                         quat/get-r)
    (def q:i                         quat/get-i)
    (def q:j                         quat/get-j)
    (def q:k                         quat/get-k)

    ;; SICM-book mathematical built-ins not in emmy.env.
    (defn vector-length [v]
      (sqrt (apply + (map #(* % %) (seq v)))))
    (def euclidean-norm vector-length)

    ;; Type-signature shorthand (literal-function 'x R/R2/R3) and the
    ;; lowercase form some corpus snippets carry.
    (def R  '(-> Real Real))
    (def R2 '(-> Real Real Real))
    (def R3 '(-> Real Real Real Real))
    (def r  '(-> Real Real))

    ;; SICM's L-pend takes a y-position-of-pivot fn; emmy's L-pendulum
    ;; doesn't. Implement the SICM form so chapter-3 derived examples
    ;; (L-periodically-driven-pendulum) work.
    (defn periodic-drive [amplitude frequency phase]
      (fn [t] (* amplitude (cos (+ (* frequency t) phase)))))
    (defn L-pend [m l g ys]
      (fn [local]
        (let [theta    (coordinate local)
              thetadot (velocity local)
              vys      (D ys)
              t        (lag/state->t local)]
          (+ (* 1/2 m
                (+ (square (* l thetadot))
                   (square (vys t))
                   (* 2 (vys t) l thetadot (sin theta))))
             (* m g (- (* l (cos theta)) (ys t)))))))
    (defn L-periodically-driven-pendulum [m l g A omega]
      (let [ys (periodic-drive A omega 0)]
        (L-pend m l g ys)))"))
