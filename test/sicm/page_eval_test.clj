(ns sicm.page-eval-test
  "End-to-end test for the SICM section pages embedded in public/app.cljs.

  Strategy: each generated page is `chapter prereqs ++ section entries`,
  in textbook order. For each section, allocate a fresh per-section
  namespace (seeded with emmy.env + sicm.compat), evaluate every
  prereq+section corpus entry in order, and check `:expected` results
  for the section's entries.

  This is the equivalence test scoped per-section instead of per-chapter,
  which is what proves each page is genuinely self-contained: a missing
  prereq, broken `chapter-prereq-entries` ordering, or `:translated` form
  that doesn't actually evaluate would surface here.

  Bonus: also asserts each section's `:translated` text actually appears
  in the corresponding page string in app.cljs, so generator regressions
  are caught."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]
            [sicm.equivalence-test :as eq]
            emmy.env))

(def app-path "public/app.cljs")
(def corpus-path "test/fixtures/sicm-snippets.translated.edn")

(def begin-marker ";; --- BEGIN GENERATED SICM PAGES ---")
(def end-marker   ";; --- END GENERATED SICM PAGES ---")

(def corpus
  (->> corpus-path slurp edn/read-string (sort-by :idx) vec))

(defn extract-section-pages
  "Read the (def sicm-section-pages (array-map ... )) form between the
  BEGIN/END markers in app.cljs. Returns {page-name → page-source}."
  [app-source]
  (let [start (str/index-of app-source begin-marker)
        end   (str/index-of app-source end-marker)]
    (when (or (nil? start) (nil? end))
      (throw (ex-info "Couldn't find generated-pages markers in app.cljs"
                      {:app-path app-path})))
    (let [region (subs app-source (+ start (count begin-marker)) end)
          form   (with-open [rdr (java.io.PushbackReader.
                                   (java.io.StringReader. region))]
                   (read {:eof ::eof :read-cond :allow} rdr))]
      (when (or (not (seq? form))
                (not= 'def (first form))
                (not= 'sicm-section-pages (second form)))
        (throw (ex-info "Unexpected form between markers" {:got form})))
      (let [body (nth form 2)]
        (when (or (not (seq? body))
                  (not= 'array-map (first body)))
          (throw (ex-info "Expected (array-map ...) body" {:got body})))
        (apply array-map (rest body))))))

(def section-pages
  (extract-section-pages (slurp app-path)))

(defn corpus-by-section
  "{[chapter section] → [entry ...]} preserving idx order."
  [c]
  (->> c (group-by (juxt :chapter :section))))

(def by-section (corpus-by-section corpus))

(defn chapter-prereqs
  "All entries from `chapter` whose idx precedes the first idx of
  `section`'s entries."
  [chapter section]
  (let [section-entries (get by-section [chapter section])
        first-idx       (-> section-entries first :idx)]
    (filterv (fn [e]
               (and (= (:chapter e) chapter)
                    (< (:idx e) first-idx)))
             corpus)))

(defn page-name->section-key
  "Map a generated page name like 'SICM 1.5.2 Computing ...' back to
  the corpus's [chapter section] key."
  [page-name]
  (when-let [[_ section] (re-find #"^SICM\s+(\S+)" page-name)]
    (let [matches (filter (fn [[[_ s]]] (= s section)) by-section)]
      (when (= 1 (count matches))
        (ffirst matches)))))

(defn read-all-forms [^String src]
  (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. src))]
    (loop [forms []]
      (let [f (try (read {:eof ::eof :read-cond :allow} rdr)
                   (catch Throwable _ ::read-error))]
        (cond
          (= f ::eof) forms
          (= f ::read-error) forms
          :else (recur (conj forms f)))))))

(defn ns-sym [page-name]
  (-> page-name
      (str/replace #"[^A-Za-z0-9.]+" "-")
      (->> (str "sicm.eval.page."))
      symbol))

(defn try-eval-entry
  "Eval an entry's :translated forms in `ns`. Returns
  {:result <last-result> :stdout <captured>} on success or
  {:error <Throwable> :stdout <captured>} on failure."
  [ns entry]
  (let [out (java.io.StringWriter.)]
    (try
      (let [forms (read-all-forms (:translated entry))
            r (binding [*ns* ns *out* out]
                (last (mapv eval forms)))]
        {:result r :stdout (str out)})
      (catch Throwable t
        {:error t :stdout (str out)}))))

(defn check-section-page
  "Eval the section's prereqs + own entries in a fresh ns ONCE, in
  order, capturing each entry's last-form result. Then check each
  `:expected` entry against its captured result, and verify the page
  text contains each section entry's :translated."
  [[chapter section :as section-key] page-name page-source]
  (let [prereqs         (chapter-prereqs chapter section)
        section-entries (get by-section section-key)
        ns              (eq/fresh-eval-ns! (ns-sym page-name))
        ;; idx → {:result … :error … :stdout …}, populated in order.
        results         (atom {})]
    (doseq [entry (concat prereqs section-entries)]
      (swap! results assoc (:idx entry) (try-eval-entry ns entry)))
    (testing page-name
      (doseq [{:keys [section page idx expected translated] :as entry} section-entries
              :when (and expected (eq/readable? expected))]
        (testing (format "§%s p%s #%s" section page idx)
          ;; The translated text should be embedded in the page string.
          (is (str/includes? page-source (str/trim translated))
              ":translated text not found in page string")
          (let [{:keys [result error stdout]} (get @results idx)]
            (cond
              error
              (let [t ^Throwable error]
                (is false
                    (format "expected-snippet eval threw: %s\n  form: %s"
                            (.getMessage t)
                            (pr-str translated))))

              :else
              (is (eq/equivalent? ns result expected stdout)
                  (format "got %s\nwant %s"
                          (pr-str result)
                          (pr-str expected))))))))))

(deftest sicm-pages-end-to-end
  (doseq [[page-name page-source] section-pages]
    (if-let [k (page-name->section-key page-name)]
      (check-section-page k page-name page-source)
      (testing page-name
        (is false (str "couldn't map page-name to section-key: " page-name))))))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'sicm.page-eval-test)]
    (shutdown-agents)
    (System/exit (if (or (pos? fail) (pos? error)) 1 0))))
