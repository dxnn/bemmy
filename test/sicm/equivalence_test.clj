(ns sicm.equivalence-test
  "Behavioral-equivalence tests for the SICM→Emmy translator.

  Reads the pre-translated corpus (test/fixtures/sicm-snippets.translated.edn,
  produced by `node test/translate-corpus.js`), evaluates each translated
  snippet inside a fresh-per-chapter namespace seeded with emmy.env, and —
  for entries that carry an `:expected` field harvested from the SICM book's
  printed scmutils output — checks that the Emmy result matches.

  Snippets without `:expected` still run; they're carrying definitions that
  later snippets in the chapter depend on."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]
            [clojure.walk :as walk]
            emmy.env))

(def corpus
  (-> "test/fixtures/sicm-snippets.translated.edn" slurp edn/read-string))

(defn read-all-forms [^String src]
  (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. src))]
    (loop [forms []]
      (let [f (read {:eof ::eof :read-cond :allow} rdr)]
        (if (= f ::eof) forms (recur (conj forms f)))))))

(defn fresh-eval-ns! [sym]
  (when (find-ns sym) (remove-ns sym))
  (require 'emmy.matrix 'sicm.compat)
  (let [n (create-ns sym)
        emmy-syms (vec (keys (ns-publics 'emmy.env)))]
    (binding [*ns* n]
      (clojure.core/refer 'clojure.core :exclude emmy-syms)
      (clojure.core/refer 'emmy.env)
      ;; emmy.env doesn't re-export the row/column accessors; pull them in
      ;; explicitly so translated `m:nth-row`/`m:nth-col` resolves.
      (clojure.core/refer 'emmy.matrix :only '[row column])
      ;; Intern (not refer) sicm.compat's publics into the test ns.
      ;; A bare (refer 'sicm.compat) would pull names in as refers,
      ;; and SICM pages that legitimately redefine book names like
      ;; L-harmonic would then trip JVM's refer-shadow warning (and,
      ;; more importantly, SCI's hard throw of the same condition in
      ;; the browser). We do the intern unconditionally rather than
      ;; only when not already resolved — sicm.compat is *supposed*
      ;; to override several emmy.env names with SICM-shape wrappers
      ;; (`evolve` taking 5 args, `state-advancer` taking a tol, etc.).
      ;; Skipping when emmy.env already supplies the name would
      ;; bypass those wrappers and break SICM-style page text.
      (doseq [[s v] (ns-publics 'sicm.compat)]
        (intern n s (deref v))))
    n))

(defn eval-forms-in-ns [ns forms]
  (binding [*ns* ns]
    (last (mapv eval forms))))

(defn norm
  "Aggressively flatten an s-expression string for comparison: drop the
  Scheme/Clojure container distinction, drop bigint N-suffix, normalize
  Scheme booleans, drop whitespace."
  [s]
  (-> s
      (str/replace #"#t\b" "true")
      (str/replace #"#f\b" "false")
      (str/replace #"#\(" "(")
      (str/replace #"\[" "(")
      (str/replace #"\]" ")")
      (str/replace #"(\d)N\b" "$1")
      (str/replace #"\s+" "")))

(defn readable?
  "True iff the string contains a single readable Clojure form (and possibly
  trailing whitespace). Used to gate out LaTeX-rendered `expected` values
  from the SICM book, which we can't compare structurally."
  [s]
  (try
    (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. s))]
      (let [f (read {:eof ::eof :read-cond :allow} rdr)]
        (not= f ::eof)))
    (catch Throwable _ false)))

(defn numeric-equiv? [a b]
  (try
    (let [an (Double/parseDouble (str/trim a))
          bn (Double/parseDouble (str/trim b))
          d  (Math/abs (- an bn))]
      (or (< d 1e-9)
          (< (/ d (max (Math/abs an) (Math/abs bn) 1.0)) 1e-9)))
    (catch Throwable _ false)))

(defn read-one [^String s]
  (try
    (with-open [rdr (java.io.PushbackReader. (java.io.StringReader. s))]
      (let [f (read {:eof ::eof :read-cond :allow} rdr)]
        (when-not (= f ::eof) f)))
    (catch Throwable _ nil)))

(defn classify-symbols
  "Walk `form` and partition free symbols (those not in `known?`) into
  `:scalars` (only ever appear as a leaf) and `:funcs` (appear as the head of
  an application, mapped to the arity observed). If the same symbol is used
  with multiple arities, we conservatively give up by returning nil."
  [form known?]
  (let [scalars (volatile! #{})
        funcs   (volatile! {})
        give-up (volatile! false)
        walk (fn walk [x]
               (cond
                 @give-up nil
                 (and (sequential? x) (seq x))
                 (let [[h & args] x]
                   (when (and (symbol? h) (not (known? h)))
                     (let [arity (count args)
                           prev  (@funcs h)]
                       (cond
                         (nil? prev) (vswap! funcs assoc h arity)
                         (not= prev arity) (vreset! give-up true))))
                   (doseq [a args] (walk a)))
                 (symbol? x)
                 (when-not (known? x)
                   (when-not (contains? @funcs x)
                     (vswap! scalars conj x)))))]
    (walk form)
    (when-not @give-up
      ;; A symbol classified as :func shouldn't also be in :scalars from a
      ;; prior leaf occurrence.
      {:scalars (apply disj @scalars (keys @funcs))
       :funcs   @funcs})))

(defn random-rational []
  (/ (inc (rand-int 19)) (inc (rand-int 5))))

(defn random-poly-fn
  "Random polynomial function of the given arity, with small rational
  coefficients. Picks degree ≥ 3 in each variable so D, D² of f are nonzero.
  Built with Emmy's generic ops so `D` can autodiff through it."
  [arity]
  (let [+_ emmy.env/+
        *_ emmy.env/*
        coeffs (vec (repeatedly (* 4 arity) random-rational))]
    (case arity
      1 (let [[a b c d] coeffs]
          (fn [x] (+_ a (*_ b x) (*_ c x x) (*_ d x x x))))
      2 (let [[a b c d e f g h] coeffs]
          (fn [x y] (+_ a (*_ b x) (*_ c y) (*_ d x x) (*_ e y y)
                        (*_ f x y) (*_ g x x x) (*_ h y y y))))
      (fn [& xs]
        (apply +_ (first coeffs)
               (map-indexed
                 (fn [i v]
                   (let [c1 (nth coeffs (mod (inc (* 3 i))     (count coeffs)))
                         c2 (nth coeffs (mod (+ 2 (* 3 i)) (count coeffs)))
                         c3 (nth coeffs (mod (+ 3 (* 3 i)) (count coeffs)))]
                     (+_ (*_ c1 v) (*_ c2 v v) (*_ c3 v v v))))
                 xs))))))

(defn build-substitution
  "Given a classification, return a map from free symbol → concrete value
  (rational for scalars, fn for functions)."
  [{:keys [scalars funcs]}]
  (merge
    (zipmap scalars (repeatedly random-rational))
    (into {} (map (fn [[s a]] [s (random-poly-fn a)]) funcs))))

(defn substitute [form bindings]
  (walk/postwalk
    (fn [x] (if (and (symbol? x) (contains? bindings x)) (bindings x) x))
    form))

(defn coerce-number [v]
  (cond
    (number? v) (double v)
    (and (sequential? v) (every? number? v)) (double (first v))
    :else nil))

(defn numeric-close? [a b]
  (and (number? a) (number? b)
       (let [da (double a) db (double b)
             diff (Math/abs (- da db))]
         (or (< diff 1e-9)
             (< (/ diff (max (Math/abs da) (Math/abs db) 1.0)) 1e-7)))))

(defn structurally-equiv-numeric?
  "Both `a` and `b` should be either numbers or comparable structured values
  (Emmy structures, Clojure sequentials of numbers). Walks them in lockstep."
  [a b]
  (cond
    (and (number? a) (number? b)) (numeric-close? a b)
    (and (sequential? a) (sequential? b) (= (count a) (count b)))
    (every? true? (map structurally-equiv-numeric? a b))
    :else
    (try (numeric-close? (double a) (double b)) (catch Throwable _ false))))

(def ^:private clj-special
  '#{& fn fn* let let* if do quote var . new throw try catch finally
     loop recur set!})

(defn known?
  "Computed fresh each call so lazily-loaded Emmy sub-namespaces (e.g.
  emmy.matrix) don't escape classification once they get pulled in."
  [s]
  (or (contains? clj-special s)
      (contains? (ns-map 'emmy.env) s)
      (contains? (ns-map 'clojure.core) s)))

(defn- act->form [actual]
  (read-one (try (pr-str (emmy.env/simplify actual))
                 (catch Throwable _ (pr-str actual)))))

(defn- compatible-classifications? [a b]
  (and a b
       (= (set (keys (:funcs a))) (set (keys (:funcs b))))
       (every? (fn [[s ar]] (= ar (get (:funcs b) s))) (:funcs a))))

(defn algebraic-equiv?
  "Build a let that binds each free symbol — `(literal-function 'f)` for
  functions, `'sym` for scalars — eval the expected form in `eval-ns`, then
  compare to `actual` via Emmy value equality, falling back to subtract +
  simplify + zero?. Handles forms with literal-function `D` and matrix /
  structure values."
  [eval-ns actual expected-form classification]
  (let [{:keys [scalars funcs]} classification
        letform `(let [~@(mapcat (fn [s] [s `(quote ~s)]) scalars)
                       ~@(mapcat (fn [s] [s `(emmy.env/literal-function (quote ~s))])
                                 (keys funcs))]
                   ~expected-form)]
    (try
      (let [exp-val (binding [*ns* eval-ns] (eval letform))]
        (or (= actual exp-val)
            (try
              (let [diff (emmy.env/- actual exp-val)
                    simp (emmy.env/simplify diff)]
                (or (emmy.env/zero? simp)
                    (and (number? simp) (numeric-close? (double simp) 0.0))))
              (catch Throwable _ false))))
      (catch Throwable _ false))))

(defn numeric-probe-equiv?
  "Numeric-probe equivalence for forms whose only free symbols are scalars:
  substitute random rationals consistently in both forms, eval, compare.
  Repeats over several seeds. Skips when free function symbols are present —
  those go through `algebraic-equiv?` instead."
  [eval-ns actual-form expected-form classification & {:keys [trials] :or {trials 4}}]
  (when (empty? (:funcs classification))
    (try
      (every?
        (fn [_]
          (let [bindings (build-substitution classification)
                a-sub (substitute actual-form bindings)
                e-sub (substitute expected-form bindings)
                a-val (binding [*ns* eval-ns] (eval a-sub))
                e-val (binding [*ns* eval-ns] (eval e-sub))]
            (structurally-equiv-numeric? a-val e-val)))
        (range trials))
      (catch Throwable _ false))))

(defn minimize-result-equiv?
  "Emmy's `minimize` returns a map of {:result … :value … :iterations …};
  scmutils prints a 3-tuple `(error optimum iterations)`. Only the optimum
  is implementation-stable (and only to ~5 sig figs at convergence), so we
  compare just that with a relaxed tolerance."
  [actual exp-form]
  (when (and (map? actual)
             (every? #(contains? actual %) [:result :value :iterations])
             (sequential? exp-form)
             (= 3 (count exp-form))
             (every? number? exp-form))
    (let [a   (double (:value actual))
          b   (double (nth exp-form 1))
          rel (/ (Math/abs (- a b)) (max (Math/abs a) (Math/abs b) 1.0))]
      (< rel 1e-5))))

(defn semantic-equiv?
  "Try multiple equivalence strategies in order of cost. Returns true if any
  succeeds."
  [eval-ns actual expected-str]
  (when-let [exp-form (read-one expected-str)]
    (or
      (minimize-result-equiv? actual exp-form)
      (when-let [exp-cls (classify-symbols exp-form known?)]
        (or
          ;; Algebraic path needs only the expected: build the let, eval it,
          ;; compare directly to `actual`. Works even when `actual` doesn't
          ;; round-trip through pr-str (matrices, opaque records, ...).
          (algebraic-equiv? eval-ns actual exp-form exp-cls)
          ;; Numeric probe additionally requires `actual` to be re-readable
          ;; so we can substitute symbols in both forms in lockstep.
          (when-let [act-form (act->form actual)]
            (when-let [act-cls (classify-symbols act-form known?)]
              (when (compatible-classifications? act-cls exp-cls)
                (let [combined {:scalars (into (:scalars act-cls) (:scalars exp-cls))
                                :funcs   (merge (:funcs act-cls) (:funcs exp-cls))}]
                  (numeric-probe-equiv? eval-ns act-form exp-form combined))))))))))

(defn classify-joint
  "Classify free symbols across a sequence of forms together, so a name that
  appears as a leaf in one form and as a function head in another is
  consistently tagged as a function."
  [forms known?]
  (let [classifications (keep #(when % (classify-symbols % known?)) forms)
        funcs   (reduce merge {} (map :funcs classifications))
        scalars (apply disj (reduce into #{} (map :scalars classifications))
                       (keys funcs))]
    {:scalars scalars :funcs funcs}))

(defn algebraic-forms-equiv?
  "Bind free symbols (literal-function for funcs, 'sym for scalars) per
  the supplied classification, eval both forms in `eval-ns`, then compare
  via Emmy = or simplify-of-difference."
  [eval-ns a-form e-form classification]
  (let [{:keys [scalars funcs]} classification
        wrap (fn [form]
               `(let [~@(mapcat (fn [s] [s `(quote ~s)]) scalars)
                      ~@(mapcat (fn [s] [s `(emmy.env/literal-function (quote ~s))])
                                (keys funcs))]
                  ~form))]
    (try
      (let [a-val (binding [*ns* eval-ns] (eval (wrap a-form)))
            e-val (binding [*ns* eval-ns] (eval (wrap e-form)))]
        (or (= a-val e-val)
            (try
              (let [d (emmy.env/- a-val e-val)
                    s (emmy.env/simplify d)]
                (or (emmy.env/zero? s)
                    (and (number? s) (numeric-close? (double s) 0.0))))
              (catch Throwable _ false))))
      (catch Throwable _ false))))

(defn ellipsis-stdout-equiv?
  "When `expected` ends with `...`, treat it as a truncated print transcript
  and check that `stdout` matches line-by-line, falling back to algebraic
  equivalence per line for terms reordered by Emmy's printer. Free symbols
  are classified jointly across all lines so e.g. `f` in `(f t)` is tagged
  as a function consistently with `f` in `((D f) t)`."
  [eval-ns stdout expected]
  (when (and (string? stdout) (seq stdout))
    (let [exp-lines (str/split-lines expected)]
      (when (and (seq exp-lines)
                 (= "..." (str/trim (last exp-lines))))
        (let [target  (->> (butlast exp-lines) (remove str/blank?) vec)
              actuals (->> (str/split-lines stdout) (remove str/blank?) vec)]
          (when (>= (count actuals) (count target))
            (let [exp-forms (mapv read-one target)
                  act-forms (mapv read-one (subvec actuals 0 (count target)))
                  cls (classify-joint (concat exp-forms act-forms) known?)]
              (every? (fn [i]
                        (or (= (norm (nth actuals i)) (norm (nth target i)))
                            (let [af (nth act-forms i) ef (nth exp-forms i)]
                              (when (and af ef)
                                (algebraic-forms-equiv? eval-ns af ef cls)))))
                      (range (count target))))))))))

(defn equivalent? [ns actual expected stdout]
  (let [simplified (try (emmy.env/simplify actual)
                        (catch Throwable _ actual))
        a-str (pr-str simplified)
        last-line (->> (str/split-lines expected)
                       (remove str/blank?)
                       last)
        candidates (remove nil? [expected last-line])]
    (or (boolean
          (some (fn [exp]
                  (or (= (norm a-str) (norm exp))
                      (numeric-equiv? a-str exp)))
                candidates))
        (boolean (semantic-equiv? ns actual expected))
        (boolean (ellipsis-stdout-equiv? ns stdout expected)))))

(defn try-eval [ns forms]
  (let [out (java.io.StringWriter.)]
    (try
      (let [r (binding [*out* out] (eval-forms-in-ns ns forms))]
        {:ok r :stdout (str out)})
      (catch Throwable t
        {:err t :stdout (str out)}))))

(def ^:private skip-counter (atom 0))

(defn- check-entry [n {:keys [section page idx translated expected]}]
  (let [forms (try (read-all-forms translated)
                   (catch Throwable t [::read-error t]))
        {:keys [ok err stdout]} (if (and (vector? forms)
                                         (= ::read-error (first forms)))
                                  {:err (second forms)}
                                  (try-eval n forms))]
    (when expected
      (testing (format "§%s p%s #%s" section page idx)
        (cond
          (not (readable? expected))
          (swap! skip-counter inc)

          err
          (let [cause (loop [t ^Throwable err] (if-let [c (.getCause t)] (recur c) t))]
            (is false (format "eval threw: %s\n  cause: %s\n  translated: %s"
                              (.getMessage ^Throwable err)
                              (.getMessage ^Throwable cause)
                              (pr-str translated))))

          :else
          (is (equivalent? n ok expected stdout)
              (format "got %s\nwant %s"
                      (pr-str ok) (pr-str expected))))))))

(deftest sicm-equivalence
  (reset! skip-counter 0)
  (doseq [[chapter entries] (sort-by key (group-by :chapter corpus))]
    (testing (str "ch" chapter)
      (let [n (fresh-eval-ns! (symbol (str "sicm.eval.ch" chapter)))]
        (doseq [entry entries]
          (check-entry n entry)))))
  (println (format "(skipped %d non-Scheme expected values)" @skip-counter)))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'sicm.equivalence-test)]
    (shutdown-agents)
    (System/exit (if (or (pos? fail) (pos? error)) 1 0))))
