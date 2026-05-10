(ns sicm.compat
  "SICM-compatibility shim. Defines scmutils built-ins that Emmy doesn't
  ship, so SICM book code translated by sicm2emmy.js evaluates against
  Emmy without per-snippet hand-edits.

  Add helpers here as the equivalence corpus surfaces missing names.
  Each definition mirrors the canonical scmutils form."
  (:refer-clojure :exclude [+ - * / partial ref])
  (:require [emmy.env :as e :refer :all]
            [emmy.generic :as g]
            [emmy.matrix :as matrix]))

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
