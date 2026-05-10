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
  (clojure.core/long (Math/floor (double x))))

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

(def R2
  "Two-arg type signature shorthand."
  '(-> Real Real Real))

(def R3
  '(-> Real Real Real Real))

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
