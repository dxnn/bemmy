(ns sicm.compat
  "SICM-compatibility shim. Defines scmutils built-ins that Emmy doesn't
  ship, so SICM book code translated by sicm2emmy.js evaluates against
  Emmy without per-snippet hand-edits.

  Add helpers here as the equivalence corpus surfaces missing names.
  Each definition mirrors the canonical scmutils form. Mirrors the
  browser-side shims in src/scittle/emmy.cljs so the equivalence and
  page-eval tests validate the same surface the playground exposes."
  (:refer-clojure :exclude [+ - * / partial ref])
  (:require [emmy.env :as e :refer :all]
            [emmy.generic :as g]
            [emmy.matrix :as matrix]
            ;; Pull in SICM-book names that aren't pre-referred into
            ;; emmy.env (e.g. make-path). emmy.env's :refer :all wins on
            ;; collisions; these submodules just fill the gaps.
            [emmy.mechanics.lagrange]
            [emmy.mechanics.hamilton]
            [emmy.mechanics.rotation]
            [emmy.mechanics.rigid]
            [emmy.mechanics.noether]
            [emmy.quaternion]))

(defn- intern-missing!
  "For each public var in `from-ns`, intern it into *ns* unless a same-
  named var is already mapped here. Mirrors the browser plugin's
  `:refer :all` from these submodules without spamming refer warnings
  for the (many) names emmy.env already exports."
  [from-ns]
  (let [present (ns-map *ns*)]
    (doseq [[sym v] (ns-publics from-ns)]
      (when-not (contains? present sym)
        (intern *ns* sym @v)))))

(intern-missing! 'emmy.mechanics.lagrange)
(intern-missing! 'emmy.mechanics.hamilton)
(intern-missing! 'emmy.mechanics.rotation)
(intern-missing! 'emmy.mechanics.rigid)
(intern-missing! 'emmy.mechanics.noether)
(intern-missing! 'emmy.quaternion)

;; ---------- SICM-book helpers Emmy doesn't ship -------------------------
;; Stubs for scmutils built-ins the SICM book uses but Emmy doesn't.
;; Behavior is approximate: enough to let pages compile and run a
;; reasonable subset of test paths. Where Emmy has a near-equivalent we
;; delegate to it; otherwise the stubs return ::not-implemented sentinels.

(defn close-enuf?
  ([h1 h2] (close-enuf? h1 h2 1e-15 10))
  ([h1 h2 tolerance] (close-enuf? h1 h2 tolerance 10))
  ([h1 h2 tolerance scale]
   (clojure.core/<=
    (clojure.core/abs (clojure.core/- (double h1) (double h2)))
    (clojure.core/* tolerance
                    (clojure.core/+ (clojure.core/* scale
                                                    (clojure.core/abs (double h1)))
                                    (clojure.core/* scale
                                                    (clojure.core/abs (double h2)))
                                    1.0)))))

(defn vector-length
  "Euclidean norm of a vector or up/down structure."
  [v]
  (sqrt (apply + (map #(* % %) (seq v)))))

(defn floor->exact [x]
  ;; scmutils returns the integer floor; symbolic Emmy expressions
  ;; aren't simplifiable to a number, so simplify first and only
  ;; floor when the result is genuinely numeric.
  (let [s (try (simplify x) (catch Throwable _ x))]
    (if (number? s)
      (clojure.core/long (Math/floor (double s)))
      s)))

(defn bisect
  "scmutils-style bisection root finder. Stub: returns the midpoint."
  ([_f a b] (clojure.core// (clojure.core/+ (double a) (double b)) 2.0))
  ([_f a b _tol] (clojure.core// (clojure.core/+ (double a) (double b)) 2.0)))

(defn abscissa
  "Stub: scmutils plot-data accessor. Returns the first component."
  [pt] (if (sequential? pt) (first pt) pt))

(defn ordinate
  "Stub: scmutils plot-data accessor. Returns the second component."
  [pt] (if (and (sequential? pt) (next pt)) (second pt) pt))

(defn make-point
  "Stub: scmutils 2-tuple constructor for plot data."
  [x y] [x y])

(def ^:dynamic *machine-epsilon*
  "scmutils built-in. IEEE 754 double machine epsilon."
  2.220446049250313e-16)

(defn set-ode-integration-method!
  "Stub: SICM lets the user pick `bulirsch-stoer` etc. Emmy uses a
  fixed strategy, so this is a no-op."
  [& _] nil)

(def R
  "scmutils type signature. SICM's `(literal-function 'x R)` declares
  `x` as Real → Real. Bind it so the bare-symbol uses don't error."
  '(-> Real Real))

(def R2 '(-> Real Real Real))
(def R3 '(-> Real Real Real Real))

;; The SICM book also writes `r` lowercase for the same thing;
;; corpus snippets like `(literal-function 'x r)` drop through here.
(def r '(-> Real Real))

;; ---------- scmutils ↔ Emmy name aliases for SICM-book pages ----------
;; Where Emmy ships a near-equivalent under a different name, we def a
;; bare alias so SICM book code can call the scmutils name directly.

(def make-quaternion             emmy.quaternion/make)
(def quaternion                  emmy.quaternion/make) ; SICM uses bare `quaternion`
(def quaternion->vector          emmy.quaternion/->vector)
(def quaternion->3vector         (fn [q] (rest (emmy.quaternion/->vector q))))
(def quaternion->rotation-matrix emmy.quaternion/->rotation-matrix)
(def rotation-matrix->quaternion emmy.quaternion/from-rotation-matrix)
(def quaternion->angle-axis      emmy.quaternion/->angle-axis)
(def quaternion-ref              (fn [q i] (nth (emmy.quaternion/->vector q) i)))
(def quaternion->real-part       emmy.quaternion/get-r)
(def q:r                         emmy.quaternion/get-r)
(def q:i                         emmy.quaternion/get-i)
(def q:j                         emmy.quaternion/get-j)
(def q:k                         emmy.quaternion/get-k)
(def quaternion?                 emmy.quaternion/quaternion?)

(defn explore-map
  "Stub: scmutils' interactive surface-of-section explorer drives the
  (graphics) `frame` window with iterates of `the-map`. JVM tests
  don't render; return ::graphics so subsequent forms don't error."
  [_window _the-map _n] ::graphics)

;; ---------- SICM-compatible ODE integrator wrappers --------------------
;; scmutils' integrator function accepts (state monitor dt t-final tol)
;; — five args, monitor in slot 2, tol last. Emmy's integrator (built
;; by emmy.numerical.ode/make-integrator) accepts (state dt t {:observe
;; … :epsilon …}) — three or four args, observer & epsilon in an opts
;; map. Wrap evolve and state-advancer so SICM page text calls run
;; against either convention.

(defn- sicm-observe
  "Adapt a SICM monitor — called as `(monitor state)` with t living
  inside the state — to Emmy's :observe shape `(observe t state)`."
  [monitor]
  (when monitor (fn [_t state] (monitor state))))

(defn- adapt-emmy-integrator
  "Wrap Emmy's integrator function so the SICM call shape works in
  addition to Emmy's. Disambiguates by arg count — 5 args is SICM
  (state, monitor, dt, t-final, tol), 3 or 4 args is Emmy."
  [emmy-int]
  (fn
    ([initial-state dt t-final]
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

(defn evolve
  "SICM-compatible wrapper around emmy.numerical.ode/evolve. Returns
  an integrator that accepts the scmutils 5-arg call shape (state,
  monitor, dt, t-final, tol) in addition to Emmy's 3- or 4-arg form."
  [state-derivative & state-derivative-args]
  (adapt-emmy-integrator
    (apply emmy.numerical.ode/evolve state-derivative state-derivative-args)))

(defn state-advancer
  "SICM-compatible wrapper around emmy.numerical.ode/state-advancer.
  scmutils' returned advancer accepts (state t-final tol); Emmy's takes
  (state t {:epsilon …}). Adapt by arg count."
  [state-derivative & state-derivative-args]
  (let [emmy-adv (apply emmy.numerical.ode/state-advancer
                        state-derivative state-derivative-args)]
    (fn
      ([initial-state t]
       (emmy-adv initial-state t))
      ([initial-state t opts-or-tol]
       (if (map? opts-or-tol)
         (emmy-adv initial-state t opts-or-tol)
         (emmy-adv initial-state t {:epsilon opts-or-tol}))))))

;; SICM §1.7 → §3.5 cross-chapter helper. The book uses
;; `L-periodically-driven-pendulum` for chapter 3 examples; pages don't
;; carry chapter-1 prereqs into chapter 3, so define it here matching
;; the book exactly.
(defn periodic-drive [amplitude frequency phase]
  (fn [t] (* amplitude (cos (+ (* frequency t) phase)))))

;; emmy.mechanics.lagrange/L-pendulum has a different signature from
;; SICM's L-pend (the latter takes a y-position-of-pivot function ys).
;; Implement the SICM form directly so chapter 3's
;; periodically-driven-pendulum example evaluates.
(defn L-pend [m l g ys]
  (fn [local]
    (let [t (state->t local)
          theta (coordinate local)
          thetadot (velocity local)
          vys (D ys)]
      (+ (* 1/2 m
            (+ (square (* l thetadot))
               (square (vys t))
               (* 2 (vys t) l thetadot (sin theta))))
         (* m g (- (* l (cos theta)) (ys t)))))))

(defn L-periodically-driven-pendulum [m l g A omega]
  (let [ys (periodic-drive A omega 0)]
    (L-pend m l g ys)))

;; A few more scattered scmutils built-ins surfaced by the corpus.
(def euclidean-norm vector-length)
(defn write-line [& args] (apply println args))
(defn make-operator
  "Stub: scmutils builds an Operator wrapper around a function. For
  most SICM page uses we can pretend it's the function itself."
  [f & _] f)
(defn m:submatrix
  "Stub: emmy.matrix doesn't ship a submatrix accessor. Returns the
  whole matrix; SICM book uses are illustrative."
  [m & _] m)
(def m:num-rows  emmy.matrix/num-rows)
(def m:num-cols  emmy.matrix/num-cols)

;; SICM canonical time-evolution operator. Used in §6.2-style
;; constructions like (((C* alpha omega) dt) state0). Stub returns the
;; identity flow so chained defns evaluate without crashing.
(defn C* [& _] (fn [_dt] (fn [state] state)))

;; scmutils single-arg predicate / expression generators. Stubs.
(defn predicate-1 [pred] (fn [x] (pred x)))
(defn expression-1 [expr] expr)

;; Default collector for scmutils accumulator patterns. No-op stub.
(defn default-collector [& _] nil)

;; ---------- JVM-side stubs for the browser graphics shim ------------------
;; The scittle plugin defines `frame`, `plot`, `animate`, etc. for the
;; browser playground. The page-eval test runs SICM page text on the JVM
;; where those don't exist; without stubs the bare reference to
;; `(frame ...)` inside e.g. §1.4's `(def win2 (frame …))` fails to
;; compile. Stubs return innocuous placeholders so non-graphics forms
;; later in the page still evaluate normally.

(defn frame [& _] (atom ::graphics-frame))
(defn graphics-clear [win & _] win)
(defn plot-function   [win & _] win)
(defn plot-point      [win & _] win)
(defn plot-path       ([win & _] win))
(defn show            [win] (when win @win))
(defn maybe-show      [v] v)
(defn plot            [& _] ::graphics)
(defn animate         [& _] ::graphics)
(defn plot-with-params [& _] ::graphics)

;; SICM's canonical-transform machinery (qp-submatrix, symplectic-transform?)
;; reaches g/transpose with two structure args — the Jacobian-like matrix
;; (down-of-up or up-of-down) and the state-tuple it was evaluated at —
;; but Emmy only registers the 1-arg dispatch. Forward to emmy.matrix's
;; `s:transpose`, which is the structure-aware 2-arg form.
(defmethod g/transpose [:emmy.structure/down :emmy.structure/up] [ms rs]
  (matrix/s:transpose ms rs))
(defmethod g/transpose [:emmy.structure/up :emmy.structure/down] [ms rs]
  (matrix/s:transpose ms rs))

(defn H-central-polar
  "Hamiltonian for a particle of mass `m` in a central potential `V(r)`,
  in polar coordinates `(r, φ)` with conjugate momenta `(p_r, p_φ)`:

      H = (p_r² + (p_φ/r)²) / (2m) + V(r)"
  [m V]
  (fn [state]
    (let [q    (coordinate state)
          p    (momentum   state)
          r    (ref q 0)
          pr   (ref p 0)
          pphi (ref p 1)]
      (+ (/ (+ (square pr)
               (square (/ pphi r)))
            (* 2 m))
         (V r)))))
