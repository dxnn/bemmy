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

;; --- System pages: read-only templates baked into the build. Editing
;; one transparently forks it into a fresh user page so the template
;; itself stays canonical and updates whenever we ship new content.
;; array-map preserves insertion order so the dropdown renders these in
;; the order written here rather than alphabetically.
(def system-pages
  (array-map
    "Welcome"  basics-page
    "SICM"     sicm-page
    "Graphics" graphics-page
    "3D"       graphics-3d-page))

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
  (or (try (when-let [s (.getItem js/localStorage ui-storage-key)]
             (js->clj (js/JSON.parse s) :keywordize-keys true))
           (catch :default _ nil))
      {:vim-on false}))

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
      (swap! !pages assoc-in [:pages n] ";; New page\n"))
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
          ;; Wrap our Cmd-Enter binding in Prec.highest so vim mode (or any
          ;; other keymap) can't shadow it.
          user-keymap (cond->> (.of js/CM.keymap #js [eval-cmd])
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
                 js/CM.defaultExtensions   (conj js/CM.defaultExtensions)
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

(defn- insert-at-cursor! []
  (when-let [view @!view]
    (let [output (:output @!shelf)]
      (when-not (clojure.string/blank? output)
        (let [sel  (.. view -state -selection -main)
              from (.-from sel)
              to   (.-to sel)]
          (.dispatch view #js {:changes #js {:from from :to to :insert output}})
          (.focus view)
          (swap! !shelf assoc :open? false))))))

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
    [:span.tagline "BEmmy :: Emmy in the Browser"]]
   [:div.panes
    [:div.pane
     [:div.label "Code"]
     [pages-bar]
     ;; Key on vim-on AND OS theme so toggling either forces CM to
     ;; remount; CM6's vim extension and theme are baked in at editor
     ;; construction time.
     ^{:key (str "cm-" (:vim-on @!ui) "-" @!dark?)} [cm-editor]
     [shelf]
     [:div.toolbar
      [:button.action {:on-click eval!} "Evaluate"]
      [:button.btn
       {:class    (when (:open? @!shelf) "is-on")
        :on-click #(swap! !shelf update :open? not)
        :title    "Toggle SICM → Emmy translator"}
       "SICM → Emmy"]
      [:label.check
       {:title "Vim keybindings (persisted across reloads)"}
       [:input {:type      "checkbox"
                :checked   (boolean (:vim-on @!ui))
                :on-change #(swap! !ui update :vim-on not)}]
       "vim"]
      [:button.btn.share-btn
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
