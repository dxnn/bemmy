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
     :color \"#3090ff\"}]])"]})

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
    "1.7" "1.8.2" "1.8.3" "1.8.4" "1.8.5" "1.9"
    ;; ch2 — Rigid Bodies / Rotation
    "2.7" "2.10"
    ;; ch3 — Hamiltonian Mechanics
    "3.1" "3.2" "3.4" "3.5"
    ;; ch5 — Canonical Transformations
    "5.1" "5.2" "5.3"
    ;; ch6 — Canonical Evolution
    "6.2"
    ;; ch7 — Canonical Perturbation Theory
    "7.2"})

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
