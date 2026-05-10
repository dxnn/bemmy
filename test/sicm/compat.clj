(ns sicm.compat
  "SICM-compatibility shim. Defines scmutils built-ins that Emmy doesn't
  ship, so SICM book code translated by sicm2emmy.js evaluates against
  Emmy without per-snippet hand-edits.

  Add helpers here as the equivalence corpus surfaces missing names.
  Each definition mirrors the canonical scmutils form."
  (:refer-clojure :exclude [+ - * / partial ref])
  (:require [emmy.env :as e :refer :all]))

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
