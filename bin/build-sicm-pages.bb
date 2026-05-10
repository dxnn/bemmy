#!/usr/bin/env bb
;; Generate SICM section pages for the BEmmy playground from the
;; pre-translated SICM corpus. Splices a `(def sicm-section-pages
;; (array-map ...))` form into public/app.cljs between BEGIN/END
;; GENERATED SICM PAGES markers. Each page is self-contained: prereqs
;; from earlier sections of the same chapter are prepended so the page
;; stands alone in the editor.

(ns build-sicm-pages
  (:require [clojure.edn :as edn]
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
  "True when the entry's :translated contains a literal `...` symbol —
  the SICM book uses ... as a 'fill in the rest' pedagogical marker
  inside an otherwise-incomplete defn (e.g. §1.5.1's `delta`). Such
  snippets aren't runnable code; the generator skips them entirely
  rather than emit a defn whose body has an unresolvable `...`."
  [entry]
  (boolean (re-find #"(\s|\()\.\.\.(\s|\))" (:translated entry ""))))

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
    (.append sb (str/trim runnable-text))
    (when (and (not prereq?) effective-expected
               (readable-expected? effective-expected))
      (.append sb (str "\n;;=> "
                       (-> effective-expected
                           (str/replace #"\n" "\n;;   ")))))
    (.toString sb)))

(defn dedupe-prereqs
  "If the SICM book redefines a name in a later section (e.g.
  L-central-polar in §1.5.2 then again in §1.6 and §1.6.1), keep all
  occurrences in prereq order — Clojure just rebinds, and order matches
  textbook reading. So this is currently identity; left here to flag
  the question."
  [entries]
  entries)

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
  "Render `(declare X Y Z)` for every name the page defines. This
  forward-declares them so cross-snippet references resolve regardless
  of definition order (the SICM book defines e.g. qp->H-state-path
  after its first use), and once each name is locally interned, the
  subsequent (defn …) forms don't re-trigger the emmy.env shadow
  warning per def — at most once at the declare line."
  [def-names]
  (when (seq def-names)
    (str "(declare " (clojure.string/join " " (map name def-names)) ")")))

(defn render-page
  [section]
  (let [{:keys [chapter section-title chapter-title source entries]} section
        keep-runnable   (complement placeholder-entry?)
        prereqs (dedupe-prereqs
                  (filterv keep-runnable (chapter-prereq-entries section)))
        entries (filterv keep-runnable entries)
        all-entries (concat prereqs entries)
        def-names   (page-def-names all-entries)
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

(defn -main [& _]
  (let [pages (mapv (fn [s] {:name (page-name s)
                             :source (render-page s)}) sections)
        body  (render-array-map pages)]
    (splice-app-cljs! body)
    (println (format "Wrote %d SICM section pages into %s"
                     (count pages) app-path))))

(-main)
