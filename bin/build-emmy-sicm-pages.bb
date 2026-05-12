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
                                          (str "\n" indent ";;   "))))
          :true (.append out (str/trim body)))
        (recur out (long end)))
      (do (.append out (subs text i)) (str out)))))

(defn pp-str [form]
  (let [s (with-out-str (pp/with-pprint-dispatch pp/code-dispatch (pp/pprint form)))]
    (str/trimr s)))

(defn strip-namespaces [form]
  (walk/postwalk strip-known-namespace form))

(defn render-body-form [form]
  ;; Each form in the deftest body. with-literal-functions is unwrapped;
  ;; leading let is unwrapped. Then assertion markers are inserted.
  (let [unwrapped (unwrap-with-literal-functions form)]
    (if (= 1 (count unwrapped))
      (let [single (first unwrapped)
            [defs body] (unwrap-top-let single)]
        (concat defs body))
      ;; with-literal-functions case: defs + remaining body. The body
      ;; might itself be a let — recurse on the last element only.
      (let [[defs-from-wlf body-forms] [(butlast unwrapped) [(last unwrapped)]]
            ;; Find inner let in the (only) body form
            inner (first body-forms)
            [more-defs final-body] (unwrap-top-let inner)]
        (concat defs-from-wlf more-defs final-body)))))

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

(defn- prune-require-by-aliases
  "Given a `(require '[ns :as a :refer […]] …)` helper form and the set
  of `:as` aliases this page actually references via `alias/sym`, drop
  quoted specs whose `:as` alias isn't in `used` AND that have no
  `:refer` list. Specs with `:refer` are conservatively retained — the
  reffered names get used as bare symbols which the alias walker can't
  track. When the result is empty, return nil so the caller can drop
  the helper."
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
        kept (filter keep? (rest require-form))]
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

(defn- prepend-unmap-if-def
  "If the chunk's first top-level form is a `(def X …)`, `(defn X …)`,
  or `(defn- X …)`, prepend `(ns-unmap *ns* 'X)` on its own line above
  the chunk. SCI throws (rather than warns, as JVM Clojure does) when a
  `def` collides with a name already `:refer`'d into the user ns via
  `emmy.env` or one of the `emmy.mechanics.*` namespaces; the canonical
  case here is `(def simplify (comp e/freeze e/simplify))` emitted as a
  helper. Unmap is idempotent for names that aren't currently reffered,
  so this is safe to apply uniformly."
  [chunk]
  (let [trimmed (str/triml chunk)
        f (read-form-or-nil trimmed)
        n (when (and (seq? f)
                     (contains? '#{def defn defn-} (first f))
                     (symbol? (second f)))
            (second f))]
    (if n
      (str "(ns-unmap *ns* '" n ")\n" trimmed)
      chunk)))

(defn- inject-defn-unmaps
  "Walk top-level chunks of `text` (split on blank lines, the
  inter-form separator used by the renderer) and prepend a per-defn
  `(ns-unmap *ns* 'X)` to each chunk whose lead form is a def/defn.
  Mirrors the same fix in build-sicm-pages.bb."
  [text]
  (->> (str/split text #"\n[\t ]*\n")
       (map prepend-unmap-if-def)
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
                      (map (comp assertion-marker strip-namespaces))
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
                    ";; ============================================================\n")]
    (->> [header
          (when helper-text
            (str ";; --- helpers from " source-file " ---\n" helper-text))
          rendered]
         (remove nil?)
         (str/join "\n\n")
         render-markers
         inject-defn-unmaps
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
