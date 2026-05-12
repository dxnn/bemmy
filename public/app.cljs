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
;; --- BEGIN GENERATED SICM PAGES ---
(def sicm-section-pages
  (array-map
    "SICM 1.4 Computing Actions (Emmy)"
    ";; ============================================================
;; SICM 1.4 Computing Actions (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-4
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.generic :as g]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(def x (literal-function 'x))

(def y (literal-function 'y))

(def z (literal-function 'z))

(def q (up x y z))

(def test-path (fn [t] (up (+ (* 4 t) 7) (+ (* 3 t) 5) (+ (* 2 t) 1))))

(def make-η (fn [ν t1 t2] (fn [t] (* (- t t1) (- t t2) (ν t)))))

(def varied-free-particle-action
 (fn [mass q ν t1 t2]
   (fn [ε]
     (let [η (make-η ν t1 t2)]
       (Lagrangian-action
         (L-free-particle mass)
         (+ q (* ε η))
         t1
         t2)))))

(near 2.0 (definite-integral sin 0 Math/PI))

(simplify (q 't))
;;=> '(up (x t) (y t) (z t))

(simplify ((D q) 't))
;;=> '(up ((D x) t) ((D y) t) ((D z) t))

(simplify (((expt D 2) q) 't))
;;=> '(up (((expt D 2) x) t) (((expt D 2) y) t) (((expt D 2) z) t))

(simplify ((D (D q)) 't))
;;=> '(up (((expt D 2) x) t) (((expt D 2) y) t) (((expt D 2) z) t))

(simplify ((Gamma q) 't))
;;=> '(up t (up (x t) (y t) (z t)) (up ((D x) t) ((D y) t) ((D z) t)))

(freeze (simplify ((compose (L-free-particle 'm) (Gamma q)) 't)))
;;=> '(+
;;        (* (/ 1 2) m (expt ((D x) t) 2))
;;        (* (/ 1 2) m (expt ((D y) t) 2))
;;        (* (/ 1 2) m (expt ((D z) t) 2)))

(Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0)
;;=> 435.0

(let [m (minimize
          (varied-free-particle-action
            3.0
            test-path
            (up sin cos square)
            0.0
            10.0)
          -2.0
          1.0)]
  (near 0.0 (:result m))
  ((within 1.0E-4) 435 (:value m)))

(near
    436.2912143
    ((varied-free-particle-action
       3.0
       test-path
       (up sin cos square)
       0.0
       10.0)
      0.001))

(let [values (atom [])
      minimal-path (find-path
                     (L-harmonic 1.0 1.0)
                     0.0
                     1.0
                     (/ Math/PI 2)
                     0.0
                     3
                     :observe
                     (fn [_ pt _] (swap! values conj pt)))
      good? (partial (within 2.0E-4) 0)
      errors (for
               [x (range 0.0 (/ Math/PI 2) 0.02)]
               (abs (- (Math/cos x) (minimal-path x))))]
  ((within 1.0E-4) 1 (minimal-path 0))
  ((within 1.0E-5) 0 (minimal-path (/ Math/PI 2)))
  (every? good? errors))"
    "SICM 1.5 The Euler-Lagrange Equations (Emmy)"
    ";; ============================================================
;; SICM 1.5 The Euler-Lagrange Equations (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-5
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

(def x (literal-function 'x))

(def f (literal-function 'f))

(def g (literal-function 'g))

(def q (literal-function 'q))

(def η (literal-function 'η))

(def φ (literal-function 'φ))

(def F (fn [q] (fn [t] (f (q t)))))

(def G (fn [q] (fn [t] (g (q t)))))

(def δ_η (δ η))

(def φ (fn [f] (fn [q] (fn [t] (φ ((f q) t))))))

(def test-path
 (fn [t] (up (+ 'a0 (* 'a t)) (+ 'b0 (* 'b t)) (+ 'c0 (* 'c t)))))

(def proposed-solution (fn [t] (* 'a (cos (+ (* 'ω t) 'φ)))))

(simplify (((δ_η identity) q) 't))
;;=> '(η t)

(simplify (((δ_η F) q) 't))
;;=> '(* (η t) ((D f) (q t)))

(simplify (((δ_η G) q) 't))
;;=> '(* (η t) ((D g) (q t)))

(simplify (((δ_η (* F G)) q) 't))
;;=> '(+
;;        (* (η t) ((D f) (q t)) (g (q t)))
;;        (* (η t) ((D g) (q t)) (f (q t))))

(simplify (((δ_η (* F G)) q) 't))
;;=> '(+
;;        (* (η t) ((D f) (q t)) (g (q t)))
;;        (* (η t) ((D g) (q t)) (f (q t))))

(simplify (((δ_η (φ F)) q) 't))
;;=> '(* (η t) ((D f) (q t)) ((D φ) (f (q t))))

(((Lagrange-equations (L-free-particle 'm)) test-path) 't)
;;=> (down 0 0 0)

(simplify (((Lagrange-equations (L-free-particle 'm)) x) 't))
;;=> '(* m (((expt D 2) x) t))

(simplify
    (((Lagrange-equations (L-harmonic 'm 'k)) proposed-solution) 't))
;;=> '(+
;;        (* -1 a m (expt ω 2) (cos (+ (* t ω) φ)))
;;        (* a k (cos (+ (* t ω) φ))))"
    "SICM 1.6 How to Find Lagrangians (Emmy)"
    ";; ============================================================
;; SICM 1.6 How to Find Lagrangians (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-6
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.examples.pendulum :as pendulum]
  '[emmy.generic :as g]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

(def x (literal-function 'x))

(def y (literal-function 'y))

(def r (literal-function 'r))

(def θ (literal-function 'θ))

(def φ (literal-function 'φ))

(def U (literal-function 'U))

(def y_s (literal-function 'y_s))

(def L-alternate-central-polar
 (fn [m U] (compose (L-central-rectangular m U) (F->C p->r))))

(simplify
    (((Lagrange-equations (L-uniform-acceleration 'm 'g)) (up x y))
      't))
;;=> '(down (* m (((expt D 2) x) t)) (+ (* g m) (* m (((expt D 2) y) t))))

(simplify
    (((Lagrange-equations (L-central-rectangular 'm U)) (up x y)) 't))
;;=> '(down
;;        (/
;;          (+
;;            (*
;;              m
;;              (((expt D 2) x) t)
;;              (sqrt (+ (expt (x t) 2) (expt (y t) 2))))
;;            (* (x t) ((D U) (sqrt (+ (expt (x t) 2) (expt (y t) 2))))))
;;          (sqrt (+ (expt (x t) 2) (expt (y t) 2))))
;;        (/
;;          (+
;;            (*
;;              m
;;              (((expt D 2) y) t)
;;              (sqrt (+ (expt (x t) 2) (expt (y t) 2))))
;;            (* (y t) ((D U) (sqrt (+ (expt (x t) 2) (expt (y t) 2))))))
;;          (sqrt (+ (expt (x t) 2) (expt (y t) 2)))))

(simplify
    (((Lagrange-equations (L-central-polar 'm U)) (up r φ)) 't))
;;=> '(down
;;        (+
;;          (* -1 m (r t) (expt ((D φ) t) 2))
;;          (* m (((expt D 2) r) t))
;;          ((D U) (r t)))
;;        (+
;;          (* m (expt (r t) 2) (((expt D 2) φ) t))
;;          (* 2 m (r t) ((D φ) t) ((D r) t))))

(simplify
    (velocity ((F->C p->r) (->local 't (up 'r 'φ) (up 'rdot 'φdot)))))
;;=> '(up
;;        (+ (* -1 r φdot (sin φ)) (* rdot (cos φ)))
;;        (+ (* r φdot (cos φ)) (* rdot (sin φ))))

(freeze
    (simplify
      ((L-alternate-central-polar 'm U)
        (->local 't (up 'r 'φ) (up 'rdot 'φdot)))))
;;=> '(+
;;        (* (/ 1 2) m (expt r 2) (expt φdot 2))
;;        (* (/ 1 2) m (expt rdot 2))
;;        (* -1 (U r)))

(simplify
    (((Lagrange-equations (L-alternate-central-polar 'm U)) (up r φ))
      't))
;;=> '(down
;;        (+
;;          (* -1 m (r t) (expt ((D φ) t) 2))
;;          (* m (((expt D 2) r) t))
;;          ((D U) (r t)))
;;        (+
;;          (* m (expt (r t) 2) (((expt D 2) φ) t))
;;          (* 2 m (r t) ((D φ) t) ((D r) t))))

(simplify
    (((Lagrange-equations (pendulum/L 'm 'l 'g (up (fn [_] 0) y_s))) θ)
      't))
;;=> '(+
;;        (* g l m (sin (θ t)))
;;        (* (expt l 2) m (((expt D 2) θ) t))
;;        (* l m (sin (θ t)) (((expt D 2) y_s) t)))

(let [Lf (fn [m g]
           (fn [[_ [_ y] v]] (- (* 1/2 m (square v)) (* m g y))))
      dp-coordinates (fn [l y_s]
                       (fn [[t θ]]
                         (let [x (* l (sin θ))
                               y (- (y_s t) (* l (cos θ)))]
                           (up x y))))
      L-pend2 (fn [m l g y_s]
                (compose (Lf m g) (F->C (dp-coordinates l y_s))))]
  (freeze (simplify ((L-pend2 'm 'l 'g y_s) (->local 't 'θ 'θdot))))
  ;;=> '(+
  ;;          (* (/ 1 2) (expt l 2) m (expt θdot 2))
  ;;          (* l m θdot (sin θ) ((D y_s) t))
  ;;          (* g l m (cos θ))
  ;;          (* -1 g m (y_s t))
  ;;          (* (/ 1 2) m (expt ((D y_s) t) 2))))"
    "SICM 1.7 Evolution of Dynamical State – part 1 (Emmy)"
    ";; ============================================================
;; SICM 1.7 Evolution of Dynamical State – part 1 (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-7-1
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.generic :as g]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

(def x (literal-function 'x))

(def y (literal-function 'y))

(def v_x (literal-function 'v_x))

(def v_y (literal-function 'v_y))

(def harmonic-state-derivative
 (fn [m k] (Lagrangian->state-derivative (L-harmonic m k))))

(simplify
    ((harmonic-state-derivative 'm 'k)
      (up 't (up 'x 'y) (up 'v_x 'v_y))))
;;=> '(up 1 (up v_x v_y) (up (/ (* -1 k x) m) (/ (* -1 k y) m)))

(simplify
    (((Lagrange-equations-first-order (L-harmonic 'm 'k))
       (up x y)
       (up v_x v_y))
      't))
;;=> '(up
;;        0
;;        (up (+ ((D x) t) (* -1 (v_x t))) (+ ((D y) t) (* -1 (v_y t))))
;;        (up
;;          (/ (+ (* k (x t)) (* m ((D v_x) t))) m)
;;          (/ (+ (* k (y t)) (* m ((D v_y) t))) m)))

((harmonic-state-derivative 2.0 1.0)
    (up 0 (up 1.0 2.0) (up 3.0 4.0)))
;;=> (up 1 (up 3.0 4.0) (up -1/2 -1.0))

(freeze
    (flatten
      ((harmonic-state-derivative 2.0 1.0)
        (up 0 (up 1.0 2.0) (up 3.0 4.0)))))
;;=> '(1 3.0 4.0 (/ -1 2) -1.0)

(dotimes [_ 1]
  (let [answer ((state-advancer harmonic-state-derivative 2.0 1.0)
                 (up 0.0 (up 1.0 2.0) (up 3.0 4.0))
                 10.0
                 {:epsilon 1.0E-12, :compile? true})
        expected (up
                   10.0
                   (up 3.71279166 5.42062082)
                   (up 1.61480309 1.81891037))
        delta (->> answer (- expected) flatten (map abs) (reduce max))]
    (< delta 1.0E-8)))"
    "SICM 1.7 Evolution of Dynamical State – part 2 (Emmy)"
    ";; ============================================================
;; SICM 1.7 Evolution of Dynamical State – part 2 (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-7-2
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.examples.driven-pendulum :as driven]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

(def pend-state-derivative
 (fn [m l g a ω] (Lagrangian->state-derivative (driven/L m l g a ω))))

(simplify
    (((Lagrange-equations (driven/L 'm 'l 'g 'a 'ω))
       (literal-function 'θ))
      't))
;;=> '(+
;;        (* -1 a l m (expt ω 2) (sin (θ t)) (cos (* t ω)))
;;        (* g l m (sin (θ t)))
;;        (* (expt l 2) m (((expt D 2) θ) t)))

(simplify ((pend-state-derivative 'm 'l 'g 'a 'ω) (up 't 'θ 'θdot)))
;;=> '(up
;;        1
;;        θdot
;;        (/ (+ (* a (expt ω 2) (cos (* t ω)) (sin θ)) (* -1 g (sin θ))) l))

(let [opts {:compile? true, :epsilon 1.0E-13}
      evolve-fn (evolve
                  pend-state-derivative
                  1.0
                  1.0
                  9.8
                  0.1
                  (* 2.0 (sqrt 9.8)))
      answer (evolve-fn (up 0.0 1.0 0.0) 0.01 1.0 opts)
      expected (up 1.0 -1.030115687 -1.40985359)
      delta (->> answer (- expected) flatten (map abs) (reduce max))]
  (< delta 1.0E-8))"
    "SICM 1.8 Conserved Quantities (Emmy)"
    ";; ============================================================
;; SICM 1.8 Conserved Quantities (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-8
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.generic :as g]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

(def U (literal-function 'U))

(def V (literal-function 'V))

(def spherical-state (up 't (up 'r 'θ 'φ) (up 'rdot 'θdot 'φdot)))

(def T3-spherical
 (fn [m]
   (fn [[_ [r θ _] [rdot θdot φdot]]]
     (*
       (/ 1 2)
       m
       (+
         (square rdot)
         (square (* r θdot))
         (square (* r (sin θ) φdot)))))))

(def L3-central
 (fn [m Vr] (let [Vs (fn [[_ [r]]] (Vr r))] (- (T3-spherical m) Vs))))

(def ang-mom-z
 (fn [m] (fn [[_ q v]] (nth (cross-product q (* m v)) 2))))

(simplify (((partial 1) (L3-central 'm V)) spherical-state))
;;=> '(down
;;        (+
;;          (* m r (expt φdot 2) (expt (sin θ) 2))
;;          (* m r (expt θdot 2))
;;          (* -1 ((D V) r)))
;;        (* m (expt r 2) (expt φdot 2) (sin θ) (cos θ))
;;        0)

(simplify (((partial 2) (L3-central 'm V)) spherical-state))
;;=> '(down
;;        (* m rdot)
;;        (* m (expt r 2) θdot)
;;        (* m (expt r 2) φdot (expt (sin θ) 2)))

(simplify ((compose (ang-mom-z 'm) (F->C s->r)) spherical-state))
;;=> '(* m (expt r 2) φdot (expt (sin θ) 2))

(freeze
    (simplify
      ((Lagrangian->energy (L3-central 'm V)) spherical-state)))
;;=> '(+
;;        (* (/ 1 2) m (expt r 2) (expt φdot 2) (expt (sin θ) 2))
;;        (* (/ 1 2) m (expt r 2) (expt θdot 2))
;;        (* (/ 1 2) m (expt rdot 2))
;;        (V r))

(let [L (L-central-rectangular 'm U)
      F-tilde (fn [angle-x angle-y angle-z]
                (compose
                  (Rx angle-x)
                  (Ry angle-y)
                  (Rz angle-z)
                  coordinate))
      Noether-integral (* ((partial 2) L) ((D F-tilde) 0 0 0))
      state (up 't (up 'x 'y 'z) (up 'vx 'vy 'vz))]
  (simplify (((partial 2) L) state))
  ;;=> '(down (* m vx) (* m vy) (* m vz))
  (simplify ((F-tilde 0 0 0) state))
  ;;=> '(up x y z)
  (simplify (((D F-tilde) 0 0 0) state))
  ;;=> '(down (up 0 (* -1 z) y) (up z 0 (* -1 x)) (up (* -1 y) x 0))
  (simplify (Noether-integral state))
  ;;=> '(down
  ;;          (+ (* -1 m vy z) (* m vz y))
  ;;          (+ (* m vx z) (* -1 m vz x))
  ;;          (+ (* -1 m vx y) (* m vy x))))"
    "SICM 1.9 Abstraction of Path Functions (Emmy)"
    ";; ============================================================
;; SICM 1.9 Abstraction of Path Functions (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch1_test.cljc)
;; License: GPL-3.0
;; deftest: section-1-9
;; ============================================================


;; --- helpers from ch1_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.value :as v :refer [within]])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def near (within 1.0E-6))

(defn δ
  \"The variation operator (p. 28).\"
  [η]
  (fn [f] (fn [q] (let [g (fn [ε] (f (+ q (* ε η))))] ((D g) 0)))))

;; (Pedagogical redef of `F->C` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def F->C
;;  (fn [F]
;;    (let [f-bar #(->> % Gamma (compose F) Gamma)] (Gamma-bar f-bar))))

(simplify ((F->C p->r) (->local 't (up 'r 'θ) (up 'rdot 'θdot))))
;;=> '(up
;;        t
;;        (up (* r (cos θ)) (* r (sin θ)))
;;        (up
;;          (+ (* -1 r θdot (sin θ)) (* rdot (cos θ)))
;;          (+ (* r θdot (cos θ)) (* rdot (sin θ)))))

(simplify
    ((Euler-Lagrange-operator (L-harmonic 'm 'k))
      (->local 't 'x 'v 'a)))
;;=> '(+ (* a m) (* k x))

(def x (literal-function 'x))

(simplify
    ((compose (Euler-Lagrange-operator (L-harmonic 'm 'k)) (Gamma x 4))
      't))
;;=> '(+ (* k (x t)) (* m (((expt D 2) x) t)))"
    "SICM 2.7 Euler Angles (Emmy)"
    ";; ============================================================
;; SICM 2.7 Euler Angles (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch2_test.cljc)
;; License: GPL-3.0
;; deftest: section-2-7
;; ============================================================


;; --- helpers from ch2_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.rigid :as r]
  '[emmy.mechanics.rotation])

(def Euler-state (up 't (up 'θ 'φ 'ψ) (up 'θdot 'φdot 'ψdot)))

(def θ (literal-function 'θ))

(def φ (literal-function 'φ))

(def ψ (literal-function 'ψ))

(def q (up θ φ ψ))

(def M-on-path (compose Euler->M q))

(freeze (simplify (M-on-path 't)))
;;=> '(matrix-by-rows
;;        [(+
;;           (* -1 (sin (φ t)) (cos (θ t)) (sin (ψ t)))
;;           (* (cos (φ t)) (cos (ψ t))))
;;         (+
;;           (* -1 (sin (φ t)) (cos (θ t)) (cos (ψ t)))
;;           (* -1 (sin (ψ t)) (cos (φ t))))
;;         (* (sin (φ t)) (sin (θ t)))]
;;        [(+
;;           (* (cos (θ t)) (sin (ψ t)) (cos (φ t)))
;;           (* (sin (φ t)) (cos (ψ t))))
;;         (+
;;           (* (cos (θ t)) (cos (φ t)) (cos (ψ t)))
;;           (* -1 (sin (φ t)) (sin (ψ t))))
;;         (* -1 (cos (φ t)) (sin (θ t)))]
;;        [(* (sin (ψ t)) (sin (θ t)))
;;         (* (cos (ψ t)) (sin (θ t)))
;;         (cos (θ t))])

(freeze (simplify (((r/M-of-q->omega-body-of-t Euler->M) q) 't)))
;;=> '(column-matrix
;;        (+
;;          (* (sin (ψ t)) (sin (θ t)) ((D φ) t))
;;          (* (cos (ψ t)) ((D θ) t)))
;;        (+
;;          (* (cos (ψ t)) (sin (θ t)) ((D φ) t))
;;          (* -1 (sin (ψ t)) ((D θ) t)))
;;        (+ (* (cos (θ t)) ((D φ) t)) ((D ψ) t)))

(freeze (simplify ((r/M->omega-body Euler->M) Euler-state)))
;;=> '(column-matrix
;;        (+ (* φdot (sin ψ) (sin θ)) (* θdot (cos ψ)))
;;        (+ (* φdot (sin θ) (cos ψ)) (* -1 θdot (sin ψ)))
;;        (+ (* φdot (cos θ)) ψdot))"
    "SICM 2.9 Vector Angular Momentum – part 1 (Emmy)"
    ";; ============================================================
;; SICM 2.9 Vector Angular Momentum – part 1 (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch2_test.cljc)
;; License: GPL-3.0
;; deftest: section-2-9
;; ============================================================


;; --- helpers from ch2_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.rigid :as r]
  '[emmy.mechanics.rotation]
  '[emmy.value :as v])

(def Euler-state (up 't (up 'θ 'φ 'ψ) (up 'θdot 'φdot 'ψdot)))

(v/=
    '(+
       (* A φdot (expt (sin θ) 2) (expt (sin ψ) 2))
       (* B φdot (expt (sin θ) 2) (expt (cos ψ) 2))
       (* A θdot (sin θ) (sin ψ) (cos ψ))
       (* -1N B θdot (sin θ) (sin ψ) (cos ψ))
       (* C φdot (expt (cos θ) 2))
       (* C ψdot (cos θ)))
    (simplify
      (ref (((partial 2) (r/T-rigid-body 'A 'B 'C)) Euler-state) 1)))

(zero?
    (simplify
      (-
        (ref ((r/Euler-state->L-space 'A 'B 'C) Euler-state) 2)
        (ref
          (((partial 2) (r/T-rigid-body 'A 'B 'C)) Euler-state)
          1))))

(v/=
    '(* A B C (expt (sin θ) 2))
    (simplify
      (determinant
        (((square (partial 2)) (r/T-rigid-body 'A 'B 'C))
          Euler-state))))"
    "SICM 2.9 Vector Angular Momentum – part 2 (Emmy)"
    ";; ============================================================
;; SICM 2.9 Vector Angular Momentum – part 2 (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch2_test.cljc)
;; License: GPL-3.0
;; deftest: section-2-9b
;; ============================================================


;; --- helpers from ch2_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.rigid :as r]
  '[emmy.mechanics.rotation]
  '[emmy.polynomial.gcd :as pg]
  '[emmy.util :as u])

(def Euler-state (up 't (up 'θ 'φ 'ψ) (up 'θdot 'φdot 'ψdot)))

(def relative-error
 (fn [value reference-value]
   (when (zero? reference-value) (u/illegal \"zero reference value\"))
   (/ (- value reference-value) reference-value)))

(def points (atom []))

(def monitor-errors
 (fn [A B C L0 E0]
   (fn [t state]
     (let [L ((r/Euler-state->L-space A B C) state)
           E ((r/T-rigid-body A B C) state)]
       (swap!
         points
         conj
         [t
          (relative-error (ref L 0) (ref L0 0))
          (relative-error (ref L 1) (ref L0 1))
          (relative-error (ref L 2) (ref L0 2))
          (relative-error E E0)])))))

(def A 1.0)

(def B (Math/sqrt 2.0))

(def C 2.0)

(def state0 (up 0.0 (up 1.0 0.0 0.0) (up 0.1 0.1 0.1)))

(def L0 ((r/Euler-state->L-space A B C) state0))

(def E0 ((r/T-rigid-body A B C) state0))

(binding [pg/*poly-gcd-time-limit* [1 :seconds]]
  ((evolve r/rigid-sysder A B C)
    state0
    0.1
    10.0
    {:compile? true,
     :epsilon 1.0E-12,
     :observe (monitor-errors A B C L0 E0)}))

(> 1.0E-10 (->> @points (mapcat #(drop 1 %)) (map abs) (reduce max)))"
    "SICM 2.10 Axisymmetric Tops (Emmy)"
    ";; ============================================================
;; SICM 2.10 Axisymmetric Tops (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch2_test.cljc)
;; License: GPL-3.0
;; deftest: section-2-10
;; ============================================================


;; --- helpers from ch2_test.cljc ---
(require
  '[emmy.generic :as g]
  '[emmy.mechanics.rigid :as r]
  '[emmy.mechanics.rotation])

(def Euler-state (up 't (up 'θ 'φ 'ψ) (up 'θdot 'φdot 'ψdot)))

(freeze (simplify ((r/T-rigid-body 'A 'A 'C) Euler-state)))
;;=> '(+
;;        (* (/ 1 2) A (expt φdot 2) (expt (sin θ) 2))
;;        (* (/ 1 2) C (expt φdot 2) (expt (cos θ) 2))
;;        (* C φdot ψdot (cos θ))
;;        (* (/ 1 2) A (expt θdot 2))
;;        (* (/ 1 2) C (expt ψdot 2)))"
    "SICM 3.1 Hamilton's Equations (Emmy)"
    ";; ============================================================
;; SICM 3.1 Hamilton's Equations (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch3_test.cljc)
;; License: GPL-3.0
;; deftest: section-3-1
;; ============================================================


;; --- helpers from ch3_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.mechanics.hamilton :as H]
  '[emmy.mechanics.lagrange :as L])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(do
  (simplify
      (((Hamilton-equations
          (H-rectangular
            'm
            (literal-function 'V (-> (X Real Real) Real))))
         (up (literal-function 'x) (literal-function 'y))
         (down (literal-function 'p_x) (literal-function 'p_y)))
        't))
  ;;=> '(up
  ;;          0
  ;;          (up
  ;;            (/ (+ (* m ((D x) t)) (* -1 (p_x t))) m)
  ;;            (/ (+ (* m ((D y) t)) (* -1 (p_y t))) m))
  ;;          (down
  ;;            (+ ((D p_x) t) (((partial 0) V) (x t) (y t)))
  ;;            (+ ((D p_y) t) (((partial 1) V) (x t) (y t))))))

(do
  (simplify
      ((Lagrangian->Hamiltonian
         (L-rectangular
           'm
           (literal-function 'V (-> (X Real Real) Real))))
        (up 't (up 'x 'y) (down 'p_x 'p_y))))
  ;;=> '(/
  ;;          (+
  ;;            (* m (V x y))
  ;;            (* (/ 1 2) (expt p_x 2))
  ;;            (* (/ 1 2) (expt p_y 2)))
  ;;          m))"
    "SICM 3.2 Poisson Brackets (Emmy)"
    ";; ============================================================
;; SICM 3.2 Poisson Brackets (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch3_test.cljc)
;; License: GPL-3.0
;; deftest: section-3-2
;; ============================================================


;; --- helpers from ch3_test.cljc ---
(require '[emmy.env :as e] '[emmy.mechanics.hamilton :as H])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(do
  (let [F (literal-function 'F (Hamiltonian 2))
        G (literal-function 'G (Hamiltonian 2))
        H (literal-function 'H (Hamiltonian 2))]
    (zero?
        (simplify
          ((+
             (Poisson-bracket F (Poisson-bracket G H))
             (Poisson-bracket G (Poisson-bracket H F))
             (Poisson-bracket H (Poisson-bracket F G)))
            (up 't (up 'x 'y) (down 'px 'py)))))))"
    "SICM 3.4 Phase Space Reduction (Emmy)"
    ";; ============================================================
;; SICM 3.4 Phase Space Reduction (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch3_test.cljc)
;; License: GPL-3.0
;; deftest: section-3-4
;; ============================================================


;; --- helpers from ch3_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.examples.top :as top]
  '[emmy.expression.analyze :as a]
  '[emmy.expression.compile :as c]
  '[emmy.mechanics.lagrange :as L]
  '[emmy.polynomial.gcd :as pg])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(do
  (simplify
      ((Lagrangian->Hamiltonian
         (L-central-polar 'm (literal-function 'V)))
        (up 't (up 'r 'phi) (down 'p_r 'p_phi))))
  ;;=> '(/
  ;;          (+
  ;;            (* m (expt r 2) (V r))
  ;;            (* (/ 1 2) (expt p_r 2) (expt r 2))
  ;;            (* (/ 1 2) (expt p_phi 2)))
  ;;          (* m (expt r 2)))
  (simplify
      (((Hamilton-equations
          (Lagrangian->Hamiltonian
            (L-central-polar 'm (literal-function 'V))))
         (up (literal-function 'r) (literal-function 'phi))
         (down (literal-function 'p_r) (literal-function 'p_phi)))
        't))
  ;;=> '(up
  ;;          0
  ;;          (up
  ;;            (/ (+ (* m ((D r) t)) (* -1 (p_r t))) m)
  ;;            (/
  ;;              (+ (* m (expt (r t) 2) ((D phi) t)) (* -1 (p_phi t)))
  ;;              (* m (expt (r t) 2))))
  ;;          (down
  ;;            (/
  ;;              (+
  ;;                (* m (expt (r t) 3) ((D p_r) t))
  ;;                (* m (expt (r t) 3) ((D V) (r t)))
  ;;                (* -1 (expt (p_phi t) 2)))
  ;;              (* m (expt (r t) 3)))
  ;;            ((D p_phi) t))))

(do
  (simplify
      ((Lagrangian->Hamiltonian (top/L-axisymmetric 'A 'C 'gMR))
        (up 't (up 'theta 'phi 'psi) (down 'p_theta 'p_phi 'p_psi))))
  ;;=> '(/
  ;;          (+
  ;;            (* A C gMR (expt (sin theta) 2) (cos theta))
  ;;            (* (/ 1 2) A (expt p_psi 2) (expt (sin theta) 2))
  ;;            (* (/ 1 2) C (expt p_psi 2) (expt (cos theta) 2))
  ;;            (* (/ 1 2) C (expt p_theta 2) (expt (sin theta) 2))
  ;;            (* -1 C p_phi p_psi (cos theta))
  ;;            (* (/ 1 2) C (expt p_phi 2)))
  ;;          (* A C (expt (sin theta) 2))))

(do
  (let [top-state (up
                    't
                    (up 'theta 'phi 'psi)
                    (down 'p_theta 'p_phi 'p_psi))
        H (Lagrangian->Hamiltonian (top/L-axisymmetric 'A 'C 'gMR))
        sysder (Hamiltonian->state-derivative H)]
    (simplify (H top-state))
    ;;=> '(/
    ;;            (+
    ;;              (* A C gMR (expt (sin theta) 2) (cos theta))
    ;;              (* (/ 1 2) A (expt p_psi 2) (expt (sin theta) 2))
    ;;              (* (/ 1 2) C (expt p_psi 2) (expt (cos theta) 2))
    ;;              (* (/ 1 2) C (expt p_theta 2) (expt (sin theta) 2))
    ;;              (* -1 C p_phi p_psi (cos theta))
    ;;              (* (/ 1 2) C (expt p_phi 2)))
    ;;            (* A C (expt (sin theta) 2)))
    (binding [pg/*poly-gcd-time-limit* [2 :seconds]]
      (simplify (sysder top-state))
      ;;=> '(up
      ;;              1
      ;;              (up
      ;;                (/ p_theta A)
      ;;                (/
      ;;                  (+ (* -1 p_psi (cos theta)) p_phi)
      ;;                  (* A (expt (sin theta) 2)))
      ;;                (/
      ;;                  (+
      ;;                    (* A p_psi (expt (sin theta) 2))
      ;;                    (* C p_psi (expt (cos theta) 2))
      ;;                    (* -1 C p_phi (cos theta)))
      ;;                  (* A C (expt (sin theta) 2))))
      ;;              (down
      ;;                (/
      ;;                  (+
      ;;                    (* A gMR (expt (cos theta) 4))
      ;;                    (* -2 A gMR (expt (cos theta) 2))
      ;;                    (* -1 p_phi p_psi (expt (cos theta) 2))
      ;;                    (* (expt p_phi 2) (cos theta))
      ;;                    (* (expt p_psi 2) (cos theta))
      ;;                    (* A gMR)
      ;;                    (* -1 p_phi p_psi))
      ;;                  (* A (expt (sin theta) 3)))
      ;;                0
      ;;                0)))
    (c/compile-state-fn
        (fn [] sysder)
        []
        top-state
        {:mode :js, :gensym-fn (a/monotonic-symbol-generator 2)})
    ;;=> [\"[y01, [y02, y03, y04], [y05, y06, y07]]\"
    ;;          \"_\"
    ;;          (maybe-defloatify
    ;;            (s/join
    ;;              \"\\n\"
    ;;              [\"  const _08 = 1.0;\"
    ;;               \"  const _09 = y05 / A;\"
    ;;               \"  const _10 = -1.0;\"
    ;;               \"  const _11 = _10 * y07;\"
    ;;               \"  const _12 = Math.cos(y02);\"
    ;;               \"  const _13 = _11 * _12;\"
    ;;               \"  const _14 = _13 + y06;\"
    ;;               \"  const _15 = Math.sin(y02);\"
    ;;               \"  const _16 = 2.0;\"
    ;;               \"  const _17 = Math.pow(_15, _16);\"
    ;;               \"  const _18 = A * _17;\"
    ;;               \"  const _19 = _14 / _18;\"
    ;;               \"  const _20 = A * y07;\"
    ;;               \"  const _21 = _20 * _17;\"
    ;;               \"  const _22 = C * y07;\"
    ;;               \"  const _23 = Math.pow(_12, _16);\"
    ;;               \"  const _24 = _22 * _23;\"
    ;;               \"  const _25 = _21 + _24;\"
    ;;               \"  const _26 = _10 * C;\"
    ;;               \"  const _27 = _26 * y06;\"
    ;;               \"  const _28 = _27 * _12;\"
    ;;               \"  const _29 = _25 + _28;\"
    ;;               \"  const _30 = A * C;\"
    ;;               \"  const _31 = _30 * _17;\"
    ;;               \"  const _32 = _29 / _31;\"
    ;;               \"  const _33 = [_09, _19, _32];\"
    ;;               \"  const _34 = A * gMR;\"
    ;;               \"  const _35 = 4.0;\"
    ;;               \"  const _36 = Math.pow(_12, _35);\"
    ;;               \"  const _37 = _34 * _36;\"
    ;;               \"  const _38 = -2.0;\"
    ;;               \"  const _39 = _38 * A;\"
    ;;               \"  const _40 = _39 * gMR;\"
    ;;               \"  const _41 = _40 * _23;\"
    ;;               \"  const _42 = _37 + _41;\"
    ;;               \"  const _43 = _10 * y06;\"
    ;;               \"  const _44 = _43 * y07;\"
    ;;               \"  const _45 = _44 * _23;\"
    ;;               \"  const _46 = _42 + _45;\"
    ;;               \"  const _47 = Math.pow(y06, _16);\"
    ;;               \"  const _48 = _47 * _12;\"
    ;;               \"  const _49 = _46 + _48;\"
    ;;               \"  const _50 = Math.pow(y07, _16);\"
    ;;               \"  const _51 = _50 * _12;\"
    ;;               \"  const _52 = _49 + _51;\"
    ;;               \"  const _53 = _52 + _34;\"
    ;;               \"  const _54 = _53 + _44;\"
    ;;               \"  const _55 = 3.0;\"
    ;;               \"  const _56 = Math.pow(_15, _55);\"
    ;;               \"  const _57 = A * _56;\"
    ;;               \"  const _58 = _54 / _57;\"
    ;;               \"  const _59 = 0.0;\"
    ;;               \"  const _60 = [_58, _59, _59];\"
    ;;               \"  const _61 = [_08, _33, _60];\"
    ;;               \"  return _61;\"]))]))"
    "SICM 3.5 Phase Space Evolution (Emmy)"
    ";; ============================================================
;; SICM 3.5 Phase Space Evolution (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch3_test.cljc)
;; License: GPL-3.0
;; deftest: section-3-5
;; ============================================================


;; --- helpers from ch3_test.cljc ---
(require
  '[emmy.env :as e]
  '[emmy.examples.driven-pendulum :as driven]
  '[emmy.expression.analyze :as a]
  '[emmy.expression.compile :as c])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(do
  (let [H ((Lagrangian->Hamiltonian (driven/L 'm 'l 'g 'a 'omega))
            (up 't 'theta 'p_theta))]
    (simplify H)
    ;;=> '(/
    ;;            (+
    ;;              (*
    ;;                (/ -1 2)
    ;;                (expt a 2)
    ;;                (expt l 2)
    ;;                (expt m 2)
    ;;                (expt omega 2)
    ;;                (expt (sin (* omega t)) 2)
    ;;                (expt (cos theta) 2))
    ;;              (* a g (expt l 2) (expt m 2) (cos (* omega t)))
    ;;              (* a l m omega p_theta (sin (* omega t)) (sin theta))
    ;;              (* -1 g (expt l 3) (expt m 2) (cos theta))
    ;;              (* (/ 1 2) (expt p_theta 2)))
    ;;            (* (expt l 2) m)))
  (let [sysder (simplify
                 ((Hamiltonian->state-derivative
                    (Lagrangian->Hamiltonian
                      (driven/L 'm 'l 'g 'a 'omega)))
                   (up 't 'theta 'p_theta)))]
    sysder
    ;;=> '(up
    ;;            1
    ;;            (/
    ;;              (+ (* a l m omega (sin (* omega t)) (sin theta)) p_theta)
    ;;              (* (expt l 2) m))
    ;;            (/
    ;;              (+
    ;;                (*
    ;;                  -1
    ;;                  (expt a 2)
    ;;                  l
    ;;                  m
    ;;                  (expt omega 2)
    ;;                  (expt (sin (* omega t)) 2)
    ;;                  (sin theta)
    ;;                  (cos theta))
    ;;                (* -1 a omega p_theta (sin (* omega t)) (cos theta))
    ;;                (* -1 g (expt l 2) m (sin theta)))
    ;;              l))
    (c/compile-state-fn
        (fn []
          (Hamiltonian->state-derivative
            (Lagrangian->Hamiltonian (driven/L 'm 'l 'g 'a 'omega))))
        []
        (up 't 'theta 'p_theta)
        {:mode :js, :gensym-fn (a/monotonic-symbol-generator 2)})
    ;;=> [\"[y01, y02, y03]\"
    ;;          \"_\"
    ;;          (maybe-defloatify
    ;;            (s/join
    ;;              \"\\n\"
    ;;              [\"  const _04 = 1.0;\"
    ;;               \"  const _05 = a * l;\"
    ;;               \"  const _06 = _05 * m;\"
    ;;               \"  const _07 = _06 * omega;\"
    ;;               \"  const _08 = omega * y01;\"
    ;;               \"  const _09 = Math.sin(_08);\"
    ;;               \"  const _10 = _07 * _09;\"
    ;;               \"  const _11 = Math.sin(y02);\"
    ;;               \"  const _12 = _10 * _11;\"
    ;;               \"  const _13 = _12 + y03;\"
    ;;               \"  const _14 = 2.0;\"
    ;;               \"  const _15 = Math.pow(l, _14);\"
    ;;               \"  const _16 = _15 * m;\"
    ;;               \"  const _17 = _13 / _16;\"
    ;;               \"  const _18 = -1.0;\"
    ;;               \"  const _19 = Math.pow(a, _14);\"
    ;;               \"  const _20 = _18 * _19;\"
    ;;               \"  const _21 = _20 * l;\"
    ;;               \"  const _22 = _21 * m;\"
    ;;               \"  const _23 = Math.pow(omega, _14);\"
    ;;               \"  const _24 = _22 * _23;\"
    ;;               \"  const _25 = Math.pow(_09, _14);\"
    ;;               \"  const _26 = _24 * _25;\"
    ;;               \"  const _27 = _26 * _11;\"
    ;;               \"  const _28 = Math.cos(y02);\"
    ;;               \"  const _29 = _27 * _28;\"
    ;;               \"  const _30 = _18 * a;\"
    ;;               \"  const _31 = _30 * omega;\"
    ;;               \"  const _32 = _31 * y03;\"
    ;;               \"  const _33 = _32 * _09;\"
    ;;               \"  const _34 = _33 * _28;\"
    ;;               \"  const _35 = _29 + _34;\"
    ;;               \"  const _36 = _18 * g;\"
    ;;               \"  const _37 = _36 * _15;\"
    ;;               \"  const _38 = _37 * m;\"
    ;;               \"  const _39 = _38 * _11;\"
    ;;               \"  const _40 = _35 + _39;\"
    ;;               \"  const _41 = _40 / l;\"
    ;;               \"  const _42 = [_04, _17, _41];\"
    ;;               \"  return _42;\"]))]))"
    "SICM 5.1 Point Transformations (Emmy)"
    ";; ============================================================
;; SICM 5.1 Point Transformations (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch5_test.cljc)
;; License: GPL-3.0
;; deftest: section-5-1
;; ============================================================


;; --- helpers from ch5_test.cljc ---
(require '[emmy.env :as e] '[emmy.mechanics.hamilton :as H])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(do
  (simplify
      ((compose (H-central 'm (literal-function 'V)) (F->CT p->r))
        (up 't (up 'r 'phi) (down 'p_r 'p_phi))))
  ;;=> '(/
  ;;          (+
  ;;            (* m (expt r 2) (V r))
  ;;            (* (/ 1 2) (expt p_r 2) (expt r 2))
  ;;            (* (/ 1 2) (expt p_phi 2)))
  ;;          (* m (expt r 2))))"
    "SICM 5.2 General Canonical Transformations (Emmy)"
    ";; ============================================================
;; SICM 5.2 General Canonical Transformations (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch5_test.cljc)
;; License: GPL-3.0
;; deftest: section-5-2
;; ============================================================


;; --- helpers from ch5_test.cljc ---
(require '[emmy.env :as e] '[emmy.mechanics.hamilton :as H])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def J-func (fn [[_ dh1 dh2]] (up 0 dh2 (- dh1))))

(do
  (simplify
      ((compositional-canonical?
         (F->CT p->r)
         (H-central 'm (literal-function 'V)))
        (up 't (up 'r 'phi) (down 'p_r 'p_phi))))
  ;;=> '(up 0 (up 0 0) (down 0 0))
  (simplify
      ((time-independent-canonical? (polar-canonical 'alpha))
        (up 't 'theta 'I)))
  ;;=> '(up 0 0 0)
  (let [a-non-canonical-transform (fn 
                                    [[t theta p]]
                                    (let 
                                      [x (* p (sin theta))
                                       p_x (* p (cos theta))]
                                      (up t x p_x)))]
    (not=
        '(up 0 0 0)
        (simplify
          ((time-independent-canonical? a-non-canonical-transform)
            (up 't 'theta 'p)))))
  (simplify
      (let [s (up 't (up 'x 'y) (down 'px 'py))
            s* (compatible-shape s)]
        (s->m s* ((D J-func) s*) s*)))
  ;;=> '(matrix-by-rows
  ;;          [0 0 0 0 0]
  ;;          [0 0 0 1 0]
  ;;          [0 0 0 0 1]
  ;;          [0 -1 0 0 0]
  ;;          [0 0 -1 0 0])
  (let [symplectic? (fn [C]
                      (fn [s]
                        (let [s* (compatible-shape s)
                              J (s->m s* ((D J-func) s*) s*)
                              DCs (s->m s* ((D C) s) s)]
                          (- J (* DCs J (transpose DCs))))))]
    (simplify
        ((symplectic? (F->CT p->r))
          (up 't (up 'r 'varphi) (down 'p_r 'p_varphi))))
    ;;=> '(matrix-by-rows
    ;;            [0 0 0 0 0]
    ;;            [0 0 0 0 0]
    ;;            [0 0 0 0 0]
    ;;            [0 0 0 0 0]
    ;;            [0 0 0 0 0])))

(do
  (simplify
      ((symplectic-transform? (F->CT p->r))
        (up 't (up 'r 'theta) (down 'p_r 'p_theta))))
  ;;=> '(matrix-by-rows [0 0 0 0] [0 0 0 0] [0 0 0 0] [0 0 0 0]))

(do
  (let [rotating (fn [n]
                   (fn [[t [x y z]]]
                     (up
                       (+ (* (cos (* n t)) x) (* (sin (* n t)) y))
                       (- (* (cos (* n t)) y) (* (sin (* n t)) x))
                       z)))
        C-rotating (fn [Omega] (F->CT (rotating Omega)))
        K (fn [Omega]
            (fn [[_ [x y _] [p_x p_y _]]]
              (* Omega (- (* x p_y) (* y p_x)))))]
    (simplify
        ((symplectic-transform? (C-rotating 'Omega))
          (up 't (up 'x 'y 'z) (down 'p_x 'p_y 'p_z))))
    ;;=> '(matrix-by-rows
    ;;            [0 0 0 0 0 0]
    ;;            [0 0 0 0 0 0]
    ;;            [0 0 0 0 0 0]
    ;;            [0 0 0 0 0 0]
    ;;            [0 0 0 0 0 0]
    ;;            [0 0 0 0 0 0])
    (simplify
        ((canonical-K? (C-rotating 'Omega) (K 'Omega))
          (up 't (up 'x 'y 'z) (down 'p_x 'p_y 'p_z))))
    ;;=> '(up 0 (up 0 0 0) (down 0 0 0))))"
    "SICM 5.3 Invariants of Canonical Transformations (Emmy)"
    ";; ============================================================
;; SICM 5.3 Invariants of Canonical Transformations (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch5_test.cljc)
;; License: GPL-3.0
;; deftest: section-5-3
;; ============================================================


;; --- helpers from ch5_test.cljc ---
(require '[emmy.env :as e])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def omega
 (fn [zeta1 zeta2]
   (-
     (* (momentum zeta2) (coordinate zeta1))
     (* (momentum zeta1) (coordinate zeta2)))))

(def a-polar-state (up 't (up 'r 'phi) (down 'pr 'pphi)))

(def zeta1 (up 0 (up 'dr1 'dphi1) (down 'dpr1 'dpphi1)))

(def zeta2 (up 0 (up 'dr2 'dphi2) (down 'dpr2 'dpphi2)))

(let [DCs ((D (F->CT p->r)) a-polar-state)]
    (simplify
      (- (omega zeta1 zeta2) (omega (* DCs zeta1) (* DCs zeta2)))))
;;=> 0"
    "SICM 5.7 Symplectic Condition (Emmy)"
    ";; ============================================================
;; SICM 5.7 Symplectic Condition (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch5_test.cljc)
;; License: GPL-3.0
;; deftest: section-5-7
;; ============================================================


;; --- helpers from ch5_test.cljc ---
(require '[emmy.env :as e])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(def C
 (fn [alpha omega omega0]
   (fn [delta-t]
     (fn [[t0 q0 p0]]
       (let [alpha' (/ alpha (- (square omega0) (square omega)))
             M (matrix-by-rows
                 [(* (cos omega0) delta-t) (* (sin omega0) delta-t)]
                 [(- (* (sin omega0) delta-t))
                  (* (cos omega0) delta-t)])
             a (column-matrix
                 (- q0 (* alpha' (cos (* omega t0))))
                 (*
                   (/ omega0)
                   (+ p0 (* alpha' omega (sin (* omega t0))))))
             b (column-matrix
                 (* alpha' (cos (* omega (+ t0 delta-t))))
                 (-
                   (*
                     alpha'
                     (/ omega omega0)
                     (sin (* omega (+ t0 delta-t))))))]
         (+ (* M a) b))))))

(def solution
 (fn [alpha omega omega0]
   (fn [state0]
     (fn [t] (((C alpha omega omega0) (- t (first state0))) state0)))))

(def sol ((solution 'α 'ω 'ω_0) (up 't_0 'q_0 'p_0)))

(def solution-C (sol 't))

(def _q (ref solution-C 0))

(def _p (ref solution-C 1))

(def _Dsol ((D sol) 't))"
    "SICM 5.10 Generating Functions (Emmy)"
    ";; ============================================================
;; SICM 5.10 Generating Functions (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch5_test.cljc)
;; License: GPL-3.0
;; deftest: section-5-10
;; ============================================================


;; --- helpers from ch5_test.cljc ---
(require '[emmy.env :as e] '[emmy.mechanics.hamilton :as H])

;; (Pedagogical redef of `simplify` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def simplify (comp e/freeze e/simplify))

(letfn
  [(H-harmonic
     [m k]
     (fn [state]
       (+
         (/ (square (momentum state)) (* 2 m))
         (* (/ 1 2) k (square (coordinate state))))))]
  (simplify
      (take
        6
        (seq
          (((Lie-transform (H-harmonic 'm 'k) 'dt) coordinate)
            (up 0 'x0 'p0)))))
  ;;=> '(x0
  ;;          (/ (* dt p0) m)
  ;;          (/ (* (/ -1 2) (expt dt 2) k x0) m)
  ;;          (/ (* (/ -1 6) (expt dt 3) k p0) (expt m 2))
  ;;          (/ (* (/ 1 24) (expt dt 4) (expt k 2) x0) (expt m 2))
  ;;          (/ (* (/ 1 120) (expt dt 5) (expt k 2) p0) (expt m 3)))
  (simplify
      (take
        6
        (seq
          (((Lie-transform (H-harmonic 'm 'k) 'dt) momentum)
            (up 0 'x0 'p0)))))
  ;;=> '(p0
  ;;          (* -1 dt k x0)
  ;;          (/ (* (/ -1 2) (expt dt 2) k p0) m)
  ;;          (/ (* (/ 1 6) (expt dt 3) (expt k 2) x0) m)
  ;;          (/ (* (/ 1 24) (expt dt 4) (expt k 2) p0) (expt m 2))
  ;;          (/ (* (/ -1 120) (expt dt 5) (expt k 3) x0) (expt m 2)))
  (simplify
      (take
        6
        (seq
          (((Lie-transform (H-harmonic 'm 'k) 'dt) (H-harmonic 'm 'k))
            (up 0 'x0 'p0)))))
  ;;=> '((/ (+ (* (/ 1 2) k m (expt x0 2)) (* (/ 1 2) (expt p0 2))) m)
  ;;          0
  ;;          0
  ;;          0
  ;;          0
  ;;          0)
  (let [state (up 't (up 'r_0 'phi_0) (down 'p_r_0 'p_phi_0))]
    (simplify ((H-central-polar 'm (literal-function 'U)) state))
    ;;=> '(/
    ;;            (+
    ;;              (* m (expt r_0 2) (U r_0))
    ;;              (* (/ 1 2) (expt p_r_0 2) (expt r_0 2))
    ;;              (* (/ 1 2) (expt p_phi_0 2)))
    ;;            (* m (expt r_0 2)))
    (simplify
        (take
          4
          (((Lie-transform
              (H-central-polar 'm (literal-function 'U))
              'dt)
             coordinate)
            state)))
    ;;=> '((up r_0 phi_0)
    ;;            (up (/ (* dt p_r_0) m) (/ (* dt p_phi_0) (* m (expt r_0 2))))
    ;;            (up
    ;;              (/
    ;;                (+
    ;;                  (* (/ -1 2) (expt dt 2) m (expt r_0 3) ((D U) r_0))
    ;;                  (* (/ 1 2) (expt dt 2) (expt p_phi_0 2)))
    ;;                (* (expt m 2) (expt r_0 3)))
    ;;              (/
    ;;                (* -1 (expt dt 2) p_phi_0 p_r_0)
    ;;                (* (expt m 2) (expt r_0 3))))
    ;;            (up
    ;;              (/
    ;;                (+
    ;;                  (*
    ;;                    (/ -1 6)
    ;;                    (expt dt 3)
    ;;                    m
    ;;                    p_r_0
    ;;                    (expt r_0 4)
    ;;                    (((expt D 2) U) r_0))
    ;;                  (* (/ -1 2) (expt dt 3) (expt p_phi_0 2) p_r_0))
    ;;                (* (expt m 3) (expt r_0 4)))
    ;;              (/
    ;;                (+
    ;;                  (*
    ;;                    (/ 1 3)
    ;;                    (expt dt 3)
    ;;                    m
    ;;                    p_phi_0
    ;;                    (expt r_0 3)
    ;;                    ((D U) r_0))
    ;;                  (* (expt dt 3) p_phi_0 (expt p_r_0 2) (expt r_0 2))
    ;;                  (* (/ -1 3) (expt dt 3) (expt p_phi_0 3)))
    ;;                (* (expt m 3) (expt r_0 6)))))))"
    "SICM 6.2 Time Evolution is Canonical (Emmy)"
    ";; ============================================================
;; SICM 6.2 Time Evolution is Canonical (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch6_test.cljc)
;; License: GPL-3.0
;; deftest: section-6-2
;; ============================================================


;; --- helpers from ch6_test.cljc ---
(require '[emmy.env :as e])

(def H0
 (fn [alpha] (fn [[_ _ ptheta]] (/ (square ptheta) (* 2 alpha)))))

(def H1 (fn [beta] (fn [[_ theta _]] (* -1 beta (cos theta)))))

(def H-pendulum-series
 (fn [alpha beta epsilon] (series (H0 alpha) (* epsilon (H1 beta)))))

(def W
 (fn [alpha beta]
   (fn [[_ theta ptheta]] (/ (* -1 alpha beta (sin theta)) ptheta))))

(def a-state (up 't 'theta 'p_theta))

(def L (Lie-derivative (W 'α 'β)))

(def H (H-pendulum-series 'α 'β 'ε))

(def E (((exp (* 'ε L)) H) a-state))

(def C
 (fn [alpha beta epsilon order]
   (fn [state]
     (series:sum
       (((Lie-transform (W alpha beta) epsilon) identity) state)
       order))))

(zero?
    (simplify
      ((+ ((Lie-derivative (W 'alpha 'beta)) (H0 'alpha)) (H1 'beta))
        a-state)))

(freeze (simplify (take 6 E)))
;;=> '((/ (* (/ 1 2) (expt p_theta 2)) α)
;;        0
;;        (/
;;          (* (/ 1 2) α (expt β 2) (expt ε 2) (expt (sin theta) 2))
;;          (expt p_theta 2))
;;        0
;;        0
;;        0)

(freeze (simplify (series:sum E 2)))
;;=> '(/
;;        (+
;;          (*
;;            (/ 1 2)
;;            (expt α 2)
;;            (expt β 2)
;;            (expt ε 2)
;;            (expt (sin theta) 2))
;;          (* (/ 1 2) (expt p_theta 4)))
;;        (* (expt p_theta 2) α))

(freeze (simplify ((C 'α 'β 'ε 2) a-state)))
;;=> '(up
;;        t
;;        (/
;;          (+
;;            (*
;;              (/ -1 2)
;;              (expt α 2)
;;              (expt β 2)
;;              (expt ε 2)
;;              (sin theta)
;;              (cos theta))
;;            (* (expt p_theta 2) α β ε (sin theta))
;;            (* (expt p_theta 4) theta))
;;          (expt p_theta 4))
;;        (/
;;          (+
;;            (* (expt p_theta 2) α β ε (cos theta))
;;            (* (/ -1 2) (expt α 2) (expt β 2) (expt ε 2))
;;            (expt p_theta 4))
;;          (expt p_theta 3)))"
    "SICM 7.1 Composition of Functions (Emmy)"
    ";; ============================================================
;; SICM 7.1 Composition of Functions (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch7_test.cljc)
;; License: GPL-3.0
;; deftest: section-1
;; ============================================================


;; --- helpers from ch7_test.cljc ---
(require '[emmy.value :refer [within]])

(def near (within 1.0E-6))

(def h (compose cube sin))

(def g (* cube sin))

(cube (sin 2))
;;=> (h 2)

(near (h 2) 0.7518269)

(* (cube 2) (sin 2))
;;=> (g 2)

(near (g 2) 7.2743794)

(simplify (h 'a))
;;=> '(expt (sin a) 3)

(simplify ((- (+ (square sin) (square cos)) 1) 'a))
;;=> 0

(simplify ((literal-function 'f) 'x))
;;=> '(f x)

(simplify ((compose (literal-function 'f) (literal-function 'g)) 'x))
;;=> '(f (g x))"
    "SICM 7.2 Pendulum as a Perturbed Rotor (Emmy)"
    ";; ============================================================
;; SICM 7.2 Pendulum as a Perturbed Rotor (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch7_test.cljc)
;; License: GPL-3.0
;; deftest: section-2
;; ============================================================


;; --- helpers from ch7_test.cljc ---
(require '[emmy.env :as e] '[emmy.value :refer [within]])

(def near (within 1.0E-6))

(do
  (simplify ((literal-function 'g [0 0] 0) 'x 'y))
  ;;=> '(g x y))

(do
  (let [s (up 't (up 'x 'y) (down 'p_x 'p_y))
        H (literal-function 'H (up 0 (up 0 0) (down 0 0)) 0)]
    (simplify (H s))
    ;;=> '(H (up t (up x y) (down p_x p_y)))
    (thrown?
        IllegalArgumentException
        (H (up 0 (up 1 2) (down 1 2 3))))
    (thrown? IllegalArgumentException (H (up 0 (up 1) (down 1 2))))
    (thrown?
        IllegalArgumentException
        (H (up (up 1 2) (up 1 2) (down 1 2))))
    (freeze (simplify ((D H) s)))
    ;;=> '(down
    ;;            (((partial 0) H) (up t (up x y) (down p_x p_y)))
    ;;            (down
    ;;              (((partial 1 0) H) (up t (up x y) (down p_x p_y)))
    ;;              (((partial 1 1) H) (up t (up x y) (down p_x p_y))))
    ;;            (up
    ;;              (((partial 2 0) H) (up t (up x y) (down p_x p_y)))
    ;;              (((partial 2 1) H) (up t (up x y) (down p_x p_y)))))))"
    "SICM 7.3 Two Frequencies (Emmy)"
    ";; ============================================================
;; SICM 7.3 Two Frequencies (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch7_test.cljc)
;; License: GPL-3.0
;; deftest: section-3
;; ============================================================


;; --- helpers from ch7_test.cljc ---
(require '[emmy.value :refer [within]])

(def near (within 1.0E-6))

(def derivative-of-sine (D sin))

(simplify (derivative-of-sine 'x))
;;=> '(cos x)

(simplify (((* (- D I) (+ D I)) (literal-function 'f)) 'x))
;;=> '(+ (((expt D 2) f) x) (* -1 (f x)))"
    "SICM 7.4 Higher Order (Emmy)"
    ";; ============================================================
;; SICM 7.4 Higher Order (Emmy)
;; ============================================================
;; Source : github.com/mentat-collective/emmy (test/emmy/sicm/ch7_test.cljc)
;; License: GPL-3.0
;; deftest: section-4
;; ============================================================


;; --- helpers from ch7_test.cljc ---
(require '[emmy.env :as e] '[emmy.value :refer [within]])

(def near (within 1.0E-6))

(def g (fn [x y] (up (square (+ x y)) (cube (- y x)) (exp (+ x y)))))

(freeze (simplify ((D g) 'x 'y)))
;;=> '(down
;;        (up
;;          (+ (* 2 x) (* 2 y))
;;          (+ (* -3 (expt x 2)) (* 6 x y) (* -3 (expt y 2)))
;;          (* (exp x) (exp y)))
;;        (up
;;          (+ (* 2 x) (* 2 y))
;;          (+ (* 3 (expt x 2)) (* -6 x y) (* 3 (expt y 2)))
;;          (* (exp x) (exp y))))"
    "SICM 1.12 Projects"
    ";; ===========================================
;; SICM §1.12 — Projects
;; Chapter 1 — Lagrangian Mechanics
;; https://tgvaughan.github.io/sicm/chapter001.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[Dt Euler-Lagrange-operator F F->C F-tilde Gamma-bar L-central-polar L-central-rectangular L-free-particle L-free-polar L-free-rectangular L-harmonic L-pend L-periodically-driven-pendulum L-rotating-polar L-rotating-rectangular L-uniform-acceleration L0 L3-central LR3B LR3B1 Lagrange-equations Lagrange-equations-first-order Lagrangian->acceleration Lagrangian->energy Lagrangian->state-derivative Lagrangian-action Rx T-pend T3-spherical V V-pend ang-mom-z dp-coordinates f find-path gravitational-energy harmonic-state-derivative make-eta make-path monitor-theta p->r parametric-path-action pend-state-derivative periodic-drive plot-win proposed-solution q qv->state-path s->r test-path the-Noether-integral varied-free-particle-action win2]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 1 ---

(defn L-free-particle [mass]
  (fn [local]
        (let [v (velocity local)] (* 1/2 mass (dot-product v v)))))

(def q
  (up
       (literal-function 'x) (literal-function 'y) (literal-function 'z)))

(q 't)

((D q) 't)

((Gamma q) 't)

((compose (L-free-particle 'm) (Gamma q)) 't)

(simplify ((compose (L-free-particle 'm) (Gamma q)) 't))

;; (Pedagogical redef of `Lagrangian-action` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian-action [L q t1 t2]
;;   (definite-integral (compose L (Gamma q)) t1 t2))

(defn test-path [t] (up (+ (* 4 t) 7) (+ (* 3 t) 5) (+ (* 2 t) 1)))

(Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0)

(defn make-eta [nu t1 t2] (fn [t] (* (- t t1) (- t t2) (nu t))))

(defn varied-free-particle-action [mass q nu t1 t2]
  (fn [eps]
        (let [eta (make-eta nu t1 t2)]
            (Lagrangian-action
                 (L-free-particle mass) (+ q (* eps eta)) t1 t2))))

((varied-free-particle-action
   3.0 test-path (up sin cos square) 0.0 10.0)
  0.001)

(minimize
  (varied-free-particle-action
            3.0 test-path (up sin cos square) 0.0 10.0)
  -2.0 1.0)

(defn parametric-path-action [Lagrangian t0 q0 t1 q1]
  (fn [qs]
        (let [path (make-path t0 q0 t1 q1 qs)]
            (Lagrangian-action Lagrangian path t0 t1))))

;; (Pedagogical redef of `find-path` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn find-path [Lagrangian t0 q0 t1 q1 n]
;;   (let [initial-qs (linear-interpolants q0 q1 n)]
;;         (let [minimizing-qs (multidimensional-minimize
;;                                  (parametric-path-action
;;                                                             Lagrangian
;;                                                             t0 q0 t1 q1)
;;                                  initial-qs)]
;;              (make-path t0 q0 t1 q1 minimizing-qs))))

(defn L-harmonic [m k]
  (fn [local]
        (let [q (coordinate local) v (velocity local)]
            (- (* 1/2 m (square v)) (* 1/2 k (square q))))))

(def q (find-path (L-harmonic 1.0 1.0) 0.0 1.0 (/ Math/PI 2) 0.0 3))

(def win2 (frame 0.0 (/ Math/PI 2) 0.0 1.2))


(defn parametric-path-action [Lagrangian t0 q0 t1 q1]
  (fn [intermediate-qs]
        (let [path (make-path t0 q0 t1 q1 intermediate-qs)]
            (graphics-clear win2)
            (plot-function win2 path t0 t1 (/ (- t1 t0) 100))
            (Lagrangian-action Lagrangian path t0 t1))))


(find-path (L-harmonic 1.0 1.0) 0.0 1.0 (/ Math/PI 2) 0.0 2)

(defn f [q]
  (compose
        (literal-function
                 'F
                 '(->
                                    (UP Real (UP* Real) (UP* Real)) Real))
        (Gamma q)))

;; (Pedagogical redef of `Lagrange-equations` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrange-equations [Lagrangian]
;;   (fn [q]
;;         (-
;;             (D (compose ((partial 2) Lagrangian) (Gamma q)))
;;             (compose ((partial 1) Lagrangian) (Gamma q)))))

(defn test-path [t]
  (up (+ (* 'a t) 'a0) (+ (* 'b t) 'b0) (+ (* 'c t) 'c0)))


(((Lagrange-equations (L-free-particle 'm)) test-path) 't)

(simplify
  (((Lagrange-equations (L-free-particle 'm))
             (literal-function
                                                        'x))
            't))

(defn proposed-solution [t] (* 'A (cos (+ (* 'omega t) 'phi))))


(simplify
  (((Lagrange-equations (L-harmonic 'm 'k)) proposed-solution)
            't))

(defn L-central-polar [m V]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (let [r (ref q 0)
                     phi (ref q 1)
                     rdot (ref qdot 0)
                     phidot (ref qdot 1)]
                 (-
                      (* 1/2 m (+ (square rdot) (square (* r phidot))))
                      (V r))))))

(defn gravitational-energy [G m1 m2] (fn [r] (- (/ (* G m1 m2) r))))

(defn L-uniform-acceleration [m g]
  (fn [local]
        (let [q (coordinate local) v (velocity local)]
            (let [y (ref q 1)] (- (* 1/2 m (square v)) (* m g y))))))


(simplify
  (((Lagrange-equations (L-uniform-acceleration 'm 'g))
             (up
                                                                  (literal-function
                                                                      'x)
                                                                  (literal-function
                                                                      'y)))
            't))

(defn L-central-rectangular [m U]
  (fn [local]
        (let [q (coordinate local) v (velocity local)]
            (- (* 1/2 m (square v)) (U (sqrt (square q)))))))

(simplify
  (((Lagrange-equations
              (L-central-rectangular
                                  'm
                                  (literal-function
                                                         'U)))
             (up
                                                                                                                                                                                                                               (literal-function
                                                                                                                                                                                                                                   'x)
                                                                                                                                                                                                                               (literal-function
                                                                                                                                                                                                                                   'y)))
            't))

(defn L-central-polar [m U]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (let [r (ref q 0)
                     phi (ref q 1)
                     rdot (ref qdot 0)
                     phidot (ref qdot 1)]
                 (-
                      (* 1/2 m (+ (square rdot) (square (* r phidot))))
                      (U r))))))

(simplify
  (((Lagrange-equations
              (L-central-polar
                                  'm (literal-function 'U)))
             (up
                                                                                                                              (literal-function
                                                                                                                                  'r)
                                                                                                                              (literal-function
                                                                                                                                  'phi)))
            't))

;; (Pedagogical redef of `F->C` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn F->C [F]
;;   (fn [local]
;;         (up
;;             (state->t local) (F local)
;;             (+
;;                 (((partial 0) F) local)
;;                 (* (((partial 1) F) local) (velocity local))))))

;; (Pedagogical redef of `p->r` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn p->r [local]
;;   (let [polar-tuple (coordinate local)]
;;         (let [r (ref polar-tuple 0) phi (ref polar-tuple 1)]
;;              (let [x (* r (cos phi)) y (* r (sin phi))] (up x y)))))

(simplify
  (velocity
            ((F->C p->r)
                      (up 't (up 'r 'phi) (up 'rdot 'phidot)))))

(defn L-central-polar [m U]
  (compose (L-central-rectangular m U) (F->C p->r)))


(simplify
  ((L-central-polar 'm (literal-function 'U))
            (up
                                                        't (up 'r 'phi)
                                                        (up
                                                            'rdot
                                                            'phidot))))

(defn L-free-rectangular [m]
  (fn [local]
        (let [vx (ref (velocities local) 0)
                vy (ref (velocities local) 1)]
            (* 1/2 m (+ (square vx) (square vy))))))

(defn L-free-polar [m] (compose (L-free-rectangular m) (F->C p->r)))

(defn F [Omega]
  (fn [local]
        (let [t (state->t local)
                r (ref (coordinate local) 0)
                theta (ref (coordinate local) 1)]
            (up r (+ theta (* Omega t))))))


(defn L-rotating-polar [m Omega]
  (compose (L-free-polar m) (F->C (F Omega))))

(defn L-rotating-rectangular [m Omega]
  (compose (L-rotating-polar m Omega) (F->C r->p)))

((L-rotating-rectangular 'm 'Omega)
  (up
                                      't (up 'x_r 'y_r)
                                      (up 'xdot_r 'ydot_r)))

(((Lagrange-equations (L-rotating-rectangular 'm 'Omega))
   (up
                                                            (literal-function
                                                                'x r)
                                                            (literal-function
                                                                'y r)))
  't)

(defn T-pend [m l g ys]
  (fn [local]
        (let [t (state->t local)
                theta (coordinate local)
                thetadot (velocity local)]
            (let [vys (D ys)]
                 (*
                      1/2 m
                      (+
                         (square (* l thetadot)) (square (vys t))
                         (* 2 l (vys t) thetadot (sin theta))))))))


(defn V-pend [m l g ys]
  (fn [local]
        (let [t (state->t local) theta (coordinate local)]
            (* m g (- (ys t) (* l (cos theta)))))))


(def L-pend (- T-pend V-pend))

(simplify
  (((Lagrange-equations
              (L-pend
                                  'm 'l 'g (literal-function 'y_s)))
             (literal-function
                                                                                                                             'theta))
            't))

(defn L-uniform-acceleration [m g]
  (fn [local]
        (let [q (coordinate local) v (velocity local)]
            (let [y (ref q 1)] (- (* 1/2 m (square v)) (* m g y))))))

(defn dp-coordinates [l y_s]
  (fn [local]
        (let [t (state->t local) theta (coordinate local)]
            (let [x (* l (sin theta))
                     y (- (y_s t) (* l (cos theta)))]
                 (up x y)))))

(defn L-pend [m l g y_s]
  (compose
        (L-uniform-acceleration m g) (F->C (dp-coordinates l y_s))))

(simplify
  ((L-pend 'm 'l 'g (literal-function 'y_s))
            (up
                                                       't 'theta
                                                       'thetadot)))

((compose (Rz (* 'Omega 't)) (Ry 'phi)) (up 'x_0 'y_0 'z_0))

(defn Lagrangian->acceleration [L]
  (let [P ((partial 2) L) F ((partial 1) L)]
        (solve-linear-left
             ((partial 2) P)
             (-
                                F
                                (+
                                   ((partial 0) P)
                                   (* ((partial 1) P) velocity))))))

;; (Pedagogical redef of `Lagrangian->state-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->state-derivative [L]
;;   (let [acceleration (Lagrangian->acceleration L)]
;;         (fn [state] (up 1 (velocity state) (acceleration state)))))

(defn harmonic-state-derivative [m k]
  (Lagrangian->state-derivative (L-harmonic m k)))


((harmonic-state-derivative 'm 'k) (up 't (up 'x 'y) (up 'v_x 'v_y)))

;; (Pedagogical redef of `Lagrange-equations-first-order` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrange-equations-first-order [L]
;;   (fn [q v]
;;         (let [state-path (qv->state-path q v)]
;;             (-
;;                  (D state-path)
;;                  (compose (Lagrangian->state-derivative L) state-path)))))


(defn qv->state-path [q v] (fn [t] (up t (q t) (v t))))

(simplify
  (((Lagrange-equations-first-order (L-harmonic 'm 'k))
             (up
                                                                  (literal-function
                                                                      'x)
                                                                  (literal-function
                                                                      'y))
             (up
                                                                  (literal-function
                                                                      'v_x)
                                                                  (literal-function
                                                                      'v_y)))
            't))

((state-advancer harmonic-state-derivative 2.0 1.0)
  (up
                                                      1.0 (up 1.0 2.0)
                                                      (up 3.0 4.0))
  10.0 1.0e-12)

(defn periodic-drive [amplitude frequency phase]
  (fn [t] (* amplitude (cos (+ (* frequency t) phase)))))


(defn L-periodically-driven-pendulum [m l g A omega]
  (let [ys (periodic-drive A omega 0)] (L-pend m l g ys)))

(simplify
  (((Lagrange-equations
              (L-periodically-driven-pendulum
                                  'm 'l 'g 'A 'omega))
             (literal-function
                                                                                                                                       'theta))
            't))

(defn pend-state-derivative [m l g A omega]
  (Lagrangian->state-derivative
        (L-periodically-driven-pendulum
                                      m l g A omega)))


(simplify
  ((pend-state-derivative 'm 'l 'g 'A 'omega)
            (up
                                                        't 'theta
                                                        'thetadot)))

(defn monitor-theta [win]
  (fn [state]
        (let [theta ((principal-value Math/PI) (coordinate state))]
            (plot-point win (state->t state) theta))))


(def plot-win (frame 0.0 100.0 (- Math/PI) Math/PI))


((evolve pend-state-derivative 1.0 1.0 9.8 0.1 (* 2.0 (sqrt 9.8)))
  (up
                                                                     0.0
                                                                     1.0
                                                                     0.0)
  (monitor-theta
                                                                     plot-win)
  0.01 100.0 1.0e-13)
;local error tolerance

;; (Pedagogical redef of `Lagrangian->energy` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->energy [L]
;;   (let [P ((partial 2) L)] (- (* P velocity) L)))

(defn T3-spherical [m]
  (fn [state]
        (let [q (coordinate state) qdot (velocity state)]
            (let [r (ref q 0)
                     theta (ref q 1)
                     rdot (ref qdot 0)
                     thetadot (ref qdot 1)
                     phidot (ref qdot 2)]
                 (*
                      1/2 m
                      (+
                         (square rdot) (square (* r thetadot))
                         (square (* r (sin theta) phidot))))))))

(defn L3-central [m Vr]
  (letfn [(Vs [state] (let [r (ref (coordinate state) 0)] (Vr r)))]
        (- (T3-spherical m) Vs)))

(simplify
  (((partial 1) (L3-central 'm (literal-function 'V)))
            (up
                                                                 't
                                                                 (up
                                                                     'r
                                                                     'theta
                                                                     'phi)
                                                                 (up
                                                                     'rdot
                                                                     'thetadot
                                                                     'phidot))))

(simplify
  (((partial 2) (L3-central 'm (literal-function 'V)))
            (up
                                                                 't
                                                                 (up
                                                                     'r
                                                                     'theta
                                                                     'phi)
                                                                 (up
                                                                     'rdot
                                                                     'thetadot
                                                                     'phidot))))

(defn ang-mom-z [m]
  (fn [rectangular-state]
        (let [xyz (coordinate rectangular-state)
                v (velocity rectangular-state)]
            (ref (cross-product xyz (* m v)) 2))))

;; (Pedagogical redef of `s->r` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn s->r [spherical-state]
;;   (let [q (coordinate spherical-state)]
;;         (let [r (ref q 0) theta (ref q 1) phi (ref q 2)]
;;              (let [x (* r (sin theta) (cos phi))
;;                       y (* r (sin theta) (sin phi))
;;                       z (* r (cos theta))]
;;                   (up x y z)))))

(simplify
  ((compose (ang-mom-z 'm) (F->C s->r))
            (up
                                                  't (up 'r 'theta 'phi)
                                                  (up
                                                      'rdot 'thetadot
                                                      'phidot))))

(simplify
  ((Lagrangian->energy (L3-central 'm (literal-function 'V)))
            (up
                                                                        't
                                                                        (up
                                                                            'r
                                                                            'theta
                                                                            'phi)
                                                                        (up
                                                                            'rdot
                                                                            'thetadot
                                                                            'phidot))))

(defn L0 [m V]
  (fn [local]
        (let [t (state->t local)
                q (coordinate local)
                v (velocities local)]
            (- (* 1/2 m (square v)) (V t q)))))

(defn V [a GM0 GM1 m]
  (fn [t xy]
        (let [Omega (sqrt (/ (+ GM0 GM1) (expt a 3)))
                a0 (* (/ GM1 (+ GM0 GM1)) a)
                a1 (* (/ GM0 (+ GM0 GM1)) a)]
            (let [x (ref xy 0)
                     y (ref xy 1)
                     x0 (* -1 a0 (cos (* Omega t)))
                     y0 (* -1 a0 (sin (* Omega t)))
                     x1 (* +1 a1 (cos (* Omega t)))
                     y1 (* +1 a1 (sin (* Omega t)))]
                 (let [r0 (sqrt
                               (+
                                     (square (- x x0)) (square (- y y0))))
                          r1 (sqrt
                               (+
                                     (square (- x x1)) (square (- y y1))))]
                      (- (+ (/ (* GM0 m) r0) (/ (* GM1 m) r1))))))))

(defn LR3B [m a GM0 GM1]
  (fn [local]
        (let [q (coordinate local)
                qdot (velocities local)
                Omega (sqrt (/ (+ GM0 GM1) (expt a 3)))
                a0 (* (/ GM1 (+ GM0 GM1)) a)
                a1 (* (/ GM0 (+ GM0 GM1)) a)]
            (let [x (ref q 0)
                     y (ref q 1)
                     xdot (ref qdot 0)
                     ydot (ref qdot 1)]
                 (let [r0 (sqrt (+ (square (+ x a0)) (square y)))
                          r1 (sqrt (+ (square (- x a1)) (square y)))]
                      (+
                           (* 1/2 m (square qdot))
                           (* 1/2 m (square Omega) (square q))
                           (* m Omega (- (* x ydot) (* xdot y)))
                           (/ (* GM0 m) r0) (/ (* GM1 m) r1)))))))

(defn LR3B1 [m a0 a1 Omega GM0 GM1]
  (fn [local]
        (let [q (coordinate local) qdot (velocities local)]
            (let [x (ref q 0)
                     y (ref q 1)
                     xdot (ref qdot 0)
                     ydot (ref qdot 1)]
                 (let [r0 (sqrt (+ (square (+ x a0)) (square y)))
                          r1 (sqrt (+ (square (- x a1)) (square y)))]
                      (+
                           (* 1/2 m (square qdot))
                           (* 1/2 m (square Omega) (square q))
                           (* m Omega (- (* x ydot) (* xdot y)))
                           (/ (* GM0 m) r0) (/ (* GM1 m) r1)))))))

((Lagrangian->energy (LR3B1 'm 'a_0 'a_1 'Omega 'GM_0 'GM_1))
  (up
                                                                't
                                                                (up
                                                                    'x_r
                                                                    'y_r)
                                                                (up
                                                                    'v_r↑x
                                                                    'v_r↑y)))

(defn F-tilde [angle-x angle-y angle-z]
  (compose (Rx angle-x) (Ry angle-y) (Rz angle-z) coordinate))

(defn L-central-rectangular [m U]
  (fn [state]
        (let [q (coordinate state) v (velocity state)]
            (- (* 1/2 m (square v)) (U (sqrt (square q)))))))

(def the-Noether-integral
  (let [L (L-central-rectangular 'm (literal-function 'U))]
       (* ((partial 2) L) ((D F-tilde) 0 0 0))))


(the-Noether-integral (up 't (up 'x 'y 'z) (up 'vx 'vy 'vz)))

;; (Pedagogical redef of `Gamma-bar` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Gamma-bar [f-bar]
;;   (fn [local] ((f-bar (osculating-path local)) (state->t local))))

;; (Pedagogical redef of `F->C` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn F->C [F]
;;   (letfn [(C
;;                 [local]
;;                 (let [n (vector-length local)]
;;                    (letfn [(f-bar
;;                                 [q-prime]
;;                                 (let [q (compose F (Gamma q-prime))]
;;                                        (Gamma q n)))]
;;                         ((Gamma-bar f-bar) local))))]
;;         C))


(simplify ((F->C p->r) (up 't (up 'r 'theta) (up 'rdot 'thetadot))))

;; (Pedagogical redef of `Dt` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Dt [F]
;;   (letfn [(DtF
;;                 [state]
;;                 (let [n (vector-length state)]
;;                      (letfn [(DF-on-path
;;                                   [q]
;;                                   (D
;;                                               (compose
;;                                                  F
;;                                                  (Gamma
;;                                                           q (- n 1)))))]
;;                           ((Gamma-bar DF-on-path) state))))]
;;         DtF))

;; (Pedagogical redef of `Euler-Lagrange-operator` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Euler-Lagrange-operator [L]
;;   (- (Dt ((partial 2) L)) ((partial 1) L)))
;; .

((Euler-Lagrange-operator (L-harmonic 'm 'k)) (up 't 'x 'v 'a))

((compose
   (Euler-Lagrange-operator (L-harmonic 'm 'k))
   (Gamma (literal-function 'x) 4))
  't)

;; --- §1.12 — Projects ---

;; (book p. 118)
(defn make-path [t0 q0 t1 q1 qs]
  (let [n (count qs)]
        (let [ts (linear-interpolants t0 t1 n)]
             (Lagrange-interpolation-function
                  (concat
                                                   (list q0) qs
                                                   (list q1))
                  (concat
                                                   (list t0) ts
                                                   (list t1))))))

;; (book p. 118)
;; (Pedagogical redef of `Rx` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Rx [angle]
;;   (fn [q]
;;         (let [ca (cos angle) sa (sin angle)]
;;             (let [x (ref q 0) y (ref q 1) z (ref q 2)]
;;                  (up x (- (* ca y) (* sa z)) (+ (* sa y) (* ca z)))))))"
    "SICM 2.2 Kinematics of Rotation"
    ";; ===========================================
;; SICM §2.2 — Kinematics of Rotation
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))


;; --- Implementation of angular velocity functions ---

;; (book p. 126)
(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

;; (book p. 126)
(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

;; (book p. 126)
(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))"
    "SICM 2.5 Principal Moments of Inertia"
    ";; ===========================================
;; SICM §2.5 — Principal Moments of Inertia
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t T-body]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

;; --- §2.5 — Principal Moments of Inertia ---

;; (book p. 134)
(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))"
    "SICM 2.6 Vector Angular Momentum"
    ";; ===========================================
;; SICM §2.6 — Vector Angular Momentum
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[L-body L-space M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t T-body]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))

;; --- §2.6 — Vector Angular Momentum ---

;; (book p. 137)
(defn L-body [A B C]
  (fn [omega-body]
        (down
            (* A (ref omega-body 0)) (* B (ref omega-body 1))
            (* C (ref omega-body 2)))))

;; (book p. 137)
(defn L-space [M]
  (fn [A B C]
        (fn [omega-body]
            (* ((L-body A B C) omega-body) (transpose M)))))"
    "SICM 2.8 Motion of a Free Rigid Body"
    ";; ===========================================
;; SICM §2.8 — Motion of a Free Rigid Body
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[Euler->M Euler-state Euler-state->omega-body L-body L-body-Euler L-space L-space-Euler M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t Rx-matrix Rz-matrix T-body T-body-Euler]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))

(defn L-body [A B C]
  (fn [omega-body]
        (down
            (* A (ref omega-body 0)) (* B (ref omega-body 1))
            (* C (ref omega-body 2)))))

(defn L-space [M]
  (fn [A B C]
        (fn [omega-body]
            (* ((L-body A B C) omega-body) (transpose M)))))

(defn Rz-matrix [angle]
  (matrix-by-rows
        (list (cos angle) (- (sin angle)) 0)
        (list (sin angle) (cos angle) 0) (list 0 0 1)))


(defn Rx-matrix [angle]
  (matrix-by-rows
        (list 1 0 0) (list 0 (cos angle) (- (sin angle)))
        (list 0 (sin angle) (cos angle))))

;; (Pedagogical redef of `Euler->M` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Euler->M [angles]
;;   (let [theta (ref angles 0) phi (ref angles 1) psi (ref angles 2)]
;;         (* (Rz-matrix phi) (Rx-matrix theta) (Rz-matrix psi))))

(simplify
  (((M-of-q->omega-body-of-t Euler->M)
             (up
                                                 (literal-function
                                                     'theta)
                                                 (literal-function
                                                     'phi)
                                                 (literal-function
                                                     'psi)))
            't))

(simplify
  ((M->omega-body Euler->M)
            (up
                                      't (up 'theta 'phi 'psi)
                                      (up 'thetadot 'phidot 'psidot))))

(defn Euler-state->omega-body [local]
  (let [q (coordinate local) qdot (velocity local)]
        (let [theta (ref q 0)
                 psi (ref q 2)
                 thetadot (ref qdot 0)
                 phidot (ref qdot 1)
                 psidot (ref qdot 2)]
             (let [omega-a (+
                                (* thetadot (cos psi))
                                (* phidot (sin theta) (sin psi)))
                      omega-b (+
                                (* -1 thetadot (sin psi))
                                (* phidot (sin theta) (cos psi)))
                      omega-c (+ (* phidot (cos theta)) psidot)]
                  (up omega-a omega-b omega-c)))))

(defn T-body-Euler [A B C]
  (fn [local] ((T-body A B C) (Euler-state->omega-body local))))

(defn L-body-Euler [A B C]
  (fn [local] ((L-body A B C) (Euler-state->omega-body local))))

(defn L-space-Euler [A B C]
  (fn [local]
        (let [angles (coordinate local)]
            (*
                 ((L-body-Euler A B C) local)
                 (transpose (Euler->M angles))))))

;; --- §2.8 — Motion of a Free Rigid Body ---


;; --- Conserved quantities ---

;; (book p. 142)
(def Euler-state
  (up 't (up 'theta 'phi 'psi) (up 'thetadot 'phidot 'psidot)))


(simplify (ref (((partial 2) (T-body-Euler 'A 'B 'C)) Euler-state) 1))"
    "SICM 2.8.1 Computing the Motion of Free Rigid Bodies"
    ";; ===========================================
;; SICM §2.8.1 — Computing the Motion of Free Rigid Bodies
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[Euler->M Euler-state Euler-state->omega-body L-body L-body-Euler L-space L-space-Euler M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t Rx-matrix Rz-matrix T-body T-body-Euler monitor-errors relative-error rigid-sysder win]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))

(defn L-body [A B C]
  (fn [omega-body]
        (down
            (* A (ref omega-body 0)) (* B (ref omega-body 1))
            (* C (ref omega-body 2)))))

(defn L-space [M]
  (fn [A B C]
        (fn [omega-body]
            (* ((L-body A B C) omega-body) (transpose M)))))

(defn Rz-matrix [angle]
  (matrix-by-rows
        (list (cos angle) (- (sin angle)) 0)
        (list (sin angle) (cos angle) 0) (list 0 0 1)))


(defn Rx-matrix [angle]
  (matrix-by-rows
        (list 1 0 0) (list 0 (cos angle) (- (sin angle)))
        (list 0 (sin angle) (cos angle))))

;; (Pedagogical redef of `Euler->M` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Euler->M [angles]
;;   (let [theta (ref angles 0) phi (ref angles 1) psi (ref angles 2)]
;;         (* (Rz-matrix phi) (Rx-matrix theta) (Rz-matrix psi))))

(simplify
  (((M-of-q->omega-body-of-t Euler->M)
             (up
                                                 (literal-function
                                                     'theta)
                                                 (literal-function
                                                     'phi)
                                                 (literal-function
                                                     'psi)))
            't))

(simplify
  ((M->omega-body Euler->M)
            (up
                                      't (up 'theta 'phi 'psi)
                                      (up 'thetadot 'phidot 'psidot))))

(defn Euler-state->omega-body [local]
  (let [q (coordinate local) qdot (velocity local)]
        (let [theta (ref q 0)
                 psi (ref q 2)
                 thetadot (ref qdot 0)
                 phidot (ref qdot 1)
                 psidot (ref qdot 2)]
             (let [omega-a (+
                                (* thetadot (cos psi))
                                (* phidot (sin theta) (sin psi)))
                      omega-b (+
                                (* -1 thetadot (sin psi))
                                (* phidot (sin theta) (cos psi)))
                      omega-c (+ (* phidot (cos theta)) psidot)]
                  (up omega-a omega-b omega-c)))))

(defn T-body-Euler [A B C]
  (fn [local] ((T-body A B C) (Euler-state->omega-body local))))

(defn L-body-Euler [A B C]
  (fn [local] ((L-body A B C) (Euler-state->omega-body local))))

(defn L-space-Euler [A B C]
  (fn [local]
        (let [angles (coordinate local)]
            (*
                 ((L-body-Euler A B C) local)
                 (transpose (Euler->M angles))))))

(def Euler-state
  (up 't (up 'theta 'phi 'psi) (up 'thetadot 'phidot 'psidot)))


(simplify (ref (((partial 2) (T-body-Euler 'A 'B 'C)) Euler-state) 1))

;; --- §2.8.1 — Computing the Motion of Free Rigid Bodies ---

;; (book p. 143)
(simplify
  (determinant
            (((square (partial 2)) (T-body-Euler 'A 'B 'C))
                         Euler-state)))

;; (book p. 143)
(defn rigid-sysder [A B C]
  (Lagrangian->state-derivative (T-body-Euler A B C)))

;; (book p. 143)
(defn monitor-errors [win A B C L0 E0]
  (fn [state]
        (let [t (state->t state)
                L ((L-space-Euler A B C) state)
                E ((T-body-Euler A B C) state)]
            (plot-point win t (relative-error (ref L 0) (ref L0 0)))
            (plot-point win t (relative-error (ref L 1) (ref L0 1)))
            (plot-point win t (relative-error (ref L 2) (ref L0 2)))
            (plot-point win t (relative-error E E0)))))


(defn relative-error [value reference-value]
  (if (zero? reference-value)
        (throw (ex-info \"Zero reference value -- RELATIVE-ERROR\" {}))
        (/ (- value reference-value) reference-value)))

;; (book p. 143)
(def win (frame 0.0 100.0 -1.0e-12 1.0e-12))

;; (book p. 143)
(set-ode-integration-method! 'qcrk4)"
    "SICM 2.12 Nonsingular Coordinates and Quaternions"
    ";; ===========================================
;; SICM §2.12 — Nonsingular Coordinates and Quaternions
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[Euler->M Euler-state Euler-state->omega-body L-body L-body-Euler L-space L-space-Euler M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t Rx-matrix Rz-matrix T-body T-body-Euler angle-axis->rotation-matrix monitor-errors quaternion->RM quaternion->angle-axis quaternion->rotation-matrix quaternion-state->omega-body relative-error rigid-sysder win]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))

(defn L-body [A B C]
  (fn [omega-body]
        (down
            (* A (ref omega-body 0)) (* B (ref omega-body 1))
            (* C (ref omega-body 2)))))

(defn L-space [M]
  (fn [A B C]
        (fn [omega-body]
            (* ((L-body A B C) omega-body) (transpose M)))))

(defn Rz-matrix [angle]
  (matrix-by-rows
        (list (cos angle) (- (sin angle)) 0)
        (list (sin angle) (cos angle) 0) (list 0 0 1)))


(defn Rx-matrix [angle]
  (matrix-by-rows
        (list 1 0 0) (list 0 (cos angle) (- (sin angle)))
        (list 0 (sin angle) (cos angle))))

;; (Pedagogical redef of `Euler->M` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Euler->M [angles]
;;   (let [theta (ref angles 0) phi (ref angles 1) psi (ref angles 2)]
;;         (* (Rz-matrix phi) (Rx-matrix theta) (Rz-matrix psi))))

(simplify
  (((M-of-q->omega-body-of-t Euler->M)
             (up
                                                 (literal-function
                                                     'theta)
                                                 (literal-function
                                                     'phi)
                                                 (literal-function
                                                     'psi)))
            't))

(simplify
  ((M->omega-body Euler->M)
            (up
                                      't (up 'theta 'phi 'psi)
                                      (up 'thetadot 'phidot 'psidot))))

(defn Euler-state->omega-body [local]
  (let [q (coordinate local) qdot (velocity local)]
        (let [theta (ref q 0)
                 psi (ref q 2)
                 thetadot (ref qdot 0)
                 phidot (ref qdot 1)
                 psidot (ref qdot 2)]
             (let [omega-a (+
                                (* thetadot (cos psi))
                                (* phidot (sin theta) (sin psi)))
                      omega-b (+
                                (* -1 thetadot (sin psi))
                                (* phidot (sin theta) (cos psi)))
                      omega-c (+ (* phidot (cos theta)) psidot)]
                  (up omega-a omega-b omega-c)))))

(defn T-body-Euler [A B C]
  (fn [local] ((T-body A B C) (Euler-state->omega-body local))))

(defn L-body-Euler [A B C]
  (fn [local] ((L-body A B C) (Euler-state->omega-body local))))

(defn L-space-Euler [A B C]
  (fn [local]
        (let [angles (coordinate local)]
            (*
                 ((L-body-Euler A B C) local)
                 (transpose (Euler->M angles))))))

(def Euler-state
  (up 't (up 'theta 'phi 'psi) (up 'thetadot 'phidot 'psidot)))


(simplify (ref (((partial 2) (T-body-Euler 'A 'B 'C)) Euler-state) 1))

(simplify
  (determinant
            (((square (partial 2)) (T-body-Euler 'A 'B 'C))
                         Euler-state)))

(defn rigid-sysder [A B C]
  (Lagrangian->state-derivative (T-body-Euler A B C)))

(defn monitor-errors [win A B C L0 E0]
  (fn [state]
        (let [t (state->t state)
                L ((L-space-Euler A B C) state)
                E ((T-body-Euler A B C) state)]
            (plot-point win t (relative-error (ref L 0) (ref L0 0)))
            (plot-point win t (relative-error (ref L 1) (ref L0 1)))
            (plot-point win t (relative-error (ref L 2) (ref L0 2)))
            (plot-point win t (relative-error E E0)))))


(defn relative-error [value reference-value]
  (if (zero? reference-value)
        (throw (ex-info \"Zero reference value -- RELATIVE-ERROR\" {}))
        (/ (- value reference-value) reference-value)))

(def win (frame 0.0 100.0 -1.0e-12 1.0e-12))

(set-ode-integration-method! 'qcrk4)

(simplify
  ((T-body-Euler 'A 'A 'C)
            (up
                                     't (up 'theta 'phi 'psi)
                                     (up 'thetadot 'phidot 'psidot))))

;; --- §2.12 — Nonsingular Coordinates and Quaternions ---

;; (book p. 184)
;; (Pedagogical redef of `angle-axis->rotation-matrix` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn angle-axis->rotation-matrix [theta n]
;;   (let [nx (ref n 0) ny (ref n 1) nz (ref n 2)]
;;         (let [colatitude (acos nz) longitude (atan ny nx)]
;;              (*
;;                   (Rz-matrix longitude) (Ry-matrix colatitude)
;;                   (Rz-matrix theta) (transpose (Ry-matrix colatitude))
;;                   (transpose (Rz-matrix longitude))))))

;; (book p. 184)
(defn quaternion->angle-axis [q]
  (let [v (quaternion->3vector q)
            theta (*
                    2
                    (atan (euclidean-norm v) (quaternion->real-part q)))
            axis (/ v (euclidean-norm v))]
        (list theta axis)))

;; (book p. 184)
(defn quaternion->RM [q]
  (let [aa (quaternion->angle-axis q)]
        (let [theta (ref aa 0) n (ref aa 1)]
             (angle-axis->rotation-matrix theta n))))

;; (book p. 184)
(simplify
  (let [v (up 'q_0 'q_1 'q_2 'q_3)]
            (let [m↑2 (dot-product v v)]
                 (* m↑2 (quaternion->RM (make-quaternion v))))))

;; (book p. 185)
(defn quaternion->rotation-matrix [q]
  (let [q0 (quaternion-ref q 0)
            q1 (quaternion-ref q 1)
            q2 (quaternion-ref q 2)
            q3 (quaternion-ref q 3)]
        (let [m↑2 (+ (expt q0 2) (expt q1 2) (expt q2 2) (expt q3 2))]
             (/
                  (matrix-by-rows
                     (list
                                     (-
                                           (+ (expt q0 2) (expt q1 2))
                                           (+ (expt q2 2) (expt q3 2)))
                                     (* 2 (- (* q1 q2) (* q0 q3)))
                                     (* 2 (+ (* q1 q3) (* q0 q2))))
                     (list
                                     (* 2 (+ (* q1 q2) (* q0 q3)))
                                     (-
                                           (+ (expt q0 2) (expt q2 2))
                                           (+ (expt q1 2) (expt q3 2)))
                                     (* 2 (- (* q2 q3) (* q0 q1))))
                     (list
                                     (* 2 (- (* q1 q3) (* q0 q2)))
                                     (* 2 (+ (* q2 q3) (* q0 q1)))
                                     (-
                                           (+ (expt q0 2) (expt q3 2))
                                           (+ (expt q1 2) (expt q2 2)))))
                  m↑2))))

;; (book p. 185)
(simplify
  ((M->omega-body
             (compose
                            quaternion->rotation-matrix make-quaternion))
            (up
                                                                                                                            't
                                                                                                                            (up
                                                                                                                                'q_0
                                                                                                                                'q_1
                                                                                                                                'q_2
                                                                                                                                'q_3)
                                                                                                                            (up
                                                                                                                                'qdot_0
                                                                                                                                'qdot_1
                                                                                                                                'qdot_2
                                                                                                                                'qdot_3))))

;; (book p. 186)
(defn quaternion-state->omega-body [s]
  (let [q (coordinate s) qdot (velocities s)]
        (let [m↑2 (dot-product q q)]
             (let [omega↑a (/ (* 2 (dot-product q (* q:i qdot))) m↑2)
                      omega↑b (/ (* 2 (dot-product q (* q:j qdot))) m↑2)
                      omega↑c (/ (* 2 (dot-product q (* q:k qdot))) m↑2)]
                  (up omega↑a omega↑b omega↑c)))))


;; --- Composition of rotations ---

;; (book p. 187)
(let [q (quaternion 'q_0 'q_1 'q_2 'q_3)
      p (quaternion 'p_0 'p_1 'p_2 'p_3)]
  (let [Mq (quaternion->rotation-matrix q)
           Mp (quaternion->rotation-matrix p)]
       (rotation-matrix->quaternion (* Mq Mp))))"
    "SICM 2.12.1 Motion in Terms of Quaternions"
    ";; ===========================================
;; SICM §2.12.1 — Motion in Terms of Quaternions
;; Chapter 2 — Rigid Bodies
;; https://tgvaughan.github.io/sicm/chapter002.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[Euler->M Euler-state Euler-state->omega-body L-body L-body-Euler L-space L-space-Euler M->omega M->omega-body M-of-q->omega-body-of-t M-of-q->omega-of-t Rx-matrix Rz-matrix T-body T-body-Euler angle-axis->rotation-matrix monitor-errors quaternion->RM quaternion->angle-axis quaternion->rotation-matrix quaternion-state->omega-body qw-state->L-space qw-sysder relative-error rigid-sysder win]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 2 ---

(defn M-of-q->omega-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (let [M-on-path (compose M-of-q q)]
                (letfn [(omega-cross
                             [t]
                             (*
                                          ((D M-on-path) t)
                                          (transpose (M-on-path t))))]
                     (antisymmetric->column-matrix (omega-cross t)))))))

(defn M-of-q->omega-body-of-t [M-of-q]
  (fn [q]
        (fn [t]
            (*
                (transpose (M-of-q (q t)))
                (((M-of-q->omega-of-t M-of-q) q) t)))))

(defn M->omega [M-of-q] (Gamma-bar (M-of-q->omega-of-t M-of-q)))


(defn M->omega-body [M-of-q]
  (Gamma-bar (M-of-q->omega-body-of-t M-of-q)))

(defn T-body [A B C]
  (fn [omega-body]
        (*
            1/2
            (+
               (* A (square (ref omega-body 0)))
               (* B (square (ref omega-body 1)))
               (* C (square (ref omega-body 2)))))))

(defn L-body [A B C]
  (fn [omega-body]
        (down
            (* A (ref omega-body 0)) (* B (ref omega-body 1))
            (* C (ref omega-body 2)))))

(defn L-space [M]
  (fn [A B C]
        (fn [omega-body]
            (* ((L-body A B C) omega-body) (transpose M)))))

(defn Rz-matrix [angle]
  (matrix-by-rows
        (list (cos angle) (- (sin angle)) 0)
        (list (sin angle) (cos angle) 0) (list 0 0 1)))


(defn Rx-matrix [angle]
  (matrix-by-rows
        (list 1 0 0) (list 0 (cos angle) (- (sin angle)))
        (list 0 (sin angle) (cos angle))))

;; (Pedagogical redef of `Euler->M` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Euler->M [angles]
;;   (let [theta (ref angles 0) phi (ref angles 1) psi (ref angles 2)]
;;         (* (Rz-matrix phi) (Rx-matrix theta) (Rz-matrix psi))))

(simplify
  (((M-of-q->omega-body-of-t Euler->M)
             (up
                                                 (literal-function
                                                     'theta)
                                                 (literal-function
                                                     'phi)
                                                 (literal-function
                                                     'psi)))
            't))

(simplify
  ((M->omega-body Euler->M)
            (up
                                      't (up 'theta 'phi 'psi)
                                      (up 'thetadot 'phidot 'psidot))))

(defn Euler-state->omega-body [local]
  (let [q (coordinate local) qdot (velocity local)]
        (let [theta (ref q 0)
                 psi (ref q 2)
                 thetadot (ref qdot 0)
                 phidot (ref qdot 1)
                 psidot (ref qdot 2)]
             (let [omega-a (+
                                (* thetadot (cos psi))
                                (* phidot (sin theta) (sin psi)))
                      omega-b (+
                                (* -1 thetadot (sin psi))
                                (* phidot (sin theta) (cos psi)))
                      omega-c (+ (* phidot (cos theta)) psidot)]
                  (up omega-a omega-b omega-c)))))

(defn T-body-Euler [A B C]
  (fn [local] ((T-body A B C) (Euler-state->omega-body local))))

(defn L-body-Euler [A B C]
  (fn [local] ((L-body A B C) (Euler-state->omega-body local))))

(defn L-space-Euler [A B C]
  (fn [local]
        (let [angles (coordinate local)]
            (*
                 ((L-body-Euler A B C) local)
                 (transpose (Euler->M angles))))))

(def Euler-state
  (up 't (up 'theta 'phi 'psi) (up 'thetadot 'phidot 'psidot)))


(simplify (ref (((partial 2) (T-body-Euler 'A 'B 'C)) Euler-state) 1))

(simplify
  (determinant
            (((square (partial 2)) (T-body-Euler 'A 'B 'C))
                         Euler-state)))

(defn rigid-sysder [A B C]
  (Lagrangian->state-derivative (T-body-Euler A B C)))

(defn monitor-errors [win A B C L0 E0]
  (fn [state]
        (let [t (state->t state)
                L ((L-space-Euler A B C) state)
                E ((T-body-Euler A B C) state)]
            (plot-point win t (relative-error (ref L 0) (ref L0 0)))
            (plot-point win t (relative-error (ref L 1) (ref L0 1)))
            (plot-point win t (relative-error (ref L 2) (ref L0 2)))
            (plot-point win t (relative-error E E0)))))


(defn relative-error [value reference-value]
  (if (zero? reference-value)
        (throw (ex-info \"Zero reference value -- RELATIVE-ERROR\" {}))
        (/ (- value reference-value) reference-value)))

(def win (frame 0.0 100.0 -1.0e-12 1.0e-12))

(set-ode-integration-method! 'qcrk4)

(simplify
  ((T-body-Euler 'A 'A 'C)
            (up
                                     't (up 'theta 'phi 'psi)
                                     (up 'thetadot 'phidot 'psidot))))

;; (Pedagogical redef of `angle-axis->rotation-matrix` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn angle-axis->rotation-matrix [theta n]
;;   (let [nx (ref n 0) ny (ref n 1) nz (ref n 2)]
;;         (let [colatitude (acos nz) longitude (atan ny nx)]
;;              (*
;;                   (Rz-matrix longitude) (Ry-matrix colatitude)
;;                   (Rz-matrix theta) (transpose (Ry-matrix colatitude))
;;                   (transpose (Rz-matrix longitude))))))

(defn quaternion->angle-axis [q]
  (let [v (quaternion->3vector q)
            theta (*
                    2
                    (atan (euclidean-norm v) (quaternion->real-part q)))
            axis (/ v (euclidean-norm v))]
        (list theta axis)))

(defn quaternion->RM [q]
  (let [aa (quaternion->angle-axis q)]
        (let [theta (ref aa 0) n (ref aa 1)]
             (angle-axis->rotation-matrix theta n))))

(simplify
  (let [v (up 'q_0 'q_1 'q_2 'q_3)]
            (let [m↑2 (dot-product v v)]
                 (* m↑2 (quaternion->RM (make-quaternion v))))))

(defn quaternion->rotation-matrix [q]
  (let [q0 (quaternion-ref q 0)
            q1 (quaternion-ref q 1)
            q2 (quaternion-ref q 2)
            q3 (quaternion-ref q 3)]
        (let [m↑2 (+ (expt q0 2) (expt q1 2) (expt q2 2) (expt q3 2))]
             (/
                  (matrix-by-rows
                     (list
                                     (-
                                           (+ (expt q0 2) (expt q1 2))
                                           (+ (expt q2 2) (expt q3 2)))
                                     (* 2 (- (* q1 q2) (* q0 q3)))
                                     (* 2 (+ (* q1 q3) (* q0 q2))))
                     (list
                                     (* 2 (+ (* q1 q2) (* q0 q3)))
                                     (-
                                           (+ (expt q0 2) (expt q2 2))
                                           (+ (expt q1 2) (expt q3 2)))
                                     (* 2 (- (* q2 q3) (* q0 q1))))
                     (list
                                     (* 2 (- (* q1 q3) (* q0 q2)))
                                     (* 2 (+ (* q2 q3) (* q0 q1)))
                                     (-
                                           (+ (expt q0 2) (expt q3 2))
                                           (+ (expt q1 2) (expt q2 2)))))
                  m↑2))))

(simplify
  ((M->omega-body
             (compose
                            quaternion->rotation-matrix make-quaternion))
            (up
                                                                                                                            't
                                                                                                                            (up
                                                                                                                                'q_0
                                                                                                                                'q_1
                                                                                                                                'q_2
                                                                                                                                'q_3)
                                                                                                                            (up
                                                                                                                                'qdot_0
                                                                                                                                'qdot_1
                                                                                                                                'qdot_2
                                                                                                                                'qdot_3))))

(defn quaternion-state->omega-body [s]
  (let [q (coordinate s) qdot (velocities s)]
        (let [m↑2 (dot-product q q)]
             (let [omega↑a (/ (* 2 (dot-product q (* q:i qdot))) m↑2)
                      omega↑b (/ (* 2 (dot-product q (* q:j qdot))) m↑2)
                      omega↑c (/ (* 2 (dot-product q (* q:k qdot))) m↑2)]
                  (up omega↑a omega↑b omega↑c)))))

(let [q (quaternion 'q_0 'q_1 'q_2 'q_3)
      p (quaternion 'p_0 'p_1 'p_2 'p_3)]
  (let [Mq (quaternion->rotation-matrix q)
           Mp (quaternion->rotation-matrix p)]
       (rotation-matrix->quaternion (* Mq Mp))))

;; --- §2.12.1 — Motion in Terms of Quaternions ---

;; (book p. 189)
(defn qw-sysder [A B C]
  (let [B-C∕A (/ (- B C) A) C-A∕B (/ (- C A) B) A-B∕C (/ (- A B) C)]
        (letfn [(the-deriv
                     [qw-state]
                     (let [t (state->t qw-state)
                                    q (coordinate qw-state)
                                    omega-body (ref qw-state 2)]
                                (let [omega↑a (ref omega-body 0)
                                         omega↑b (ref omega-body 1)
                                         omega↑c (ref omega-body 2)]
                                     (let [tdot 1
                                              qdot (*
                                                     -1/2
                                                     (+
                                                        (* omega↑a q:i)
                                                        (* omega↑b q:j)
                                                        (* omega↑c q:k))
                                                     q)
                                              omegadot (up
                                                         (*
                                                             B-C∕A
                                                             omega↑b
                                                             omega↑c)
                                                         (*
                                                             C-A∕B
                                                             omega↑c
                                                             omega↑a)
                                                         (*
                                                             A-B∕C
                                                             omega↑a
                                                             omega↑b))]
                                          (up tdot qdot omegadot)))))]
             the-deriv)))

;; (book p. 190)
(defn qw-state->L-space [A B C]
  (fn [qw-state]
        (let [q (coordinate qw-state)]
            (let [Lbody ((L-body A B C) (ref qw-state 2))
                     M (quaternion->rotation-matrix (make-quaternion q))]
                 (* Lbody (transpose M))))))

;; (book p. 190)
(defn monitor-errors [win A B C L0 E0]
  (fn [qw-state]
        (let [t (state->t qw-state)
                L ((qw-state->L-space A B C) qw-state)
                E ((T-body A B C) (ref qw-state 2))]
            (plot-point win t (relative-error (ref L 0) (ref L0 0)))
            (plot-point win t (relative-error (ref L 1) (ref L0 1)))
            (plot-point win t (relative-error (ref L 2) (ref L0 2)))
            (plot-point win t (relative-error E E0))
            qw-state)))

;; (book p. 190)
(def win (frame 0.0 100.0 -1.0e-13 1.0e-13))


(let [A 1.0
      B (sqrt 2.0)
      C 2.0
      Euler-state (up 0.0 (up 1.0 0.0 0.0) (up 0.1 0.1 0.1))
      M (Euler->M (coordinate Euler-state))
      q (quaternion->vector (rotation-matrix->quaternion M))
      qw-state0 (up
                  (state->t Euler-state) q
                  (Euler-state->omega-body Euler-state))]
  (let [L0 ((qw-state->L-space A B C) qw-state0)
           E0 ((T-body A B C) (ref qw-state0 2))]
       ((evolve qw-sysder A B C)
            qw-state0 (monitor-errors win A B C L0 E0) 0.1 100.0 1.0e-12)))"
    "SICM 3.1.1 The Legendre Transformation"
    ";; ===========================================
;; SICM §3.1.1 — The Legendre Transformation
;; Chapter 3 — Hamiltonian Mechanics
;; https://tgvaughan.github.io/sicm/chapter003.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[H-rectangular Hamilton-equations Hamiltonian->Lagrangian Hamiltonian->state-derivative L-rectangular Lagrangian->Hamiltonian Legendre-transform qp->H-state-path]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 3 ---

;; (Pedagogical redef of `Hamilton-equations` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamilton-equations [Hamiltonian]
;;   (fn [q p]
;;         (let [state-path (qp->H-state-path q p)]
;;             (-
;;                  (D state-path)
;;                  (compose
;;                     (Hamiltonian->state-derivative Hamiltonian)
;;                     state-path)))))

;; (Pedagogical redef of `Hamiltonian->state-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamiltonian->state-derivative [Hamiltonian]
;;   (fn [H-state]
;;         (up
;;             1 (((partial 2) Hamiltonian) H-state)
;;             (- (((partial 1) Hamiltonian) H-state)))))

(defn qp->H-state-path [q p] (fn [t] (up t (q t) (p t))))

(defn H-rectangular [m V]
  (fn [state]
        (let [q (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (ref q 0) (ref q 1))))))

(simplify
  (let [V (literal-function 'V '(-> (X Real Real) Real))
                q (up (literal-function 'x) (literal-function 'y))
                p (down (literal-function 'p_x) (literal-function 'p_y))]
            (((Hamilton-equations (H-rectangular 'm V)) q p) 't)))

;; --- §3.1.1 — The Legendre Transformation ---


;; --- Computing Hamiltonians ---

;; (book p. 212)
;; (Pedagogical redef of `Legendre-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Legendre-transform [F]
;;   (let [w-of-v (D F)]
;;         (letfn [(G
;;                      [w]
;;                      (let [zero (compatible-zero w)]
;;                         (let [M ((D w-of-v) zero) b (w-of-v zero)]
;;                              (let [v (solve-linear-left M (- w b))]
;;                                   (- (* w v) (F v))))))]
;;              G)))

;; (book p. 212)
;; (Pedagogical redef of `Lagrangian->Hamiltonian` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->Hamiltonian [Lagrangian]
;;   (fn [H-state]
;;         (let [t (state->t H-state)
;;                 q (coordinate H-state)
;;                 p (momentum H-state)]
;;             (letfn [(L [qdot] (Lagrangian (up t q qdot)))]
;;                  ((Legendre-transform L) p)))))

;; (book p. 212)
(defn Hamiltonian->Lagrangian [Hamiltonian]
  (fn [L-state]
        (let [t (state->t L-state)
                q (coordinate L-state)
                qdot (velocity L-state)]
            (letfn [(H [p] (Hamiltonian (up t q p)))]
                 ((Legendre-transform H) qdot)))))

;; (book p. 212)
(defn L-rectangular [m V]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (- (* 1/2 m (square qdot)) (V (ref q 0) (ref q 1))))))

;; (book p. 212)
(simplify
  ((Lagrangian->Hamiltonian
             (L-rectangular
                                      'm
                                      (literal-function
                                                     'V
                                                     '(->
                                                                        (X
                                                                            Real
                                                                            Real)
                                                                        Real))))
            (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    't
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'y)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (down
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_y))))"
    "SICM 3.6.2 Computing Stroboscopic Surfaces of Section"
    ";; ===========================================
;; SICM §3.6.2 — Computing Stroboscopic Surfaces of Section
;; Chapter 3 — Hamiltonian Mechanics
;; https://tgvaughan.github.io/sicm/chapter003.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[F G H H-pend-sysder H-rectangular Hamilton-equations Hamiltonian->Lagrangian Hamiltonian->state-derivative L-axisymmetric-top L-rectangular Lagrangian->Hamiltonian Legendre-transform driven-pendulum-map monitor-p-theta qp->H-state-path win window]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 3 ---

;; (Pedagogical redef of `Hamilton-equations` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamilton-equations [Hamiltonian]
;;   (fn [q p]
;;         (let [state-path (qp->H-state-path q p)]
;;             (-
;;                  (D state-path)
;;                  (compose
;;                     (Hamiltonian->state-derivative Hamiltonian)
;;                     state-path)))))

;; (Pedagogical redef of `Hamiltonian->state-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamiltonian->state-derivative [Hamiltonian]
;;   (fn [H-state]
;;         (up
;;             1 (((partial 2) Hamiltonian) H-state)
;;             (- (((partial 1) Hamiltonian) H-state)))))

(defn qp->H-state-path [q p] (fn [t] (up t (q t) (p t))))

(defn H-rectangular [m V]
  (fn [state]
        (let [q (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (ref q 0) (ref q 1))))))

(simplify
  (let [V (literal-function 'V '(-> (X Real Real) Real))
                q (up (literal-function 'x) (literal-function 'y))
                p (down (literal-function 'p_x) (literal-function 'p_y))]
            (((Hamilton-equations (H-rectangular 'm V)) q p) 't)))

;; (Pedagogical redef of `Legendre-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Legendre-transform [F]
;;   (let [w-of-v (D F)]
;;         (letfn [(G
;;                      [w]
;;                      (let [zero (compatible-zero w)]
;;                         (let [M ((D w-of-v) zero) b (w-of-v zero)]
;;                              (let [v (solve-linear-left M (- w b))]
;;                                   (- (* w v) (F v))))))]
;;              G)))

;; (Pedagogical redef of `Lagrangian->Hamiltonian` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->Hamiltonian [Lagrangian]
;;   (fn [H-state]
;;         (let [t (state->t H-state)
;;                 q (coordinate H-state)
;;                 p (momentum H-state)]
;;             (letfn [(L [qdot] (Lagrangian (up t q qdot)))]
;;                  ((Legendre-transform L) p)))))

(defn Hamiltonian->Lagrangian [Hamiltonian]
  (fn [L-state]
        (let [t (state->t L-state)
                q (coordinate L-state)
                qdot (velocity L-state)]
            (letfn [(H [p] (Hamiltonian (up t q p)))]
                 ((Legendre-transform H) qdot)))))

(defn L-rectangular [m V]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (- (* 1/2 m (square qdot)) (V (ref q 0) (ref q 1))))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-rectangular
                                      'm
                                      (literal-function
                                                     'V
                                                     '(->
                                                                        (X
                                                                            Real
                                                                            Real)
                                                                        Real))))
            (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    't
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'y)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (down
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_y))))

(def F
  (literal-function
       'F
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def G
  (literal-function
       'G
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def H
  (literal-function
       'H
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))

((+
   (Poisson-bracket F (Poisson-bracket G H))
   (Poisson-bracket G (Poisson-bracket H F))
   (Poisson-bracket H (Poisson-bracket F G)))
  (up
                                                                                                                                              't
                                                                                                                                              (up
                                                                                                                                                  'x
                                                                                                                                                  'y)
                                                                                                                                              (down
                                                                                                                                                  'px
                                                                                                                                                  'py)))

(simplify
  ((Lagrangian->Hamiltonian
             (L-central-polar
                                      'm
                                      (literal-function
                                                       'V)))
            (up
                                                                                                                                                                                                                                  't
                                                                                                                                                                                                                                  (up
                                                                                                                                                                                                                                      'r
                                                                                                                                                                                                                                      'phi)
                                                                                                                                                                                                                                  (down
                                                                                                                                                                                                                                      'p_r
                                                                                                                                                                                                                                      'p_phi))))

(simplify
  (((Hamilton-equations
              (Lagrangian->Hamiltonian
                                  (L-central-polar
                                                           'm
                                                           (literal-function
                                                                            'V))))
             (up
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'phi))
             (down
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_phi)))
            't))

(defn L-axisymmetric-top [A C gMR]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (let [theta (ref q 0)
                     thetadot (ref qdot 0)
                     phidot (ref qdot 1)
                     psidot (ref qdot 2)]
                 (+
                      (*
                         1/2 A
                         (+
                            (square thetadot)
                            (square (* phidot (sin theta)))))
                      (*
                         1/2 C
                         (square (+ psidot (* phidot (cos theta)))))
                      (* -1 gMR (cos theta)))))))

(simplify
  ((Lagrangian->Hamiltonian (L-axisymmetric-top 'A 'C 'gMR))
            (up
                                                                       't
                                                                       (up
                                                                           'theta
                                                                           'phi
                                                                           'psi)
                                                                       (down
                                                                           'p_theta
                                                                           'p_phi
                                                                           'p_psi))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-periodically-driven-pendulum
                                      'm 'l 'g 'a 'omega))
            (up
                                                                                                                                              't
                                                                                                                                              'theta
                                                                                                                                              'p_theta)))

(defn H-pend-sysder [m l g a omega]
  (Hamiltonian->state-derivative
        (Lagrangian->Hamiltonian
                                       (L-periodically-driven-pendulum
                                                                m l g a
                                                                omega))))

(def window (frame (- Math/PI) Math/PI -10.0 10.0))


(defn monitor-p-theta [win]
  (fn [state]
        (let [q ((principal-value Math/PI) (coordinate state))
                p (momentum state)]
            (plot-point win q p))))

(let [m 1.0
      l 1.0
      g 9.8
      A 0.1
      omega (* 2 (sqrt 9.8))
      (evolve H-pend-sysder m l g A omega) (up 0.0 1.0 0.0)])

;; --- §3.6.2 — Computing Stroboscopic Surfaces of Section ---

;; (book p. 247)
(defn driven-pendulum-map [m l g A omega]
  (let [advance (state-advancer H-pend-sysder m l g A omega)
            map-period (/ (* 2 Math/PI) omega)]
        (fn [theta ptheta return fail]
             (let [ns (advance (up 0 theta ptheta) map-period)]
                 (return
                      ((principal-value Math/PI) (coordinate ns))
                      (momentum ns))))))

;; (book p. 248)
(def win (frame (- Math/PI) Math/PI -20 20))

(let [m 1.0 l 1.0 g 9.8 A 0.05]
  (let [omega0 (sqrt (/ g l))]
       (let [omega (* 4.2 omega0)]
            (explore-map
                 win (driven-pendulum-map m l g A omega) 1000))))
;1000 points for each initial condition"
    "SICM 3.6.4 Computing Hénon–Heiles Surfaces of Section"
    ";; ===========================================
;; SICM §3.6.4 — Computing Hénon–Heiles Surfaces of Section
;; Chapter 3 — Hamiltonian Mechanics
;; https://tgvaughan.github.io/sicm/chapter003.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[F G H H-pend-sysder H-rectangular HHHam HHmap HHpotential HHsysder Hamilton-equations Hamiltonian->Lagrangian Hamiltonian->state-derivative L-axisymmetric-top L-rectangular Lagrangian->Hamiltonian Legendre-transform driven-pendulum-map find-next-crossing monitor-p-theta qp->H-state-path refine-crossing section->state win window]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 3 ---

;; (Pedagogical redef of `Hamilton-equations` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamilton-equations [Hamiltonian]
;;   (fn [q p]
;;         (let [state-path (qp->H-state-path q p)]
;;             (-
;;                  (D state-path)
;;                  (compose
;;                     (Hamiltonian->state-derivative Hamiltonian)
;;                     state-path)))))

;; (Pedagogical redef of `Hamiltonian->state-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamiltonian->state-derivative [Hamiltonian]
;;   (fn [H-state]
;;         (up
;;             1 (((partial 2) Hamiltonian) H-state)
;;             (- (((partial 1) Hamiltonian) H-state)))))

(defn qp->H-state-path [q p] (fn [t] (up t (q t) (p t))))

(defn H-rectangular [m V]
  (fn [state]
        (let [q (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (ref q 0) (ref q 1))))))

(simplify
  (let [V (literal-function 'V '(-> (X Real Real) Real))
                q (up (literal-function 'x) (literal-function 'y))
                p (down (literal-function 'p_x) (literal-function 'p_y))]
            (((Hamilton-equations (H-rectangular 'm V)) q p) 't)))

;; (Pedagogical redef of `Legendre-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Legendre-transform [F]
;;   (let [w-of-v (D F)]
;;         (letfn [(G
;;                      [w]
;;                      (let [zero (compatible-zero w)]
;;                         (let [M ((D w-of-v) zero) b (w-of-v zero)]
;;                              (let [v (solve-linear-left M (- w b))]
;;                                   (- (* w v) (F v))))))]
;;              G)))

;; (Pedagogical redef of `Lagrangian->Hamiltonian` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->Hamiltonian [Lagrangian]
;;   (fn [H-state]
;;         (let [t (state->t H-state)
;;                 q (coordinate H-state)
;;                 p (momentum H-state)]
;;             (letfn [(L [qdot] (Lagrangian (up t q qdot)))]
;;                  ((Legendre-transform L) p)))))

(defn Hamiltonian->Lagrangian [Hamiltonian]
  (fn [L-state]
        (let [t (state->t L-state)
                q (coordinate L-state)
                qdot (velocity L-state)]
            (letfn [(H [p] (Hamiltonian (up t q p)))]
                 ((Legendre-transform H) qdot)))))

(defn L-rectangular [m V]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (- (* 1/2 m (square qdot)) (V (ref q 0) (ref q 1))))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-rectangular
                                      'm
                                      (literal-function
                                                     'V
                                                     '(->
                                                                        (X
                                                                            Real
                                                                            Real)
                                                                        Real))))
            (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    't
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'y)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (down
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_y))))

(def F
  (literal-function
       'F
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def G
  (literal-function
       'G
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def H
  (literal-function
       'H
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))

((+
   (Poisson-bracket F (Poisson-bracket G H))
   (Poisson-bracket G (Poisson-bracket H F))
   (Poisson-bracket H (Poisson-bracket F G)))
  (up
                                                                                                                                              't
                                                                                                                                              (up
                                                                                                                                                  'x
                                                                                                                                                  'y)
                                                                                                                                              (down
                                                                                                                                                  'px
                                                                                                                                                  'py)))

(simplify
  ((Lagrangian->Hamiltonian
             (L-central-polar
                                      'm
                                      (literal-function
                                                       'V)))
            (up
                                                                                                                                                                                                                                  't
                                                                                                                                                                                                                                  (up
                                                                                                                                                                                                                                      'r
                                                                                                                                                                                                                                      'phi)
                                                                                                                                                                                                                                  (down
                                                                                                                                                                                                                                      'p_r
                                                                                                                                                                                                                                      'p_phi))))

(simplify
  (((Hamilton-equations
              (Lagrangian->Hamiltonian
                                  (L-central-polar
                                                           'm
                                                           (literal-function
                                                                            'V))))
             (up
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'phi))
             (down
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_phi)))
            't))

(defn L-axisymmetric-top [A C gMR]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (let [theta (ref q 0)
                     thetadot (ref qdot 0)
                     phidot (ref qdot 1)
                     psidot (ref qdot 2)]
                 (+
                      (*
                         1/2 A
                         (+
                            (square thetadot)
                            (square (* phidot (sin theta)))))
                      (*
                         1/2 C
                         (square (+ psidot (* phidot (cos theta)))))
                      (* -1 gMR (cos theta)))))))

(simplify
  ((Lagrangian->Hamiltonian (L-axisymmetric-top 'A 'C 'gMR))
            (up
                                                                       't
                                                                       (up
                                                                           'theta
                                                                           'phi
                                                                           'psi)
                                                                       (down
                                                                           'p_theta
                                                                           'p_phi
                                                                           'p_psi))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-periodically-driven-pendulum
                                      'm 'l 'g 'a 'omega))
            (up
                                                                                                                                              't
                                                                                                                                              'theta
                                                                                                                                              'p_theta)))

(defn H-pend-sysder [m l g a omega]
  (Hamiltonian->state-derivative
        (Lagrangian->Hamiltonian
                                       (L-periodically-driven-pendulum
                                                                m l g a
                                                                omega))))

(def window (frame (- Math/PI) Math/PI -10.0 10.0))


(defn monitor-p-theta [win]
  (fn [state]
        (let [q ((principal-value Math/PI) (coordinate state))
                p (momentum state)]
            (plot-point win q p))))

(let [m 1.0
      l 1.0
      g 9.8
      A 0.1
      omega (* 2 (sqrt 9.8))
      (evolve H-pend-sysder m l g A omega) (up 0.0 1.0 0.0)])

(defn driven-pendulum-map [m l g A omega]
  (let [advance (state-advancer H-pend-sysder m l g A omega)
            map-period (/ (* 2 Math/PI) omega)]
        (fn [theta ptheta return fail]
             (let [ns (advance (up 0 theta ptheta) map-period)]
                 (return
                      ((principal-value Math/PI) (coordinate ns))
                      (momentum ns))))))

(def win (frame (- Math/PI) Math/PI -20 20))

(let [m 1.0 l 1.0 g 9.8 A 0.05]
  (let [omega0 (sqrt (/ g l))]
       (let [omega (* 4.2 omega0)]
            (explore-map
                 win (driven-pendulum-map m l g A omega) 1000))))
;1000 points for each initial condition

;; --- §3.6.4 — Computing Hénon–Heiles Surfaces of Section ---

;; (book p. 261)
(defn HHmap [E dt sec-eps int-eps]
  (letfn [(make-advance
                [advancer eps] (fn [s dt] (advancer s dt eps)))]
        (let [adv (make-advance (state-advancer HHsysder) int-eps)]
               (fn [y py cont fail]
                    (let [initial-state (section->state E y py)]
                        (if (not initial-state)
                             (fail)
                             (find-next-crossing
                                 initial-state adv dt sec-eps
                                 (fn [crossing-state
                                                        running-state]
                                                     (cont
                                                         (ref
                                                               (coordinate
                                                                    crossing-state)
                                                               1)
                                                         (ref
                                                               (momentum
                                                                    crossing-state)
                                                               1))))))))))

;; (book p. 261)
(defn section->state [E y py]
  (let [d (-
                E (+ (HHpotential (up 0 (up 0 y))) (* 1/2 (square py))))]
        (if (>= d 0.0)
             (let [px (sqrt (* 2 d))] (up 0 (up 0 y) (down px py)))
             false)))

;; (book p. 261)
(defn HHHam [s] (+ (* 1/2 (square (momentum s))) (HHpotential s)))

;; (book p. 261)
(defn HHpotential [s]
  (let [x (ref (coordinate s) 0) y (ref (coordinate s) 1)]
        (+
             (* 1/2 (+ (square x) (square y)))
             (- (* (square x) y) (* 1/3 (cube y))))))

;; (book p. 261)
(defn HHsysder [] (Hamiltonian->state-derivative HHHam))

;; (book p. 261)
(defn find-next-crossing [state advance dt sec-eps cont]
  (letfn [(lp
                [s]
                (let [next-state (advance s dt)]
                    (if (and
                             (> (ref (coordinate next-state) 0) 0)
                             (< (ref (coordinate s) 0) 0))
                         (let [crossing-state (refine-crossing
                                                  sec-eps advance s)]
                             (cont crossing-state next-state))
                         (lp next-state))))]
        (lp state)))

;; (book p. 261)
(defn refine-crossing [sec-eps advance state]
  (letfn [(lp
                [state]
                (let [x (ref (coordinate state) 0)
                        xd (ref (momentum state) 0)]
                    (let [zstate (advance state (- (/ x xd)))]
                         (if (<
                                  (abs (ref (coordinate zstate) 0))
                                  sec-eps)
                              zstate
                              (lp zstate)))))]
        (lp state)))

;; (book p. 261)
(def win (frame -0.5 0.7 -0.6 0.6))

(explore-map win (HHmap 0.125 0.1 1.0e-10 1.0e-12) 500)"
    "SICM 3.9 Standard Map"
    ";; ===========================================
;; SICM §3.9 — Standard Map
;; Chapter 3 — Hamiltonian Mechanics
;; https://tgvaughan.github.io/sicm/chapter003.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[F G H H-pend-sysder H-rectangular HHHam HHmap HHpotential HHsysder Hamilton-equations Hamiltonian->Lagrangian Hamiltonian->state-derivative L-axisymmetric-top L-rectangular Lagrangian->Hamiltonian Legendre-transform driven-pendulum-map find-next-crossing monitor-p-theta qp->H-state-path refine-crossing section->state standard-map win window]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 3 ---

;; (Pedagogical redef of `Hamilton-equations` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamilton-equations [Hamiltonian]
;;   (fn [q p]
;;         (let [state-path (qp->H-state-path q p)]
;;             (-
;;                  (D state-path)
;;                  (compose
;;                     (Hamiltonian->state-derivative Hamiltonian)
;;                     state-path)))))

;; (Pedagogical redef of `Hamiltonian->state-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Hamiltonian->state-derivative [Hamiltonian]
;;   (fn [H-state]
;;         (up
;;             1 (((partial 2) Hamiltonian) H-state)
;;             (- (((partial 1) Hamiltonian) H-state)))))

(defn qp->H-state-path [q p] (fn [t] (up t (q t) (p t))))

(defn H-rectangular [m V]
  (fn [state]
        (let [q (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (ref q 0) (ref q 1))))))

(simplify
  (let [V (literal-function 'V '(-> (X Real Real) Real))
                q (up (literal-function 'x) (literal-function 'y))
                p (down (literal-function 'p_x) (literal-function 'p_y))]
            (((Hamilton-equations (H-rectangular 'm V)) q p) 't)))

;; (Pedagogical redef of `Legendre-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Legendre-transform [F]
;;   (let [w-of-v (D F)]
;;         (letfn [(G
;;                      [w]
;;                      (let [zero (compatible-zero w)]
;;                         (let [M ((D w-of-v) zero) b (w-of-v zero)]
;;                              (let [v (solve-linear-left M (- w b))]
;;                                   (- (* w v) (F v))))))]
;;              G)))

;; (Pedagogical redef of `Lagrangian->Hamiltonian` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lagrangian->Hamiltonian [Lagrangian]
;;   (fn [H-state]
;;         (let [t (state->t H-state)
;;                 q (coordinate H-state)
;;                 p (momentum H-state)]
;;             (letfn [(L [qdot] (Lagrangian (up t q qdot)))]
;;                  ((Legendre-transform L) p)))))

(defn Hamiltonian->Lagrangian [Hamiltonian]
  (fn [L-state]
        (let [t (state->t L-state)
                q (coordinate L-state)
                qdot (velocity L-state)]
            (letfn [(H [p] (Hamiltonian (up t q p)))]
                 ((Legendre-transform H) qdot)))))

(defn L-rectangular [m V]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (- (* 1/2 m (square qdot)) (V (ref q 0) (ref q 1))))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-rectangular
                                      'm
                                      (literal-function
                                                     'V
                                                     '(->
                                                                        (X
                                                                            Real
                                                                            Real)
                                                                        Real))))
            (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    't
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (up
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'y)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    (down
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_x
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        'p_y))))

(def F
  (literal-function
       'F
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def G
  (literal-function
       'G
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(def H
  (literal-function
       'H
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))

((+
   (Poisson-bracket F (Poisson-bracket G H))
   (Poisson-bracket G (Poisson-bracket H F))
   (Poisson-bracket H (Poisson-bracket F G)))
  (up
                                                                                                                                              't
                                                                                                                                              (up
                                                                                                                                                  'x
                                                                                                                                                  'y)
                                                                                                                                              (down
                                                                                                                                                  'px
                                                                                                                                                  'py)))

(simplify
  ((Lagrangian->Hamiltonian
             (L-central-polar
                                      'm
                                      (literal-function
                                                       'V)))
            (up
                                                                                                                                                                                                                                  't
                                                                                                                                                                                                                                  (up
                                                                                                                                                                                                                                      'r
                                                                                                                                                                                                                                      'phi)
                                                                                                                                                                                                                                  (down
                                                                                                                                                                                                                                      'p_r
                                                                                                                                                                                                                                      'p_phi))))

(simplify
  (((Hamilton-equations
              (Lagrangian->Hamiltonian
                                  (L-central-polar
                                                           'm
                                                           (literal-function
                                                                            'V))))
             (up
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                              'phi))
             (down
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_r)
                                                                                                                                                                                                                                                                                                                                                          (literal-function
                                                                                                                                                                                                                                                                                                                                                                'p_phi)))
            't))

(defn L-axisymmetric-top [A C gMR]
  (fn [local]
        (let [q (coordinate local) qdot (velocity local)]
            (let [theta (ref q 0)
                     thetadot (ref qdot 0)
                     phidot (ref qdot 1)
                     psidot (ref qdot 2)]
                 (+
                      (*
                         1/2 A
                         (+
                            (square thetadot)
                            (square (* phidot (sin theta)))))
                      (*
                         1/2 C
                         (square (+ psidot (* phidot (cos theta)))))
                      (* -1 gMR (cos theta)))))))

(simplify
  ((Lagrangian->Hamiltonian (L-axisymmetric-top 'A 'C 'gMR))
            (up
                                                                       't
                                                                       (up
                                                                           'theta
                                                                           'phi
                                                                           'psi)
                                                                       (down
                                                                           'p_theta
                                                                           'p_phi
                                                                           'p_psi))))

(simplify
  ((Lagrangian->Hamiltonian
             (L-periodically-driven-pendulum
                                      'm 'l 'g 'a 'omega))
            (up
                                                                                                                                              't
                                                                                                                                              'theta
                                                                                                                                              'p_theta)))

(defn H-pend-sysder [m l g a omega]
  (Hamiltonian->state-derivative
        (Lagrangian->Hamiltonian
                                       (L-periodically-driven-pendulum
                                                                m l g a
                                                                omega))))

(def window (frame (- Math/PI) Math/PI -10.0 10.0))


(defn monitor-p-theta [win]
  (fn [state]
        (let [q ((principal-value Math/PI) (coordinate state))
                p (momentum state)]
            (plot-point win q p))))

(let [m 1.0
      l 1.0
      g 9.8
      A 0.1
      omega (* 2 (sqrt 9.8))
      (evolve H-pend-sysder m l g A omega) (up 0.0 1.0 0.0)])

(defn driven-pendulum-map [m l g A omega]
  (let [advance (state-advancer H-pend-sysder m l g A omega)
            map-period (/ (* 2 Math/PI) omega)]
        (fn [theta ptheta return fail]
             (let [ns (advance (up 0 theta ptheta) map-period)]
                 (return
                      ((principal-value Math/PI) (coordinate ns))
                      (momentum ns))))))

(def win (frame (- Math/PI) Math/PI -20 20))

(let [m 1.0 l 1.0 g 9.8 A 0.05]
  (let [omega0 (sqrt (/ g l))]
       (let [omega (* 4.2 omega0)]
            (explore-map
                 win (driven-pendulum-map m l g A omega) 1000))))
;1000 points for each initial condition

(defn HHmap [E dt sec-eps int-eps]
  (letfn [(make-advance
                [advancer eps] (fn [s dt] (advancer s dt eps)))]
        (let [adv (make-advance (state-advancer HHsysder) int-eps)]
               (fn [y py cont fail]
                    (let [initial-state (section->state E y py)]
                        (if (not initial-state)
                             (fail)
                             (find-next-crossing
                                 initial-state adv dt sec-eps
                                 (fn [crossing-state
                                                        running-state]
                                                     (cont
                                                         (ref
                                                               (coordinate
                                                                    crossing-state)
                                                               1)
                                                         (ref
                                                               (momentum
                                                                    crossing-state)
                                                               1))))))))))

(defn section->state [E y py]
  (let [d (-
                E (+ (HHpotential (up 0 (up 0 y))) (* 1/2 (square py))))]
        (if (>= d 0.0)
             (let [px (sqrt (* 2 d))] (up 0 (up 0 y) (down px py)))
             false)))

(defn HHHam [s] (+ (* 1/2 (square (momentum s))) (HHpotential s)))

(defn HHpotential [s]
  (let [x (ref (coordinate s) 0) y (ref (coordinate s) 1)]
        (+
             (* 1/2 (+ (square x) (square y)))
             (- (* (square x) y) (* 1/3 (cube y))))))

(defn HHsysder [] (Hamiltonian->state-derivative HHHam))

(defn find-next-crossing [state advance dt sec-eps cont]
  (letfn [(lp
                [s]
                (let [next-state (advance s dt)]
                    (if (and
                             (> (ref (coordinate next-state) 0) 0)
                             (< (ref (coordinate s) 0) 0))
                         (let [crossing-state (refine-crossing
                                                  sec-eps advance s)]
                             (cont crossing-state next-state))
                         (lp next-state))))]
        (lp state)))

(defn refine-crossing [sec-eps advance state]
  (letfn [(lp
                [state]
                (let [x (ref (coordinate state) 0)
                        xd (ref (momentum state) 0)]
                    (let [zstate (advance state (- (/ x xd)))]
                         (if (<
                                  (abs (ref (coordinate zstate) 0))
                                  sec-eps)
                              zstate
                              (lp zstate)))))]
        (lp state)))

(def win (frame -0.5 0.7 -0.6 0.6))

(explore-map win (HHmap 0.125 0.1 1.0e-10 1.0e-12) 500)

;; --- §3.9 — Standard Map ---

;; (book p. 278)
;; (Pedagogical redef of `standard-map` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn standard-map [K]
;;   (fn [theta I return failure]
;;         (let [nI (+ I (* K (sin theta)))]
;;             (return
;;                  ((principal-value (* 2 Math/PI)) (+ theta nI))
;;                  ((principal-value (* 2 Math/PI)) nI)))))

;; (book p. 279)
(def window (frame 0.0 (* 2 Math/PI) 0.0 (* 2 Math/PI)))

(explore-map window (standard-map 0.6) 2000)"
    "SICM 4.3.1 Computation of Stable and Unstable Manifolds"
    ";; ===========================================
;; SICM §4.3.1 — Computation of Stable and Unstable Manifolds
;; Chapter 4 — Phase Space Structure
;; https://tgvaughan.github.io/sicm/chapter004.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[cylinder-near? plot-parametric-fill unstable-manifold]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; (book p. 308)
(defn unstable-manifold [T xe ye dx dy rho eps]
  (fn [param]
        (let [n (floor->exact (/ (log (/ param eps)) (log rho)))]
            ((iterated-map T n)
                 (+ xe (* dx (/ param (expt rho n))))
                 (+ ye (* dy (/ param (expt rho n)))) make-point
                 (fn [] (throw (ex-info \"Failed\" {})))))))

;; (book p. 308)
(defn plot-parametric-fill [win f a b near?]
  (letfn [(loop [a xa b xb]
                (when (not
                            (close-enuf?
                                 a b (* 10 *machine-epsilon*)))
                      (let [m (/ (+ a b) 2)]
                            (let [xm (f m)]
                                 (plot-point
                                      win (abscissa xm) (ordinate xm))
                                 (when (not (near? xa xm))
                                      (loop a xa m xm))
                                 (when (not (near? xb xm))
                                      (loop m xm b xb))))))]
        (loop a (f a) b (f b))))

;; (book p. 309)
(defn cylinder-near? [eps]
  (let [eps2 (square eps)]
        (fn [point1 point2]
             (<
                 (+
                    (square
                       ((principal-value pi)
                               (-
                                                     (abscissa point1)
                                                     (abscissa point2))))
                    (square (- (ordinate point1) (ordinate point2))))
                 eps2))))"
    "SICM 4.5.1 Computing the Poincaré–Birkhoff Construction"
    ";; ===========================================
;; SICM §4.5.1 — Computing the Poincaré–Birkhoff Construction
;; Chapter 4 — Phase Space Structure
;; https://tgvaughan.github.io/sicm/chapter004.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[cylinder-near? plot-parametric-fill radially-mapping-points unstable-manifold]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 4 ---

(defn unstable-manifold [T xe ye dx dy rho eps]
  (fn [param]
        (let [n (floor->exact (/ (log (/ param eps)) (log rho)))]
            ((iterated-map T n)
                 (+ xe (* dx (/ param (expt rho n))))
                 (+ ye (* dy (/ param (expt rho n)))) make-point
                 (fn [] (throw (ex-info \"Failed\" {})))))))

(defn plot-parametric-fill [win f a b near?]
  (letfn [(loop [a xa b xb]
                (when (not
                            (close-enuf?
                                 a b (* 10 *machine-epsilon*)))
                      (let [m (/ (+ a b) 2)]
                            (let [xm (f m)]
                                 (plot-point
                                      win (abscissa xm) (ordinate xm))
                                 (when (not (near? xa xm))
                                      (loop a xa m xm))
                                 (when (not (near? xb xm))
                                      (loop m xm b xb))))))]
        (loop a (f a) b (f b))))

(defn cylinder-near? [eps]
  (let [eps2 (square eps)]
        (fn [point1 point2]
             (<
                 (+
                    (square
                       ((principal-value pi)
                               (-
                                                     (abscissa point1)
                                                     (abscissa point2))))
                    (square (- (ordinate point1) (ordinate point2))))
                 eps2))))

;; --- §4.5.1 — Computing the Poincaré–Birkhoff Construction ---

;; (book p. 321)
(defn radially-mapping-points [Tmap Jmin Jmax phi eps]
  (bisect
        (fn [J]
                ((principal-value pi)
                    (Tmap
                                          phi J
                                          (fn [phip Jp]
                                                (- phi phip))
                                          (fn []
                                                (throw
                                                    (ex-info
                                                           \"should not get here\"
                                                           {}))))))
        Jmin Jmax eps))"
    "SICM 4.6.1 Finding Invariant Curves"
    ";; ===========================================
;; SICM §4.6.1 — Finding Invariant Curves
;; Chapter 4 — Phase Space Structure
;; https://tgvaughan.github.io/sicm/chapter004.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[cylinder-near? find-invariant-curve plot-parametric-fill radially-mapping-points unstable-manifold which-way?]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 4 ---

(defn unstable-manifold [T xe ye dx dy rho eps]
  (fn [param]
        (let [n (floor->exact (/ (log (/ param eps)) (log rho)))]
            ((iterated-map T n)
                 (+ xe (* dx (/ param (expt rho n))))
                 (+ ye (* dy (/ param (expt rho n)))) make-point
                 (fn [] (throw (ex-info \"Failed\" {})))))))

(defn plot-parametric-fill [win f a b near?]
  (letfn [(loop [a xa b xb]
                (when (not
                            (close-enuf?
                                 a b (* 10 *machine-epsilon*)))
                      (let [m (/ (+ a b) 2)]
                            (let [xm (f m)]
                                 (plot-point
                                      win (abscissa xm) (ordinate xm))
                                 (when (not (near? xa xm))
                                      (loop a xa m xm))
                                 (when (not (near? xb xm))
                                      (loop m xm b xb))))))]
        (loop a (f a) b (f b))))

(defn cylinder-near? [eps]
  (let [eps2 (square eps)]
        (fn [point1 point2]
             (<
                 (+
                    (square
                       ((principal-value pi)
                               (-
                                                     (abscissa point1)
                                                     (abscissa point2))))
                    (square (- (ordinate point1) (ordinate point2))))
                 eps2))))

(defn radially-mapping-points [Tmap Jmin Jmax phi eps]
  (bisect
        (fn [J]
                ((principal-value pi)
                    (Tmap
                                          phi J
                                          (fn [phip Jp]
                                                (- phi phip))
                                          (fn []
                                                (throw
                                                    (ex-info
                                                           \"should not get here\"
                                                           {}))))))
        Jmin Jmax eps))

;; --- §4.6.1 — Finding Invariant Curves ---

;; (book p. 326)
(defn find-invariant-curve [the-map rn theta0 Jmin Jmax eps]
  (bisect (fn [J] (which-way? rn theta0 J the-map)) Jmin Jmax eps))

;; (book p. 327)
(defn which-way? [rotation-number x0 y0 the-map]
  (let [pv (principal-value (x0 pi))]
        (letfn [(lp
                     [z zmin zmax x xmin xmax y]
                     (let [nz (pv
                                  (+
                                      z
                                      (* (* 2 Math/PI) rotation-number)))]
                         (the-map
                              x y
                              (fn [nx ny]
                                       (let [nx (pv nx)]
                                           (cond
                                                (< x0 z zmax)
                                                (if (< x0 x xmax)
                                                      (lp
                                                          nz zmin z nx
                                                          xmin x ny)
                                                      (if (> x xmax)
                                                          1
                                                          -1))
                                                (< zmin z x0)
                                                (if (< xmin x x0)
                                                      (lp
                                                          nz z zmax nx x
                                                          xmax ny)
                                                      (if (< x xmin)
                                                          -1
                                                          1))
                                                :else
                                                (lp
                                                      nz zmin zmax nx
                                                      xmin xmax ny))))
                              (fn []
                                       (throw
                                           (ex-info
                                                  \"Map failed\" {}))))))]
             (lp
                    x0 (- x0 (* 2 Math/PI)) (+ x0 (* 2 Math/PI)) x0
                    (- x0 (* 2 Math/PI)) (+ x0 (* 2 Math/PI)) y0))))"
    "SICM 5.2.1 Time-Dependent Transformations"
    ";; ===========================================
;; SICM §5.2.1 — Time-Dependent Transformations
;; Chapter 5 — Canonical Transformations
;; https://tgvaughan.github.io/sicm/chapter005.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[C-rotating D-phase-space F->CH F->K H-arbitrary H-central H-free H-harmonic H-prime K T-func canonical-H? canonical-K? canonical? polar-canonical rotating translating]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 5 ---

(defn F->CH [F]
  (fn [state]
        (up
            (state->t state) (F state)
            (solve-linear-right
                (momentum state) (((partial 1) F) state)))))

(defn H-central [m V]
  (fn [state]
        (let [x (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (sqrt (square x)))))))

(simplify
  ((compose (H-central 'm (literal-function 'V)) (F->CH p->r))
            (up
                                                                         't
                                                                         (up
                                                                             'r
                                                                             'phi)
                                                                         (down
                                                                             'p_r
                                                                             'p_phi))))

(defn F->K [F]
  (fn [state]
        (-
            (*
               (solve-linear-right
                  (momentum state) (((partial 1) F) state))
               (((partial 0) F) state)))))

(defn translating [v]
  (fn [state] (+ (coordinate state) (* v (state->t state)))))

((F->K (translating (up 'v↑x 'v↑y 'v↑z)))
  (up
                                            't (up 'x 'y 'z)
                                            (down 'p_x 'p_y 'p_z)))

(defn H-free [m] (fn [s] (/ (square (momentum s)) (* 2 m))))

(def H-prime
  (+
       (compose (H-free 'm) (F->CH (translating (up 'v↑x 'v↑y 'v↑z))))
       (F->K (translating (up 'v↑x 'v↑y 'v↑z)))))

(H-prime
  (up
           't (up 'xprime 'yprime 'zprime)
           (down 'pprime_x 'pprime_y 'pprime_z)))

(defn canonical? [C H Hprime]
  (-
        (compose (Hamiltonian->state-derivative H) C)
        (* (D C) (Hamiltonian->state-derivative Hprime))))

;; (Pedagogical redef of `polar-canonical` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn polar-canonical [alpha]
;;   (fn [state]
;;         (let [t (state->t state)
;;                 theta (coordinate state)
;;                 I (momentum state)]
;;             (let [x (* (sqrt (/ (* 2 I) alpha)) (sin theta))
;;                      p_x (* (sqrt (* 2 alpha I)) (cos theta))]
;;                  (up t x p_x)))))

(defn H-harmonic [m k]
  (fn [s]
        (+
            (/ (square (momentum s)) (* 2 m))
            (* 1/2 k (square (coordinate s))))))


((canonical?
   (polar-canonical 'alpha) (H-harmonic 'm 'k)
   (compose (H-harmonic 'm 'k) (polar-canonical 'alpha)))
  (up
                                                                                                                        't
                                                                                                                        'theta
                                                                                                                        'I))

;; --- §5.2.1 — Time-Dependent Transformations ---

;; (book p. 347)
(defn T-func [s]
  (up 1 (zero-like (coordinate s)) (zero-like (momentum s))))


(defn D-phase-space [H]
  (fn [s] (up 0 (((partial 2) H) s) (- (((partial 1) H) s)))))

;; (book p. 348)
(defn canonical-H? [C H]
  (-
        (compose (D-phase-space H) C)
        (* (D C) (D-phase-space (compose H C)))))


(defn canonical-K? [C K]
  (- (compose T-func C) (* (D C) (+ T-func (D-phase-space K)))))


;; --- Rotating coordinates ---

;; (book p. 348)
(defn rotating [Omega]
  (fn [state]
        (let [t (state->t state) qp (coordinate state)]
            (let [xp (ref qp 0) yp (ref qp 1) zp (ref qp 2)]
                 (up
                      (-
                          (* (cos (* Omega t)) xp)
                          (* (sin (* Omega t)) yp))
                      (+
                          (* (sin (* Omega t)) xp)
                          (* (cos (* Omega t)) yp))
                      zp)))))

;; (book p. 348)
(defn C-rotating [Omega] (F->CH (rotating Omega)))

;; (book p. 348)
(def H-arbitrary
  (literal-function
       'H
       '(->
                          (UP
                              Real (UP Real Real Real)
                              (DOWN Real Real Real))
                          Real)))


((canonical-H? (C-rotating 'Omega) H-arbitrary)
  (up
                                                  't (up 'xp 'yp 'zp)
                                                  (down
                                                      'pp_x 'pp_y 'pp_z)))
;;=> (up 0 (up 0 0 0) (down 0 0 0))

;; (book p. 348)
((F->K (rotating 'Omega))
  (up
                            't (up 'xp 'yp 'zp) (down 'pp_x 'pp_y 'pp_z)))
;;=> (+ (* Omega pp_x yp) (* -1 Omega pp_y xp))

;; (book p. 348)
(defn K [Omega]
  (fn [s]
        (let [qp (coordinate s) pprint (momentum s)]
            (let [xp (ref qp 0)
                     yp (ref qp 1)
                     ppx (ref pprint 0)
                     ppy (ref pprint 1)]
                 (* -1 Omega (- (* xp ppy) (* yp ppx)))))))

;; (book p. 348)
((canonical-K? (C-rotating 'Omega) (K 'Omega))
  (up
                                                 't (up 'xp 'yp 'zp)
                                                 (down
                                                     'pp_x 'pp_y 'pp_z)))
;;=> (up 0 (up 0 0 0) (down 0 0 0))"
    "SICM 5.2.2 Abstracting the Canonical Condition"
    ";; ===========================================
;; SICM §5.2.2 — Abstracting the Canonical Condition
;; Chapter 5 — Canonical Transformations
;; https://tgvaughan.github.io/sicm/chapter005.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[C-general C-rotating C-simple-time D-phase-space F F->CH F->K H-arbitrary H-central H-free H-harmonic H-prime J-func K T-func a-non-canonical-transform canonical-H? canonical-K? canonical-transform? canonical? polar-canonical rotating symplectic-matrix? symplectic-transform? translating]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 5 ---

(defn F->CH [F]
  (fn [state]
        (up
            (state->t state) (F state)
            (solve-linear-right
                (momentum state) (((partial 1) F) state)))))

(defn H-central [m V]
  (fn [state]
        (let [x (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (sqrt (square x)))))))

(simplify
  ((compose (H-central 'm (literal-function 'V)) (F->CH p->r))
            (up
                                                                         't
                                                                         (up
                                                                             'r
                                                                             'phi)
                                                                         (down
                                                                             'p_r
                                                                             'p_phi))))

(defn F->K [F]
  (fn [state]
        (-
            (*
               (solve-linear-right
                  (momentum state) (((partial 1) F) state))
               (((partial 0) F) state)))))

(defn translating [v]
  (fn [state] (+ (coordinate state) (* v (state->t state)))))

((F->K (translating (up 'v↑x 'v↑y 'v↑z)))
  (up
                                            't (up 'x 'y 'z)
                                            (down 'p_x 'p_y 'p_z)))

(defn H-free [m] (fn [s] (/ (square (momentum s)) (* 2 m))))

(def H-prime
  (+
       (compose (H-free 'm) (F->CH (translating (up 'v↑x 'v↑y 'v↑z))))
       (F->K (translating (up 'v↑x 'v↑y 'v↑z)))))

(H-prime
  (up
           't (up 'xprime 'yprime 'zprime)
           (down 'pprime_x 'pprime_y 'pprime_z)))

(defn canonical? [C H Hprime]
  (-
        (compose (Hamiltonian->state-derivative H) C)
        (* (D C) (Hamiltonian->state-derivative Hprime))))

;; (Pedagogical redef of `polar-canonical` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn polar-canonical [alpha]
;;   (fn [state]
;;         (let [t (state->t state)
;;                 theta (coordinate state)
;;                 I (momentum state)]
;;             (let [x (* (sqrt (/ (* 2 I) alpha)) (sin theta))
;;                      p_x (* (sqrt (* 2 alpha I)) (cos theta))]
;;                  (up t x p_x)))))

(defn H-harmonic [m k]
  (fn [s]
        (+
            (/ (square (momentum s)) (* 2 m))
            (* 1/2 k (square (coordinate s))))))


((canonical?
   (polar-canonical 'alpha) (H-harmonic 'm 'k)
   (compose (H-harmonic 'm 'k) (polar-canonical 'alpha)))
  (up
                                                                                                                        't
                                                                                                                        'theta
                                                                                                                        'I))

(defn T-func [s]
  (up 1 (zero-like (coordinate s)) (zero-like (momentum s))))


(defn D-phase-space [H]
  (fn [s] (up 0 (((partial 2) H) s) (- (((partial 1) H) s)))))

(defn canonical-H? [C H]
  (-
        (compose (D-phase-space H) C)
        (* (D C) (D-phase-space (compose H C)))))


(defn canonical-K? [C K]
  (- (compose T-func C) (* (D C) (+ T-func (D-phase-space K)))))

(defn rotating [Omega]
  (fn [state]
        (let [t (state->t state) qp (coordinate state)]
            (let [xp (ref qp 0) yp (ref qp 1) zp (ref qp 2)]
                 (up
                      (-
                          (* (cos (* Omega t)) xp)
                          (* (sin (* Omega t)) yp))
                      (+
                          (* (sin (* Omega t)) xp)
                          (* (cos (* Omega t)) yp))
                      zp)))))

(defn C-rotating [Omega] (F->CH (rotating Omega)))

(def H-arbitrary
  (literal-function
       'H
       '(->
                          (UP
                              Real (UP Real Real Real)
                              (DOWN Real Real Real))
                          Real)))


((canonical-H? (C-rotating 'Omega) H-arbitrary)
  (up
                                                  't (up 'xp 'yp 'zp)
                                                  (down
                                                      'pp_x 'pp_y 'pp_z)))

((F->K (rotating 'Omega))
  (up
                            't (up 'xp 'yp 'zp) (down 'pp_x 'pp_y 'pp_z)))

(defn K [Omega]
  (fn [s]
        (let [qp (coordinate s) pprint (momentum s)]
            (let [xp (ref qp 0)
                     yp (ref qp 1)
                     ppx (ref pprint 0)
                     ppy (ref pprint 1)]
                 (* -1 Omega (- (* xp ppy) (* yp ppx)))))))

((canonical-K? (C-rotating 'Omega) (K 'Omega))
  (up
                                                 't (up 'xp 'yp 'zp)
                                                 (down
                                                     'pp_x 'pp_y 'pp_z)))

;; --- §5.2.2 — Abstracting the Canonical Condition ---

;; (book p. 351)
(defn J-func [DHs] (up 0 (ref DHs 2) (- (ref DHs 1))))


(defn canonical-transform? [C]
  (fn [s]
        (let [J ((D J-func) (compatible-shape s)) DCs ((D C) s)]
            (- J (* DCs J (transpose DCs s))))))


;; --- Examples ---

;; (book p. 351)
((canonical-transform? (polar-canonical 'alpha)) (up 't 'theta 'I))
;;=> (up (up 0 0 0) (up 0 0 0) (up 0 0 0))

;; (book p. 352)
(defn a-non-canonical-transform [state]
  (let [t (state->t state)
            theta (coordinate state)
            p (momentum state)]
        (let [x (* p (sin theta)) p_x (* p (cos theta))]
             (up t x p_x))))


((canonical-transform? a-non-canonical-transform) (up 't 'theta 'p))
;;=> (up (up 0 0 0) (up 0 0 (+ -1 p)) (up 0 (+ 1 (* -1 p)) 0))


;; --- Symplectic matrices ---

;; (book p. 353)
(let [s (up 't (up 'x 'y) (down 'px 'py))
      s* (compatible-shape s)
      J ((D J-func) s*)]
  (s->m s* J s*))
;;=> (matrix-by-rows
;;     (list 0 0 0 0 0)
;;     (list 0 0 0 1 0)
;;     (list 0 0 0 0 1)
;;     (list 0 -1 0 0 0)
;;     (list 0 0 -1 0 0))

;; (book p. 354)
(def C-general
  (literal-function
       'C
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real))
                          (UP Real (UP Real Real) (DOWN Real Real)))))

;; (book p. 354)
(defn C-simple-time [s]
  (let [cs (C-general s)]
        (up
             ((literal-function 'tau) (state->t s)) (coordinate cs)
             (momentum cs))))

;; (book p. 354)
(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (row (s->m s* ((canonical-transform? C-simple-time) s) s*) 0))
;;=> (up 0 0 0 0 0)

;; (book p. 354)
(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (column (s->m s* ((canonical-transform? C-simple-time) s) s*) 0))
;;=> (up 0 0 0 0 0)

;; (book p. 355)
(defn symplectic-matrix? [M]
  (let [two-n (dimension M)]
        (let [J (symplectic-unit (quot two-n 2))]
             (- J (* M J (transpose M))))))

;; (book p. 355)
;; (Pedagogical redef of `symplectic-transform?` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn symplectic-transform? [C]
;;   (fn [s] (symplectic-matrix? (qp-submatrix ((D-as-matrix C) s)))))

;; (book p. 356)
(defn F [s]
  ((literal-function
         'F '(-> (X Real (UP Real Real)) (UP Real Real)))
        (state->t
                                                                                     s)
        (coordinate
                                                                                     s)))


((symplectic-transform? (F->CH F)) (up 't (up 'x 'y) (down 'px 'py)))
;;=> (matrix-by-rows
;;     (list 0 0 0 0)
;;     (list 0 0 0 0)
;;     (list 0 0 0 0)
;;     (list 0 0 0 0))

;; (book p. 356)
(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (-
       (qp-submatrix (s->m s* ((canonical-transform? C-general) s) s*))
       ((symplectic-transform? C-general) s)))
;;=> (matrix-by-rows
;;     (list 0 0 0 0)
;;     (list 0 0 0 0)
;;     (list 0 0 0 0)
;;     (list 0 0 0 0))"
    "SICM 5.8 Projects"
    ";; ===========================================
;; SICM §5.8 — Projects
;; Chapter 5 — Canonical Transformations
;; https://tgvaughan.github.io/sicm/chapter005.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[C-general C-rotating C-simple-time D-as-matrix D-phase-space F F->CH F->K H-arbitrary H-central H-free H-harmonic H-prime J-func K T-func a-non-canonical-transform canonical-H? canonical-K? canonical-transform? canonical? omega polar-canonical qp-submatrix rotating symplectic-matrix? symplectic-transform? translating]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 5 ---

(defn F->CH [F]
  (fn [state]
        (up
            (state->t state) (F state)
            (solve-linear-right
                (momentum state) (((partial 1) F) state)))))

(defn H-central [m V]
  (fn [state]
        (let [x (coordinate state) p (momentum state)]
            (+ (/ (square p) (* 2 m)) (V (sqrt (square x)))))))

(simplify
  ((compose (H-central 'm (literal-function 'V)) (F->CH p->r))
            (up
                                                                         't
                                                                         (up
                                                                             'r
                                                                             'phi)
                                                                         (down
                                                                             'p_r
                                                                             'p_phi))))

(defn F->K [F]
  (fn [state]
        (-
            (*
               (solve-linear-right
                  (momentum state) (((partial 1) F) state))
               (((partial 0) F) state)))))

(defn translating [v]
  (fn [state] (+ (coordinate state) (* v (state->t state)))))

((F->K (translating (up 'v↑x 'v↑y 'v↑z)))
  (up
                                            't (up 'x 'y 'z)
                                            (down 'p_x 'p_y 'p_z)))

(defn H-free [m] (fn [s] (/ (square (momentum s)) (* 2 m))))

(def H-prime
  (+
       (compose (H-free 'm) (F->CH (translating (up 'v↑x 'v↑y 'v↑z))))
       (F->K (translating (up 'v↑x 'v↑y 'v↑z)))))

(H-prime
  (up
           't (up 'xprime 'yprime 'zprime)
           (down 'pprime_x 'pprime_y 'pprime_z)))

(defn canonical? [C H Hprime]
  (-
        (compose (Hamiltonian->state-derivative H) C)
        (* (D C) (Hamiltonian->state-derivative Hprime))))

;; (Pedagogical redef of `polar-canonical` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn polar-canonical [alpha]
;;   (fn [state]
;;         (let [t (state->t state)
;;                 theta (coordinate state)
;;                 I (momentum state)]
;;             (let [x (* (sqrt (/ (* 2 I) alpha)) (sin theta))
;;                      p_x (* (sqrt (* 2 alpha I)) (cos theta))]
;;                  (up t x p_x)))))

(defn H-harmonic [m k]
  (fn [s]
        (+
            (/ (square (momentum s)) (* 2 m))
            (* 1/2 k (square (coordinate s))))))


((canonical?
   (polar-canonical 'alpha) (H-harmonic 'm 'k)
   (compose (H-harmonic 'm 'k) (polar-canonical 'alpha)))
  (up
                                                                                                                        't
                                                                                                                        'theta
                                                                                                                        'I))

(defn T-func [s]
  (up 1 (zero-like (coordinate s)) (zero-like (momentum s))))


(defn D-phase-space [H]
  (fn [s] (up 0 (((partial 2) H) s) (- (((partial 1) H) s)))))

(defn canonical-H? [C H]
  (-
        (compose (D-phase-space H) C)
        (* (D C) (D-phase-space (compose H C)))))


(defn canonical-K? [C K]
  (- (compose T-func C) (* (D C) (+ T-func (D-phase-space K)))))

(defn rotating [Omega]
  (fn [state]
        (let [t (state->t state) qp (coordinate state)]
            (let [xp (ref qp 0) yp (ref qp 1) zp (ref qp 2)]
                 (up
                      (-
                          (* (cos (* Omega t)) xp)
                          (* (sin (* Omega t)) yp))
                      (+
                          (* (sin (* Omega t)) xp)
                          (* (cos (* Omega t)) yp))
                      zp)))))

(defn C-rotating [Omega] (F->CH (rotating Omega)))

(def H-arbitrary
  (literal-function
       'H
       '(->
                          (UP
                              Real (UP Real Real Real)
                              (DOWN Real Real Real))
                          Real)))


((canonical-H? (C-rotating 'Omega) H-arbitrary)
  (up
                                                  't (up 'xp 'yp 'zp)
                                                  (down
                                                      'pp_x 'pp_y 'pp_z)))

((F->K (rotating 'Omega))
  (up
                            't (up 'xp 'yp 'zp) (down 'pp_x 'pp_y 'pp_z)))

(defn K [Omega]
  (fn [s]
        (let [qp (coordinate s) pprint (momentum s)]
            (let [xp (ref qp 0)
                     yp (ref qp 1)
                     ppx (ref pprint 0)
                     ppy (ref pprint 1)]
                 (* -1 Omega (- (* xp ppy) (* yp ppx)))))))

((canonical-K? (C-rotating 'Omega) (K 'Omega))
  (up
                                                 't (up 'xp 'yp 'zp)
                                                 (down
                                                     'pp_x 'pp_y 'pp_z)))

(defn J-func [DHs] (up 0 (ref DHs 2) (- (ref DHs 1))))


(defn canonical-transform? [C]
  (fn [s]
        (let [J ((D J-func) (compatible-shape s)) DCs ((D C) s)]
            (- J (* DCs J (transpose DCs s))))))

((canonical-transform? (polar-canonical 'alpha)) (up 't 'theta 'I))

(defn a-non-canonical-transform [state]
  (let [t (state->t state)
            theta (coordinate state)
            p (momentum state)]
        (let [x (* p (sin theta)) p_x (* p (cos theta))]
             (up t x p_x))))


((canonical-transform? a-non-canonical-transform) (up 't 'theta 'p))

(let [s (up 't (up 'x 'y) (down 'px 'py))
      s* (compatible-shape s)
      J ((D J-func) s*)]
  (s->m s* J s*))

(def C-general
  (literal-function
       'C
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real))
                          (UP Real (UP Real Real) (DOWN Real Real)))))

(defn C-simple-time [s]
  (let [cs (C-general s)]
        (up
             ((literal-function 'tau) (state->t s)) (coordinate cs)
             (momentum cs))))

(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (row (s->m s* ((canonical-transform? C-simple-time) s) s*) 0))

(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (column (s->m s* ((canonical-transform? C-simple-time) s) s*) 0))

(defn symplectic-matrix? [M]
  (let [two-n (dimension M)]
        (let [J (symplectic-unit (quot two-n 2))]
             (- J (* M J (transpose M))))))

;; (Pedagogical redef of `symplectic-transform?` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn symplectic-transform? [C]
;;   (fn [s] (symplectic-matrix? (qp-submatrix ((D-as-matrix C) s)))))

(defn F [s]
  ((literal-function
         'F '(-> (X Real (UP Real Real)) (UP Real Real)))
        (state->t
                                                                                     s)
        (coordinate
                                                                                     s)))


((symplectic-transform? (F->CH F)) (up 't (up 'x 'y) (down 'px 'py)))

(let [s (up 't (up 'x 'y) (down 'p_x 'p_y)) s* (compatible-shape s)]
  (-
       (qp-submatrix (s->m s* ((canonical-transform? C-general) s) s*))
       ((symplectic-transform? C-general) s)))

(defn omega [zeta1 zeta2]
  (-
        (* (momentum zeta2) (coordinate zeta1))
        (* (momentum zeta1) (coordinate zeta2))))

(defn F [s]
  ((literal-function
         'F '(-> (X Real (UP Real Real)) (UP Real Real)))
        (state->t
                                                                                     s)
        (coordinate
                                                                                     s)))


(let [s (up 't (up 'x 'y) (down 'p_x 'p_y))
      zeta1 (up 0 (up 'dx1 'dy1) (down 'dp1_x 'dp1_y))
      zeta2 (up 0 (up 'dx2 'dy2) (down 'dp2_x 'dp2_y))]
  (let [DCs ((D (F->CH F)) s)]
       (- (omega zeta1 zeta2) (omega (* DCs zeta1) (* DCs zeta2)))))

0

;; --- §5.8 — Projects ---

;; (book p. 410)
;; (Pedagogical redef of `qp-submatrix` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn qp-submatrix [m]
;;   (m:submatrix m 1 (m:num-rows m) 1 (m:num-cols m)))

;; (book p. 410)
;; (Pedagogical redef of `D-as-matrix` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn D-as-matrix [F]
;;   (fn [s] (s->m (compatible-shape (F s)) ((D F) s) s)))"
    "SICM 6.4 Lie Series"
    ";; ===========================================
;; SICM §6.4 — Lie Series
;; Chapter 6 — Canonical Evolution
;; https://tgvaughan.github.io/sicm/chapter006.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[C->Cp H->Hp H-harmonic Lie-derivative Lie-transform shift-t solution]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 6 ---

(defn C->Cp [C]
  (fn [delta-t] (compose (C delta-t) (shift-t (- delta-t)))))

(defn shift-t [delta-t]
  (fn [state]
        (up
            (+ (state->t state) delta-t) (coordinate state)
            (momentum state))))

(defn H->Hp [delta-t] (fn [H] (compose H (shift-t (- delta-t)))))

(defn solution [alpha omega omega0]
  (fn [state0]
        (fn [t]
            (((C* alpha omega omega0) (- t (state->t state0))) state0))))

;; --- §6.4 — Lie Series ---

;; (book p. 444)
(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) (literal-function 'f)) 't)))
;;=> (f t)
;;   (* ((D f) t) epsilon)
;;   (* 1/2 (((expt D 2) f) t) (expt epsilon 2))
;;   (* 1/6 (((expt D 3) f) t) (expt epsilon 3))
;;   (* 1/24 (((expt D 4) f) t) (expt epsilon 4))
;;   (* 1/120 (((expt D 5) f) t) (expt epsilon 5))
;;   ...

;; (book p. 444)
(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) sin) 0)))
;;=> 0
;;   epsilon
;;   0
;;   (* -1/6 (expt epsilon 3))
;;   0
;;   (* 1/120 (expt epsilon 5))
;;   ...

;; (book p. 445)
(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) (fn [x] (sqrt (+ x 1)))) 0)))
;;=> 1
;;   (* 1/2 epsilon)
;;   (* -1/8 (expt epsilon 2))
;;   (* 1/16 (expt epsilon 3))
;;   (* -5/128 (expt epsilon 4))
;;   (* 7/256 (expt epsilon 5))
;;   ...


;; --- Computing Lie series ---

;; (book p. 448)
;; (Pedagogical redef of `Lie-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lie-derivative [H] (fn [F] (Poisson-bracket F H)))

;; (book p. 448)
;; (Pedagogical redef of `Lie-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lie-transform [H t] (exp (* t (Lie-derivative H))))

;; (book p. 448)
(defn H-harmonic [m k]
  (fn [state]
        (+
            (/ (square (momentum state)) (* 2 m))
            (* 1/2 k (square (coordinate state))))))

;; (book p. 449)
(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) coordinate)
              (up
                                                                   0 'x0
                                                                   'p0))))
;;=> x0
;;   (/ (* dt p0) m)
;;   (/ (* -1/2 (expt dt 2) k x0) m)
;;   (/ (* -1/6 (expt dt 3) k p0) (expt m 2))
;;   (/ (* 1/24 (expt dt 4) (expt k 2) x0) (expt m 2))
;;   (/ (* 1/120 (expt dt 5) (expt k 2) p0) (expt m 3))
;;   ...

;; (book p. 449)
(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) momentum)
              (up
                                                                 0 'x0
                                                                 'p0))))
;;=> p0
;;   (* -1 dt k x0)
;;   (/ (* -1/2 (expt dt 2) k p0) m)
;;   (/ (* 1/6 (expt dt 3) (expt k 2) x0) m)
;;   (/ (* 1/24 (expt dt 4) (expt k 2) p0) (expt m 2))
;;   (/ (* -1/120 (expt dt 5) (expt k 3) x0) (expt m 2))
;;   ...

;; (book p. 449)
(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) (H-harmonic 'm 'k))
              (up
                                                                           0
                                                                           'x0
                                                                           'p0))))
;;=> (/ (+ (* 1/2 k m (expt x0 2)) (* 1/2 (expt p0 2))) m)
;;   0
;;   0
;;   0
;;   0
;;   0
;;   ...

;; (book p. 449)
(run!
  (fn [x] (println (simplify x)))
  (take
        4
        (((Lie-transform
                (H-central-polar 'm (literal-function 'U)) 'dt)
               coordinate)
              (up
                                                                                                                          0
                                                                                                                          (up
                                                                                                                              'r_0
                                                                                                                              'phi_0)
                                                                                                                          (down
                                                                                                                              'p_r_0
                                                                                                                              'p_phi_0)))))
;;=> (up r_0 phi_0)
;;   (up (/ (* dt p_r_0) m)
;;       (/ (* dt p_phi_0) (* m (expt r_0 2))))
;;   (up
;;     (+ (/ (* -1/2 ((D U) r_0) (expt dt 2)) m)
;;        (/ (* 1/2 (expt dt 2) (expt p_phi_0 2))
;;           (* (expt m 2) (expt r_0 3))))
;;     (/ (* -1 (expt dt 2) p_phi_0 p_r_0)
;;        (* (expt m 2) (expt r_0 3))))
;;   (up
;;     (+ (/ (* -1/6 (((expt D 2) U) r_0) (expt dt 3) p_r_0)
;;           (expt m 2))
;;        (/ (* -1/2 (expt dt 3) (expt p_phi_0 2) p_r_0)
;;           (* (expt m 3) (expt r_0 4)))))
;;   (+ (/ (* 1/3 ((D U) r_0) (expt dt 3) p_phi_0)
;;         (* (expt m 2) (expt r_0 3))))
;;   (/ (* -1/3 (expt dt 3) (expt p_phi_0 3))
;;      (* (expt m 3) (expt r_0 6)))
;;   (/ (* (expt dt 3) p_phi_0 (expt p_r_0 2))
;;      (* (expt m 3) (expt_r_0 4)))
;;   ..."
    "SICM 6.7 Projects"
    ";; ===========================================
;; SICM §6.7 — Projects
;; Chapter 6 — Canonical Evolution
;; https://tgvaughan.github.io/sicm/chapter006.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[C->Cp H->Hp H-harmonic HH-collector Lie-derivative Lie-derivative-procedure Lie-transform shift-t solution]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))

;; --- Prerequisites from earlier sections of Chapter 6 ---

(defn C->Cp [C]
  (fn [delta-t] (compose (C delta-t) (shift-t (- delta-t)))))

(defn shift-t [delta-t]
  (fn [state]
        (up
            (+ (state->t state) delta-t) (coordinate state)
            (momentum state))))

(defn H->Hp [delta-t] (fn [H] (compose H (shift-t (- delta-t)))))

(defn solution [alpha omega omega0]
  (fn [state0]
        (fn [t]
            (((C* alpha omega omega0) (- t (state->t state0))) state0))))

(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) (literal-function 'f)) 't)))

(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) sin) 0)))

(run!
  (fn [x] (println (simplify x)))
  (take 6 (((exp (* 'epsilon D)) (fn [x] (sqrt (+ x 1)))) 0)))

;; (Pedagogical redef of `Lie-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lie-derivative [H] (fn [F] (Poisson-bracket F H)))

;; (Pedagogical redef of `Lie-transform` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn Lie-transform [H t] (exp (* t (Lie-derivative H))))

(defn H-harmonic [m k]
  (fn [state]
        (+
            (/ (square (momentum state)) (* 2 m))
            (* 1/2 k (square (coordinate state))))))

(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) coordinate)
              (up
                                                                   0 'x0
                                                                   'p0))))

(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) momentum)
              (up
                                                                 0 'x0
                                                                 'p0))))

(run!
  (fn [x] (println (simplify x)))
  (take
        6
        (((Lie-transform (H-harmonic 'm 'k) 'dt) (H-harmonic 'm 'k))
              (up
                                                                           0
                                                                           'x0
                                                                           'p0))))

(run!
  (fn [x] (println (simplify x)))
  (take
        4
        (((Lie-transform
                (H-central-polar 'm (literal-function 'U)) 'dt)
               coordinate)
              (up
                                                                                                                          0
                                                                                                                          (up
                                                                                                                              'r_0
                                                                                                                              'phi_0)
                                                                                                                          (down
                                                                                                                              'p_r_0
                                                                                                                              'p_phi_0)))))

;; --- §6.7 — Projects ---

;; (book p. 455)
(defn HH-collector [win advance E dt sec-eps n]
  (fn [x y done fail]
        (let [collector (default-collector monitor pmap n)]
            (letfn [(monitor
                         [last-crossing-state state]
                         (plot-point
                                  win
                                  (ref
                                              (coordinate
                                                   last-crossing-state)
                                              1)
                                  (ref
                                              (momentum
                                                   last-crossing-state)
                                              1)))
                       (pmap
                         [x y cont fail]
                         (find-next-crossing
                               y advance dt sec-eps cont))]
                 (cond
                        (and (up? x) (up? y)) (collector x y done fail)
                        (and (number? x) (number? y))
                        (let [initial-state (section->state E x y)]
                              (if (not initial-state)
                                   (fail)
                                   (collector
                                       initial-state initial-state done
                                       fail)))
                        :else
                        (throw
                              (ex-info
                                     \"bad input to HH-collector\" {})))))))

;; (book p. 455)
(explore-map
  win (HH-collector win 1st-order-map 0.125 0.1 1.e-10 1000) false)

;; (book p. 455)
(defn Lie-derivative-procedure [H] (fn [F] (Poisson-bracket F H)))

;; (Pedagogical redef of `Lie-derivative` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def Lie-derivative
;;   (make-operator Lie-derivative-procedure 'Lie-derivative))"
    "SICM 8 Appendix: Scheme"
    ";; ===========================================
;; SICM §8 — Appendix: Scheme
;; Chapter 8 — Appendix: Scheme
;; https://tgvaughan.github.io/sicm/chapter008.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[a-list a-vector abs another-list c1 c2 c3 c4 compose f factorial make-collector make-counter pi square sum?]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))


;; --- Lambda expressions ---

;; (book p. 497)
(fn [x] (* x x))

;; (book p. 497)
((fn [x] (* x x)) 4)
;;=> 16


;; --- Definitions ---

;; (book p. 499)
;; (Pedagogical redef of `pi` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (def pi 3.141592653589793)

;; (Pedagogical redef of `square` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn square [x] (* x x))

;; (book p. 499)
;; (Pedagogical redef of `square` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn square [x] (* x x))

;; (book p. 499)
;; (Pedagogical redef of `compose` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn compose [f g] (fn [x] (f (g x))))


((compose square sin) 2)
;;=> .826821810431806

;; (book p. 499)
(square (sin 2))
;;=> .826821810431806

;; (book p. 499)
;; (Pedagogical redef of `compose` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn compose [f g] (fn [x] (f (g x))))

;; (Pedagogical redef of `compose` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn compose [f g] (fn [x] (f (g x))))


;; --- Conditionals ---

;; (book p. 499)
;; (Pedagogical redef of `abs` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn abs [x] (cond (< x 0) (- x) (= x 0) x (> x 0) x))

;; (book p. 499)
(cond predicate-1 consequent-1  nil predicate-n consequent-n)

;; (book p. 501)
;; (Pedagogical redef of `abs` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn abs [x] (if (< x 0) (- x) x))


;; --- Recursive procedures ---

;; (book p. 501)
;; (Pedagogical redef of `factorial` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn factorial [n] (if (= n 0) 1 (* n (factorial (- n 1)))))


(factorial 6)
;;=> 720

;; (book p. 501)
(factorial 40)
;;=> 815915283247897734345611269596115894272000000000


;; --- Local names ---

;; (book p. 501)
(defn f [radius]
  (let [area (* 4 pi (square radius))
            volume (* 4/3 pi (cube radius))]
        (/ volume area)))


(f 3)
;;=> 1

;; (book p. 502)
(let [variable-1 expression-1 variable-n expression-n] body)

;; (book p. 502)
;; (Pedagogical redef of `factorial` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn factorial [n]
;;   (letfn [(factlp
;;                 [count answer]
;;                 (if (> count n)
;;                         answer
;;                         (factlp (+ count 1) (* count answer))))]
;;         (factlp 1 1)))


(factorial 6)
;;=> 720


;; --- Compound data—lists and vectors ---

;; (book p. 503)
(def a-list (list 6 946 8 356 12 620))


a-list
;;=> (6 946 8 356 12 620)

;; (book p. 503)
(nth a-list 3)
;;=> 356

;; (book p. 503)
(nth a-list 0)
;;=> 6

;; (book p. 503)
(first a-list)
;;=> 6

;; (book p. 503)
(rest a-list)
;;=> (946 8 356 12 620)

;; (book p. 503)
(first (rest a-list))
;;=> 946

;; (book p. 503)
(def another-list (cons 32 (rest a-list)))


another-list
;;=> (32 946 8 356 12 620)

;; (book p. 503)
(first (rest another-list))
;;=> 946

;; (book p. 504)
(def a-vector (vector 37 63 49 21 88 56))


a-vector
;;=> #(37 63 49 21 88 56)

;; (book p. 504)
(nth a-vector 3)
;;=> 21

;; (book p. 504)
(nth a-vector 0)
;;=> 37


;; --- Symbols ---

;; (book p. 505)
(defn sum? [expression]
  (and (seq? expression) (identical? (first expression) '+)))


(sum? '(+ 3 a))

;; (book p. 505)
(sum? '(* 3 a))


;; --- Effects ---

;; (book p. 505)
;; (Pedagogical redef of `factorial` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn factorial [n]
;;   (letfn [(factlp
;;                 [count answer] (write-line (list count answer))
;;                 (if (> count n)
;;                         answer
;;                         (factlp (+ count 1) (* count answer))))]
;;         (factlp 1 1)))

;; (book p. 506)
(factorial 6)
;;=> (1 1)
;;   (2 1)
;;   (3 2)
;;   (4 6)
;;   (5 24)
;;   (6 120)
;;   (7 720)
;;   720

;; (book p. 506)
(defn make-counter []
  (let [count (volatile! 0)]
        (fn [] (vreset! count (+ (deref count) 1)) (deref count))))

;; (book p. 506)
(def c1 (make-counter))

(def c2 (make-counter))

;; (book p. 506)
(c1)
;;=> 1

;; (book p. 506)
(c1)
;;=> 2

;; (book p. 506)
(c2)
;;=> 1

;; (book p. 506)
(c1)
;;=> 3

;; (book p. 506)
(c2)
;;=> 2

;; (book p. 506)
(defn make-collector []
  (let [lst (volatile! '())]
        [(fn [new] (vreset! lst (cons new (deref lst))) new)
            (fn [] (deref lst))]))

;; (book p. 506)
(def c3 (make-collector))

(def c4 (make-collector))


((nth c3 0) 42)
;;=> 42

;; (book p. 506)
((nth c4 0) 'jerry)
;;=> jerry

;; (book p. 506)
((nth c3 0) 28)
;;=> 28

;; (book p. 506)
((nth c3 0) 14)
;;=> 14

;; (book p. 506)
((nth c4 0) 'jack)
;;=> jack

;; (book p. 506)
((nth c3 1))
;;=> (14 28 42)

;; (book p. 506)
((nth c4 1))
;;=> (jack jerry)"
    "SICM 9 Appendix: Our Notation"
    ";; ===========================================
;; SICM §9 — Appendix: Our Notation
;; Chapter 9 — Appendix: Our Notation
;; https://tgvaughan.github.io/sicm/chapter009.html
;; ===========================================
;; Self-contained: earlier-chapter prerequisites are
;; inlined below.

(doseq [s '[H d derivative-of-sine f g h helix p s v]]
  (when-not (ns-resolve *ns* s) (intern *ns* s)))


;; --- Functions ---

;; (book p. 510)
;; (Pedagogical redef of `d` — kept as a comment so the page
;;  doesn't collide with the same name `:refer`'d in from emmy.env
;;  or clojure.core. Calls below resolve to that referred binding.)
;; (defn d [x1 y1 x2 y2] (sqrt (+ (square (- x2 x1)) (square (- y2 y1)))))

;; (book p. 510)
(def h (compose cube sin))


(h 2)
;;=> .7518269446689928

;; (book p. 510)
(cube (sin 2))
;;=> .7518269446689928

;; (book p. 511)
(def g (* cube sin))


(g 2)
;;=> 7.274379414605454


;; --- Symbolic values ---

;; (book p. 511)
((compose cube sin) 'a)
;;=> (expt (sin a) 3)

;; (book p. 511)
((- (+ (square sin) (square cos)) 1) 'a)
;;=> 0

;; (book p. 512)
((literal-function 'f) 'x)
;;=> (f x)

;; (book p. 512)
((compose (literal-function 'f) (literal-function 'g)) 'x)
;;=> (f (g x))

;; (book p. 512)
(def g (literal-function 'g '(-> (X Real Real) Real)))

(g 'x 'y)
;;=> (g x y)


;; --- Tuples ---

;; (book p. 513)
(def v (up 'v↑0 'v↑1 'v↑2))


v
;;=> (up v↑0 v↑1 v↑2)

;; (book p. 513)
(def p (down 'p_0 'p_1 'p_2))


p
;;=> (down p_0 p_1 p_2)

;; (book p. 513)
(def s (up 't (up 'x 'y) (down 'p_x 'p_y)))

;; (book p. 514)
((component 0 1) (up (up 'a 'b) (up 'c 'd)))
;;=> b

;; (book p. 514)
(ref (up 'a 'b 'c) 1)
;;=> b

;; (book p. 514)
(ref (up (up 'a 'b) (up 'c 'd)) 0 1)
;;=> b


;; --- Derivatives ---

;; (book p. 516)
(def derivative-of-sine (D sin))


(derivative-of-sine 'x)
;;=> (cos x)

;; (book p. 517)
(((* 5 D) cos) 'x)
;;=> (* -5 (sin x))

;; (book p. 517)
(((* (+ D I) (- D I)) (literal-function 'f)) 'x)
;;=> (+ (((expt D 2) f) x) (* -1 (f x)))


;; --- Derivatives of functions of multiple arguments ---

;; (book p. 518)
((D g) 'x 'y)
;;=> (down (((partial 0) g) x y) (((partial 1) g) x y))

;; (book p. 519)
(defn h [s] (g (ref s 0) (ref s 1)))


(h (up 'x 'y))
;;=> (g x y)

;; (book p. 519)
((D g) 'x 'y)
;;=> (down (((partial 0) g) x y) (((partial 1) g) x y))

;; (book p. 519)
((D h) (up 'x 'y))
;;=> (down (((partial 0) g) x y) (((partial 1) g) x y))

;; (book p. 521)
(def H
  (literal-function
       'H
       '(->
                          (UP Real (UP Real Real) (DOWN Real Real)) Real)))


(H s)
;;=> (H (up t (up x y) (down p_x p_y)))

;; (book p. 521)
((D H) s)
;;=> (down
;;     (((partial 0) H) (up t (up x y) (down p_x p_y)))
;;     (down (((partial 1 0) H) (up t (up x y) (down p_x p_y)))
;;           (((partial 1 1) H) (up t (up x y) (down p_x p_y))))
;;     (up (((partial 2 0) H) (up t (up x y) (down p_x p_y)))
;;         (((partial 2 1) H) (up t (up x y) (down p_x p_y)))))


;; --- Structured results ---

;; (book p. 521)
(defn helix [t] (up (cos t) (sin t) t))

;; (book p. 521)
(def helix (up cos sin identity))

;; (book p. 522)
((D helix) 't)
;;=> (up (* -1 (sin t)) (cos t) 1)

;; (book p. 522)
(defn g [x y] (up (square (+ x y)) (cube (- y x)) (exp (+ x y))))


((D g) 'x 'y)
;;=> (down
;;     (up
;;       (+ (* 2 x) (* 2 y))
;;       (+ (* -3 (expt x 2)) (* 6 x y) (* -3 (expt y 2)))
;;       (* (exp y) (exp x)))
;;     (up
;;       (+ (* 2 x) (* 2 y))
;;       (+ (* 3 (expt x 2)) (* -6 x y) (* 3 (expt y 2)))
;;       (* (exp y) (exp x))))

;; (book p. 523)
(defn f [x y] (* (square x) (cube y)))


(defn g [x y] (up (f x y) y))


(defn h [x y] (f (f x y) y))

;; (book p. 523)
(defn f [v] (let [x (ref v 0) y (ref v 1)] (* (square x) (cube y))))


(defn g [v] (let [x (ref v 0) y (ref v 1)] (up (f v) y)))


(def h (compose f g))"))
;; --- END GENERATED SICM PAGES ---

;; array-map preserves insertion order so the dropdown renders these in
;; the order written here rather than alphabetically. The hand-curated
;; pages come first, then the per-section SICM library generated by
;; bin/build-sicm-pages.bb.
(def system-pages
  (into (array-map
          "Welcome"    basics-page
          "SICM"       sicm-page
          "Graphics"   graphics-page
          "Auto-graph" auto-graph-page
          "3D"         graphics-3d-page)
        sicm-section-pages))

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

;; --- Autocompletion -------------------------------------------------------
;; CodeMirror's @codemirror/autocomplete extension is wired in optionally
;; — index.html tries to import it and gracefully degrades if it isn't
;; vendored. To enable, run bin/vendor.sh after confirming
;; @codemirror/autocomplete@6 is in bin/vendor-cm.mjs's ENTRIES.
;;
;; The symbol list below is curated rather than scraped from emmy.env to
;; keep the suggestion menu short and useful. Add names you reach for
;; often; Emmy's full surface is huge, and dumping all of it would drown
;; the actual high-value matches.

(def ^:private completion-symbols
  ["plot" "animate" "plot-with-params" "plot-path" "plot-function"
   "plot-point" "frame" "graphics-clear" "show"
   "find-path"
   "Lagrange-equations" "Hamilton-equations" "literal-function"
   "state-advancer" "evolve"
   "L-harmonic" "L-free-particle" "H-harmonic"
   "coordinate" "velocity" "momentum" "up" "down"
   "D" "square" "cube" "simplify"
   "cos" "sin" "tan" "exp" "log" "sqrt"
   "Math/sin" "Math/cos" "Math/tan" "Math/sqrt"
   "Math/exp" "Math/log" "Math/PI" "Math/E"
   "mafs/Mafs" "mafs.coordinates/Cartesian" "mafs.plot/Parametric"
   "mathbox/MathBox" "mb/Cartesian" "mb/Axis" "mb/Interval"
   "mb/Area" "mb/Surface" "mb/Line"
   "reagent.core/atom" "reagent.core/create-class"])

(defn- completion-source [ctx]
  (let [word (.matchBefore ctx (js/RegExp. "[\\w./-]+"))]
    (when (and word
               (or (not= (.-from word) (.-to word))
                   (.-explicit ctx)))
      #js {:from    (.-from word)
           :options (clj->js (mapv (fn [n] {:label n}) completion-symbols))})))

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
                 ;; Optional: @codemirror/autocomplete. Both fields are
                 ;; nil when autocomplete isn't vendored — the editor
                 ;; just runs without suggestions in that case.
                 js/CM.autocompletion
                 (conj (js/CM.autocompletion
                        #js {:override #js [completion-source]}))
                 js/CM.completionKeymap
                 (conj (.of js/CM.keymap js/CM.completionKeymap))
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
           "\n  ;; For a 2D Lagrangian (q is an up-tuple), set q0/q1 to (up x0 y0)/"
           "\n  ;; (up x1 y1) above, then plot one component: (fn [t] ((path t) 0))."
           "\n  (plot path [t0 t1] [-1.5 1.5]))")

      (= kind :parametric-2d)
      (str (lagr-let-prelude name bindings call)
           "\n  ;; 1D Lagrangian: phase plane (q(t), q'(t)). For a 2D"
           "\n  ;; Lagrangian (q is an up-tuple), set q0/q1 to (up x0 y0)/"
           "\n  ;; (up x1 y1) above, and swap :xy for the trajectory body:"
           "\n  ;;   :xy (fn [t] [((path t) 0) ((path t) 1)])"
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


;; --- Hamiltonian detection ------------------------------------------------
;; Counterpart to the Lagrangian path. Where (L-name) Lagrangians get a
;; find-path variational solve, (H-name) Hamiltonians get a numerical ODE
;; integration via Emmy's state-advancer + Hamilton-equations. Same arg
;; conventions: 'm, 'k, … become let-bindings with default 1.0; concrete
;; args pass through verbatim.

(defn- hamiltonian-form [src]
  (when-let [m (re-find #"\(H-[\w-]+" src)]
    (let [start (.indexOf src m)]
      (when-let [end (find-balanced-paren-end src start)]
        (subs src start end)))))

(defn- hamiltonian-pattern? [src]
  (boolean (re-find #"\(H-[\w-]+" src)))

(defn- ham-let-prelude
  "(let […] for the Hamiltonian 1D-trajectory kinds. Defines an advancer
   that integrates Hamilton's equations from (t0 q0 p0) to a given t."
  [name bindings call]
  (let [H-call    (str "(" name
                       (when (seq call) (str " " (clojure.string/join " " call)))
                       ")")
        bind-rows (concat
                   (map (fn [[n d]] (str n " " d "       ; '" n)) bindings)
                   ["t0 0.0"
                    "t1 (/ Math/PI 2)"
                    "q0 1.0"
                    "p0 0.0"
                    (str "H        " H-call)
                    ";; state-advancer + Hamilton-equations gives a stepper:"
                    ";;   (advancer initial-state t-final) → final state."
                    ";; Verify your Emmy build exposes both at user-namespace level."
                    "advancer ((state-advancer Hamilton-equations) H)"])]
    (str "(let [" (clojure.string/join "\n      " bind-rows) "]")))

(defn- hamiltonian-template
  "Build the kind-specific Hamilton-equations template for an (H-…) source.
   Mirrors lagrangian-template; inner sample fns re-integrate from t0 each
   call (simple and slow — pre-computing an interpolation table in the let
   would be faster for many-sample plots)."
  ([kind src] (hamiltonian-template kind src nil))
  ([kind src opts]
   (let [{:keys [name args]}     (parse-lagrangian (hamiltonian-form src))
         {:keys [bindings call]} (arg-bindings args)
         sweep-name              (:sweep opts)
         H-call    (str "(" name
                        (when (seq call) (str " " (clojure.string/join " " call)))
                        ")")]
     (cond
       (= kind :plot)
       (str (ham-let-prelude name bindings call)
            "\n  (plot (fn [t] (nth (advancer (up t0 q0 p0) t) 1))"
            "\n        [t0 t1] [-1.5 1.5]))")

       (= kind :parametric-2d)
       (str (ham-let-prelude name bindings call)
            "\n  [mafs/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}"
            "\n   [mafs.coordinates/Cartesian]"
            "\n   [mafs.plot/Parametric"
            "\n    {:t  [t0 t1]"
            "\n     :xy (fn [t] (let [s (advancer (up t0 q0 p0) t)]"
            "\n                   [(nth s 1) (nth s 2)]))}]])")

       (= kind :parametric-3d)
       (str (ham-let-prelude name bindings call)
            "\n  [mathbox/MathBox"
            "\n   {:container {:style {:height \"400px\" :width \"100%\"}}}"
            "\n   [mb/Cartesian {:range [[t0 t1] [-1.5 1.5] [-1.5 1.5]] :scale [1 1 1]}"
            "\n    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]"
            "\n    [mb/Interval"
            "\n     {:range [t0 t1] :width 256 :channels 3"
            "\n      :expr (fn [emit t i time]"
            "\n              (let [s (advancer (up t0 q0 p0) t)]"
            "\n                (emit t (nth s 1) (nth s 2))))}]"
            "\n    [mb/Line {:width 4 :color \"#3090ff\"}]]])")

       (and (= kind :surface) (empty? bindings))
       (hamiltonian-template :plot src)

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
                    "p0 0.0"
                    (str ";; Sweep '" swept " over the surface's y axis. Each row pre-computes")
                    (str ";; a stepper for one " swept " value; re-integrating per surface sample.")
                    (str swept "-min 0.5")
                    (str swept "-max 5.0")
                    (str swept "-n   8")
                    (str swept "s    (mapv #(+ " swept "-min (* (/ (- " swept "-max " swept "-min) (dec " swept "-n)) %)) (range " swept "-n))")
                    (str "advs  (mapv (fn [" swept "] ((state-advancer Hamilton-equations) " H-call ")) " swept "s)")])]
         (str "(let [" (clojure.string/join "\n      " rows) "]"
              "\n  [mathbox/MathBox"
              "\n   {:container {:style {:height \"400px\" :width \"100%\"}}}"
              "\n   [mb/Cartesian {:range [[t0 t1] [" swept "-min " swept "-max] [-1.5 1.5]] :scale [1 1 1]}"
              "\n    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]"
              "\n    [mb/Area"
              "\n     {:rangeX [t0 t1] :rangeY [" swept "-min " swept "-max]"
              "\n      :width 64 :height " swept "-n :channels 3"
              "\n      :expr (fn [emit t " swept " i j time]"
              "\n              (emit t " swept " (nth ((nth advs j) (up t0 q0 p0) t) 1)))}]"
              "\n    [mb/Surface {:shaded true :color \"#3090ff\"}]]])"))

       (and (= kind :animate) (empty? bindings))
       (hamiltonian-template :plot src)

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
              "\n      p0 0.0"
              "\n      ;; memoize the stepper on the slider tuple so the ODE setup"
              "\n      ;; happens once per (m k …) combination."
              "\n      memo-adv (memoize"
              "\n                  (fn [" names-str "]"
              "\n                    ((state-advancer Hamilton-equations) " H-call ")))]"
              "\n  (plot-with-params"
              "\n    (fn [{:keys [" names-str "]} t]"
              "\n      (nth ((memo-adv " names-str ") (up t0 q0 p0) t) 1))"
              "\n    {" schema "}"
              "\n    [t0 t1] [-1.5 1.5]))"))))))


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

(defn- hamiltonian-defn?
  "Same idea as lagrangian-defn?, for the H- prefix. Routes through the
   Hamiltonian template (state-advancer + Hamilton-equations) instead of
   the generic defn wrap."
  [src]
  (and (defn-form? src)
       (some-> (defn-name src) (clojure.string/starts-with? "H-"))))

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
         template (kind->template kind)
         synth-call-from-defn
         (fn []
           (let [name (defn-name src)
                 args (defn-args src)]
             (str "(" name
                  (when (seq args)
                    (str " " (clojure.string/join " "
                                                  (map #(str "'" %) args))))
                  ")")))]
     (cond
       (lagrangian-pattern? src)
       (lagrangian-template kind src opts)

       (hamiltonian-pattern? src)
       (hamiltonian-template kind src opts)

       (lagrangian-defn? src)
       (str src "\n\n" (lagrangian-template kind (synth-call-from-defn) opts))

       (hamiltonian-defn? src)
       (str src "\n\n" (hamiltonian-template kind (synth-call-from-defn) opts))

       (defn-form? src)
       (str src "\n\n" (template (defn-name src)))

       :else
       (template (wrap-as-fn-of src (expected-vars-for kind)))))))

(defn- available-quoted-args
  "Names of the free symbols ('m, 'k, …) inside the first (L-…) or (H-…)
   sub-form in src, or nil if none. Used by the shelf to populate the
   Surface sweep-target picker."
  [src]
  (when-let [form (or (lagrangian-form src) (hamiltonian-form src))]
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

;; Hand-curated landing pages render first in the dropdown, in this
;; order; the autogenerated SICM section pages follow, sorted by
;; semantic section number ([1 5 2] < [1 6] < [1 12]).
(def ^:private intro-page-names
  ["Welcome" "SICM" "Graphics" "Auto-graph" "3D"])

(defn- section-version
  "Parse 'SICM 1.5.2 …' → [1 5 2]. Returns [9999] for anything that
  isn't a section page so it sorts last."
  [name]
  (if-let [[_ section] (re-matches #"^SICM (\d[\d.]*).*" name)]
    (mapv js/parseInt (clojure.string/split section #"\."))
    [9999]))

(defn- compare-versions
  "Element-wise lexicographic compare of two integer vectors. CLJS's
  default `compare` doesn't handle PersistentVector (unlike JVM), so we
  walk pairwise with the scalar `compare` that does work."
  [a b]
  (loop [a (seq a) b (seq b)]
    (cond
      (and (nil? a) (nil? b)) 0
      (nil? a) -1
      (nil? b) 1
      :else (let [c (compare (first a) (first b))]
              (if (zero? c) (recur (next a) (next b)) c)))))

(defn- ordered-system-page-names []
  (let [present (set (keys system-pages))
        intros  (filter present intro-page-names)
        rest    (->> (keys system-pages)
                     (remove (set intro-page-names))
                     (sort (fn [a b]
                             (compare-versions (section-version a)
                                               (section-version b)))))]
    (concat intros rest)))

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
        (for [n (ordered-system-page-names)]
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
     [:div.label
      (let [[_ cur-name] (:current @!pages)]
        (if cur-name (str "Result of " cur-name) "Result"))]
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
