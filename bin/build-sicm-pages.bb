#!/usr/bin/env bb
;; Generate SICM section pages for the BEmmy playground from the
;; pre-translated SICM corpus. Splices a `(def sicm-section-pages
;; (array-map ...))` form into public/app.cljs between BEGIN/END
;; GENERATED SICM PAGES markers. Each page is self-contained: prereqs
;; from earlier sections of the same chapter are prepended so the page
;; stands alone in the editor.

(ns build-sicm-pages
  (:require [babashka.process :refer [shell]]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def corpus-path  "test/fixtures/sicm-snippets.translated.edn")
(def app-path     "public/app.cljs")
(def begin-marker ";; --- BEGIN GENERATED SICM PAGES ---")
(def end-marker   ";; --- END GENERATED SICM PAGES ---")

(def corpus
  (->> corpus-path slurp edn/read-string (sort-by :idx)))

(defn section-key [{:keys [chapter section]}] [chapter section])

(defn group-sections
  "Returns a vector of {:chapter :section :section-title :chapter-title
  :source :entries [...]}, in textbook (idx) order."
  [entries]
  (let [partitions (partition-by section-key entries)]
    (mapv (fn [es]
            (let [{:keys [chapter section section-title chapter-title source]}
                  (first es)]
              {:chapter chapter
               :section section
               :section-title section-title
               :chapter-title chapter-title
               :source source
               :entries (vec es)
               :first-idx (-> es first :idx)}))
          partitions)))

(def sections (group-sections corpus))

(defn chapter-prereq-entries
  "All corpus entries from the same chapter with idx strictly less than
  this section's first idx, in order."
  [section]
  (filterv (fn [e]
             (and (= (:chapter e) (:chapter section))
                  (< (:idx e) (:first-idx section))))
           corpus))

(def latex-marker-re
  ;; SICM book LaTeX renderings come through with no whitespace between
  ;; tokens like `12m` `(Dx ` etc. and never look like readable Clojure.
  ;; If the expected starts with a digit-letter run or contains certain
  ;; symbols we don't want to treat it as a comparable sexpr comment.
  #"[√≈≠⋅±∞∂∑∫π·]")

(def print-result-heads
  "Heads that mark a top-level form as a SICM-book printed expression
  rather than user-runnable code. The corpus scrape sometimes inlines
  these next to the input form in :translated, where they'd otherwise
  evaluate to 'Unable to resolve symbol' errors on free symbols."
  '#{+ - * / up down expt sqrt matrix})

(defn- read-form-or-nil [s]
  (try
    (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. s))]
      (let [f (read {:eof ::eof :read-cond :allow} rdr)]
        (when-not (= f ::eof) f)))
    (catch Throwable _ nil)))

(defn split-print-result
  "Detect SICM-book printed-result artifacts in `:translated`. When the
  last `\\n\\n`-separated chunk parses to a math-shaped form (`(+ …)`,
  `(up …)`, etc.) and isn't the only form, that chunk is the printed
  output, not user code. Returns
  {:runnable-text  text without the trailing artifact (original
                   indentation preserved for the rest)
   :printed-result pr-str of the artifact form, or nil}."
  [translated]
  (let [chunks (str/split translated #"\n[\t ]*\n")
        last-form (when (> (count chunks) 1)
                    (read-form-or-nil (last chunks)))]
    (if (and last-form
             (seq? last-form)
             (contains? print-result-heads (first last-form)))
      {:runnable-text  (str/join "\n\n" (butlast chunks))
       :printed-result (pr-str last-form)}
      {:runnable-text  translated
       :printed-result nil})))

(defn placeholder-entry?
  "True when the entry's :translated is a SICM-book syntax example
  with placeholder names rather than runnable code. Catches:
    - a literal `...` marker (§1.5.1's `delta` 'fill in the rest')
    - the book's `predicate-1 / consequent-1 / variable-n /
      expression-n / consequent-n / predicate-n` placeholders that
      appear in §8 Appendix's syntax cheatsheet (`(cond (predicate-1
      consequent-1) ...)`, `(let ((variable-1 expression-1) ...))`,
      etc.). These aren't runnable; skip them so the page doesn't
      bail out on a free symbol."
  [entry]
  (let [t (:translated entry "")]
    (boolean (or (re-find #"(\s|\()\.\.\.(\s|\))" t)
                 (re-find #"\b(predicate|consequent|variable|expression)-(?:1|n)\b" t)))))

(defn print-result-only-entry?
  "True when the entry's :translated parses to a single math-shaped
  form like `(+ … expt n …)` with free symbols (n, x_r, Omega, …).
  These are SICM-book printed-result excerpts the scrape captured as
  standalone snippets without an associated input expression. Skip
  them — they have no defs to contribute and just blow up on the free
  symbol when the page evaluates."
  [entry]
  (let [forms (try (let [chunks (str/split (:translated entry "") #"\n[\t ]*\n")]
                     (keep read-form-or-nil chunks))
                   (catch Throwable _ nil))]
    (and forms
         (= 1 (count forms))
         (let [f (first forms)]
           (and (seq? f)
                (contains? print-result-heads (first f)))))))

(defn readable-expected?
  "Cheap predicate: only render :expected as an inline comment if it
  parses as a Clojure form (or is a bare number/string). Filters out
  LaTeX-rendered show-expression output."
  [s]
  (try
    (when (and (string? s)
               (not (re-find latex-marker-re s)))
      (with-open [rdr (java.io.PushbackReader.
                        (java.io.StringReader. s))]
        (let [f (read {:eof ::eof :read-cond :allow} rdr)]
          (not= f ::eof))))
    (catch Throwable _ false)))

(defn comment-block
  "Indents `s` so each line starts with `;; `."
  [s]
  (->> (str/split-lines s)
       (map #(str ";; " %))
       (str/join "\n")))

(def reserved-names
  "Names referred into the BEmmy user ns at startup (emmy.env :refer
  :all + clojure.core's full refer set). A page-local `(def X …)` for
  any of these triggers SCI's hard 'X already refers to …' throw in
  the browser. Pre-computed on the JVM into the EDN fixture."
  (-> "test/fixtures/reserved-names.edn"
      slurp
      edn/read-string
      set))

(defn- comment-out-if-reserved-def
  "If `chunk`'s first top-level form is a `(def X …)` / `(defn X …)` /
  `(defn- X …)` where X collides with the user-ns refer set, convert
  the whole chunk to `;;` line comments with a brief header; the
  pedagogical text stays readable but isn't evaluated. Mirrors the
  same helper in build-emmy-sicm-pages.bb."
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
  [text]
  (->> (str/split text #"\n[\t ]*\n")
       (map comment-out-if-reserved-def)
       (str/join "\n\n")))

(defn render-entry
  "Render one corpus entry as either prereq or main content. For prereq
  use, we just dump the runnable form(s); for main use, we add a
  page-number comment, optional subheading divider (only on change from
  prior), and an inline ;;=> comment showing either the entry's
  :expected or the SICM-book printed result split out of :translated."
  [entry {:keys [prereq? prev-subheading]}]
  (let [{:keys [translated expected page subheading]} entry
        {:keys [runnable-text printed-result]} (split-print-result translated)
        ;; Prefer the corpus's :expected (string transcribed from the
        ;; book); fall back to the trailing printed-result form we
        ;; just split out of :translated.
        effective-expected (or expected printed-result)
        sb (StringBuilder.)]
    (when (and (not prereq?)
               subheading
               (not= subheading prev-subheading))
      (.append sb (str "\n;; --- " subheading " ---\n\n")))
    (when (and (not prereq?) page)
      (.append sb (str ";; (book p. " page ")\n")))
    (.append sb (-> runnable-text str/trim comment-out-reserved-defs))
    (when (and (not prereq?) effective-expected
               (readable-expected? effective-expected))
      (.append sb (str "\n;;=> "
                       (-> effective-expected
                           (str/replace #"\n" "\n;;   ")))))
    (.toString sb)))

(defn- read-entry-forms
  "Read the entry's :translated text into a vector of top-level forms,
  tolerating reader errors (some translated SICM text contains symbols
  like 'v_r^x where ^ collides with Clojure's metadata reader)."
  [translated]
  (with-open [rdr (java.io.PushbackReader.
                    (java.io.StringReader. translated))]
    (loop [acc []]
      (let [f (try (read {:eof ::eof :read-cond :allow} rdr)
                   (catch Throwable _ ::eof))]
        (if (= f ::eof) acc (recur (conj acc f)))))))

(defn- def-names-in-form
  "If `f` is a (def X …) / (defn X …) / (defn- X …) form, return X;
  otherwise nil."
  [f]
  (when (and (seq? f)
             (contains? '#{def defn defn-} (first f))
             (symbol? (second f)))
    (second f)))

(defn page-def-names
  "Set of symbols bound by top-level def/defn forms across all entries
  on the page (prereqs + section). Used to ns-unmap them at the top of
  the page so re-evaluating doesn't trigger 'X already refers to
  #'emmy.env/X' warnings."
  [entries]
  (->> entries
       (mapcat #(read-entry-forms (:translated %)))
       (keep def-names-in-form)
       (distinct)
       (sort)
       vec))

(defn page-name [{:keys [section section-title]}]
  (str "SICM " section
       (when (and section-title (seq section-title))
         (str " " section-title))))

(defn- declare-setup-form
  "Forward-declare each page-defined name, but ONLY when it isn't
  already resolvable in the current ns (via emmy.env :refer or a
  compat shim). Otherwise an unconditional `(declare X)` would shadow
  a working `emmy.env/X` with an unbound local var, breaking prereq
  forms that were previously calling into the env (e.g. find-path's
  body references make-path; emmy.env/make-path is fine, but a bare
  `(declare make-path)` before §1.12's (defn make-path …) leaves the
  call unbound during the prereq evaluation)."
  [def-names]
  (when (seq def-names)
    (str "(doseq [s '"
         (pr-str (vec def-names))
         "]\n  (when-not (ns-resolve *ns* s) (intern *ns* s)))")))

;; ----------------------------------------------------------------------
;; Per-section enrichments — appended below the scrape-derived body to
;; give otherwise def-only pages a concrete invocation, usually a plot.
;; Mirrors `page-extras` in build-emmy-sicm-pages.bb; here the key is
;; `[chapter section]` so the table sticks with the build script's own
;; section grouping rather than depending on titles that may drift.
(def section-extras
  {["2" "2.2"]
   ["a rotating frame's axis after a sequence of small rotations"
    ";; Apply infinitesimal rotations Rₓ(α) ∘ R_y(α) repeatedly to (0, 0, 1)
;; and project the resulting tip onto the xy-plane. The trace shows how
;; the composition of rotations doesn't commute — even small steps walk
;; the symmetry axis away from where pure z-rotation would leave it.
(let [α       0.04
      n-steps 200
      step    (fn [v]
                (let [v0 (nth v 0)
                      v1 (nth v 1)
                      v2 (nth v 2)
                      ;; Rₓ(α) then R_y(α). Math/sin/cos for plain doubles.
                      ca (Math/cos α)
                      sa (Math/sin α)
                      v1' (- (* ca v1) (* sa v2))
                      v2' (+ (* sa v1) (* ca v2))
                      v0' (+ (* ca v0) (* sa v2'))
                      v2'' (- (* ca v2') (* sa v0))]
                  [v0' v1' v2'']))
      tips (->> (iterate step [0.0 0.0 1.0])
                (take n-steps)
                vec)]
  [mafs.core/Mafs {:viewBox {:x [-1.2 1.2] :y [-1.2 1.2]}}
   [mafs.coordinates/Cartesian]
   ;; Plot the (x, y) projection of each tip — z scales the radius.
   [mafs.plot/Parametric
    {:t [0 (dec n-steps)]
     :xy (fn [t]
           (let [i (Math/floor t)
                 i (max 0 (min (dec n-steps) i))
                 tip (nth tips i)]
             [(nth tip 0) (nth tip 1)]))
     :color \"#3090ff\"}]])"]

   ["2" "2.5"]
   ["3D: the ellipsoid of inertia for principal moments (A, B, C) = (1, 1.6, 0.4)"
    ";; The kinetic energy T_body = ½(A ω_a² + B ω_b² + C ω_c²) defines an
;; ellipsoid in body-frame angular-velocity space. Drag to rotate the
;; view — the long axis points along the smallest moment (C, here ẑ̂),
;; the short axes along the larger A, B.
(let [A 1.0
      B 1.6
      C 0.4
      ;; ω_a² A + ω_b² B + ω_c² C = 1 has semi-axes (1/√A, 1/√B, 1/√C).
      a (cljs.core// 1.0 (Math/sqrt A))
      b (cljs.core// 1.0 (Math/sqrt B))
      c (cljs.core// 1.0 (Math/sqrt C))]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    ;; Parametric (θ, φ) → ellipsoid surface point.
    [mb/Area
     {:rangeX [0 Math/PI]
      :rangeY [0 (cljs.core/* 2 Math/PI)]
      :width 32 :height 32 :channels 3
      :expr (fn [emit θ φ]
              (emit (cljs.core/* a (Math/sin θ) (Math/cos φ))
                    (cljs.core/* b (Math/sin θ) (Math/sin φ))
                    (cljs.core/* c (Math/cos θ))))}]
    [mb/Surface {:shaded true :color \"#3090ff\" :opacity 0.7}]]])"]

   ["2" "2.8"]
   ["3D: Poinsot's construction — inertia ellipsoid with ω and L vectors"
    ";; The free rigid body's motion has a geometric construction: the
;; inertia ellipsoid ½I(ω) = T = const rolls without slipping on an
;; invariable plane perpendicular to L. The point of contact is where ω
;; touches the surface. For diagonal I = diag(A, B, C) and a fixed ω,
;; ω is red, L = I·ω is green; for an anisotropic body their directions
;; differ. (The plane itself is omitted — the visual focus is the
;; ellipsoid + two vectors.)
(let [A 1.0
      B 1.6
      C 0.4
      ;; A specific angular velocity in body frame.
      ωx 0.6 ωy 0.5 ωz 0.8
      ;; Ellipsoid semi-axes in ω-space for ½I(ω) = 1.
      a (cljs.core// 1.0 (Math/sqrt A))
      b (cljs.core// 1.0 (Math/sqrt B))
      c (cljs.core// 1.0 (Math/sqrt C))
      ;; L = I·ω
      Lx (cljs.core/* A ωx) Ly (cljs.core/* B ωy) Lz (cljs.core/* C ωz)]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-2 2] [-2 2] [-2 2]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    ;; Inertia ellipsoid — translucent surface.
    [mb/Area
     {:rangeX [0 Math/PI]
      :rangeY [0 (cljs.core/* 2 Math/PI)]
      :width 32 :height 32 :channels 3
      :expr (fn [emit θ φ]
              (emit (cljs.core/* a (Math/sin θ) (Math/cos φ))
                    (cljs.core/* b (Math/sin θ) (Math/sin φ))
                    (cljs.core/* c (Math/cos θ))))}]
    [mb/Surface {:shaded true :color \"#3090ff\" :opacity 0.4}]
    ;; ω vector — red.
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x ωx) (cljs.core/* x ωy) (cljs.core/* x ωz)))}]
    [mb/Line {:color \"#e63946\" :width 5}]
    ;; L vector — green.
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x Lx) (cljs.core/* x Ly) (cljs.core/* x Lz)))}]
    [mb/Line {:color \"#2a9d8f\" :width 5}]]])"]

   ["2" "2.6"]
   ["3D: angular momentum decomposition for a tumbling brick"
    ";; A 'brick' is a long-thin-flat rigid body. With moments (A, B, C) =
;; (0.4, 1.0, 1.6) and ω = (0.7, 0.7, 0.2), L = I·ω comes out NOT
;; parallel to ω: the projection along the small-A axis is bigger than
;; you'd guess from ω alone. Drag to rotate the view.
(let [A 0.4 B 1.0 C 1.6
      ω [0.7 0.7 0.2]
      L [(cljs.core/* A (nth ω 0))
         (cljs.core/* B (nth ω 1))
         (cljs.core/* C (nth ω 2))]]
  [mathbox/MathBox
   {:container {:style {:height \"400px\" :width \"100%\"}}}
   [mb/Cartesian {:range [[-1.5 1.5] [-1.5 1.5] [-1.5 1.5]] :scale [1 1 1]}
    [mb/Axis {:axis 1}] [mb/Axis {:axis 2}] [mb/Axis {:axis 3}]
    ;; ω — red
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth ω 0))
                                           (cljs.core/* x (nth ω 1))
                                           (cljs.core/* x (nth ω 2))))}]
    [mb/Line {:color \"#e63946\" :width 5}]
    ;; L — green
    [mb/Interval {:range [0 1] :width 2 :channels 3
                  :expr (fn [emit x] (emit (cljs.core/* x (nth L 0))
                                           (cljs.core/* x (nth L 1))
                                           (cljs.core/* x (nth L 2))))}]
    [mb/Line {:color \"#2a9d8f\" :width 5}]]])"]

   ["5" "5.2.1"]
   ["uniformly-rotating frame: x'(t) vs x(t) for the same particle"
    ";; A particle at rest in the body-fixed rotating frame at radius 1 traces
;; a circle of radius 1 in the inertial frame at angular rate Ω. The
;; canonical transformation (x, p) → (x', p') = (R(Ωt) x, R(Ωt) p) makes
;; this trivial: in the rotating frame the particle is stationary.
(let [Ω 1.0
      r 1.0]
  [mafs.core/Mafs {:viewBox {:x [-1.5 1.5] :y [-1.5 1.5]}}
   [mafs.coordinates/Cartesian]
   ;; Inertial-frame trajectory (full circle).
   [mafs.plot/Parametric
    {:t [0 (cljs.core/* 2 Math/PI)]
     :xy (fn [t] [(cljs.core/* r (Math/cos (cljs.core/* Ω t)))
                  (cljs.core/* r (Math/sin (cljs.core/* Ω t)))])
     :color \"#3090ff\"}]
   ;; Rotating-frame position — a single point.
   [mafs.core/Point {:x r :y 0 :color \"#e63946\"}]])"]

   ["6" "6.4"]
   ["animated Taylor truncations of cos(t): order grows, error shrinks"
    ";; The Lie series for the flow generated by D is a Taylor expansion:
;; cos(t) = Σₖ (-1)ᵏ t²ᵏ/(2k)!. Animate higher-order truncations
;; converging to the true cosine — the curve refines as more terms come in.
(let [factorial (fn fact [n] (if (cljs.core/<= n 1) 1 (cljs.core/* n (fact (cljs.core/- n 1)))))
      cos-trunc (fn [n x]
                  (loop [k 0 acc 0.0]
                    (if (cljs.core/> k n)
                      acc
                      (recur (cljs.core/+ k 2)
                             (cljs.core/+ acc
                                          (cljs.core/* (if (cljs.core/zero? (cljs.core/mod k 4)) 1.0 -1.0)
                                                       (cljs.core// (Math/pow x k)
                                                                    (factorial k))))))))]
  (animate
   (fn [t x]
     ;; t auto-advances; the truncation order grows 2, 4, 6, 8, ... and loops.
     (let [n (cljs.core/+ 2 (cljs.core/* 2 (cljs.core/mod (int t) 5)))]
       (cos-trunc n x)))
   [(cljs.core/- (cljs.core/* 2 Math/PI)) (cljs.core/* 2 Math/PI)]
   [-2.0 2.0] 0.4))"]

   ["3" "3.1.1"]
   ["Legendre transform: L(v) vs H(p), with a tangent at v=1.5"
    ";; For L(v) = ½v² (free particle, m=1), the Legendre transform gives
;; H(p) = ½p² with p = ∂L/∂v = v. The slider picks v; the orange
;; tangent line at v has slope p = v and y-intercept −H(p) — its
;; intersection with the p axis IS p, and its negative y-intercept is H.
(plot-with-params
 (fn [{:keys [v]} x]
   (let [L  (cljs.core/* 0.5 x x)        ; L(x) = ½ x²
         p  v                            ; p = ∂L/∂v|v = v
         L0 (cljs.core/* 0.5 v v)
         ;; Tangent line: ŷ = L(v) + p(x − v) = ½v² + v(x − v) = vx − ½v².
         tan (cljs.core/- (cljs.core/* p x) L0)
         dx  (cljs.core/- x v)]
     (if (cljs.core/< (Math/abs dx) 0.06)
       ;; Highlight tangent line as a thicker stub near the touch point.
       tan
       L)))
 {:v {:value 1.5 :min 0.0 :max 2.5 :step 0.05}}
 [-0.5 3.0] [-1.0 5.0])"]

   ["1" "1.8.4"]
   ["2D animation in the rotating co-frame — edit the call to vary μ / IC"
    ";; Numerical RK4 integration of the LR3B1 equations of motion in the
;; rotating frame. The two primaries sit fixed on the x-axis at
;; x = -μ (heavier, mass 1-μ) and x = 1-μ (lighter, mass μ); the
;; massless test particle's EOM carries Coriolis (2ẏ, -2ẋ) and
;; centrifugal (x, y) terms beyond the gravity from each primary.
;; Trajectory is precomputed; the red dot replays it on a 1s/unit-time
;; loop. Edit the map below to change the mass ratio or initial state.
(defn cr3bp-anim [{:keys [μ x0 y0 vx0 vy0 t-max]}]
  (let [n-steps 3000
        dt      (cljs.core// t-max n-steps)
        μ'      (cljs.core/- 1 μ)
        primary-0 (cljs.core/- μ)        ; heavier, mass 1-μ
        primary-1 μ'                     ; lighter, mass μ
        derivs (fn [x y vx vy]
                 (let [dx0     (+ x μ)
                       dx1     (- x μ')
                       r0-sq   (+ (* dx0 dx0) (* y y))
                       r1-sq   (+ (* dx1 dx1) (* y y))
                       r0-cube (* r0-sq (Math/sqrt r0-sq))
                       r1-cube (* r1-sq (Math/sqrt r1-sq))
                       ax (+ (* 2.0 vy) x
                             (- (/ (* μ' dx0) r0-cube))
                             (- (/ (* μ dx1) r1-cube)))
                       ay (+ (* -2.0 vx) y
                             (- (/ (* μ' y) r0-cube))
                             (- (/ (* μ y) r1-cube)))]
                   [vx vy ax ay]))
        ;; RK4 step on state vector [x y vx vy].
        step (fn [[x y vx vy]]
               (let [h  dt
                     k1 (derivs x y vx vy)
                     k2 (derivs (+ x  (* 0.5 h (nth k1 0)))
                                (+ y  (* 0.5 h (nth k1 1)))
                                (+ vx (* 0.5 h (nth k1 2)))
                                (+ vy (* 0.5 h (nth k1 3))))
                     k3 (derivs (+ x  (* 0.5 h (nth k2 0)))
                                (+ y  (* 0.5 h (nth k2 1)))
                                (+ vx (* 0.5 h (nth k2 2)))
                                (+ vy (* 0.5 h (nth k2 3))))
                     k4 (derivs (+ x  (* h (nth k3 0)))
                                (+ y  (* h (nth k3 1)))
                                (+ vx (* h (nth k3 2)))
                                (+ vy (* h (nth k3 3))))
                     w  (/ h 6.0)
                     acc (fn [base i]
                           (+ base (* w (+ (nth k1 i)
                                           (* 2.0 (nth k2 i))
                                           (* 2.0 (nth k3 i))
                                           (nth k4 i)))))]
                 [(acc x 0) (acc y 1) (acc vx 2) (acc vy 3)]))
        positions (vec (take (inc n-steps)
                             (map (fn [s] [(nth s 0) (nth s 1)])
                                  (iterate step [x0 y0 vx0 vy0]))))
        pos-at (fn [t]
                 (let [i (max 0 (min n-steps
                                     (cljs.core/int (Math/floor (cljs.core// t dt)))))]
                   (nth positions i)))
        !t     (reagent.core/atom 0.0)
        !start (atom nil)
        timer  (atom nil)]
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
                       (reset! !t (cljs.core/mod elapsed t-max))))
                   33)))
       :component-will-unmount
       (fn [_] (when (deref timer) (js/clearInterval (deref timer))))
       :reagent-render
       (fn [_]
         (let [t     @!t
               [x y] (pos-at t)]
           [mafs.core/Mafs {:viewBox {:x [-2 2] :y [-1.5 1.5]}}
            [mafs.coordinates/Cartesian]
            [mafs.plot/Parametric
             {:t [0 t-max] :xy pos-at :color \"#3090ff\"}]
            ;; Primaries — fixed in the rotating frame.
            [mafs.core/Point {:x primary-0 :y 0 :color \"#444444\"}]
            [mafs.core/Point {:x primary-1 :y 0 :color \"#888888\"}]
            ;; Current test-particle position.
            [mafs.core/Point {:x (double x) :y (double y) :color \"#e63946\"}]]))})))

;; Default: μ = 0.1 (Sun-Jupiter-ish ratio). Test particle at (-0.6, 0)
;; with retrograde tangential velocity — gives a stable orbit looping
;; around the heavier primary that gets perturbed by the lighter one
;; into a precessing rosette filling roughly x∈[-0.6, 0.4], y∈[-0.5, 0.5].
;; Cmd-Enter to run.
[cr3bp-anim {:μ 0.1 :x0 -0.6 :y0 0.0 :vx0 0.0 :vy0 -0.742 :t-max 30.0}]

;; Other invocations to try (un-comment one at a time):
;; Tight retrograde just outside the lighter primary — a small clean loop:
;; [cr3bp-anim {:μ 0.1  :x0 0.85 :y0 0.0 :vx0 0.0 :vy0 -1.5  :t-max 25.0}]
;; Smaller mass ratio (μ = 0.05), nearly-Keplerian around primary 0:
;; [cr3bp-anim {:μ 0.05 :x0 -0.5 :y0 0.0 :vx0 0.0 :vy0 -0.8  :t-max 30.0}]"]})

(defn render-page
  [section]
  (let [{:keys [chapter section-title chapter-title source entries]} section
        keep-runnable   #(not (or (placeholder-entry? %)
                                  (print-result-only-entry? %)))
        prereqs (filterv keep-runnable (chapter-prereq-entries section))
        entries (filterv keep-runnable entries)
        all-entries (concat prereqs entries)
        def-names   (page-def-names all-entries)
        extras      (get section-extras [(:chapter section) (:section section)])
        sb      (StringBuilder.)]
    (.append sb (comment-block
                  (str "===========================================\n"
                       "SICM §" (:section section)
                       (when (seq section-title) (str " — " section-title))
                       "\n"
                       "Chapter " chapter
                       (when (seq chapter-title) (str " — " chapter-title))
                       "\n"
                       (when source (str source "\n"))
                       "===========================================\n"
                       "Self-contained: earlier-chapter prerequisites are\n"
                       "inlined below.")))
    (.append sb "\n\n")
    (when-let [setup (declare-setup-form def-names)]
      (.append sb setup)
      (.append sb "\n\n"))
    (when (seq prereqs)
      (.append sb (str ";; --- Prerequisites from earlier sections of Chapter "
                       chapter " ---\n\n"))
      (doseq [e prereqs]
        (.append sb (render-entry e {:prereq? true}))
        (.append sb "\n\n"))
      (.append sb (str ";; --- §" (:section section)
                       (when (seq section-title) (str " — " section-title))
                       " ---\n\n")))
    (loop [[e & rest] entries
           prev-sub nil]
      (when e
        (.append sb (render-entry e {:prereq? false
                                     :prev-subheading prev-sub}))
        (.append sb "\n\n")
        (recur rest (:subheading e))))
    (when extras
      (let [[title body] extras]
        (.append sb (str ";; --- Example: " title " ---\n\n" body "\n"))))
    (str/trimr (.toString sb))))

(defn escape-clj-string [s]
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")))

(defn render-array-map [pages]
  (let [sb (StringBuilder.)]
    (.append sb "(def sicm-section-pages\n  (array-map")
    (doseq [{:keys [name source]} pages]
      (.append sb (str "\n    \"" (escape-clj-string name) "\"\n"))
      (.append sb (str "    \"" (escape-clj-string source) "\"")))
    (.append sb "))\n")
    (.toString sb)))

(defn splice-app-cljs! [generated]
  (let [app    (slurp app-path)
        pat    (re-pattern
                 (str "(?s)(\\Q" begin-marker "\\E\\n).*?(\\Q" end-marker "\\E)"))]
    (when-not (re-find pat app)
      (throw (ex-info (str "Markers not found in " app-path
                           "; add\n  " begin-marker "\n  " end-marker
                           "\nbefore (def system-pages …).")
                      {})))
    (let [replaced (str/replace-first app pat
                                      (fn [_]
                                        (str begin-marker "\n"
                                             generated
                                             end-marker)))]
      (spit app-path replaced))))

;; Sections covered by Emmy's own SICM tests
;; (test/fixtures/emmy-sicm/chN_test.cljc) — for these we ship the
;; canonical Emmy port instead of the scrape-and-translate output, and
;; the scrape sections drop out of the page list.
(def emmy-covered-sections
  ;; Each entry is the SICM section number as it appears in the scrape
  ;; corpus's :section field. An Emmy `section-1-6` deftest covers
  ;; §1.6 plus its sub-sections, so all four corresponding scrape
  ;; sections drop.
  #{;; ch1 — Lagrangian Mechanics
    "1.4" "1.5.1" "1.5.2" "1.6" "1.6.1" "1.6.2" "1.6.3"
    "1.7" "1.8.2" "1.8.3" "1.8.5" "1.9"
    ;; §1.8.4 (Restricted Three-Body Problem — L0/V-eff/LR3B/LR3B1) is
    ;; NOT exercised by the section-1-8 deftest; let scrape through.
    ;; ch2 — Rigid Bodies / Rotation
    "2.7" "2.10"
    ;; ch3 — Hamiltonian Mechanics
    "3.1" "3.2" "3.4" "3.5"
    ;; ch5 — Canonical Transformations
    "5.1" "5.2" "5.3"
    ;; ch6 — Canonical Evolution
    "6.2"
    ;; ch7 — Canonical Perturbation Theory
    ;; (§7.2's H0/H1/W/Lie-derivative / H-pendulum-series are NOT
    ;; exercised by ch7's `section-2` deftest — that deftest just
    ;; covers literal-function arity. Let scrape through.)
    })

(defn- read-emmy-pages
  "Shell out to the sibling generator and read its EDN. Returns a vec
  of {:name :source} maps; empty vec if the script fails."
  []
  (try
    (let [{:keys [out exit]} (shell {:out :string :continue true}
                                    "bb" "bin/build-emmy-sicm-pages.bb" "--edn")]
      (if (zero? exit) (edn/read-string out) []))
    (catch Throwable _ [])))

(defn -main [& _]
  (let [emmy-pages    (read-emmy-pages)
        scrape-pages  (->> sections
                           (remove (comp emmy-covered-sections :section))
                           (mapv (fn [s] {:name (page-name s)
                                          :source (render-page s)})))
        all-pages     (concat emmy-pages scrape-pages)
        body          (render-array-map all-pages)]
    (splice-app-cljs! body)
    (println (format "Wrote %d SICM section pages (%d Emmy + %d scrape) into %s"
                     (count all-pages)
                     (count emmy-pages)
                     (count scrape-pages)
                     app-path))))

(-main)
