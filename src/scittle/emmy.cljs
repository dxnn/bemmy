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
             ;; make-path used inside parametric-path-action). Required
             ;; without :refer here so the names land via intern below;
             ;; that lets pages legitimately redefine book names like
             ;; L-harmonic / L-free-particle without SCI's analyzer
             ;; rejecting the def with 'already refers to …'.
             'emmy.mechanics.lagrange
             'emmy.mechanics.hamilton
             'emmy.mechanics.rotation
             'emmy.mechanics.rigid
             'emmy.mechanics.noether
             '[mafs.core :as mafs]
             '[mafs.coordinates]
             '[mafs.plot]
             '[mafs.line]
             '[mathbox.core :as mathbox]
             '[mathbox.primitives :as mb])
    ;; Mirror sicm.compat/intern-missing! on the JVM side: pull each
    ;; mechanics submodule's publics into the user ns as locally-interned
    ;; vars, skipping any name already mapped (emmy.env wins). Using
    ;; intern instead of :refer means subsequent (def X …) for the same
    ;; name is a plain re-intern, which SCI allows; a :refer would make
    ;; the def collide with the referred binding.
    (doseq [m '[emmy.mechanics.lagrange
                emmy.mechanics.hamilton
                emmy.mechanics.rotation
                emmy.mechanics.rigid
                emmy.mechanics.noether]]
      (doseq [[s v] (ns-publics m)]
        (when-not (ns-resolve *ns* s)
          (intern *ns* s (deref v)))))")
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
  ;;
  ;; Deferred via setTimeout 500: when run inline, our ns-unmap of
  ;; names in emmy.env's SCI exposure (R2, R3, evolve, state-advancer,
  ;; …) takes effect briefly — a probe inside this eval-string
  ;; confirms (ns-resolve *ns* 'evolve) returns nil right after the
  ;; unmap — but by the time the page is interactive, the names are
  ;; back to #'emmy.env/*. Something in scittle's post-init pipeline
  ;; (likely emmy.sci/config running once the plugin registry settles)
  ;; re-applies emmy.env :refer :all on user, clobbering our local
  ;; interns. setTimeout 0 isn't enough to outlast it; 500ms reliably
  ;; lands the shim after the post-init refer-replay. Names that
  ;; aren't in emmy.env's SCI exposure (L-pend, make-quaternion, …)
  ;; would work inline too, but bundling them in the same deferred
  ;; block keeps the shim coherent and easy to reason about.
  (js/setTimeout
   #(scittle/eval-string
   "(require '[emmy.generic :as g]
             '[emmy.matrix :as matrix]
             '[emmy.mechanics.hamilton :as ham]
             '[emmy.mechanics.lagrange :as lag]
             '[emmy.quaternion :as quat])

    ;; Several names below shadow `:refer`'d bindings from emmy.env
    ;; (R / R2 / R3 type-signature shorthands, evolve / state-advancer
    ;; SICM-shape wrappers, etc.). SCI's analyzer hard-throws on
    ;; `(def X …)` when X is currently referred from another ns, so
    ;; clear the slate first. ns-unmap is idempotent on names that
    ;; aren't currently mapped, so the unconditional sweep is safe.
    ;;
    ;; The sweep alone isn't sufficient for *every* name in practice —
    ;; for `R2`, `R3`, `evolve`, `state-advancer` the subsequent
    ;; `(def …)` form still resolves the name back to its emmy.env
    ;; refer during this plugin-init eval-string (probably because the
    ;; analyzer captures the binding before the unmap is observed).
    ;; The defs for those four names below use `intern` directly
    ;; (which bypasses the def-collision check entirely) instead of
    ;; `def`. The others use plain `def` and pick up the unmap fine.
    (doseq [s '[H-central-polar
                make-quaternion quaternion->vector quaternion->3vector
                quaternion->rotation-matrix rotation-matrix->quaternion
                quaternion-ref quaternion->real-part q:r q:i q:j q:k
                vector-length euclidean-norm
                R R2 R3 r
                periodic-drive L-pend L-periodically-driven-pendulum
                evolve state-advancer]]
      (ns-unmap *ns* s))

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
    (intern *ns* 'R2 '(-> Real Real Real))
    (intern *ns* 'R3 '(-> Real Real Real Real))
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
        (L-pend m l g ys)))

    ;; SICM-compatible ODE integrator wrappers. scmutils' integrator
    ;; takes (state monitor dt t-final tol); Emmy's takes (state dt t
    ;; {:observe … :epsilon …}). Adapt by arg count + map shape, and
    ;; call SICM-style monitors with just the state (t lives inside).
    (require '[emmy.numerical.ode :as ode])

    (defn- sicm-observe [monitor]
      (when monitor (fn [_t state] (monitor state))))

    (defn- adapt-emmy-integrator [emmy-int]
      (fn ([initial-state dt t-final]
           (emmy-int initial-state dt t-final))
        ([initial-state dt t-final opts-or-monitor]
           (if (map? opts-or-monitor)
             (emmy-int initial-state dt t-final opts-or-monitor)
             (emmy-int initial-state dt t-final
                       {:observe (sicm-observe opts-or-monitor)})))
        ([initial-state monitor dt t-final tol]
           (emmy-int initial-state dt t-final
                     {:observe (sicm-observe monitor)
                      :epsilon tol}))))

    ;; evolve / state-advancer use `intern` instead of `defn` —
    ;; SCI's def-collision check otherwise leaves them pointing at
    ;; emmy.env/evolve and emmy.env/state-advancer (which only accept
    ;; 3-arg call shapes) instead of these SICM-shape wrappers (which
    ;; accept the 5-arg `(state monitor dt t-final tol)` shape SICM
    ;; pages use).
    (intern *ns* 'evolve
      (fn [state-derivative & state-derivative-args]
        (adapt-emmy-integrator
          (apply ode/evolve state-derivative state-derivative-args))))

    (intern *ns* 'state-advancer
      (fn [state-derivative & state-derivative-args]
        (let [emmy-adv (apply ode/state-advancer
                              state-derivative state-derivative-args)]
          (fn ([initial-state t]
               (emmy-adv initial-state t))
            ([initial-state t opts-or-tol]
               (if (map? opts-or-tol)
                 (emmy-adv initial-state t opts-or-tol)
                 (emmy-adv initial-state t {:epsilon opts-or-tol})))))))

    ;; --- scmutils built-ins SICM pages reach for that Emmy doesn't
    ;; --- ship. Mirrors the JVM-side `test/sicm/compat.clj`. Stubs
    ;; --- return innocuous placeholders where there's no Emmy
    ;; --- equivalent — enough to keep the surrounding page text
    ;; --- evaluating instead of bailing out on a free symbol.

    ;; scmutils picker for the ODE integrator method. Emmy uses a
    ;; fixed strategy, so this is a no-op.
    (defn set-ode-integration-method! [& _] nil)

    ;; SICM's integer floor. Symbolic Emmy expressions don't reduce
    ;; to a number, so simplify first and only floor when the result
    ;; is genuinely numeric.
    (defn floor->exact [x]
      (let [s (try (simplify x) (catch :default _ x))]
        (if (number? s) (long (Math/floor (double s))) s)))

    ;; SICM canonical time-evolution operator `C*`. Used in
    ;; §6.2-style constructions like `(((C* alpha omega) dt) state0)`.
    ;; Stub returns the identity flow so chained defns evaluate
    ;; without crashing.
    (defn C* [& _] (fn [_dt] (fn [state] state)))

    ;; scmutils single-arg predicate / expression wrappers. Stubs.
    (defn predicate-1 [pred] (fn [x] (pred x)))
    (defn expression-1 [expr] expr)
    (defn default-collector [& _] nil)
    (defn write-line [& args] (apply println args))

    ;; scmutils `make-operator` builds an Operator wrapper around a
    ;; function. For most SICM page uses we can pretend it's the
    ;; function itself.
    (defn make-operator [f & _] f)

    ;; scmutils `bisect` root finder. Stub: midpoint.
    (defn bisect
      ([_f a b] (/ (+ (double a) (double b)) 2.0))
      ([_f a b _tol] (/ (+ (double a) (double b)) 2.0)))

    ;; scmutils 2D-point plot-data accessors. Stubs.
    (defn abscissa [pt] (if (sequential? pt) (first pt) pt))
    (defn ordinate [pt] (if (and (sequential? pt) (next pt)) (second pt) pt))
    (defn make-point [x y] [x y])

    ;; scmutils interactive surface-of-section explorer drives a
    ;; `frame` window with iterates of `the-map`. JVM tests don't
    ;; render; return a sentinel so subsequent forms don't error.
    (defn explore-map [_window _the-map _n] :graphics)

    ;; scmutils numerical-method bookkeeping. SICM exercises read this
    ;; bare; expose it as a normal var (SCI doesn't need ^:dynamic
    ;; metadata unless someone tries to `binding` it).
    (def *machine-epsilon* 2.220446049250313e-16)

    ;; scmutils close-enuf? — interval equality. Mirrors the JVM
    ;; stub in test/sicm/compat.clj.
    (defn close-enuf?
      ([h1 h2] (close-enuf? h1 h2 1e-15 10))
      ([h1 h2 tolerance] (close-enuf? h1 h2 tolerance 10))
      ([h1 h2 tolerance scale]
       (<= (abs (- (double h1) (double h2)))
           (* tolerance
              (+ (* scale (abs (double h1)))
                 (* scale (abs (double h2)))
                 1.0)))))

    ;; scmutils matrix accessors. Emmy ships row / column in
    ;; emmy.matrix; expose them bare so SICM page text resolves.
    ;; `matrix` alias is already bound at the top of this eval-string.
    (def row    matrix/row)
    (def column matrix/column)
    (defn m:submatrix [m & _] m)
    (def m:num-rows matrix/num-rows)
    (def m:num-cols matrix/num-cols)")
   500))
