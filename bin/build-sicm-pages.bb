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
  use, we just dump the form; for main use, we add a page-number
  comment, optional subheading divider (only on change from prior),
  and an inline expected-value comment when readable."
  [entry {:keys [prereq? prev-subheading]}]
  (let [{:keys [translated expected page subheading]} entry
        sb (StringBuilder.)]
    (when (and (not prereq?)
               subheading
               (not= subheading prev-subheading))
      (.append sb (str "\n;; --- " subheading " ---\n\n")))
    (when (and (not prereq?) page)
      (.append sb (str ";; (book p. " page ")\n")))
    (.append sb (str/trim translated))
    (when (and (not prereq?) expected (readable-expected? expected))
      (.append sb (str "\n;;=> "
                       (-> expected
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

(defn page-name [{:keys [section section-title]}]
  (str "SICM " section
       (when (and section-title (seq section-title))
         (str " " section-title))))

(defn render-page
  [section]
  (let [{:keys [chapter section-title chapter-title source entries]} section
        prereqs (dedupe-prereqs (chapter-prereq-entries section))
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
