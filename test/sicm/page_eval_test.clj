(ns sicm.page-eval-test
  "Strict end-to-end test for the SICM section pages embedded in
  public/app.cljs.

  For each generated section page, allocate a fresh per-section
  namespace seeded with emmy.env + sicm.compat (which mirrors the
  browser-side scittle plugin shims), read the page text top-to-bottom,
  evaluate every form in order in that ns, and check each `:expected`
  snippet from the SICM book against the corresponding form's result.

  Hard-fails on any non-graphics eval error: the goal is parity with
  the browser modulo graphical output. Graphics shim names (frame,
  plot, plot-point, …) get JVM stubs in sicm.compat that return
  innocuous placeholders, so non-graphics state continues to evolve
  through the page even when graphics calls are sprinkled in.

  `:expected` entries are matched to forms in the eval log by
  occurrence count rather than first/last match, so when the corpus
  has the same surface form repeated (e.g. §8's mutable counter
  `(c1)` called multiple times with different expected results),
  each call still maps to its own result."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]
            [sicm.equivalence-test :as eq]
            sicm.compat
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

(def by-section
  (->> corpus (group-by (juxt :chapter :section))))

(defn chapter-prereqs
  "All entries from `chapter` whose idx precedes the first idx of
  `section`'s entries — same prefix the generator inlines into the
  page."
  [chapter section]
  (let [section-entries (get by-section [chapter section])
        first-idx       (-> section-entries first :idx)]
    (filterv (fn [e]
               (and (= (:chapter e) chapter)
                    (< (:idx e) first-idx)))
             corpus)))

(defn page-name->section-key
  [page-name]
  (when-let [[_ section] (re-find #"^SICM\s+(\S+)" page-name)]
    (let [matches (filter (fn [[[_ s]]] (= s section)) by-section)]
      (when (= 1 (count matches))
        (ffirst matches)))))

(defn- read-forms-from-chunk [^String chunk]
  (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. chunk))]
    (loop [forms []]
      (let [f (try (read {:eof ::eof :read-cond :allow} rdr)
                   (catch Throwable _ ::read-error))]
        (cond
          (= f ::eof) forms
          (= f ::read-error) ::read-error
          :else (recur (conj forms f)))))))

(defn read-all-forms
  "Read top-level forms from a generated page or :translated text.
  Splits on blank lines (the generator separates top-level forms with
  `\\n\\n`) and reads each chunk independently, so a chunk whose `^`
  symbol collides with Clojure's metadata reader (e.g. `'v_r^x`)
  doesn't poison the entire page. Returns the concatenation of
  successfully-read forms."
  [^String src]
  (let [chunks (str/split src #"\n[\t ]*\n")]
    (vec (mapcat (fn [c]
                   (let [r (read-forms-from-chunk c)]
                     (if (= r ::read-error) [] r)))
                 chunks))))

(defn ns-sym [page-name]
  (-> page-name
      (str/replace #"[^A-Za-z0-9.]+" "-")
      (->> (str "sicm.eval.page."))
      symbol))

(defn- try-eval-form
  "Eval one form in the given ns, capturing stdout and any thrown."
  [ns form]
  (let [out (java.io.StringWriter.)]
    (try
      (let [r (binding [*ns* ns *out* out] (eval form))]
        {:form form :result r :stdout (str out)})
      (catch Throwable t
        {:form form :error t :stdout (str out)}))))

(def ^:private print-result-heads
  "See bin/build-sicm-pages.bb. Trailing forms with these heads are
  SICM-book printed-result artifacts, stripped from page text by the
  generator. We mirror the strip here so the entry's target form
  lines up with what's actually in the page eval log."
  '#{+ - * / up down expt sqrt matrix})

(defn- runnable-entry-forms
  "Drop trailing SICM-book print-result forms (same heuristic as the
  generator's split-print-result). Returns the runnable forms only."
  [entry]
  (let [forms (read-all-forms (:translated entry))]
    (if (and (> (count forms) 1)
             (seq? (last forms))
             (contains? print-result-heads (first (last forms))))
      (vec (butlast forms))
      (vec forms))))

(defn- entry-target-form
  "The form whose evaluation result corresponds to this corpus entry's
  :expected — the LAST runnable top-level form (printed-result
  artifacts stripped)."
  [entry]
  (last (runnable-entry-forms entry)))

(defn- match-occurrence
  "Return the n-th (0-indexed) eval-log entry whose :form equals
  `target`, or nil."
  [eval-log target n]
  (nth (vec (filter #(= (:form %) target) eval-log)) n nil))

(defn- root-cause-msg [^Throwable t]
  (.getMessage ^Throwable
               (loop [x t] (if-let [c (.getCause x)] (recur c) x))))

(defn- silence-warnings
  "Run `f` with *err* redirected to a sink so the noise from JVM
  Clojure's refer-shadow warnings (`X already refers to …`) doesn't
  pollute test output. Each generated page's (declare …) at the top
  emits one such warning per emmy.env-shadowed name; harmless but
  voluminous."
  [f]
  (binding [*err* (java.io.StringWriter.)]
    (f)))

(defn check-section-page
  [[chapter section :as section-key] page-name page-source]
  (let [section-entries (get by-section section-key)
        prereqs         (chapter-prereqs chapter section)
        all-entries     (concat prereqs section-entries)
        ns              (eq/fresh-eval-ns! (ns-sym page-name))
        page-forms      (read-all-forms page-source)
        eval-log        (silence-warnings
                          (fn [] (mapv #(try-eval-form ns %) page-forms)))
        ;; idx → eval-log entry, via occurrence-counted matching so
        ;; repeated forms (e.g. §8's `(c1)`) line up by position
        ;; rather than collapsing to first/last.
        idx->match
        (loop [entries all-entries
               counts {}
               acc {}]
          (if-let [entry (first entries)]
            (let [target (entry-target-form entry)
                  n      (get counts target 0)
                  match  (when target (match-occurrence eval-log target n))]
              (recur (rest entries)
                     (update counts target (fnil inc 0))
                     (assoc acc (:idx entry) match)))
            acc))]
    (testing page-name
      ;; Hard fail on any unexpected eval error in the page. Graphics
      ;; calls are stubbed via sicm.compat so they don't error here.
      (doseq [[i log-entry] (map-indexed vector eval-log)]
        (when-let [t (:error log-entry)]
          (is false
              (format "page form #%d eval threw: %s\n  form: %s\n  cause: %s"
                      i
                      (.getMessage ^Throwable t)
                      (pr-str (:form log-entry))
                      (root-cause-msg t)))))
      ;; Check :expected for each section entry that carries one.
      (doseq [{:keys [section page idx expected translated]} section-entries
              :when (and expected (eq/readable? expected))]
        (testing (format "§%s p%s #%s" section page idx)
          (let [m (get idx->match idx)]
            (cond
              (nil? m)
              (is false
                  (format "no match in eval log for entry's last form\n  translated: %s"
                          (pr-str translated)))

              (:error m)
              (is false
                  (format "expected-snippet eval threw: %s\n  form: %s"
                          (.getMessage ^Throwable (:error m))
                          (pr-str (:form m))))

              :else
              (is (eq/equivalent? ns (:result m) expected (:stdout m))
                  (format "got %s\nwant %s"
                          (pr-str (:result m))
                          (pr-str expected))))))))))

(defn- emmy-sourced?
  "Emmy-sourced pages (from test/fixtures/emmy-sicm/chN_test.cljc) end
  with ' (Emmy)' in their dropdown name. They are tested by Emmy's
  own CI; the scrape corpus's :expected/:translated doesn't apply
  there since the page content comes from a different source."
  [page-name]
  (str/ends-with? page-name " (Emmy)"))

(defn- check-emmy-page
  "For Emmy-sourced pages, just eval the page text in a fresh ns and
  hard-fail on any non-graphics error. The page text already has
  Emmy's own `(is …)` assertions embedded as `;;=>` comments — the
  ground truth is Emmy's CI."
  [page-name page-source]
  (let [ns         (eq/fresh-eval-ns! (ns-sym page-name))
        page-forms (read-all-forms page-source)
        eval-log   (silence-warnings
                     (fn [] (mapv #(try-eval-form ns %) page-forms)))]
    (testing page-name
      (doseq [[i log-entry] (map-indexed vector eval-log)]
        (when-let [t (:error log-entry)]
          (is false
              (format "page form #%d eval threw: %s\n  form: %s\n  cause: %s"
                      i
                      (.getMessage ^Throwable t)
                      (pr-str (:form log-entry))
                      (root-cause-msg t))))))))

(deftest sicm-pages-end-to-end
  (doseq [[page-name page-source] section-pages]
    (cond
      (emmy-sourced? page-name)
      (check-emmy-page page-name page-source)

      :else
      (if-let [k (page-name->section-key page-name)]
        (check-section-page k page-name page-source)
        (testing page-name
          (is false (str "couldn't map page-name to section-key: " page-name)))))))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'sicm.page-eval-test)]
    (shutdown-agents)
    (System/exit (if (or (pos? fail) (pos? error)) 1 0))))
