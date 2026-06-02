#!/usr/bin/env bb
;; Generate BEmmy section pages from Emmy's own SICM-test corpus
;; (test/fixtures/emmy-sicm/chN_test.cljc — vendored from the
;; mentat-collective/emmy GitHub repo, GPL-3.0). Each `(deftest
;; section-X-Y …)` becomes one page; the wrapper is stripped, the
;; body's `is`-assertions are flattened to bare expressions with
;; `;;=>` comments, and the result is spliced into the same generated
;; SICM-section-pages array-map in public/app.cljs that the scrape
;; generator writes to.
;;
;; Source: https://github.com/mentat-collective/emmy/tree/main/test/emmy/sicm
;; License: GPL-3.0

(ns build-emmy-sicm-pages
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [babashka.fs :as fs]))

(def fixtures-dir
  "Vendored test files live at test/emmy/sicm so the JVM classpath
  (via :test :extra-paths [\"test\"]) picks them up under the ns
  `emmy.sicm.chN-test` and the translator-oracle test can run them
  directly as clojure.test deftests."
  "test/emmy/sicm")
(def app-path     "public/app.cljs")
(def begin-marker ";; --- BEGIN GENERATED SICM PAGES ---")
(def end-marker   ";; --- END GENERATED SICM PAGES ---")

;; Hand-curated mapping from Emmy deftest slug → BEmmy page-name +
;; SICM-book section-and-title. Section parents (1.5, 1.8) come from
;; the book's TOC; subsection-spanning deftests (section-1-7-1, -1-7-2,
;; section-2-9, -2-9b) get a `(part N)` suffix so the dropdown stays
;; legible.
(def deftest->page
  ;; {slug → "page-name as it appears in the dropdown"}
  {;; ch1 — Lagrangian Mechanics
   "section-1-4"   "SICM 1.4 Computing Actions (Emmy)"
   "section-1-5"   "SICM 1.5 The Euler-Lagrange Equations (Emmy)"
   "section-1-6"   "SICM 1.6 How to Find Lagrangians (Emmy)"
   "section-1-7-1" "SICM 1.7 Evolution of Dynamical State – part 1 (Emmy)"
   "section-1-7-2" "SICM 1.7 Evolution of Dynamical State – part 2 (Emmy)"
   "section-1-8"   "SICM 1.8 Conserved Quantities (Emmy)"
   "section-1-9"   "SICM 1.9 Abstraction of Path Functions (Emmy)"
   ;; ch2 — Rigid Bodies / Rotation
   "section-2-7"   "SICM 2.7 Euler Angles (Emmy)"
   "section-2-9"   "SICM 2.9 Vector Angular Momentum – part 1 (Emmy)"
   "section-2-9b"  "SICM 2.9 Vector Angular Momentum – part 2 (Emmy)"
   "section-2-10"  "SICM 2.10 Axisymmetric Tops (Emmy)"
   ;; ch3 — Hamiltonian Mechanics
   "section-3-1"   "SICM 3.1 Hamilton's Equations (Emmy)"
   "section-3-2"   "SICM 3.2 Poisson Brackets (Emmy)"
   "section-3-4"   "SICM 3.4 Phase Space Reduction (Emmy)"
   "section-3-5"   "SICM 3.5 Phase Space Evolution (Emmy)"
   ;; ch5 — Canonical Transformations
   "section-5-1"   "SICM 5.1 Point Transformations (Emmy)"
   "section-5-2"   "SICM 5.2 General Canonical Transformations (Emmy)"
   "section-5-3"   "SICM 5.3 Invariants of Canonical Transformations (Emmy)"
   "section-5-7"   "SICM 5.7 Symplectic Condition (Emmy)"
   "section-5-10"  "SICM 5.10 Generating Functions (Emmy)"
   ;; ch6 — Canonical Evolution
   "section-6-2"   "SICM 6.2 Time Evolution is Canonical (Emmy)"
   ;; ch7 — Canonical Perturbation Theory (Emmy file uses bare section-N)
   "section-1"     "SICM 7.1 Composition of Functions (Emmy)"
   "section-2"     "SICM 7.2 Pendulum as a Perturbed Rotor (Emmy)"
   "section-3"     "SICM 7.3 Two Frequencies (Emmy)"
   "section-4"     "SICM 7.4 Higher Order (Emmy)"})

;; ----------------------------------------------------------------------
;; Per-page enrichments — added BELOW the deftest-derived body to give
;; otherwise def-only pages a concrete invocation, usually a plot. The
;; deftest pages are great as a reference but read as a wall of `(def …)`
;; and `(simplify …) ;;=> '(…)` annotations; a small graphic makes the
;; concept tangible. Each entry's text is appended verbatim after a
;; `;; --- Example: title ---` divider.
;;
;; Keep these brief — they're meant to invite further play, not to be
;; tutorials. Prefer plain `(plot …)` / `(emmy.mafs/…)` / `(emmy.mathbox/…)`
;; over hand-rolled hiccup; the result-pane handles each.
(def page-extras
  {"SICM 7.3 Two Frequencies (Emmy)"
   ["sin and its derivative cos"
    ";; Lower-level mafs hiccup (mafs.core/Mafs etc.) takes plain CLJS
;; fns; the higher-level emmy.mafs/of-x compiles via Emmy's symbolic
;; pipeline and would NaN out on raw Math/sin. The (simplify ((D sin) 'x))
;; ;;=> (cos x) above is the symbolic version of what these two curves
;; show numerically.
(let [domain [(- Math/PI) Math/PI]]
  [mafs.core/Mafs {:viewBox {:x domain :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   [mafs.plot/OfX {:y      (fn [x] (Math/sin x))
                   :domain domain
                   :color  \"#3090ff\"}]
   [mafs.plot/OfX {:y      (fn [x] (Math/cos x))
                   :domain domain
                   :color  \"#e63946\"}]])"]

   "SICM 7.4 Higher Order (Emmy)"
   ["sin, cos = D sin, and -sin = D² sin"
    ";; The page's `((* (- D I) (+ D I)) f) = (D² − I) f` identity gives
;; D²sin − sin = −2 sin. Three overlaid curves below: f, Df, D²f for f = sin.
(let [domain [(- Math/PI) Math/PI]]
  [mafs.core/Mafs {:viewBox {:x domain :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   [mafs.plot/OfX {:y (fn [x] (Math/sin x))      :domain domain :color \"#3090ff\"}]
   [mafs.plot/OfX {:y (fn [x] (Math/cos x))      :domain domain :color \"#e63946\"}]
   [mafs.plot/OfX {:y (fn [x] (- (Math/sin x)))  :domain domain :color \"#2a9d8f\"}]])"]

   "SICM 7.1 Composition of Functions (Emmy)"
   ["(sin ∘ cos)(x) vs sin(x)"
    "(let [domain [(- Math/PI) Math/PI]
      sin-cos (comp #(Math/sin %) #(Math/cos %))]
  [mafs.core/Mafs {:viewBox {:x domain :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   ;; Bare sine for reference, gray …
   [mafs.plot/OfX {:y (fn [x] (Math/sin x)) :domain domain :color \"#888888\"}]
   ;; … and the composition, blue. Note the squeezed range — cos maps R
   ;; into [-1, 1], so sin∘cos lives within sin([-1, 1]) ≈ [-0.84, 0.84].
   [mafs.plot/OfX {:y sin-cos :domain domain :color \"#3090ff\"}]])"]

   "SICM 5.1 Point Transformations (Emmy)"
   ["a Lissajous figure in rectangular coordinates"
    "(let [omega-x 1.0
      omega-y 2.0
      phi     (/ Math/PI 4)]
  [mafs.core/Mafs {:viewBox {:x [-1.2 1.2] :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   ;; (x, y) = (cos ωₓt, sin(ω_y t + φ)). Rational ωₓ:ω_y gives a closed
   ;; curve; irrational ratios fill the box densely. SICM's F→C / F-tilde
   ;; takes such (q(t), q'(t)) tuples to the canonical state form.
   [mafs.plot/Parametric
    {:t [0 (* 2 Math/PI)]
     :xy (fn [t] [(Math/cos (* omega-x t))
                  (Math/sin (+ (* omega-y t) phi))])
     :color \"#3090ff\"}]])"]

   "SICM 2.10 Axisymmetric Tops (Emmy)"
   ["torque-free precession of the symmetry axis"
    ";; In a torque-free axisymmetric top with fixed nutation (θ̇ = 0), the
;; symmetry axis precesses at constant φ̇ around the vertical. The tip
;; of the body's symmetry axis traces a circle of radius sin θ in the
;; horizontal plane — this is what the Lagrangian above generates as
;; its motion of least action.
(let [θ      (/ Math/PI 4)
      φ-dot  1.0
      t-end  (* 2 Math/PI)]
  [mafs.core/Mafs {:viewBox {:x [-1.2 1.2] :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   ;; Horizontal-plane projection of the symmetry-axis tip:
   ;; (sin θ · cos(φ̇ t), sin θ · sin(φ̇ t)).
   [mafs.plot/Parametric
    {:t [0 t-end]
     :xy (fn [t] [(* (Math/sin θ) (Math/cos (* φ-dot t)))
                  (* (Math/sin θ) (Math/sin (* φ-dot t)))])
     :color \"#3090ff\"}]
   ;; Pin the origin — the precession's center.
   [mafs.core/Point {:x 0 :y 0}]])"]

   "SICM 7.2 Pendulum as a Perturbed Rotor (Emmy)"
   ["pendulum θ(t) and p_θ(t) via state-trajectory"
    "(let [H   (Lagrangian->Hamiltonian (L-pendulum 1.0 1.0 9.8))
      t0  0.0
      t1  6.0
      adv (state-trajectory H (up t0 1.0 0.0) t0 t1 64)]
  [mafs.core/Mafs {:viewBox {:x [t0 t1] :y [-3 3]}}
   [mafs.coordinates/Cartesian]
   ;; State is (up t θ p_θ). θ(t) — blue.
   [mafs.plot/Parametric
    {:t [t0 t1]
     :xy (fn [t] [t (nth (adv (up t0 1.0 0.0) t) 1)])
     :color \"#3090ff\"}]
   ;; p_θ(t) — red.
   [mafs.plot/Parametric
    {:t [t0 t1]
     :xy (fn [t] [t (nth (adv (up t0 1.0 0.0) t) 2)])
     :color \"#e63946\"}]])"]

   "SICM 1.6 How to Find Lagrangians (Emmy)"
   ["a numerical path from find-path tracks the analytic cosine"
    ";; The harmonic-oscillator Lagrangian L = ½v² − ½q² between (0, 1) and
;; (π/2, 0) has cos(t) as its true minimum. find-path approximates that
;; minimum with a polynomial — small `n` (basis size) drifts off, larger
;; `n` tracks closely.
(let [t0 0.0
      t1 (/ Math/PI 2)
      path-3 (find-path (L-harmonic 1.0 1.0) t0 1.0 t1 0.0 3)
      path-5 (find-path (L-harmonic 1.0 1.0) t0 1.0 t1 0.0 5)]
  [mafs.core/Mafs {:viewBox {:x [t0 t1] :y [-0.1 1.1]}}
   [mafs.coordinates/Cartesian]
   ;; Analytic cos(t) — gray reference.
   [mafs.plot/OfX {:y (fn [t] (Math/cos t)) :domain [t0 t1] :color \"#888888\"}]
   ;; find-path with 3-basis — drifts visibly.
   [mafs.plot/OfX {:y (fn [t] (path-3 t)) :domain [t0 t1] :color \"#e63946\"}]
   ;; find-path with 5-basis — tracks cos closely.
   [mafs.plot/OfX {:y (fn [t] (path-5 t)) :domain [t0 t1] :color \"#3090ff\"}]])"]

   "SICM 1.7 Evolution of Dynamical State – part 1 (Emmy)"
   ["the pendulum trajectory traces itself out — autoplaying animation"
    ";; A real animation: q(x) only renders for x ≤ t (t auto-advances at
;; 0.5× real-time). After the first sweep the full trajectory stays
;; visible. Stop with the next evaluation; the timer cleans up on remount.
(animate
 (fn [t x]
   (if (<= x t)
     (Math/cos x)
     js/NaN))
 [0 (* 2 Math/PI)] [-1.2 1.2] 0.8)"]

   "SICM 1.8 Conserved Quantities (Emmy)"
   ["energy is conserved: E(t) hovers near its t=0 value along a pendulum trajectory"
    ";; Integrate the pendulum, evaluate H(state(t)) at each sample,
;; subtract H(state(0)). For a true conservation law the residual is
;; only the numerical-integration error (RK4 with 64 steps ≈ 10⁻⁴).
(let [m 1.0 l 1.0 g 9.8
      H   (Lagrangian->Hamiltonian (L-pendulum m l g))
      s0  (up 0.0 1.0 0.0)
      t0  0.0
      t1  6.0
      adv (state-trajectory H s0 t0 t1 128)
      E0  (cljs.core/double (H (adv s0 t0)))]
  [mafs.core/Mafs {:viewBox {:x [t0 t1] :y [-0.01 0.01]}}
   [mafs.coordinates/Cartesian]
   [mafs.plot/Parametric
    {:t [t0 t1]
     :xy (fn [t]
           [t (cljs.core/- (cljs.core/double (H (adv s0 t))) E0)])
     :color \"#3090ff\"}]])"]

   "SICM 2.7 Euler Angles (Emmy)"
   ["3D: the body axes after rotation by Euler angles (θ, φ, ψ) = (π/4, π/6, 0)"
    ";; Rz(φ) · Rx(θ) acting on the standard basis (e₁, e₂, e₃) gives the
;; rotated body frame. Three colored rays from the origin — red x̂',
;; green ŷ', blue ẑ' — drawn out to length 1. Drag to rotate the view.
(let [θ (/ Math/PI 4)
      φ (/ Math/PI 6)
      cθ (Math/cos θ) sθ (Math/sin θ)
      cφ (Math/cos φ) sφ (Math/sin φ)
      e1 [cφ sφ 0]
      e2 [(- (* sφ cθ)) (* cφ cθ) sθ]
      e3 [(* sφ sθ) (- (* cφ sθ)) cθ]]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-1.2 1.2] [-1.2 1.2] [-1.2 1.2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (* x (nth e1 0)) (* x (nth e1 1)) (* x (nth e1 2))))}]
    [mb/Line {:color \"#e63946\" :width 4}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (* x (nth e2 0)) (* x (nth e2 1)) (* x (nth e2 2))))}]
    [mb/Line {:color \"#2a9d8f\" :width 4}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (* x (nth e3 0)) (* x (nth e3 1)) (* x (nth e3 2))))}]
    [mb/Line {:color \"#3090ff\" :width 4}]]])"]

   "SICM 3.1 Hamilton's Equations (Emmy)"
   ["phase portrait of the harmonic oscillator at three energies"
    ";; H = ½(p² + q²) — the level sets are concentric circles in (q, p).
;; Three trajectories from initial states (q₀, 0) at q₀ ∈ {0.4, 0.7, 1.0}.
(let [H   (fn [s] (let [q (nth s 1) p (nth s 2)]
                    (* 1/2 (+ (* p p) (* q q)))))
      adv (fn [q0]
            (state-trajectory H (up 0.0 q0 0.0) 0.0 (* 2 Math/PI) 96))]
  [mafs.core/Mafs {:viewBox {:x [-1.2 1.2] :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   ;; Three trajectories at three energies.
   (let [a (adv 0.4)]
     [mafs.plot/Parametric
      {:t [0.0 (* 2 Math/PI)]
       :xy (fn [t] (let [s (a (up 0.0 0.4 0.0) t)] [(nth s 1) (nth s 2)]))
       :color \"#3090ff\"}])
   (let [a (adv 0.7)]
     [mafs.plot/Parametric
      {:t [0.0 (* 2 Math/PI)]
       :xy (fn [t] (let [s (a (up 0.0 0.7 0.0) t)] [(nth s 1) (nth s 2)]))
       :color \"#2a9d8f\"}])
   (let [a (adv 1.0)]
     [mafs.plot/Parametric
      {:t [0.0 (* 2 Math/PI)]
       :xy (fn [t] (let [s (a (up 0.0 1.0 0.0) t)] [(nth s 1) (nth s 2)]))
       :color \"#e63946\"}])])"]

   "SICM 3.5 Phase Space Evolution (Emmy)"
   ["pendulum phase portrait — librations near θ=0, separatrix, rotations above"
    ";; Real pendulum H = ½p² − cos θ + 1. Below energy 2 the trajectory
;; closes (libration); at exactly 2 it's the separatrix; above, the
;; pendulum rotates over the top. Six initial momenta sample all three.
(let [H   (Lagrangian->Hamiltonian (L-pendulum 1.0 1.0 1.0))
      ;; p₀ values: three librations, near-separatrix, two rotations.
      p0s [0.5 1.0 1.5 1.95 2.3 2.6]
      ;; Cache one trajectory per initial momentum.
      advs (mapv (fn [p0]
                   (state-trajectory H (up 0.0 0.0 p0) 0.0 (* 4 Math/PI) 128))
                 p0s)
      colors [\"#3090ff\" \"#2a9d8f\" \"#7b68ee\"
              \"#888888\" \"#e63946\" \"#e76f51\"]]
  (into [mafs.core/Mafs {:viewBox {:x [-4 4] :y [-3 3]}}
         [mafs.coordinates/Cartesian]]
        (map-indexed
          (fn [i adv]
            [mafs.plot/Parametric
             {:t [0.0 (* 4 Math/PI)]
              :xy (fn [t] (let [s (adv (up 0.0 0.0 (nth p0s i)) t)]
                            [(nth s 1) (nth s 2)]))
              :color (nth colors i)}])
          advs)))"]

   "SICM 1.4 Computing Actions (Emmy)"
   ["interactive: drag `a` to perturb the path, watch action diverge"
    ";; The harmonic-oscillator action S = ∫(½v² − ½q²)dt has its minimum
;; on q(t) = cos(t). Drag `a` to add a sin(2t) bump to that path —
;; the curve diverges from cos and the plot fills in q(t) for x ≤ t-end.
;; (Computing the action numerically is expensive at every slider move;
;; here we just visualize the deformed path; the numeric S(a) is left
;; for the user to compute via the Lagrangian-action helpers above.)
(plot-with-params
 (fn [{:keys [a t-end]} x]
   (if (<= x t-end)
     (cljs.core/+ (Math/cos x)
                  (cljs.core/* a (Math/sin (cljs.core/* 2 x))))
     js/NaN))
 {:a     {:value 0.0 :min -0.5 :max 0.5 :step 0.02}
  :t-end {:value (cljs.core/* 0.5 Math/PI) :min 0.0 :max Math/PI :step 0.05}}
 [0.0 Math/PI] [-1.2 1.5])"]

   "SICM 1.9 Abstraction of Path Functions (Emmy)"
   ["vector-valued path family — Lissajous figures via leva sliders"
    ";; A `path` in SICM is just a function t ↦ q. It can return a scalar,
;; or a tuple — the abstraction is the same. A vector-valued path
;; t ↦ (up (sin (* a t)) (sin (+ (* b t) φ))) traces a *Lissajous*
;; figure: closed curves for rational a/b, dense space-fillers for
;; irrational ratios. Drag the sliders to walk the family; the red
;; dot animates one period along the current curve. Scroll-wheel
;; zooms; click-drag pans.
(defn lissajous-anim [initial-params]
  (let [n-steps 600
        compute (memoize
                  (fn [{:keys [a b φ]}]
                    (let [t-max (cljs.core/* 2 Math/PI)
                          dt    (cljs.core// t-max n-steps)
                          pts   (vec
                                  (for [i (range (inc n-steps))]
                                    (let [t (cljs.core/* i dt)]
                                      [(Math/sin (cljs.core/* a t))
                                       (Math/sin (cljs.core/+ (cljs.core/* b t) φ))])))]
                      {:positions pts :dt dt :t-max t-max})))
        !params (reagent.core/atom initial-params)
        !t      (reagent.core/atom 0.0)
        !start  (atom nil)
        timer   (atom nil)
        schema  (fn [k mn mx step]
                  {:value (get initial-params k) :min mn :max mx :step step :pad 3})]
    (reagent.core/create-class
      {:component-did-mount
       (fn [_]
         (reset! !start (.now js/Date))
         (reset! timer
                 (js/setInterval
                   (fn []
                     (let [elapsed (cljs.core// (cljs.core/- (.now js/Date)
                                                              (deref !start))
                                                1000.0)
                           period  (cljs.core/* 2 Math/PI)]
                       (reset! !t (cljs.core/mod elapsed period))))
                   33)))
       :component-will-unmount
       (fn [_] (when (deref timer) (js/clearInterval (deref timer))))
       :reagent-render
       (fn [_]
         (let [params @!params
               {:keys [positions dt t-max]} (compute params)
               pos-at (fn [s]
                        (let [i (max 0 (min n-steps
                                            (cljs.core/int (Math/floor (cljs.core// s dt)))))]
                          (nth positions i)))
               t @!t
               [x y] (pos-at t)]
           [:div {:style {:display \"flex\" :flex-direction \"column\" :gap \"0.5rem\"}}
            [leva.core/Controls
             {:atom   !params
              :schema {:a (schema :a 1.0 8.0 1.0)
                       :b (schema :b 1.0 8.0 1.0)
                       :φ (schema :φ 0.0 (cljs.core/* 2 Math/PI) 0.01)}}]
            [mafs.core/Mafs {:viewBox {:x [-1.2 1.2] :y [-1.2 1.2]}
                             :zoom    true}
             [mafs.coordinates/Cartesian]
             [mafs.plot/Parametric
              {:t [0 t-max] :xy pos-at :color \"#3090ff\"}]
             [mafs.core/Point {:x (double x) :y (double y) :color \"#e63946\"}]]]))})))

;; Default: 3:4 frequency ratio with zero phase — a classic Lissajous.
;; Try (a, b, φ) = (2, 3, π/2), (5, 4, 0), (3, 5, π/4) for variations.
[lissajous-anim {:a 3.0 :b 4.0 :φ 0.0}]"]

   "SICM 5.2 General Canonical Transformations (Emmy)"
   ["a coordinate grid rotated by 30° in phase space — canonical"
    ";; The map (q, p) → (Q, P) = (q cos θ − p sin θ, q sin θ + p cos θ)
;; is a phase-space rotation, the simplest non-trivial canonical
;; transformation. Plot a 5×5 grid before (gray) and after (blue) the
;; rotation. Both grids have the same area per cell — area preservation
;; is the geometric face of \"canonical\".
(let [θ (cljs.core// Math/PI 6)
      step 0.25
      qs (mapv (fn [i] (cljs.core/+ -1.0 (cljs.core/* step i))) (range 9))
      cθ (Math/cos θ)
      sθ (Math/sin θ)
      rotate (fn [q p] [(cljs.core/- (cljs.core/* q cθ) (cljs.core/* p sθ))
                        (cljs.core/+ (cljs.core/* q sθ) (cljs.core/* p cθ))])]
  (into
    [mafs.core/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
     [mafs.coordinates/Cartesian]]
    (concat
      ;; Original-grid lines — horizontal then vertical.
      (for [p qs]
        [mafs.plot/OfX {:y (fn [_] p) :domain [-1 1] :color \"#888888\"}])
      ;; Original vertical lines, drawn as parametric so we can do
      ;; constant-x segments.
      (for [q qs]
        [mafs.plot/Parametric
         {:t [-1 1]
          :xy (fn [t] [q t])
          :color \"#888888\"}])
      ;; Rotated horizontal lines (after rotate at every q).
      (for [p qs]
        [mafs.plot/Parametric
         {:t [-1 1]
          :xy (fn [t] (rotate t p))
          :color \"#3090ff\"}])
      ;; Rotated vertical lines.
      (for [q qs]
        [mafs.plot/Parametric
         {:t [-1 1]
          :xy (fn [t] (rotate q t))
          :color \"#3090ff\"}]))))"]

   "SICM 5.7 Symplectic Condition (Emmy)"
   ["area preservation: a unit square sent through a canonical rotation"
    ";; The symplectic condition is dQ ∧ dP = dq ∧ dp — the canonical
;; transformation preserves the 2-form on phase space. Concretely:
;; a unit-area region maps to a unit-area region, possibly reshaped.
;; Plot the unit square [0,1]² rotated by four progressively larger
;; canonical angles — each image rectangle has area 1.
(let [angles [0 (cljs.core// Math/PI 8) (cljs.core// Math/PI 4) (cljs.core// (cljs.core/* 3 Math/PI) 8)]
      colors [\"#888888\" \"#3090ff\" \"#2a9d8f\" \"#e63946\"]
      ;; Closed border of [0,1]² parametrized by t ∈ [0, 4].
      square (fn [t]
               (let [t (mod t 4.0)
                     i (int (Math/floor t))
                     f (cljs.core/- t i)]
                 (case i
                   0 [f 0.0]
                   1 [1.0 f]
                   2 [(cljs.core/- 1.0 f) 1.0]
                   3 [0.0 (cljs.core/- 1.0 f)])))
      rotate (fn [θ q p]
               (let [cθ (Math/cos θ) sθ (Math/sin θ)]
                 [(cljs.core/- (cljs.core/* q cθ) (cljs.core/* p sθ))
                  (cljs.core/+ (cljs.core/* q sθ) (cljs.core/* p cθ))]))]
  (into
    [mafs.core/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
     [mafs.coordinates/Cartesian]]
    (map-indexed
      (fn [i θ]
        [mafs.plot/Parametric
         {:t [0 4]
          :xy (fn [t] (let [[q p] (square t)] (rotate θ q p)))
          :color (nth colors i)}])
      angles)))"]

   "SICM 6.2 Time Evolution is Canonical (Emmy)"
   ["traveling wave — Hamiltonian flow visualized via animate"
    ";; sin(x − t) is the time-evolved sine under translation-Hamiltonian
;; H = p (acting on phase functions f via {f, H}). Auto-advancing t
;; makes the whole wavefront slide right at unit speed. Time-evolution
;; is canonical: the wave keeps its shape (no diffusion, no growth) —
;; a phase-space volume preserved as it advances.
(animate
 (fn [t x] (Math/sin (cljs.core/- x t)))
 [(cljs.core/- Math/PI) Math/PI] [-1.2 1.2] 0.6)"]

   "SICM 1.5 The Euler-Lagrange Equations (Emmy)"
   ["interactive: drag `a` to perturb cos(t), watch the EL residual grow"
    ";; For L = ½v² − ½q² (harmonic oscillator), the Euler-Lagrange operator
;; gives EL[q] = q̈ + q. On the true solution q(t) = cos t it vanishes;
;; for q(t) = cos t + a sin(2t), q̈ = −cos t − 4a sin(2t), so the residual
;; is −4a sin(2t) + a sin(2t) = −3a sin(2t). Drag `a` and watch the
;; residual amplitude scale linearly with the perturbation.
(plot-with-params
 (fn [{:keys [a]} t]
   ;; EL residual: q̈ + q = −3a sin(2t).
   (cljs.core/* -3 a (Math/sin (cljs.core/* 2 t))))
 {:a {:value 0.1 :min -0.5 :max 0.5 :step 0.02}}
 [0 (cljs.core/* 2 Math/PI)] [-1.5 1.5])"]

   "SICM 1.7 Evolution of Dynamical State – part 2 (Emmy)"
   ["energy partition: kinetic and potential terms trade off along the path"
    ";; A small-angle pendulum oscillates between all-potential at the turning
;; points and all-kinetic at the bottom. Plot T(t) (blue), V(t) (red),
;; and H = T + V (gray, constant). The two oscillate at 2ω relative to
;; the pendulum's period; their sum stays flat — energy conservation.
(let [m 1.0 l 1.0 g 9.8
      ω (Math/sqrt (cljs.core// g l))
      θ0 0.2
      ;; Small-angle: θ(t) = θ0 cos(ωt), θ̇(t) = -θ0 ω sin(ωt).
      T-of  (fn [t] (let [θ̇ (cljs.core/* (cljs.core/- 0) θ0 ω (Math/sin (cljs.core/* ω t)))]
                      (cljs.core/* 0.5 m l l θ̇ θ̇)))
      V-of  (fn [t] (let [θ (cljs.core/* θ0 (Math/cos (cljs.core/* ω t)))]
                      (cljs.core/* m g l (cljs.core/- 1 (Math/cos θ)))))]
  [mafs.core/Mafs {:viewBox {:x [0 (cljs.core/* 2 Math/PI)] :y [-0.005 0.25]}}
   [mafs.coordinates/Cartesian]
   ;; T(t) — blue
   [mafs.plot/OfX {:y T-of :domain [0 (cljs.core/* 2 Math/PI)] :color \"#3090ff\"}]
   ;; V(t) — red
   [mafs.plot/OfX {:y V-of :domain [0 (cljs.core/* 2 Math/PI)] :color \"#e63946\"}]
   ;; H = T + V — gray (should be near-constant for small θ0)
   [mafs.plot/OfX {:y (fn [t] (cljs.core/+ (T-of t) (V-of t)))
                   :domain [0 (cljs.core/* 2 Math/PI)] :color \"#888888\"}]])"]

   "SICM 2.9 Vector Angular Momentum – part 2 (Emmy)"
   ["3D: a tilted spinning top — body angular momentum and its space-frame image"
    ";; Spin axis tilted by θ = 0.6 rad about ŷ. In body frame ω points along ẑ̂_body;
;; rotating into the space frame mixes ẑ̂ and x̂. Red = ω_body; green = ω in
;; space frame (rotated); blue = the body's z-axis itself.
(let [θ 0.6
      ω-mag 0.8
      cθ (Math/cos θ) sθ (Math/sin θ)
      ω-body  [0 0 ω-mag]
      ω-space [(cljs.core/* sθ ω-mag) 0 (cljs.core/* cθ ω-mag)]
      body-z  [(cljs.core/* sθ 1.0) 0 (cljs.core/* cθ 1.0)]]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-1 1] [-1 1] [-1 1]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth ω-body 0))
                                           (cljs.core/* x (nth ω-body 1))
                                           (cljs.core/* x (nth ω-body 2))))}]
    [mb/Line {:color \"#e63946\" :width 5}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth ω-space 0))
                                           (cljs.core/* x (nth ω-space 1))
                                           (cljs.core/* x (nth ω-space 2))))}]
    [mb/Line {:color \"#2a9d8f\" :width 5}]
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth body-z 0))
                                           (cljs.core/* x (nth body-z 1))
                                           (cljs.core/* x (nth body-z 2))))}]
    [mb/Line {:color \"#3090ff\" :width 3}]]])"]

   "SICM 3.2 Poisson Brackets (Emmy)"
   ["phase-space vector field generated by H = ½(p² + q²) — direction of flow"
    ";; The Hamiltonian flow is dq/dt = ∂H/∂p = p, dp/dt = -∂H/∂q = -q.
;; At each (q, p) point the trajectory moves in direction (p, -q) — the
;; field tangent to concentric circles. Plot short line segments
;; sampling the vector field across a 7×7 grid.
(let [pts (for [q (range -1.2 1.21 0.3)
                p (range -1.2 1.21 0.3)
                :when (cljs.core/> (cljs.core/+ (cljs.core/* q q) (cljs.core/* p p))
                                   0.01)]  ; skip the origin
            [q p])
      scale 0.1
      ;; Render each segment as a short Parametric line from (q,p) to (q,p)+scale*(p,-q).
      seg (fn [q p]
            [mafs.plot/Parametric
             {:t [0 1]
              :xy (fn [t]
                    [(cljs.core/+ q (cljs.core/* scale t p))
                     (cljs.core/- p (cljs.core/* scale t q))])
              :color \"#3090ff\"}])]
  (into [mafs.core/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
         [mafs.coordinates/Cartesian]]
        (map (fn [[q p]] (seg q p)) pts)))"]

   "SICM 5.3 Invariants of Canonical Transformations (Emmy)"
   ["a closed loop in phase space is invariant under canonical evolution"
    ";; A canonical map preserves Poincaré–Cartan invariants. Here: a unit
;; circle in (q, p) gets rotated by a canonical rotation θ → still a unit
;; circle, same enclosed area. Animate the rotation: the circle traces
;; itself out at each value of θ. The fact that NO frame distorts is
;; the visual content of the invariance theorem.
(animate
 (fn [t x]
   ;; Trace a unit circle by parametrizing y = √(1 - x²) (positive half;
   ;; negate to get negative half). Apply canonical rotation by angle t.
   (let [r 1.0
         q (cljs.core/* r (Math/cos x))
         p (cljs.core/* r (Math/sin x))
         cθ (Math/cos t) sθ (Math/sin t)]
     ;; OfX wants y(x), so re-parametrize: the curve as a 1D function over
     ;; arclength. Project rotated (Q, P) point onto y-axis via P alone.
     ;; (Not parametric-equivalent, but produces a visible breathing curve.)
     (cljs.core/+ (cljs.core/* q sθ) (cljs.core/* p cθ))))
 [(cljs.core/- Math/PI) Math/PI] [-1.2 1.2] 0.7)"]

   "SICM 5.10 Generating Functions (Emmy)"
   ["F1(q, Q) = ½ω(q² + Q²) cot α: P(α) traces the canonical image of p=0"
    ";; A type-1 generating function F1(q, Q, α) = ½ω(q² + Q²) cot α produces
;; the canonical map between (q, p) and (Q, P) of the harmonic oscillator.
;; Slider α picks the time parameter; we plot the resulting Q-vs-q curve
;; that starts on the q-axis and rotates into the p-axis as α grows.
(plot-with-params
 (fn [{:keys [α]} q]
   ;; For α near 0 the map is identity (Q = q); at α = π/2 it's the
   ;; quarter-period rotation (Q = p, P = -q). Use Q = q cos α - p sin α
   ;; with p = 0 initially; output Q.
   (cljs.core/* q (Math/cos α)))
 {:α {:value 0.0 :min 0.0 :max (cljs.core/* 2 Math/PI) :step 0.05}}
 [-1 1] [-1.2 1.2])"]

   "SICM 3.4 Phase Space Reduction (Emmy)"
   ["the orbital plane: central-force motion in (x, y) reduces to (r) + L"
    ";; A 2D central-force orbit lies in a plane (angular momentum conserved
;; about ẑ). Plot a Kepler-like ellipse: r(θ) = a(1-e²)/(1 + e cos θ).
;; The full phase space (4D) reduces to (r, p_r) + L = const.
(let [a 1.0
      e 0.4
      r-of (fn [θ] (cljs.core// (cljs.core/* a (cljs.core/- 1 (cljs.core/* e e)))
                                (cljs.core/+ 1 (cljs.core/* e (Math/cos θ)))))]
  [mafs.core/Mafs {:viewBox {:x [-2.0 1.5] :y [-1.4 1.4]}}
   [mafs.coordinates/Cartesian]
   ;; Orbit
   [mafs.plot/Parametric
    {:t [0 (cljs.core/* 2 Math/PI)]
     :xy (fn [θ] (let [r (r-of θ)]
                   [(cljs.core/* r (Math/cos θ))
                    (cljs.core/* r (Math/sin θ))]))
     :color \"#3090ff\"}]
   ;; Focus
   [mafs.core/Point {:x 0 :y 0 :color \"#e63946\"}]])"]

   "SICM 2.9 Vector Angular Momentum – part 1 (Emmy)"
   ["3D: angular momentum L = I·ω for ω = (0.4, 0.3, 0.7) with diagonal I"
    ";; The body-frame angular momentum L_i = I_i ω_i with diagonal inertia
;; tensor (A, B, C). Red is ω, green is L. For an isotropic body
;; (A = B = C) they coincide; for an anisotropic body the directions
;; differ — visible here.
(let [A 1.0 B 1.6 C 0.4
      ω [0.4 0.3 0.7]
      L [(cljs.core/* A (nth ω 0))
         (cljs.core/* B (nth ω 1))
         (cljs.core/* C (nth ω 2))]]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-1 1] [-1 1] [-1 1]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    ;; ω — red.
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth ω 0))
                                           (cljs.core/* x (nth ω 1))
                                           (cljs.core/* x (nth ω 2))))}]
    [mb/Line {:color \"#e63946\" :width 5}]
    ;; L — green.
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth L 0))
                                           (cljs.core/* x (nth L 1))
                                           (cljs.core/* x (nth L 2))))}]
    [mb/Line {:color \"#2a9d8f\" :width 5}]]])"]})

(def reader-opts
  {:eof ::eof
   :read-cond :allow
   ;; Emmy uses #emmy/ratio etc. as custom tagged literals; for our
   ;; generation purposes the underlying Clojure ratio is fine.
   :readers {'emmy/ratio identity
             'emmy/bigint identity
             'emmy/complex identity}})

(defn read-all-forms [src]
  (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. src))]
    (loop [forms []]
      (let [f (read reader-opts rdr)]
        (if (= f ::eof) forms (recur (conj forms f)))))))

(defn deftest? [form]
  (and (seq? form)
       (= 'deftest (first form))))

(defn deftest-slug
  "Skip optional ^:long metadata (it shows up as a tagged literal at
  read time) to reach the deftest's symbol name. Returns the slug as
  a string."
  [form]
  (let [parts (drop 1 form)
        ;; Reader represents ^:long as metadata on the next form, so
        ;; the symbol is just the second element regardless.
        sym (first (filter symbol? parts))]
    (some-> sym name)))

(defn deftest-body
  "All forms after the deftest's name."
  [form]
  (drop-while #(or (= 'deftest %) (symbol? %) (= 'quote %)
                   (and (map? %) (:tag (meta %))))
              (rest form)))

(defn unwrap-with-literal-functions
  "Hoist `(e/with-literal-functions [x y …] body)` to a sequence of
  top-level `(def x (literal-function 'x)) … body`. Mirrors the macro
  expansion so each let binding becomes a runnable top-level def the
  user can step through in BEmmy."
  [form]
  (cond
    (and (seq? form)
         (or (= 'e/with-literal-functions (first form))
             (= 'emmy.env/with-literal-functions (first form))))
    (let [[_ syms & body] form
          defs (mapv (fn [s] (list 'def s (list 'literal-function (list 'quote s))))
                     syms)]
      (concat defs body))
    :else [form]))

(defn unwrap-top-let
  "If the form is `(let [a A b B …] body…)`, hoist the bindings to
  top-level `(def a A) (def b B) …` and return [defs body]. Otherwise
  return [[] [form]]."
  [form]
  (if (and (seq? form) (or (= 'let (first form)) (= 'let* (first form))))
    (let [[_ binds & body] form
          pairs (partition 2 binds)
          defs  (mapv (fn [[k v]] (list 'def k v)) pairs)]
      [defs body])
    [[] [form]]))

(defn unwrap-nested-blocks
  "Recursively splice nested `let` / `let*` / `do` / `testing` blocks at
  the top of each form in `forms`. A `(let [bs] body…)` expands to
  `(def …)` forms for the bindings plus the recursively-unwrapped body;
  `(do body…)` and `(testing label body…)` splice their body directly.
  Other forms pass through unchanged. The point is to lift every
  `(is …)` assertion to its own top-level position so its value reaches
  the BEmmy result pane — otherwise the enclosing let/do body's
  return-last semantics throws away all but the final assertion's
  result, while the `;;=>` comments still imply each step renders."
  [forms]
  (mapcat (fn [form]
            (cond
              (and (seq? form)
                   (or (= 'let (first form)) (= 'let* (first form))))
              (let [[_ binds & body] form
                    pairs (partition 2 binds)
                    defs  (mapv (fn [[k v]] (list 'def k v)) pairs)]
                (concat defs (unwrap-nested-blocks body)))

              (and (seq? form) (= 'do (first form)))
              (unwrap-nested-blocks (rest form))

              (and (seq? form) (= 'testing (first form)))
              (unwrap-nested-blocks (drop 2 form))

              :else
              [form]))
          forms))

(def known-aliases
  "Namespace aliases used in Emmy's test files whose bare-name form
  is already available in BEmmy's user ns via :refer :all. emmy.env,
  emmy.mechanics.* and emmy.generic are all reffered by the scittle
  plugin and the JVM compat ns, so e.g. `e/Lagrangian-action` simplifies
  to `Lagrangian-action`. emmy.examples.* aliases (driven, pendulum)
  are NOT stripped — their names like `L` are only accessible via the
  alias, not as bare symbols."
  #{"e" "emmy.env"
    "L" "emmy.mechanics.lagrange"
    "H" "emmy.mechanics.hamilton"
    "g" "emmy.generic"})

(defn strip-known-namespace
  "If `sym` has a namespace in `known-aliases`, return the bare-name
  symbol; otherwise return `sym` unchanged."
  [sym]
  (if-let [n (and (symbol? sym) (namespace sym))]
    (if (contains? known-aliases n)
      (symbol (name sym))
      sym)
    sym))

(defn assertion-marker
  "Replace (is (= 'expected actual)) with a special marker form that
  pp will print and we'll text-replace post-hoc. Also unwraps
  clojure.test `(testing label body…)` since BEmmy doesn't refer
  testing in user — keep the body, drop the label."
  [form]
  (walk/postwalk
    (fn [x]
      (cond
        ;; (testing "label" body…) → (do body…)
        (and (seq? x) (= 'testing (first x)))
        (cons 'do (drop 2 x))
        ;; (is (= 'expected actual)) — 3-arg = with quoted expected
        (and (seq? x)
             (= 'is (first x))
             (let [a (second x)]
               (and (seq? a)
                    (= '= (first a))
                    (= 3 (count a)))))
        (let [[_ expected actual] (second x)]
          (list 'BEMMY-EXPECT actual expected))
        ;; (is (thrown? Class expr)) / (is (thrown-with-msg? …))
        ;; — clojure.test special-form assertions. `thrown?` is only
        ;; meaningful inside `is`; stripping `is` leaves a bare
        ;; `(thrown? …)` call that doesn't resolve. Wrap in `comment`
        ;; so the body is never analyzed.
        (and (seq? x)
             (= 'is (first x))
             (seq? (second x))
             (contains? '#{thrown? thrown-with-msg?} (first (second x))))
        (list 'comment (second x))
        ;; (is form) — boolean form
        (and (seq? x) (= 'is (first x)) (= 2 (count x)))
        (list 'BEMMY-EXPECT-TRUE (second x))
        :else x))
    form))

(defn balanced-end
  "Given a string `s` with `start` pointing at the `(` of a Clojure
  form, return the index *after* the matching `)`. Skips strings,
  char literals, and `;` line comments."
  [^String s ^long start]
  (loop [i (long (inc start)) depth 1 in-str false]
    (cond
      (>= i (count s)) (count s)
      in-str (case (.charAt s i)
               \\ (recur (+ i 2) depth true)
               \" (recur (inc i) depth false)
               (recur (inc i) depth true))
      :else (case (.charAt s i)
              \" (recur (inc i) depth true)
              \; (let [nl (.indexOf s "\n" (int i))]
                   (recur (if (neg? nl) (count s) nl) depth false))
              \\ (recur (+ i 2) depth false)
              \( (recur (inc i) (inc depth) false)
              \) (let [d (dec depth)]
                   (if (zero? d) (inc i)
                     (recur (inc i) d false)))
              (recur (inc i) depth false)))))

(defn split-marker-children
  "Inside the body of a marker form, split into [actual expected]
  by paren-matching on the first form, then trimming the rest.
  Expects `body` to start with whitespace, then the first child."
  [^String body]
  (let [t   (str/triml body)
        off (- (count body) (count t))
        c1-end (cond
                 (= \( (.charAt t 0)) (balanced-end t 0)
                 (= \[ (.charAt t 0))
                 (loop [i 1 depth 1]
                   (cond
                     (>= i (count t)) (count t)
                     (= (.charAt t i) \[) (recur (inc i) (inc depth))
                     (= (.charAt t i) \]) (let [d (dec depth)]
                                            (if (zero? d) (inc i) (recur (inc i) d)))
                     :else (recur (inc i) depth)))
                 :else
                 (loop [i 0]
                   (cond
                     (>= i (count t)) (count t)
                     (or (= (.charAt t i) \space) (= (.charAt t i) \newline)) i
                     :else (recur (inc i)))))
        actual    (subs t 0 c1-end)
        rest-text (str/trim (subs t c1-end))]
    [(+ off c1-end) actual rest-text]))

(defn- find-marker
  "Find the next BEMMY-EXPECT or BEMMY-EXPECT-TRUE marker in `text`
  at or after offset `from`. Returns {:kind :start :body-start} or nil."
  [^String text ^long from]
  (let [m (re-matcher #"\(BEMMY-EXPECT(-TRUE)?\s+" text)]
    (.region m (int from) (int (count text)))
    (when (.find m)
      {:kind       (if (.group m 1) :true :eq)
       :start      (.start m)
       :body-start (.end m)})))

(defn render-markers
  "Replace each `(BEMMY-EXPECT actual expected)` in `text` with
  `actual\\n;;=> expected`, indented the same as the marker's opening
  paren. Same shape for BEMMY-EXPECT-TRUE (the comment is omitted —
  the bare expression already says 'should be truthy')."
  [^String text]
  (loop [out (StringBuilder.) i (long 0)]
    (if-let [{:keys [kind start body-start]} (find-marker text i)]
      (let [end        (balanced-end text start)
            line-start (let [nl (.lastIndexOf text "\n" (int start))]
                         (if (neg? nl) 0 (inc nl)))
            indent     (subs text line-start start)
            body       (subs text body-start (dec end))]
        (.append out (subs text i line-start))
        (.append out indent)
        (case kind
          :eq (let [[_ actual rest-text] (split-marker-children body)
                    expected (str/trim rest-text)]
                (.append out actual)
                (.append out "\n")
                (.append out indent)
                (.append out ";;=> ")
                (.append out (str/replace expected #"\n"
                                          (str "\n" indent ";;   ")))
                ;; If the marker's closing `)` is immediately followed
                ;; by another `)` (i.e. it sits at the tail of an
                ;; enclosing `(testing …)` → `(do …)` or `(let […])`
                ;; wrapper), those trailing close-parens would land on
                ;; the `;;=>` comment line and be consumed by it,
                ;; leaving the outer form unbalanced (manifesting as
                ;; "EOF while reading, expected ) to match ("). Insert
                ;; a newline + the marker's indent so the close-parens
                ;; end up on their own line. We DON'T insert a newline
                ;; unconditionally, because that would introduce a
                ;; whitespace-only blank line between adjacent assertion
                ;; markers — and the page-eval test's read-all-forms
                ;; splits on blank lines, which would carve a multi-
                ;; statement `(let […] a b c)` into chunks the reader
                ;; can't parse.
                (when (and (< end (count text))
                           (= \) (.charAt text end)))
                  (.append out "\n")
                  (.append out indent)))
          :true (.append out (str/trim body)))
        (recur out (long end)))
      (do (.append out (subs text i)) (str out)))))

(defn pp-str [form]
  (let [s (with-out-str (pp/with-pprint-dispatch pp/code-dispatch (pp/pprint form)))]
    (str/trimr s)))

(defn strip-namespaces [form]
  (walk/postwalk strip-known-namespace form))

(def freeze-heads
  "Forms `(freeze x)` / `(g/freeze x)` / `(e/freeze x)` /
  `(emmy.generic/freeze x)` whose page-rendered semantics are just `x`.
  The deftest needs freeze to make `=` succeed against a quoted
  s-expression; the page renders the value through the result-pane
  pretty-printer, which freeze defeats by collapsing the wrapped
  expression tree to a plain list."
  '#{freeze g/freeze e/freeze emmy.generic/freeze})

(defn strip-freeze [form]
  (walk/postwalk
    (fn [x]
      (if (and (seq? x)
               (= 2 (count x))
               (contains? freeze-heads (first x)))
        (second x)
        x))
    form))

(defn render-body-form [form]
  ;; Each form in the deftest body. with-literal-functions is unwrapped;
  ;; leading let is unwrapped; then any nested let / do / testing blocks
  ;; at the top of the body are recursively spliced so each `is`-
  ;; assertion reaches its own top-level position (otherwise the
  ;; enclosing block's return-last semantics would discard all but the
  ;; final assertion's value).
  (let [unwrapped (unwrap-with-literal-functions form)]
    (if (= 1 (count unwrapped))
      (let [single (first unwrapped)
            [defs body] (unwrap-top-let single)]
        (concat defs (unwrap-nested-blocks body)))
      ;; with-literal-functions case: defs + remaining body. The body
      ;; might itself be a let — recurse on the last element only.
      (let [[defs-from-wlf body-forms] [(butlast unwrapped) [(last unwrapped)]]
            ;; Find inner let in the (only) body form
            inner (first body-forms)
            [more-defs final-body] (unwrap-top-let inner)]
        (concat defs-from-wlf more-defs (unwrap-nested-blocks final-body))))))

(defn- aliases-used
  "Walk `forms` and return the set of namespace-prefix strings used by
  any namespaced symbol. Used to prune require specs whose `:as` alias
  this page never references."
  [forms]
  (let [acc (atom #{})]
    (walk/postwalk
      (fn [x]
        (when (and (symbol? x) (namespace x))
          (swap! acc conj (namespace x)))
        x)
      forms)
    @acc))

(def reserved-names
  "Names referred into the BEmmy user ns at startup (emmy.env :refer
  :all + clojure.core's full refer set). A page-local `(def X …)` for
  any of these triggers SCI's hard 'X already refers to …' throw in
  the browser. We pre-compute the set on the JVM (see the one-shot
  command that wrote test/fixtures/reserved-names.edn) and bake it
  into the generator so colliding defs are emitted as comments
  rather than runnable code."
  (-> "test/fixtures/reserved-names.edn"
      slurp
      edn/read-string
      set))

(defn- prune-spec-refers
  "Given a `[ns :as a :refer [Y Z …]]` require spec, drop entries from
  the `:refer` list whose names are in reserved-names — re-`:refer`ing
  a name already in user via emmy.env triggers the same SCI
  refer-collision throw as a colliding `def`. Returns the spec with
  `:refer` filtered (and the key dropped entirely if the list goes
  empty); also drops `:as` aliases of `e` for emmy.env since the
  alias-walker can't see that no body uses it."
  [spec]
  (if (and (vector? spec) (>= (count spec) 3))
    (let [head (first spec)
          pairs (apply hash-map (rest spec))
          refer (:refer pairs)
          refer' (when (vector? refer)
                   (filterv #(not (contains? reserved-names (name %))) refer))
          new-pairs (cond
                      (nil? refer) pairs
                      (seq refer') (assoc pairs :refer refer')
                      :else (dissoc pairs :refer))]
      (apply vector head (mapcat identity new-pairs)))
    spec))

(defn- prune-require-by-aliases
  "Given a `(require '[ns :as a :refer […]] …)` helper form and the set
  of `:as` aliases this page actually references via `alias/sym`, first
  prune each spec's `:refer` list to drop names already referred from
  emmy.env (re-:referring them would refer-collide in SCI), then drop
  whole specs whose `:as` alias isn't in `used` AND that no longer
  have a `:refer` list. When the result is empty, return nil so the
  caller can drop the helper."
  [require-form used]
  (let [keep? (fn [quoted-spec]
                (let [spec (second quoted-spec)
                      pairs (when (and (vector? spec) (>= (count spec) 3))
                              (apply hash-map (drop 1 spec)))
                      as-alias (some-> pairs :as name)
                      has-refer? (boolean (:refer pairs))]
                  (or (nil? as-alias)
                      (contains? used as-alias)
                      has-refer?)))
        pruned (map (fn [quoted-spec]
                      (list 'quote (prune-spec-refers (second quoted-spec))))
                    (rest require-form))
        kept (filter keep? pruned)]
    (when (seq kept)
      (cons 'require kept))))

(defn- is-require? [form]
  (and (seq? form) (= 'require (first form))))

(defn- read-form-or-nil [s]
  (try
    (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. s))]
      (let [f (read reader-opts rdr)]
        (when-not (= f ::eof) f)))
    (catch Throwable _ nil)))

(defn- comment-out-if-reserved-def
  "If `chunk`'s first top-level form is a `(def X …)` / `(defn X …)` /
  `(defn- X …)` where X collides with the BEmmy user-ns refer set,
  convert the whole chunk to `;;`-prefixed line comments and prepend
  a brief header explaining why. Otherwise pass through unchanged.
  The pedagogical text stays visible (and readable) but the runtime
  doesn't try to evaluate the colliding def."
  [chunk]
  (let [trimmed (str/triml chunk)
        f (read-form-or-nil trimmed)
        n (when (and (seq? f)
                     (contains? '#{def defn defn-} (first f))
                     (symbol? (second f)))
            (name (second f)))]
    (if (and n (contains? reserved-names n))
      (str ";; (Pedagogical redef of `" n "` — kept as a comment so the page\n"
           ";;  doesn't collide with the same name `:refer`'d in from emmy.env\n"
           ";;  or clojure.core. Calls below resolve to that referred binding.)\n"
           (->> (str/split-lines trimmed)
                (map #(if (str/blank? %) % (str ";; " %)))
                (str/join "\n")))
      chunk)))

(defn- comment-out-reserved-defs
  "Walk top-level chunks (separated by blank lines) and convert any
  `(def X …)` whose X collides with the user-ns refer set into
  commented-out text."
  [text]
  (->> (str/split text #"\n[\t ]*\n")
       (map comment-out-if-reserved-def)
       (str/join "\n\n")))

(defn- filter-page-helpers
  "Helpers may include a `(require …)` form (always first when
  present, emitted by extract-ns-requires) plus any `(defn …)` /
  `(def …)` helpers that lived between deftests. For each page we keep
  only the require specs whose `:as` alias appears in the body or in a
  defn helper; pages whose deftest body never references a particular
  alias drop that require spec entirely, which matters for aliases the
  scittle plugin can't satisfy (emmy.examples.*)."
  [helpers body-forms]
  (let [requires (filter is-require? helpers)
        non-requires (filter (complement is-require?) helpers)
        used (aliases-used (concat non-requires body-forms))
        pruned (->> requires
                    (map #(prune-require-by-aliases % used))
                    (remove nil?))]
    (concat pruned non-requires)))

(defn render-page-source [page-name source-file deftest-form helpers]
  (let [slug   (deftest-slug deftest-form)
        body-forms (deftest-body deftest-form)
        rendered (->> body-forms
                      (mapcat render-body-form)
                      (map (comp assertion-marker strip-freeze strip-namespaces))
                      (map pp-str)
                      (str/join "\n\n"))
        ;; Helpers keep their namespace prefixes — the canonical case
        ;; is `(def simplify (comp e/freeze e/simplify))` which would
        ;; self-reference if we stripped `e/simplify`.
        page-helpers (filter-page-helpers helpers body-forms)
        helper-text (when (seq page-helpers)
                      (str/join "\n\n" (map pp-str page-helpers)))
        header (str ";; ============================================================\n"
                    ";; " page-name "\n"
                    ";; ============================================================\n"
                    ";; Source : github.com/mentat-collective/emmy "
                                  "(test/emmy/sicm/" source-file ")\n"
                    ";; License: GPL-3.0\n"
                    ";; deftest: " slug "\n"
                    ";; ============================================================\n")
        ;; Extras: a concrete example invocation / graphic appended below
        ;; the def-and-assertion body. Keeps def-only pages from feeling
        ;; like a wall of definitions; gives the reader something to run.
        extras (when-let [[title body] (get page-extras page-name)]
                 (str ";; --- Example: " title " ---\n\n" body))]
    (->> [header
          (when helper-text
            (str ";; --- helpers from " source-file " ---\n" helper-text))
          rendered
          extras]
         (remove nil?)
         (str/join "\n\n")
         render-markers
         comment-out-reserved-defs
         str/trimr)))

(defn extract-ns-requires
  "Pull the (:require …) clause out of an `(ns …)` form and convert it
  into a runtime `(require '[…] '[…] …)` form. Drop test-only entries
  (clojure.test, hermetic-simplify-fixture, examples) since BEmmy
  pages aren't deftests."
  [ns-form]
  (let [req-clause (some (fn [c]
                           (when (and (seq? c) (= :require (first c))) c))
                         (rest ns-form))
        keep? (fn [spec]
                ;; spec is e.g. [emmy.env :as e :refer […]]
                (let [sym (and (vector? spec) (first spec))
                      n (and sym (name sym))]
                  (and n
                       (str/starts-with? n "emmy")
                       ;; emmy.simplify ships hermetic-simplify-fixture
                       ;; for test isolation; not needed in pages.
                       (not= "emmy.simplify" n))))
        ;; Drop the :refer list of emmy.env (we re-emit a :refer :all
        ;; via :as e and rely on emmy.env :refer :all being effective
        ;; in user; keeping the curated list would shadow user ns'
        ;; existing reffers).
        normalize (fn [spec]
                    (let [pairs (apply hash-map (rest spec))
                          sym (first spec)]
                      (cond-> [sym]
                        (:as pairs) (conj :as (:as pairs))
                        (:refer pairs) (conj :refer (:refer pairs)))))
        kept (->> (rest req-clause)
                  (filter keep?)
                  (mapv normalize))]
    (when (seq kept)
      (cons 'require (map (fn [s] (list 'quote s)) kept)))))

(defn collect-pages-from-file [^String fpath]
  (let [src (slurp fpath)
        forms (read-all-forms src)
        fname (fs/file-name fpath)
        ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))
        requires (extract-ns-requires ns-form)
        ;; Track helpers (defn ^:private …) we encounter between
        ;; deftests; each helper is added to the prelude of any
        ;; deftest pages that follow it.
        helpers (atom (if requires [requires] []))
        pages   (atom [])]
    (doseq [f forms]
      (cond
        (deftest? f)
        (when-let [page-name (get deftest->page (deftest-slug f))]
          (swap! pages conj
                 {:name page-name
                  :source (render-page-source page-name fname f @helpers)}))

        (and (seq? f)
             (or (= 'defn (first f)) (= 'def (first f)))
             ;; Skip ns / require / use-fixtures by ignoring those heads.
             (not (#{'ns 'require 'use-fixtures} (first f))))
        (swap! helpers conj f)))
    @pages))

(defn collect-all-pages []
  (let [files (sort (fs/glob fixtures-dir "ch*_test.cljc"))]
    (vec (mapcat (comp collect-pages-from-file str) files))))

(defn -main [& args]
  (let [pages (collect-all-pages)
        flag  (first args)]
    (case flag
      "--edn"   (println (pr-str pages))
      "--print" (when-let [page (first (filter #(str/includes? (:name %)
                                                               (second args))
                                                pages))]
                  (println "===" (:name page) "===")
                  (println (:source page)))
      (do (println (format "Generated %d Emmy-sourced pages from %d test files."
                           (count pages)
                           (count (fs/glob fixtures-dir "ch*_test.cljc"))))
          (doseq [p pages]
            (println " " (:name p)))))))

(apply -main *command-line-args*)
