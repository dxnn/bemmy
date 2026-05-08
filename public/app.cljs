;; No (ns ...) form: stay in `user` so the plugin init's
;; (require '[emmy.env :refer :all]) carries over to user-typed code.
(require '[reagent.core :as r]
         '[reagent.dom :as rdom]
         '[emmy.env :refer :all])

(def basics-page
  ";; ====================================================================
;; BEmmy — Welcome!
;; ====================================================================
;; Emmy is a computer algebra system written in Clojure(Script). 
;; It's based on SICMUtils, which is based on scmutils, from SICM.
;; 
;; This is that, but in the browser.
;;
;; It's pretty experimental, so let me know if things get weird.
;; 
;; emmy.env is pre-referred, so D, square, simplify, cos, sin, up, down,
;; literal-function, definite-integral, etc. are all in scope.
;; ====================================================================


;; ----- Clojure: arithmetic, let, lambdas ------------------------------
(+ 1 2 3)

(* 2 3 4)

(let [m 2 k 1]
  (Math/sqrt (/ k m)))           ; angular frequency of a harmonic osc.

((fn [x] (* x x)) 7)


;; ----- Symbolic differentiation ---------------------------------------
;; D is the derivative operator. Apply it to any function — Clojure or
;; Emmy — and you get back a new function.

((D (fn [x] (square x))) 'x)            ; symbolic — (* 2 x)

((D (fn [x] (square x))) 3)             ; numeric  — 6

((D (D (fn [x] (cube x)))) 'x)          ; second derivative of x³

((D sin) 'x)                            ; (cos x)

;; D works on Clojure-only fns that use Emmy's generic ops:

((D (fn [x] (+ (sin x) (* x x)))) 'x)


;; ----- Simplification + TeX rendering ---------------------------------
;; The result pane auto-renders Emmy expressions to TeX. simplify
;; reduces redundant structure first.

(simplify
  (+ (square (sin 'x))
     (square (cos 'x))))                ; → 1

(simplify ((D (fn [x] (* x x x))) 'x))


;; ----- Vectors, tuples, structures ------------------------------------
;; Emmy uses 'up' (column / position) and 'down' (row / momentum)
;; tuples to mirror the geometric distinction.

(up 1 2 3)
(down 1 2 3)
(* (down 1 2 3) (up 4 5 6))             ; inner product → 32

;; Differentiating a vector-valued function gives a vector of partials.

((D (fn [r] (up (cos r) (sin r)))) 't)
")

(def sicm-page
  ";; ====================================================================
;; BEmmy — SICM
;; ====================================================================
;; Lagrangian mechanics, action principles, find-path, etc. Lean on
;; Emmy's emmy.env (pre-referred): D, square, sin, cos, up, down,
;; coordinate, velocity, Lagrange-equations, find-path, etc.
;;
;; Translating SICM book code: click 'SICM → Emmy' in the toolbar to
;; open a translator panel — paste scmutils Scheme on the left, click
;; 'Insert at cursor' to drop the converted Clojure into this editor.
;; ====================================================================


;; ----- The harmonic-oscillator Lagrangian -----------------------------
;; A Lagrangian L(t, q, v) for the harmonic oscillator is m·v²/2 −
;; k·q²/2. Define it the SICM-book way: a curried function that takes
;; the parameters first and returns a function of the local tuple.

(defn L-harmonic [m k]
  (fn [local]
    (let [q (coordinate local)
          v (velocity local)]
      (- (* 1/2 m (square v))
         (* 1/2 k (square q))))))


;; ----- Equations of motion --------------------------------------------
;; Lagrange-equations turns a Lagrangian into the Euler–Lagrange
;; equations as functions of t.

(((Lagrange-equations (L-harmonic 'm 'k))
  (literal-function 'q))
 't)

;; That's m·q''(t) + k·q(t) = 0.


;; ----- Path-finding numerically ---------------------------------------
;; find-path minimizes the action functional over an n-coefficient
;; polynomial path between (t0, q0) and (t1, q1).

(find-path (L-harmonic 1.0 1.0)
           0.0 1.0
           (/ Math/PI 2) 0.0
           2)

;; The result is a polynomial path you can call as a function:

(let [path (find-path (L-harmonic 1.0 1.0)
                      0.0 1.0 (/ Math/PI 2) 0.0 2)]
  (path 0.5))                          ; q(0.5)


;; ----- Plot the path --------------------------------------------------
;; (See Graphics for the full 2D visualization tour.)

(let [path (find-path (L-harmonic 1.0 1.0)
                      0.0 1.0 (/ Math/PI 2) 0.0 2)]
  (plot path [0 (/ Math/PI 2)] [0 1.2]))
")

(def graphics-3d-page
  ";; ====================================================================
;; BEmmy — 3D Graphics
;; ====================================================================
;; 3D visualization via MathBox 2 (WebGL on Three.js). Click and drag
;; the rendered scene to rotate; scroll to zoom.
;;
;; Two namespaces are pre-aliased:
;;   mathbox  →  mathbox.core         the [mathbox/MathBox] container
;;   mb       →  mathbox.primitives   Cartesian, Axis, Interval, ...
;;
;; MathBox uses a data → draw composition pattern: a 'data' primitive
;; (Interval, Area, Volume) emits points; a 'draw' primitive (Line,
;; Surface, Point) consumes them.
;; ====================================================================


;; ----- Cartesian with three axes --------------------------------------

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1 :width 3}]
    [mb/Axis {:axis 2 :width 3}]
    [mb/Axis {:axis 3 :width 3}]]]


;; ----- A 3D parametric helix ------------------------------------------
;; Interval emits points along t; Line draws them as a curve.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}]
    [mb/Axis {:axis 2}]
    [mb/Axis {:axis 3}]
    [mb/Interval
      {:range    [0 (* 4 Math/PI)]
       :width    256
       :channels 3
       :expr     (fn [emit t i time]
                   (emit (Math/cos t) (* 0.2 t) (Math/sin t)))}]
    [mb/Line {:width 4 :color \"#3090ff\"}]]]


;; ----- A 3D surface z = f(x, y) ---------------------------------------
;; Area emits a grid of points; Surface draws them.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}]
    [mb/Axis {:axis 2}]
    [mb/Axis {:axis 3}]
    [mb/Area
      {:rangeX   [-2 2]
       :rangeY   [-2 2]
       :width    32
       :height   32
       :channels 3
       :expr     (fn [emit x y i j time]
                   (emit x (Math/sin (* x y)) y))}]
    [mb/Surface {:shaded true :color \"#3090ff\"}]]]


;; ====================================================================
;; SICM exercises
;; ====================================================================
;;
;; The 'time' arg in :expr callbacks is MathBox's global animation
;; clock; reference it inside expr to animate without writing your own
;; timer. The clock advances automatically.


;; ----- Animated rotating helix ----------------------------------------
;; Same helix as above, but rotated by `time` so it spins continuously.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Interval
      {:range    [0 (* 4 Math/PI)]
       :width    256
       :channels 3
       :expr     (fn [emit t i time]
                   (emit (Math/cos (+ t time))
                         (* 0.2 t)
                         (Math/sin (+ t time))))}]
    [mb/Line {:width 4 :color \"#3090ff\"}]]]


;; ----- 3D Lissajous knot ----------------------------------------------
;; Three perpendicular harmonic oscillations with rational frequency
;; ratios trace closed knot-like curves — what a particle in a 3D
;; anisotropic harmonic well would do.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-1.5 1.5] [-1.5 1.5] [-1.5 1.5]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Interval
      {:range    [0 (* 2 Math/PI)]
       :width    512
       :channels 3
       :expr     (fn [emit t i time]
                   (emit (Math/cos (* 3 t))
                         (Math/sin (* 4 t))
                         (Math/sin (* 5 t))))}]
    [mb/Line {:width 3 :color \"#a060ff\"}]]]


;; ----- Lagrangian L(q, v) = ½v² − ½q² as a surface --------------------
;; The harmonic-oscillator Lagrangian over its (q, v) phase space.
;; Saddle-shaped: kinetic energy lifts in v, potential energy depresses
;; in q.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Area
      {:rangeX   [-1.5 1.5]
       :rangeY   [-1.5 1.5]
       :width    32
       :height   32
       :channels 3
       :expr     (fn [emit q v i j time]
                   (emit q (- (* 0.5 v v) (* 0.5 q q)) v))}]
    [mb/Surface {:shaded true :color \"#3090ff\"}]]]


;; ----- Animated harmonic-oscillator orbit -----------------------------
;; A particle's phase-space trajectory unwound along time. The (q, v)
;; circle from Graphics is here a helix in (q, t, v) space, lifting
;; with t as the particle oscillates.

[mathbox/MathBox
  {:container {:style {:height \"400px\" :width \"100%\"}}}
  [mb/Cartesian
    {:range [[-1.5 1.5] [0 (* 2 Math/PI)] [-1.5 1.5]] :scale [1 1.5 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    [mb/Interval
      {:range    [0 (* 2 Math/PI)]
       :width    256
       :channels 3
       :expr     (fn [emit t i time]
                   (emit (Math/cos t) t (- (Math/sin t))))}]
    [mb/Line {:width 4 :color \"#1f883d\"}]]]
")

(def graphics-page
  ";; ====================================================================
;; GRAPHICS — visualization tour
;; ====================================================================
;; Three layers, simplest to most flexible:
;;
;;   plot               one-shot plot of y = f(x)
;;   plot-with-params   plot with slider-controlled parameters
;;   frame + plot-fn    SICM-book imperative graphics model
;;
;; Plus the underlying Mafs hiccup for finer control.
;; Evaluate sections individually with Cmd-Enter on a single form.
;; ====================================================================


;; A few examples below use the harmonic-oscillator Lagrangian. Define
;; it once here; it'll stay in scope for every form on this page.

(defn L-harmonic [m k]
  (fn [local]
    (let [q (coordinate local)
          v (velocity local)]
      (- (* 1/2 m (square v))
         (* 1/2 k (square q))))))


;; ----- Layer 1: plot ----------------------------------------------------
;; Anything callable that returns a number for numeric x: a Clojure fn,
;; an Emmy polynomial, a path returned from find-path, Math/sin, ...
;; Domain defaults to [-5,5] x [-5,5]; pass extra args to override.

(plot Math/sin)

(plot Math/cos [(- Math/PI) Math/PI])

(plot (fn [x] (* x x x)) [-3 3] [-10 10])

;; The path returned by Emmy's find-path is just a callable polynomial:
(let [path (find-path (L-harmonic 1.0 1.0) 0.0 1.0 (/ Math/PI 2) 0.0 2)]
  (plot path [0 (/ Math/PI 2)] [0 1.2]))


;; ----- Layer 2: plot-with-params ---------------------------------------
;; A Leva slider panel plus a plot. f receives (params, x); the params
;; come from the panel's current values. Drag a slider and the curve
;; updates in real time.

(plot-with-params
  (fn [{:keys [m k]} t] (* m (Math/sin (* k t))))
  {:m {:value 1 :min 0   :max 5 :step 0.05}
   :k {:value 1 :min 0.1 :max 5 :step 0.05}}
  [0 (* 2 Math/PI)]
  [-5 5])

;; The schema map is straight Leva config: :value seeds the initial
;; slider position, :min/:max/:step shape the range. Any keys f
;; destructures must appear in the schema.


;; ----- Layer 3: SICM-book imperative graphics --------------------------
;; (frame x-min x-max y-min y-max) returns a graphics window — a Reagent
;; atom holding a viewBox and a vector of drawables. The book's
;; graphics-clear / plot-function / plot-point all mutate it. (show win)
;; renders it, and BEmmy auto-shows when the last form returns a frame.

(def gfx-win (frame -3 3 -3 3))

(graphics-clear gfx-win)
(plot-function gfx-win Math/sin -3 3 0.05)
(plot-function gfx-win Math/cos -3 3 0.05)
gfx-win   ; auto-shows the accumulated curves

;; (plot-path win path t0 t1) is a one-liner for the common case of
;; 'clear, plot one curve, return win':

(let [win  (frame 0 (/ Math/PI 2) 0 1.2)
      path (find-path (L-harmonic 1.0 1.0) 0.0 1.0 (/ Math/PI 2) 0.0 2)]
  (plot-path win path 0 (/ Math/PI 2)))


;; ----- Underlying Mafs hiccup ------------------------------------------
;; All the helpers above produce Reagent hiccup using mafs.cljs. Drop
;; down to that level for full control. Note: VECTORS, not parens —
;; these are Reagent components, not function calls.

[mafs/Mafs {:viewBox {:x [-2 2] :y [-2 2]}}
 [mafs.coordinates/Cartesian]
 [mafs.plot/Parametric
  {:t  [0 (* 2 Math/PI)]
   :xy (fn [t] [(Math/cos t) (Math/sin t)])}]]


;; ----- emmy-viewers high-level helpers ---------------------------------
;; emmy.mafs/parametric, emmy.mafs/of-x and friends compile their f
;; through Emmy's expression machinery. They expect SYMBOLIC Emmy
;; primitives (cos, sin, up, ...), not raw JS Math/cos. Wrap with
;; emmy.mafs/mafs to provide the Mafs context.

(emmy.mafs/mafs
  {:viewBox {:x [-2 2] :y [-2 2]}}
  (emmy.mafs/parametric
    {:t [0 6.28]
     :xy (fn [t] (up (cos t) (sin t)))}))


;; ====================================================================
;; SICM exercises
;; ====================================================================


;; ----- Optimized harmonic-oscillator path -----------------------------
;; The numerically-optimized path for L = ½v² − ½q² between (0, 1) and
;; (π/2, 0) should track cos(t) closely.

(plot
  (find-path (L-harmonic 1.0 1.0) 0.0 1.0 (/ Math/PI 2) 0.0 2)
  [0 (/ Math/PI 2)] [0 1.2])


;; ----- Phase portrait of the harmonic oscillator ----------------------
;; In phase space (q, v), motion under L = ½v² − ½q² traces a circle.
;; Mafs's Parametric draws (x(t), y(t)) over a t-range.

[mafs/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
 [mafs.coordinates/Cartesian]
 [mafs.plot/Parametric
   {:t  [0 (* 2 Math/PI)]
    :xy (fn [t] [(Math/cos t) (- (Math/sin t))])}]]


;; ----- Travelling wave ψ(x, t) = sin(x − ct) --------------------------
;; animate ticks t for you; Cmd-Enter and watch.

(animate (fn [t x] (Math/sin (- x t)))
         [(- (* 2 Math/PI)) (* 2 Math/PI)]
         [-1.5 1.5])


;; ----- Standing wave ψ = sin(x) · cos(t) ------------------------------
;; The classic 'string fixed at both ends' visualization.

(animate (fn [t x] (* (Math/sin x) (Math/cos t)))
         [(- (* 2 Math/PI)) (* 2 Math/PI)]
         [-1.5 1.5])


;; ----- Damped oscillator with sliders ---------------------------------
;; Drag γ to see the envelope tighten; drag ω to see the frequency
;; shift. plot-with-params reactively re-evaluates on each tick.

(plot-with-params
  (fn [{:keys [omega gamma]} t]
    (* (Math/exp (- (* gamma t)))
       (Math/cos (* omega t))))
  {:omega {:value 2 :min 0.5 :max 6   :step 0.05}
   :gamma {:value 0.3 :min 0   :max 1.5 :step 0.01}}
  [0 (* 4 Math/PI)] [-1.2 1.2])
")

(def auto-graph-page
  ";; ====================================================================
;; BEmmy — Auto-graph
;; ====================================================================
;; The 'Auto-graph' button in the toolbar opens a shelf that wraps an
;; Emmy expression in the appropriate graphics form. Pick a kind from
;; the dropdown (Plot / Parametric 2D / Parametric 3D / Surface /
;; Animate), paste your expression on the left, see the wrapped form
;; on the right. 'Insert at cursor' drops it into the editor.
;;
;; The shelf does NOT evaluate your code. It's purely textual, so it
;; can never freeze the page on something expensive like (find-path …).
;;
;; It handles three shapes:
;;
;;   1. A function       — wrapped directly:
;;        Math/sin              → (plot Math/sin)
;;
;;   2. A symbolic body  — quotes stripped:
;;        (sin 'x)              → (plot (fn [x] (sin x)))
;;
;;   3. A Lagrangian     — find-path-based template per kind:
;;        (L-harmonic 'm 'k)    → (let [m 1.0 k 1.0 …
;;                                       L (L-harmonic m k)
;;                                       path (find-path L …)]
;;                                  (plot path …))
;;
;; The examples below show each shape — try copying any input into the
;; shelf with the suggested kind, or just evaluate the wrapped form
;; that follows.


;; ----- Function → Plot -----------------------------------------------
;; Source: Math/sin

(plot Math/sin)


;; ----- Symbolic body in 'x → Plot ------------------------------------
;; Source: (sin 'x)
;; The shelf strips 'x and wraps the body in (fn [x] …).

(plot (fn [x] (sin x)))


;; ----- Vector-returning fn → Parametric 2D ---------------------------
;; Source: (fn [t] [(Math/cos t) (Math/sin t)])

[mafs/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
 [mafs.coordinates/Cartesian]
 [mafs.plot/Parametric
  {:t  [0 (* 2 Math/PI)]
   :xy (fn [t] [(Math/cos t) (Math/sin t)])}]]


;; ====================================================================
;; Lagrangian magic
;; ====================================================================
;; Paste any expression containing a SICM-style (L-name args) sub-form —
;; even the full Euler-Lagrange wrapping — and the shelf builds a
;; find-path-based plot. The outer (Lagrange-equations …) wrapping is
;; intentionally discarded; what you actually want to see is q(t),
;; not the EL residual.
;;
;; Evaluate this defn before running the examples below — they all
;; reference L-harmonic. (Pasting (defn L-harmonic …) into the shelf
;; would also produce this defn alongside its plot, see the 'defn'd
;; Lagrangian' example further down.)

(defn L-harmonic [m k]
  (fn [local]
    (let [q (coordinate local)
          v (velocity local)]
      (- (* 1/2 m (square v))
         (* 1/2 k (square q))))))


;; ----- Lagrangian → Plot (q(t)) --------------------------------------
;; Source: (((Lagrange-equations (L-harmonic 'm 'k))
;;           (literal-function 'q))
;;          't)

(let [m 1.0       ; 'm
      k 1.0       ; 'k
      t0 0.0
      t1 (/ Math/PI 2)
      q0 1.0
      q1 0.0
      L    (L-harmonic m k)
      path (find-path L t0 q0 t1 q1 4)]
  (plot path [t0 t1] [-1.5 1.5]))


;; ----- Lagrangian → Parametric 2D (phase plane) ----------------------
;; Same source, pick Parametric 2D. Plots (q(t), q'(t)) — the canonical
;; SICM phase-plane visualization for a 1-DOF system.

(let [m 1.0       ; 'm
      k 1.0       ; 'k
      t0 0.0
      t1 (/ Math/PI 2)
      q0 1.0
      q1 0.0
      L    (L-harmonic m k)
      path (find-path L t0 q0 t1 q1 4)]
  [mafs/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
   [mafs.coordinates/Cartesian]
   [mafs.plot/Parametric
    {:t  [t0 t1]
     :xy (fn [t] [(path t) ((D path) t)])}]])


;; ----- Lagrangian → Animate (sliders) --------------------------------
;; Pick Animate. find-path is memoized so dragging a slider re-solves
;; the variational problem only on slider changes, not on every x
;; sample within a frame.

(let [t0 0.0
      t1 (/ Math/PI 2)
      q0 1.0
      q1 0.0
      memo-path (memoize
                  (fn [m k]
                    (find-path (L-harmonic m k) t0 q0 t1 q1 4)))]
  (plot-with-params
    (fn [{:keys [m k]} t]
      ((memo-path m k) t))
    {:m {:value 1.0 :min 0.1 :max 5.0 :step 0.1}
     :k {:value 1.0 :min 0.1 :max 5.0 :step 0.1}}
    [t0 t1] [-1.5 1.5]))


;; ----- defn'd Lagrangian ---------------------------------------------
;; Paste a (defn L-… …) form. The L- prefix tells the shelf to route
;; through the Lagrangian template, treating the defn's args as
;; quoted free symbols. Output: the defn itself, then a let-prelude
;; using the new name.
;;
;; Try pasting this free-particle Lagrangian into the shelf:

(defn L-free-particle [mass]
  (fn [local]
    (let [v (velocity local)]
      (* 1/2 mass (square v)))))

(let [mass 1.0       ; 'mass
      t0 0.0
      t1 (/ Math/PI 2)
      q0 1.0
      q1 0.0
      L    (L-free-particle mass)
      path (find-path L t0 q0 t1 q1 4)]
  (plot path [t0 t1] [-1.5 1.5]))


;; ====================================================================
;; Beyond auto-graph
;; ====================================================================
;; Auto-graph handles 1D Lagrangians and simple symbolic / functional
;; expressions. For problems with constraint forces, piecewise dynamics,
;; or hand-rolled visualizations, you write the form yourself. Here's
;; SICM Exercise 1.33 — a particle sliding off a horizontal cylinder —
;; with the constraint method (SICM §1.6.2) and a closed-form animation.


;; ----- Departure conditions ------------------------------------------
;; Energy conservation + (normal force = 0) at the moment of release
;; gives cos θ* = 2/3 and θ̇* = √(2g/(3R)).

(defn departure-angle
  \"Angle (rad) from the upward vertical at which the particle leaves.\"
  [_g _R]
  (Math/acos (cljs.core// 2 3)))

(defn departure-omega
  \"Angular speed |θ̇| at the moment the particle leaves the cylinder.\"
  [g R]
  (Math/sqrt (cljs.core// (* 2 g) (* 3 R))))

(let [g 9.81 R 1.0]
  {:theta-rad (departure-angle g R)
   :theta-deg (* (departure-angle g R) (cljs.core// 180 Math/PI))
   :omega     (departure-omega g R)
   :v-tangent (* R (departure-omega g R))})


;; ----- Animated trajectory --------------------------------------------
;; On the cylinder, θ̈ = (g/R) sin θ has a clean closed form (small θ₀):
;;   θ(t) = 4 atan(tan(θ₀/4) · exp(√(g/R) · t))
;; After λ → 0 the particle is in free fall with the inherited
;; tangential velocity. We splice the two phases and animate a moving
;; red marker along the resulting blue trajectory.

(defn falling-log-anim []
  (let [R       1.0
        g       9.81
        th0     0.05
        omega0  (Math/sqrt (cljs.core// g R))
        thS     (Math/acos (cljs.core// 2 3))
        thdotS  (Math/sqrt (cljs.core// (* 2 g) (* 3 R)))
        t-leave (cljs.core// (Math/log
                              (cljs.core// (Math/tan (cljs.core// thS 4))
                                           (Math/tan (cljs.core// th0 4))))
                             omega0)
        t-total (+ t-leave 0.4)
        pos     (fn [t]
                  (if (< t t-leave)
                    (let [th (* 4 (Math/atan
                                   (* (Math/tan (cljs.core// th0 4))
                                      (Math/exp (* omega0 t)))))]
                      [(* R (Math/sin th)) (* R (Math/cos th))])
                    (let [dt (- t t-leave)
                          vx (* R thdotS (Math/cos thS))
                          vy (- (* R thdotS (Math/sin thS)))
                          x0 (* R (Math/sin thS))
                          y0 (* R (Math/cos thS))]
                      [(+ x0 (* vx dt))
                       (- (+ y0 (* vy dt)) (* 0.5 g dt dt))])))
        !t      (reagent.core/atom 0)
        !start  (atom nil)
        timer   (atom nil)]
    (reagent.core/create-class
     {:component-did-mount
      (fn [_]
        (reset! !start (.now js/Date))
        (reset! timer
                (js/setInterval
                 (fn []
                   (let [elapsed (cljs.core// (cljs.core/- (.now js/Date)
                                                           (deref !start))
                                              1000.0)]
                     (reset! !t (cljs.core/mod elapsed t-total))))
                 16)))
      :component-will-unmount
      (fn [_] (when (deref timer) (js/clearInterval (deref timer))))
      :reagent-render
      (fn [_]
        (let [t     @!t
              [x y] (pos t)]
          [mafs/Mafs {:viewBox {:x [-1.5 2.5] :y [-1.5 1.5]}}
           [mafs.coordinates/Cartesian]
           [mafs.plot/Parametric
            {:t  [0 (* 2 Math/PI)]
             :xy (fn [s] [(* R (Math/cos s)) (* R (Math/sin s))])}]
           [mafs.plot/Parametric
            {:t  [0 t-total]
             :xy pos
             :color \"rgb(120,160,255)\"}]
           [mafs.core/Point
            {:x (double (* R (Math/sin thS)))
             :y (double (* R (Math/cos thS)))
             :color \"#888\"}]
           [mafs.core/Point
            {:x (double x) :y (double y)
             :color \"#d33\"}]]))})))

[falling-log-anim]
")

;; --- System pages: read-only templates baked into the build. Editing
;; one transparently forks it into a fresh user page so the template
;; itself stays canonical and updates whenever we ship new content.
;; array-map preserves insertion order so the dropdown renders these in
;; the order written here rather than alphabetically.
(def system-pages
  (array-map
    "Welcome"    basics-page
    "SICM"       sicm-page
    "Graphics"   graphics-page
    "Auto-graph" auto-graph-page
    "3D"         graphics-3d-page))

;; --- Pages: named source buffers persisted in localStorage. ----------------

(def storage-key "emmy-playground/v1")

(defn- next-fork-name
  "Generate 'base 1', 'base 2', ... that doesn't already exist."
  [base existing]
  (loop [n 1]
    (let [candidate (str base " " n)]
      (if (contains? existing candidate)
        (recur (inc n))
        candidate))))

;; --- Share-by-URL ---------------------------------------------------------
;; Source is base64-of-utf8 in the URL hash under #s=…
;; On first load with such a hash, we drop the snippet into a fresh user
;; page named "Shared" (or "Shared N") and clear the hash so refreshes
;; don't keep re-importing.

(defn- encode-share [m]
  (.btoa js/window
         (js/unescape (js/encodeURIComponent
                       (js/JSON.stringify (clj->js m))))))

(defn- decode-share [b64]
  (try
    (js->clj (js/JSON.parse (js/decodeURIComponent
                             (js/escape (.atob js/window b64))))
             :keywordize-keys true)
    (catch :default _ nil)))

(defn- share-payload-from-url []
  (let [h (.. js/window -location -hash)]
    (when (and (string? h) (.startsWith h "#s="))
      (decode-share (subs h 3)))))

(defn- next-free-name
  "Like next-fork-name, but checks an arbitrary set of taken names."
  [base taken]
  (if-not (contains? taken base) base
    (loop [n 1]
      (let [c (str base " " n)]
        (if (contains? taken c) (recur (inc n)) c)))))

(defn- clear-url-hash! []
  (try
    (.replaceState js/history nil ""
                   (str (.. js/window -location -pathname)
                        (.. js/window -location -search)))
    (catch :default _ nil)))

(declare save-state!)

(defn- load-state []
  (let [stored (or (try (when-let [s (.getItem js/localStorage storage-key)]
                          (let [obj (js/JSON.parse s)
                                c   (js->clj (.-current obj))]
                            {:pages   (js->clj (.-pages obj))
                             :current [(keyword (first c)) (second c)]}))
                        (catch :default _ nil))
                   {:pages {} :current [:system "Welcome"]})
        ;; Validate :current — the page might point to a system page we
        ;; renamed/removed. Fall back to Welcome so the editor mounts.
        [t cur-name] (:current stored)
        valid?  (case t
                  :user   (contains? (:pages stored) cur-name)
                  :system (contains? system-pages cur-name)
                  false)
        stored  (cond-> stored
                  (not valid?) (assoc :current [:system "Welcome"]))
        shared (share-payload-from-url)]
    (if (and shared (string? (:src shared)))
      (let [taken  (into (set (keys (:pages stored)))
                         (keys system-pages))
            target (next-free-name (or (not-empty (:name shared)) "Shared")
                                   taken)
            state  (-> stored
                       (assoc-in [:pages target] (:src shared))
                       (assoc :current [:user target]))]
        (clear-url-hash!)
        ;; Persist the imported page immediately. The :persist watch
        ;; only fires on subsequent !pages changes, so without this an
        ;; unedited imported page would vanish on the next reload.
        (save-state! state)
        state)
      stored)))

(defn- save-state! [{:keys [pages current]}]
  (.setItem js/localStorage storage-key
            (js/JSON.stringify #js {:pages   (clj->js pages)
                                    :current (clj->js
                                              [(name (first current))
                                               (second current)])})))

(defonce !pages (r/atom (load-state)))
(defonce _persist (add-watch !pages :persist
                             (fn [_ _ _ new] (save-state! new))))

(defn- current-page-source []
  (let [{:keys [current pages]} @!pages
        [t n] current]
    (case t
      :user   (get pages n "")
      :system (get system-pages n "")
      "")))

(defn- update-current-source!
  "Persist edits to the current user page, or fork-on-edit if the
   current view is a system page (template). The fork picks the next
   unused 'base N' name in user pages and switches to it. Programmatic
   loads of system content into CM produce an unchanged src and don't
   trigger a fork."
  [src]
  (let [{:keys [current pages] :as state} @!pages
        [t n] current]
    (case t
      :system
      (let [system-src (get system-pages n)]
        (when (not= src system-src)
          (let [nn (next-fork-name n pages)]
            (reset! !pages (-> state
                               (assoc-in [:pages nn] src)
                               (assoc :current [:user nn]))))))
      :user
      (when (not= src (get pages n))
        (swap! !pages assoc-in [:pages n] src)))))

(defonce !view   (atom nil))            ; the CodeMirror EditorView
(defonce !result (r/atom {:status :idle}))

;; --- UI prefs (vim mode etc.), persisted separately from page content ---
(def ui-storage-key "emmy-playground/ui")

(defn- load-ui []
  ;; Merge over defaults so old localStorage payloads (without :paredit-on?)
  ;; still come back with the field set, and new defaults can be added later.
  (merge {:vim-on false :paredit-on? true}
         (or (try (when-let [s (.getItem js/localStorage ui-storage-key)]
                    (js->clj (js/JSON.parse s) :keywordize-keys true))
                  (catch :default _ nil))
             {})))

(defonce !ui (r/atom (load-ui)))

;; A small ephemeral notification for things like "Share URL copied".
(defonce !toast (r/atom nil))
(defonce ^:private !toast-timer (atom nil))

(defn- toast! [msg]
  (reset! !toast msg)
  (when-let [t @!toast-timer] (js/clearTimeout t))
  (reset! !toast-timer
          (js/setTimeout #(do (reset! !toast nil)
                              (reset! !toast-timer nil))
                         2200)))

;; current-source is defined below; declared here so share-current!
;; analyses cleanly under SCI's eager symbol resolution.
(declare current-source)

(defn- share-current! []
  (when-let [src (current-source)]
    (let [[_ cur-name] (:current @!pages)
          payload      {:name cur-name :src src}
          url (str (.. js/window -location -origin)
                   (.. js/window -location -pathname)
                   "#s=" (encode-share payload))]
      (-> (.. js/navigator -clipboard (writeText url))
          (.then  (fn [_] (toast! "Share URL copied to clipboard")))
          (.catch (fn [_]
                    (js/prompt "Copy this URL:" url)
                    (toast! "Copy this URL")))))))

(defn- prefers-dark? []
  (try (.-matches (.matchMedia js/window "(prefers-color-scheme: dark)"))
       (catch :default _ false)))

(defonce !dark? (r/atom (prefers-dark?)))

(defonce _theme-listener
  (try
    (.addEventListener (.matchMedia js/window "(prefers-color-scheme: dark)")
                       "change" (fn [e] (reset! !dark? (.-matches e))))
    (catch :default _ nil)))
(defonce _persist-ui
  (add-watch !ui :save
             (fn [_ _ _ new]
               (.setItem js/localStorage ui-storage-key
                         (js/JSON.stringify (clj->js new))))))

(defn- load-into-editor! [src]
  (when-let [view @!view]
    (.dispatch view #js {:changes #js {:from   0
                                       :to     (.. view -state -doc -length)
                                       :insert src}})))

(defn- switch-to-user! [n]
  (when (contains? (:pages @!pages) n)
    (swap! !pages assoc :current [:user n])
    (load-into-editor! (get-in @!pages [:pages n]))))

(defn- switch-to-system! [n]
  (when (contains? system-pages n)
    (swap! !pages assoc :current [:system n])
    (load-into-editor! (get system-pages n))))

(defn- new-page! []
  (when-let [n (some-> (js/prompt "Page name:") .trim not-empty)]
    (when-not (contains? (:pages @!pages) n)
      (swap! !pages assoc-in [:pages n] ""))
    (switch-to-user! n)))

(defn- delete-page! [n]
  (when (js/confirm (str "Delete \"" n "\"?"))
    (let [{:keys [pages current]} @!pages
          new-pages   (dissoc pages n)
          deleting?   (= current [:user n])
          new-current (cond
                        (not deleting?)  current
                        (seq new-pages)  [:user (first (sort (keys new-pages)))]
                        :else            [:system "Welcome"])]
      (reset! !pages {:pages new-pages :current new-current})
      (when deleting?
        (let [[t nn] new-current]
          (case t :user (switch-to-user! nn) :system (switch-to-system! nn)))))))

(defn- normalize-ws
  "SCI's reader treats non-ASCII whitespace (NBSP, em-space, line-separator
   etc.) as token characters and chokes when they appear in pasted source.
   Replace common offenders with regular spaces; preserve newlines."
  [s]
  (-> s
      (.replace (js/RegExp.
                 "[\\u00A0\\u1680\\u2000-\\u200B\\u202F\\u205F\\u3000\\uFEFF]"
                 "g") " ")
      (.replace (js/RegExp. "[\\u2028\\u2029]" "g") "\n")))

(defn- current-source []
  (when-let [v @!view]
    (.. v -state -doc toString)))

(defn- emmy-fragment?
  "emmy-viewers helpers (parametric, of-x, vector-field, ...) return a
   quoted reagent form tagged with :portal.viewer/reagent? metadata so a
   downstream renderer (Clerk/Portal) knows to eval it. We replicate that
   eval step ourselves."
  [v]
  (when-let [m (try (meta v) (catch :default _ nil))]
    (or (:portal.viewer/reagent? m)
        (contains? m :nextjournal.clerk.viewer/viewer))))

(defn- expand-fragment
  "Re-evaluate a fragment form through SCI so the embedded macros
   (reagent.core/with-let etc.) expand and the symbol references
   (mafs.plot/Parametric etc.) resolve to actual Reagent component fns."
  [v]
  (try
    (js/scittle.core.eval_string (pr-str v))
    (catch :default _ v)))

(defn- eval-with-tex [src]
  ;; maybe-show turns a SICM-style frame atom into Mafs hiccup so the
  ;; user can leave `win2` (or any frame) as the last form and see the
  ;; plot inline; non-frames pass through untouched.
  (let [wrapped (str "(let [v# (do " src ")]\n"
                     "  [(maybe-show v#)\n"
                     "   (try (emmy.expression.render/->TeX v#)\n"
                     "        (catch :default _ nil))])")
        [v tex] (js/scittle.core.eval_string wrapped)]
    (if (emmy-fragment? v)
      {:value (expand-fragment v) :tex nil}
      {:value v :tex tex})))

(defn- ws-char? [c]
  (case c (" " "\n" "\t" "\r" ",") true false))

(defn- split-top-forms
  "Split src into a vector of top-level form strings. Tracks bracket depth,
   strings, line comments, and char-literal escapes (\\( \\) \\\" etc.).
   Naked top-level forms (e.g. a bare symbol) are also captured."
  [src]
  (let [n (count src)]
    (loop [i 0 start nil depth 0
           in-str false in-cmt false esc false
           acc []]
      (if (>= i n)
        (cond-> acc
          start (conj (clojure.string/trim (subs src start n))))
        (let [c (.charAt src i)]
          (cond
            esc      (recur (inc i) start depth in-str in-cmt false acc)
            in-str   (case c
                       "\\" (recur (inc i) start depth true in-cmt true  acc)
                       "\"" (recur (inc i) start depth false in-cmt false acc)
                       (recur (inc i) start depth true in-cmt false acc))
            in-cmt   (if (= c "\n")
                       (recur (inc i) start depth false false false acc)
                       (recur (inc i) start depth false true  false acc))
            (= c "\\") (recur (+ i 2) (or start i) depth false false false acc)
            (= c ";")  (recur (inc i) start depth false true  false acc)
            (= c "\"") (recur (inc i) (or start i) depth true  false false acc)
            (or (= c "(") (= c "[") (= c "{"))
            (recur (inc i) (or start i) (inc depth) false false false acc)
            (or (= c ")") (= c "]") (= c "}"))
            (let [d' (dec depth) end (inc i)]
              (if (and start (zero? d'))
                (recur end nil 0 false false false
                       (conj acc (clojure.string/trim (subs src start end))))
                (recur end start d' false false false acc)))
            (and start (zero? depth) (ws-char? c))
            (recur (inc i) nil 0 false false false
                   (conj acc (clojure.string/trim (subs src start i))))
            (and (nil? start) (not (ws-char? c)))
            (recur (inc i) i depth false false false acc)
            :else (recur (inc i) start depth false false false acc)))))))

(defn- top-forms [src]
  (filterv (complement clojure.string/blank?)
           (split-top-forms (normalize-ws src))))

(defonce !eval-id (atom 0))

(defn eval! []
  (when-let [src (current-source)]
    (let [eval-id (swap! !eval-id inc)
          results (reduce
                   (fn [acc form-src]
                     (try
                       (let [{:keys [value tex]} (eval-with-tex form-src)]
                         (conj acc {:form  form-src
                                    :value value      ; raw, for hiccup detection
                                    :pr    (pr-str value)
                                    :tex   tex}))
                       (catch :default e
                         (reduced
                          (conj acc {:form form-src
                                     :err  (or (.-message e) (str e))})))))
                   []
                   (top-forms src))]
      (reset! !result {:status :ok :results results :eval-id eval-id}))))

(defn- escape-html [s]
  (-> s
      (.replace (js/RegExp. "&" "g") "&amp;")
      (.replace (js/RegExp. "<" "g") "&lt;")
      (.replace (js/RegExp. ">" "g") "&gt;")))

(defn- highlight-clojure
  "Render a Clojure source string as a span of HTML with hljs's tokens.
   Falls back to a safely-escaped plain string if hljs or its Clojure
   language module aren't loaded, or if highlighting throws for any
   other reason — never let a display issue tank evaluation."
  [s]
  (or (when (and (exists? js/hljs)
                 (.getLanguage js/hljs "clojure"))
        (try
          (.-value (.highlight js/hljs s #js {:language       "clojure"
                                              :ignoreIllegals true}))
          (catch :default _ nil)))
      (escape-html s)))

(defn- error-boundary
  "React error boundary so a plot rendering error doesn't tear down the
   whole page. The boundary's state is per-instance; pairing it with a
   :key on the parent forces a fresh boundary each evaluation."
  [_child]
  (let [!err (r/atom nil)]
    (r/create-class
     {:display-name "PlotErrorBoundary"
      :component-did-catch
      (fn [_this err _info] (reset! !err err))
      :reagent-render
      (fn [child]
        (if-let [e @!err]
          [:div.err "Render error: "
           [:pre {:style {:font-size "0.7rem" :white-space "pre-wrap"
                          :margin    "0.25rem 0 0 0"}}
            (str e)]]
          child))})))

(defn- katex-block [tex]
  (let [!node   (atom nil)
        render! (fn []
                  (when-let [el @!node]
                    (when (exists? js/katex)
                      (.render js/katex tex el
                               #js {:throwOnError false :displayMode true}))))]
    (r/create-class
     {:component-did-mount  render!
      :component-did-update render!
      :reagent-render
      (fn [_]
        [:div.tex {:ref #(reset! !node %)}])})))

(defn- mount-cm! [el]
  (when (and el (nil? @!view) (exists? js/CM))
    (let [eval-cmd #js {:key "Mod-Enter" :run (fn [_] (eval!) true)}
          ;; Firefox-only: pressing Escape on a contenteditable can blur
          ;; the editor before vim's keymap sees the key, leaving vim
          ;; stuck in insert mode. A no-op binding with :preventDefault
          ;; stops the browser default (the blur) without claiming the
          ;; key, so vim's lower-precedence Escape still runs and exits
          ;; insert mode as expected.
          escape-cmd #js {:key            "Escape"
                          :preventDefault true
                          :run            (fn [_] false)}
          ;; Wrap our high-priority bindings in Prec.highest so vim mode
          ;; (or any other keymap) can't shadow them.
          user-keymap (cond->> (.of js/CM.keymap #js [eval-cmd escape-cmd])
                        js/CM.Prec (.highest js/CM.Prec))
          ;; Persist edits to the current page on every doc change.
          save-listener (.of (.. js/CM -EditorView -updateListener)
                             (fn [update]
                               (when (.-docChanged update)
                                 (update-current-source!
                                  (.. update -state -doc toString)))))
          ;; Compose extensions ourselves; skip anything the ESM didn't deliver.
          ;; Vim is conditionally prepended below so its keymap goes first.
          dark? @!dark?
          exts (cond-> [user-keymap save-listener]
                 js/CM.lineNumbers         (conj (js/CM.lineNumbers))
                 js/CM.history             (conj (js/CM.history))
                 js/CM.drawSelection       (conj (js/CM.drawSelection))
                 js/CM.highlightActiveLine (conj (js/CM.highlightActiveLine))
                 js/CM.bracketMatching     (conj (js/CM.bracketMatching))
                 ;; Light mode: bind defaultHighlightStyle. Dark mode:
                 ;; oneDark brings its own theme + HighlightStyle. Don't
                 ;; layer them — last-write-wins resolution leaves
                 ;; clojure-mode tags partially light-themed and unreadable.
                 (and (not dark?)
                      js/CM.syntaxHighlighting
                      js/CM.defaultHighlightStyle)
                 (conj (js/CM.syntaxHighlighting js/CM.defaultHighlightStyle))
                 ;; Paredit on: full clojure-mode bundle (syntax + close-
                 ;; brackets keymap + format-on-change filter + …).
                 ;; Paredit off: just the Clojure language definition so we
                 ;; keep syntax highlighting and indent rules without the
                 ;; auto-pair / skip-over / re-format behaviour the user
                 ;; can't disable any other way.
                 (and js/CM.defaultExtensions (:paredit-on? @!ui))
                 (conj js/CM.defaultExtensions)
                 (and js/CM.cljSyntax (not (:paredit-on? @!ui)))
                 (conj (js/CM.cljSyntax))
                 js/CM.defaultKeymap       (conj (.of js/CM.keymap
                                                      js/CM.defaultKeymap))
                 js/CM.historyKeymap       (conj (.of js/CM.keymap
                                                      js/CM.historyKeymap))
                 js/CM.completeKeymap      (conj (.of js/CM.keymap
                                                      js/CM.completeKeymap))
                 (and dark? js/CM.oneDark) (conj js/CM.oneDark))
          exts (cond->> exts
                 (and js/CM.vim (:vim-on @!ui)) (into [(js/CM.vim)]))
          state (.create js/CM.EditorState
                         #js {:doc        (current-page-source)
                              :extensions (clj->js exts)})
          view  (js/CM.EditorView. #js {:parent el :state state})]
      (reset! !view view))))

(defn- cm-editor []
  (r/create-class
   {:component-will-unmount (fn [_]
                              (when-let [v @!view] (.destroy v))
                              (reset! !view nil))
    :reagent-render
    (fn [_] [:div.cm-host {:ref mount-cm!}])}))

;; --- SICM → Emmy translator shelf ------------------------------------------

(defonce !shelf (r/atom {:open? false :input "" :output ""}))

(defn- translate-scheme [src]
  (try
    (if (exists? js/SicmToEmmy)
      (.translate js/SicmToEmmy src)
      ";; SicmToEmmy not loaded — check sicm2emmy.js script tag.")
    (catch :default e
      (str ";; Translation error: " (or (.-message e) (str e))))))

(defn- on-shelf-input [src]
  (swap! !shelf assoc :input src :output (translate-scheme src)))

(defn- insert-and-format!
  "Drop `code` at the editor's cursor / selection, then ask the language's
   indent service to re-flow the inserted lines so multi-line templates
   align with the surrounding bracket structure.

   The insert dispatch is marked userEvent 'noformat' so clojure-mode's
   transactionFilter doesn't reflow the whole wrapped output (which was
   previously eating closing parens). The followup indentSelection runs
   line-by-line and is idempotent enough to ride the filter — it just
   updates leading whitespace, so the filter's per-line re-format pass
   on those changes is a no-op.

   Returns true when an insert actually happened."
  [code]
  (when-let [view @!view]
    (when-not (clojure.string/blank? code)
      (let [sel  (.. view -state -selection -main)
            from (.-from sel)
            to   (.-to sel)
            end  (+ from (count code))]
        (.dispatch view
                   #js {:changes   #js {:from from :to to :insert code}
                        :selection #js {:anchor from :head end}
                        :userEvent "noformat"})
        (when js/CM.indentSelection
          (js/CM.indentSelection view))
        (.focus view)
        true))))

(defn- insert-at-cursor! []
  (when (insert-and-format! (:output @!shelf))
    (swap! !shelf assoc :open? false)))

(defn- shelf []
  (let [{:keys [open? input output]} @!shelf]
    (when open?
      [:div.shelf
       [:div.shelf-header
        [:span.shelf-title "SICM (Scheme) → Emmy (Clojure)"]
        [:button.shelf-close
         {:on-click #(swap! !shelf assoc :open? false)
          :title    "Close"} "×"]]
       [:div.shelf-body
        [:div.shelf-pane
         [:div.shelf-sublabel "Paste Scheme"]
         [:textarea.shelf-textarea
          {:value       input
           :spell-check false
           :placeholder ";; Paste SICM / scmutils Scheme here…"
           :on-change   #(on-shelf-input (.. % -target -value))}]]
        [:div.shelf-pane
         [:div.shelf-sublabel "Translated Clojure"]
         [:textarea.shelf-textarea
          {:value     output
           :read-only true
           :spell-check false}]]]
       [:div.shelf-toolbar
        [:button {:on-click insert-at-cursor!
                  :disabled (clojure.string/blank? output)}
         "Insert at cursor"]
        [:span.hint
         "Drops the translated text where the editor caret is."]]])))

;; --- Auto-graph shelf ------------------------------------------------------
;; Wraps an arbitrary Emmy expression in the graphics form the user picks
;; from a small dropdown — plot, parametric 2D / 3D, surface, or animate.
;; No live evaluation, no auto-detection: the user knows what they want, we
;; just do the textual transformation. Two common shapes are handled:
;;
;;   * value is already a function (Math/sin, (fn [x] …), find-path's path)
;;     → wrapped directly: (plot Math/sin), (plot (find-path …)), …
;;   * value is a symbolic Emmy expression in 'x / 'y / 't (e.g. (sin 'x))
;;     → quotes are stripped on the matching vars and the body becomes
;;       (fn [vars…] body), then wrapped: (plot (fn [x] (sin x))).
;;
;; Because we don't run user code at all, the shelf can never freeze the
;; page on an expensive expression like (find-path …) — the user inserts
;; into the editor and evaluates manually when ready.

(defonce !auto-graph
  (r/atom {:open? false :kind :plot :input "" :output "" :sweep nil}))

(def ^:private kind-options
  ;; In dropdown order. Each entry is [keyword human-label expected-vars].
  ;; expected-vars are the symbols the wrapper looks for as quoted Emmy
  ;; symbols in the source (e.g. 'x for plot, 't for parametric).
  [[:plot          "Plot — y = f(x)"               ["x" "t"]]
   [:parametric-2d "Parametric 2D — (x,y) = f(t)"   ["t"]]
   [:parametric-3d "Parametric 3D — (x,y,z) = f(t)" ["t"]]
   [:surface       "Surface — z = f(x,y)"           ["x" "y"]]
   [:animate       "Animate — y = f(t,x)"           ["t" "x"]]])

(defn- expected-vars-for [kind]
  (some (fn [[k _ vs]] (when (= k kind) vs)) kind-options))

(defn- has-quoted-var?
  "Does src contain a quoted Emmy symbol like 'x or 't, with no trailing
   word char or hyphen so 'xy / 't-now don't false-match?"
  [src v]
  (boolean (re-find (re-pattern (str "'" v "(?![\\w-])")) src)))

(defn- strip-quoted-var [src v]
  (clojure.string/replace src
                          (re-pattern (str "'" v "(?![\\w-])"))
                          v))

(defn- wrap-as-fn-of
  "If src has any of the expected quoted vars, build (fn [vars] body) over
   the ones that appear, stripping their quotes from the body. Otherwise
   return src unchanged — it's assumed to already be a function."
  [src expected-vars]
  (let [used (filter #(has-quoted-var? src %) expected-vars)]
    (if (seq used)
      (str "(fn [" (clojure.string/join " " used) "] "
           (reduce strip-quoted-var src used)
           ")")
      src)))

(defn- emmy-symbolic?
  "If the source uses Emmy's up- or down-tuple constructors, the parametric
   body should run through emmy.mafs/parametric so it gets Emmy's
   expression-machinery compilation; raw mafs.plot/Parametric expects
   JS-number components and doesn't unpack ups."
  [src]
  (boolean (re-find #"\((?:up|down)\b" src)))

;; --- Lagrangian detection -------------------------------------------------
;; Special-case: a SICM-style Lagrangian expression like (L-harmonic 'm 'k)
;; or the full Euler–Lagrange wrapping ((Lagrange-equations (L-harmonic …)) …)
;; isn't a function of one variable, but the user almost certainly wants to
;; plot the trajectory it describes. We detect the (L-<name> args) sub-form,
;; treat its quoted args as free parameters with default 1.0, and emit a
;; find-path-based template — adapted to the chosen graph kind.

(defn- find-balanced-paren-end
  "Given src and a position where '(' lives, return the index just past the
   matching ')'. Naive — doesn't track strings/escapes — but adequate for
   the syntactic shapes we expect a user to paste."
  [src start]
  (when (and (< start (count src))
             (= (.charAt src start) "("))
    (loop [i start depth 0]
      (cond
        (>= i (count src)) nil
        (= (.charAt src i) "(") (recur (inc i) (inc depth))
        (= (.charAt src i) ")") (if (= 1 depth)
                                  (inc i)
                                  (recur (inc i) (dec depth)))
        :else                   (recur (inc i) depth)))))

(defn- lagrangian-form
  "Find the first (L-<name> …) sub-form in src and return it as a substring,
   or nil if none. Matches naked `L-` names like L-harmonic, L-free-particle."
  [src]
  (when-let [m (re-find #"\(L-[\w-]+" src)]
    (let [start (.indexOf src m)]
      (when-let [end (find-balanced-paren-end src start)]
        (subs src start end)))))

(defn- parse-lagrangian
  "Parse '(L-name arg1 arg2 …)' → {:name 'L-name' :args ['arg1' 'arg2' …]}.
   Args are split on whitespace, so atomic args (numbers, symbols, quoted
   symbols) round-trip cleanly. Nested args like (L-foo (* 2 m) k) won't
   parse — acceptable for typical SICM-style direct calls."
  [form]
  (when form
    (let [inner  (subs form 1 (dec (count form)))
          tokens (-> inner
                     clojure.string/trim
                     (clojure.string/split #"\s+"))]
      {:name (first tokens) :args (vec (rest tokens))})))

(defn- arg-bindings
  "Split args into {:bindings [[name 1.0] …] :call [tok …]}. Quoted args
   ('m, 'k) become let-bindings using their stripped name with default
   1.0; concrete args (1.0, m, …) stay verbatim in :call."
  [args]
  (reduce
    (fn [acc arg]
      (if (clojure.string/starts-with? arg "'")
        (let [n (subs arg 1)]
          (-> acc
              (update :bindings conj [n 1.0])
              (update :call conj n)))
        (update acc :call conj arg)))
    {:bindings [] :call []}
    args))

(defn- lagr-let-prelude
  "Build the (let [… find-path …] portion shared by the 1D-trajectory kinds
   (:plot, :parametric-2d, :parametric-3d). Free symbols become labelled
   bindings; concrete args pass through verbatim into the L-call."
  [name bindings call]
  (let [L-call    (str "(" name
                       (when (seq call) (str " " (clojure.string/join " " call)))
                       ")")
        bind-rows (concat
                   (map (fn [[n d]] (str n " " d "       ; '" n)) bindings)
                   ["t0 0.0"
                    "t1 (/ Math/PI 2)"
                    "q0 1.0"
                    "q1 0.0"
                    (str "L    " L-call)
                    "path (find-path L t0 q0 t1 q1 4)"])]
    (str "(let [" (clojure.string/join "\n      " bind-rows) "]")))

(defn- lagrangian-template
  "Build the kind-specific find-path template for a Lagrangian source.
   :plot/:parametric-2d/:parametric-3d share a single-path prelude; :surface
   pre-computes a stack of paths over a sweep of one quoted arg (the one
   named in opts :sweep, defaulting to the first); :animate uses
   plot-with-params with a memoized find-path so dragging a slider doesn't
   re-solve the variational problem at every x sample.

   :surface and :animate fall back to :plot when there are no quoted args
   to sweep / slide over."
  ([kind src] (lagrangian-template kind src nil))
  ([kind src opts]
  (let [{:keys [name args]}     (parse-lagrangian (lagrangian-form src))
        {:keys [bindings call]} (arg-bindings args)
        sweep-name              (:sweep opts)
        L-call    (str "(" name
                       (when (seq call) (str " " (clojure.string/join " " call)))
                       ")")]
    (cond
      (= kind :plot)
      (str (lagr-let-prelude name bindings call)
           "\n  (plot path [t0 t1] [-1.5 1.5]))")

      (= kind :parametric-2d)
      (str (lagr-let-prelude name bindings call)
           "\n  [mafs/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}"
           "\n   [mafs.coordinates/Cartesian]"
           "\n   [mafs.plot/Parametric"
           "\n    {:t  [t0 t1]"
           "\n     :xy (fn [t] [(path t) ((D path) t)])}]])")

      (= kind :parametric-3d)
      (str (lagr-let-prelude name bindings call)
           "\n  [mathbox/MathBox"
           "\n   {:container {:style {:height \"400px\" :width \"100%\"}}}"
           "\n   [mb/Cartesian {:range [[t0 t1] [-1.5 1.5] [-1.5 1.5]] :scale [1 1 1]}"
           "\n    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]"
           "\n    [mb/Interval"
           "\n     {:range [t0 t1] :width 256 :channels 3"
           "\n      :expr (fn [emit t i time]"
           "\n              (emit t (path t) ((D path) t)))}]"
           "\n    [mb/Line {:width 4 :color \"#3090ff\"}]]])")

      (and (= kind :surface) (empty? bindings))
      (lagrangian-template :plot src)

      (= kind :surface)
      (let [swept-binding (or (some (fn [[n :as b]] (when (= n sweep-name) b))
                                    bindings)
                              (first bindings))
            [swept _]     swept-binding
            fixed         (remove #(= % swept-binding) bindings)
            fixed-row     (map (fn [[n d]] (str n " " d
                                                "       ; '" n " — fixed; sweeping '" swept))
                               fixed)
            rows (concat
                  fixed-row
                  ["t0 0.0"
                   "t1 (/ Math/PI 2)"
                   "q0 1.0"
                   "q1 0.0"
                   (str ";; Sweep '" swept " over its rangeY (8 paths × basis-3 keeps the")
                   (str ";; on-mount find-path freeze around 1s; bump them up for accuracy.)")
                   (str swept "-min 0.5")
                   (str swept "-max 5.0")
                   (str swept "-n   8")
                   (str swept "s    (mapv #(+ " swept "-min (* (/ (- " swept "-max " swept "-min) (dec " swept "-n)) %)) (range " swept "-n))")
                   (str "paths (mapv (fn [" swept "] (find-path " L-call " t0 q0 t1 q1 3)) " swept "s)")])]
        (str "(let [" (clojure.string/join "\n      " rows) "]"
             "\n  [mathbox/MathBox"
             "\n   {:container {:style {:height \"400px\" :width \"100%\"}}}"
             "\n   [mb/Cartesian {:range [[t0 t1] [" swept "-min " swept "-max] [-1.5 1.5]] :scale [1 1 1]}"
             "\n    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]"
             "\n    [mb/Area"
             "\n     {:rangeX [t0 t1] :rangeY [" swept "-min " swept "-max]"
             "\n      :width 64 :height " swept "-n :channels 3"
             "\n      :expr (fn [emit t " swept " i j time]"
             "\n              (emit t " swept " ((nth paths j) t)))}]"
             "\n    [mb/Surface {:shaded true :color \"#3090ff\"}]]])"))

      (and (= kind :animate) (empty? bindings))
      (lagrangian-template :plot src)

      (= kind :animate)
      (let [names     (mapv first bindings)
            names-str (clojure.string/join " " names)
            schema    (clojure.string/join "\n     "
                        (map (fn [[n d]]
                               (str ":" n " {:value " d " :min 0.1 :max 5.0 :step 0.1}"))
                             bindings))]
        (str "(let [t0 0.0"
             "\n      t1 (/ Math/PI 2)"
             "\n      q0 1.0"
             "\n      q1 0.0"
             "\n      ;; memoize so dragging a slider doesn't re-solve the"
             "\n      ;; variational problem at every x sample within a frame."
             "\n      memo-path (memoize"
             "\n                  (fn [" names-str "]"
             "\n                    (find-path " L-call " t0 q0 t1 q1 4)))]"
             "\n  (plot-with-params"
             "\n    (fn [{:keys [" names-str "]} t]"
             "\n      ((memo-path " names-str ") t))"
             "\n    {" schema "}"
             "\n    [t0 t1] [-1.5 1.5]))"))))))

(defn- lagrangian-pattern? [src]
  (boolean (re-find #"\(L-[\w-]+" src)))


(defn- defn-form?
  "Does the source begin with a top-level (defn …) or (defn- …) form?"
  [src]
  (boolean (re-find #"^\s*\(defn-?\s+" src)))

(defn- defn-name
  "Extract the name from a leading (defn name …) form, or nil."
  [src]
  (when-let [m (re-find #"^\s*\(defn-?\s+(\S+)" src)]
    (second m)))

(defn- defn-args
  "Extract arg names from a leading (defn name [args] …) form. Naive —
   the [..] capture loses nested brackets, so destructured arg lists
   come back garbled. Acceptable for the SICM-style Lagrangians we
   special-case below, which always use plain symbol args."
  [src]
  (when-let [m (re-find #"^\s*\(defn-?\s+\S+\s+\[([^\]]*)\]" src)]
    (let [s (clojure.string/trim (second m))]
      (if (clojure.string/blank? s) [] (clojure.string/split s #"\s+")))))

(defn- lagrangian-defn?
  "Is this a (defn L-… …) form? L- prefix is the SICM-book convention
   for Lagrangians; we treat it as a hint to route through the
   Lagrangian template instead of the generic defn wrap."
  [src]
  (and (defn-form? src)
       (some-> (defn-name src) (clojure.string/starts-with? "L-"))))

(defn- plot-template          [body] (str "(plot " body ")"))
(defn- animate-template       [body] (str "(animate " body ")"))
(defn- parametric-2d-template [body]
  (if (emmy-symbolic? body)
    (str "(emmy.mafs/mafs\n"
         " {:viewBox {:x [-2 2] :y [-2 2]}}\n"
         " (emmy.mafs/parametric\n"
         "  {:t  [0 (* 2 Math/PI)]\n"
         "   :xy " body "}))")
    (str "[mafs/Mafs {:viewBox {:x [-2 2] :y [-2 2]}}\n"
         " [mafs.coordinates/Cartesian]\n"
         " [mafs.plot/Parametric\n"
         "  {:t  [0 (* 2 Math/PI)]\n"
         "   :xy " body "}]]")))
(defn- parametric-3d-template [body]
  (str "[mathbox/MathBox\n"
       " {:container {:style {:height \"400px\" :width \"100%\"}}}\n"
       " [mb/Cartesian {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}\n"
       "  [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]\n"
       "  [mb/Interval\n"
       "   {:range [0 (* 2 Math/PI)] :width 256 :channels 3\n"
       "    :expr (fn [emit t i time]\n"
       "            (let [v (" body " t)]\n"
       "              (emit (nth v 0) (nth v 1) (nth v 2))))}]\n"
       "  [mb/Line {:width 4 :color \"#3090ff\"}]]]"))
(defn- surface-template [body]
  (str "[mathbox/MathBox\n"
       " {:container {:style {:height \"400px\" :width \"100%\"}}}\n"
       " [mb/Cartesian {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}\n"
       "  [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]\n"
       "  [mb/Area\n"
       "   {:rangeX [-2 2] :rangeY [-2 2]\n"
       "    :width 32 :height 32 :channels 3\n"
       "    :expr (fn [emit x y i j time]\n"
       "            (emit x (" body " x y) y))}]\n"
       "  [mb/Surface {:shaded true :color \"#3090ff\"}]]]"))

(def ^:private kind->template
  {:plot          plot-template
   :animate       animate-template
   :parametric-2d parametric-2d-template
   :parametric-3d parametric-3d-template
   :surface       surface-template})

(defn- wrap-code
  "Build the wrapped graphics form for the given kind. Pure textual.

   Five shapes, tried in order:
   * src contains a (L-<name> …) sub-form → emit a find-path-based
     template suited to the chosen kind. The user's outer wrapping
     (Lagrange-equations, literal-function, …) is intentionally
     discarded; we assume they want a trajectory plot, not the EL
     residual itself.
   * (defn L-<name> [args] body) → keep the defn as-is and append a
     synthesized (L-name 'arg …) call routed through the Lagrangian
     template, so a Lagrangian definition lands ready-to-plot.
   * Other (defn name [args] body) → keep the defn as a top-level form
     and append a separate (template name) form, so the defn evaluates
     and its name is what the second form graphs.
   * src has quoted Emmy vars matching the kind's expected names (e.g.
     'x for :plot, 't for :parametric, 'x/'y for :surface) → strip the
     quotes and wrap as (fn [vars…] body) before applying the template.
   * Otherwise → assume src is already a function, apply the template
     directly: (plot Math/sin), (plot (find-path …)).

   opts is forwarded to lagrangian-template; currently it carries
   :sweep — the name of the quoted arg the :surface kind should
   sweep instead of the default first."
  ([kind src] (wrap-code kind src nil))
  ([kind src opts]
   (let [src      (clojure.string/trim src)
         template (kind->template kind)]
     (cond
       (lagrangian-pattern? src)
       (lagrangian-template kind src opts)

       (lagrangian-defn? src)
       (let [name  (defn-name src)
             args  (defn-args src)
             synth (str "(" name
                        (when (seq args)
                          (str " " (clojure.string/join " "
                                                        (map #(str "'" %) args))))
                        ")")]
         (str src "\n\n" (lagrangian-template kind synth opts)))

       (defn-form? src)
       (str src "\n\n" (template (defn-name src)))

       :else
       (template (wrap-as-fn-of src (expected-vars-for kind)))))))

(defn- available-quoted-args
  "Names of the free symbols ('m, 'k, …) inside the first (L-…) sub-form
   in src, or nil if none. Used by the shelf to populate the Surface
   sweep-target picker."
  [src]
  (when-let [form (lagrangian-form src)]
    (let [{:keys [args]}     (parse-lagrangian form)
          {:keys [bindings]} (arg-bindings args)]
      (mapv first bindings))))

(defn- recompute-output [{:keys [kind input sweep] :as state}]
  (assoc state
         :output (if (clojure.string/blank? input)
                   ""
                   (wrap-code kind input {:sweep sweep}))))

(defn- on-auto-graph-input [src]
  ;; If the available quoted-arg names changed (or the previously-chosen
  ;; sweep target is gone), reset :sweep to the new first arg.
  (swap! !auto-graph
         (fn [s]
           (let [args (available-quoted-args src)]
             (recompute-output
              (cond-> (assoc s :input src)
                (not (some #{(:sweep s)} args))
                (assoc :sweep (first args))))))))

(defn- on-auto-graph-kind [k]
  (swap! !auto-graph #(recompute-output (assoc % :kind k))))

(defn- on-auto-graph-sweep [v]
  (swap! !auto-graph #(recompute-output (assoc % :sweep v))))

(defn- insert-auto-graph! []
  (when (insert-and-format! (:output @!auto-graph))
    (swap! !auto-graph assoc :open? false)))

(defn- toggle-translator! []
  (swap! !auto-graph assoc :open? false)
  (swap! !shelf       update :open? not))

(defn- toggle-auto-graph! []
  (swap! !shelf       assoc :open? false)
  (swap! !auto-graph  update :open? not))

(defn- auto-graph-shelf []
  (let [{:keys [open? kind input output sweep]} @!auto-graph
        quoted-args                              (available-quoted-args input)]
    (when open?
      [:div.shelf
       [:div.shelf-header
        [:span.shelf-title "Auto-graph: Emmy expression → graphics form"]
        [:select.shelf-kind
         {:value     (name kind)
          :on-change #(on-auto-graph-kind (keyword (.. % -target -value)))}
         (for [[k label _] kind-options]
           ^{:key k} [:option {:value (name k)} label])]
        ;; Sweep-target picker — only meaningful when Surface is the kind
        ;; AND there's a choice (2+ free symbols in the Lagrangian).
        (when (and (= kind :surface) (>= (count quoted-args) 2))
          [:select.shelf-kind
           {:value     (or sweep "")
            :on-change #(on-auto-graph-sweep (.. % -target -value))
            :title     "Which Lagrangian param to sweep along the surface's y axis"}
           (for [a quoted-args]
             ^{:key a} [:option {:value a} (str "Sweep '" a)])])
        [:button.shelf-close
         {:on-click #(swap! !auto-graph assoc :open? false)
          :title    "Close"} "×"]]
       [:div.shelf-body
        [:div.shelf-pane
         [:div.shelf-sublabel "Emmy expression"]
         [:textarea.shelf-textarea
          {:value       input
           :spell-check false
           :placeholder ";; A fn, a path, or a symbolic body in 'x / 't.\n;; e.g. Math/sin, (fn [x] (square x)), (sin 'x),\n;;      (find-path (L-harmonic 1.0 1.0) …)"
           :on-change   #(on-auto-graph-input (.. % -target -value))}]]
        [:div.shelf-pane
         [:div.shelf-sublabel "Wrapped form"]
         [:textarea.shelf-textarea
          {:value       output
           :read-only   true
           :spell-check false}]]]
       [:div.shelf-toolbar
        [:button {:on-click insert-auto-graph!
                  :disabled (clojure.string/blank? output)}
         "Insert at cursor"]
        [:span.hint
         "Drops the wrapped form where the editor caret is. Insert and evaluate to see it render."]]])))

(defonce !system-menu-open? (r/atom false))

(defonce _close-system-menu-on-outside
  (.addEventListener
   js/document "mousedown"
   (fn [e]
     (when @!system-menu-open?
       (let [dd (.querySelector js/document ".system-dropdown")]
         (when (and dd (not (.contains dd (.-target e))))
           (reset! !system-menu-open? false)))))))

(defn- system-dropdown []
  (let [[t cur-name]   (:current @!pages)
        on-system?     (= t :system)
        open?          @!system-menu-open?]
    [:span.system-dropdown
     [:span.page
      {:class    (when on-system? "active")
       :on-click #(swap! !system-menu-open? not)
       :title    "System pages — read-only templates. Type to fork into your pages."}
      [:span.page-name (str (if on-system? cur-name "System") " ▾")]]
     (when open?
       [:div.system-menu
        ;; Render in insertion order (system-pages is an array-map).
        (for [n (keys system-pages)]
          ^{:key n}
          [:div.system-menu-item
           {:on-click (fn []
                        (switch-to-system! n)
                        (reset! !system-menu-open? false))}
           n])])]))

(defn- pages-bar []
  (let [{:keys [pages current]} @!pages
        [t cur-name] current]
    [:div.pages
     [system-dropdown]
     (for [n (sort (keys pages))]
       (let [active? (and (= t :user) (= n cur-name))]
         ^{:key n}
         [:span.page
          {:class    (when active? "active")
           :on-click (when-not active? #(switch-to-user! n))}
          [:span.page-name n]
          [:span.page-x
           {:on-click (fn [e]
                        (.stopPropagation e)
                        (delete-page! n))
            :title    (str "Delete " n)}
           "×"]]))
     [:button.page-add {:on-click new-page! :title "New page"} "+"]]))

(defn- hiccup?
  "Heuristic: a vector whose first element is a keyword (:div etc.) or a
   function (a Reagent component reference). Symbols are deliberately
   excluded — quoted forms returned by emmy-viewers helpers shouldn't
   accidentally render as hiccup."
  [v]
  (and (vector? v)
       (pos? (count v))
       (let [h (first v)]
         (or (keyword? h) (fn? h)))))

(defn- result-row [{:keys [form value pr tex err]}]
  [:div.result-row
   [:pre.form-snippet
    {:dangerouslySetInnerHTML #js {:__html (highlight-clojure form)}}]
   (cond
     err          [:div.err err]
     (hiccup? value) [:div.viz [error-boundary value]]
     :else
     [:<>
      (when tex [katex-block ^String tex])
      [:div.pr
       {:dangerouslySetInnerHTML #js {:__html (highlight-clojure pr)}}]])])

(defn- result-pane []
  (let [{:keys [status results eval-id]} @!result]
    [:div.result
     (case status
       :idle [:span "Press " [:kbd "Cmd-Enter"] " or " [:kbd "Ctrl-Enter"]
              " to evaluate."]
       :ok   (if (empty? results)
               [:span {:style {:color "#57606a"}} "(no forms)"]
               [:<>
                ;; Prefix the key with eval-id so each evaluation forces a
                ;; fresh remount — stateful viz components (Leva-driven
                ;; plot-with-params, etc.) get rebuilt cleanly so the new
                ;; code takes effect.
                (for [[i r] (map-indexed vector results)]
                  ^{:key (str eval-id "-" i)} [result-row r])]))]))

(defn- logo []
  (r/create-class
   {:display-name "Logo"
    :component-did-mount
    (fn [_]
      (when (exists? js/PshiftLoader)
        (.mountAll js/PshiftLoader ".logo-cycle")))
    :reagent-render
    (fn [_]
      [:canvas.logo-cycle {:data-src "logo.pshift.png"}])}))

(defn- app []
  [:<>
   [:header
    [:h1 [logo]]
    [:div.header-right
     [:span.tagline "Bemmy :: Emmy in the Browser"]
     [:nav.header-links
      [:a {:href   "https://inwordsandpictures.com/bemmy/"
           :target "_blank" :rel "noopener noreferrer"
           :title  "Article: Bemmy in In Words and Pictures"}
       "Article"]
      [:a {:href   "https://github.com/dxnn/bemmy"
           :target "_blank" :rel "noopener noreferrer"
           :title  "GitHub repository"}
       "GitHub"]]]]
   [:div.panes
    [:div.pane
     [:div.label "Code"]
     [pages-bar]
     ;; Key on vim-on, paredit-on?, AND OS theme so toggling any forces CM
     ;; to remount; CM6's vim extension, paredit bundle, and theme are all
     ;; baked in at editor construction time.
     ^{:key (str "cm-" (:vim-on @!ui)
                 "-" (:paredit-on? @!ui)
                 "-" @!dark?)}
     [cm-editor]
     [shelf]
     [auto-graph-shelf]
     [:div.toolbar
      [:button.action {:on-click eval!} "Evaluate"]
      [:button.btn
       {:class    (when (:open? @!shelf) "is-on")
        :on-click toggle-translator!
        :title    "Toggle SICM → Emmy translator"}
       "SICM → Emmy"]
      [:button.btn
       {:class    (when (:open? @!auto-graph) "is-on")
        :on-click toggle-auto-graph!
        :title    "Wrap an Emmy expression in the right graphics form"}
       "Auto-graph"]
      [:label.check
       {:title "Vim keybindings (persisted across reloads)"}
       [:input {:type      "checkbox"
                :checked   (boolean (:vim-on @!ui))
                :on-change #(swap! !ui update :vim-on not)}]
       "vim"]
      [:label.check
       {:title "Paredit-style structural editing — auto-pair brackets, skip-over closing parens, format on every change. Off if you want predictable typing."}
       [:input {:type      "checkbox"
                :checked   (boolean (:paredit-on? @!ui))
                :on-change #(swap! !ui update :paredit-on? not)}]
       "paredit"]
      [:button.btn.permalink-btn
       {:on-click share-current!
        :title    "Copy a URL that loads this page's source for someone else"}
       "Share"]]]
    [:div.pane
     [:div.label "Result"]
     [result-pane]]]
   (when-let [m @!toast]
     [:div.toast m])])

;; Wait for the ESM-loaded CodeMirror modules before mounting.
(.then js/window.cm_ready
       (fn [_]
         (rdom/render [app] (.getElementById js/document "app")))
       (fn [err]
         (js/console.error "CodeMirror failed to load" err)
         (set! (.. (.getElementById js/document "app") -innerHTML)
               "Failed to load CodeMirror — check console.")))
