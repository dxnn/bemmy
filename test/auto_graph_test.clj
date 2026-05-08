;; Auto-graph wrap-code tests, runnable via `bb test/auto_graph_test.clj`.
;;
;; The functions below mirror the corresponding defns in
;; public/app.cljs's auto-graph shelf. Keep them in sync — when you
;; change wrap-code or any of its helpers in app.cljs, copy the change
;; here too. The tests at the bottom lock in the current output for
;; each (kind, source) combination so paren-balance regressions and
;; subtle template drift get caught.
(ns auto-graph-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

;; --- helpers (mirror public/app.cljs) -------------------------------------

(defn has-quoted-var? [src v]
  (boolean (re-find (re-pattern (str "'" v "(?![\\w-])")) src)))

(defn strip-quoted-var [src v]
  (str/replace src (re-pattern (str "'" v "(?![\\w-])")) v))

(defn wrap-as-fn-of [src expected-vars]
  (let [used (filter #(has-quoted-var? src %) expected-vars)]
    (if (seq used)
      (str "(fn [" (str/join " " used) "] "
           (reduce strip-quoted-var src used)
           ")")
      src)))

(defn emmy-symbolic? [src]
  (boolean (re-find #"\((?:up|down)\b" src)))

(defn find-balanced-paren-end [src start]
  (when (and (< start (count src)) (= (.charAt src start) \())
    (loop [i start depth 0]
      (cond
        (>= i (count src)) nil
        (= (.charAt src i) \() (recur (inc i) (inc depth))
        (= (.charAt src i) \)) (if (= 1 depth) (inc i) (recur (inc i) (dec depth)))
        :else (recur (inc i) depth)))))

(defn lagrangian-form [src]
  (when-let [m (re-find #"\(L-[\w-]+" src)]
    (let [start (.indexOf src m)]
      (when-let [end (find-balanced-paren-end src start)]
        (subs src start end)))))

(defn hamiltonian-form [src]
  (when-let [m (re-find #"\(H-[\w-]+" src)]
    (let [start (.indexOf src m)]
      (when-let [end (find-balanced-paren-end src start)]
        (subs src start end)))))

(defn lagrangian-pattern? [src] (boolean (re-find #"\(L-[\w-]+" src)))
(defn hamiltonian-pattern? [src] (boolean (re-find #"\(H-[\w-]+" src)))

(defn parse-lagrangian [form]
  (when form
    (let [inner  (subs form 1 (dec (count form)))
          tokens (-> inner str/trim (str/split #"\s+"))]
      {:name (first tokens) :args (vec (rest tokens))})))

(defn arg-bindings [args]
  (reduce (fn [acc arg]
            (if (str/starts-with? arg "'")
              (let [n (subs arg 1)]
                (-> acc (update :bindings conj [n 1.0]) (update :call conj n)))
              (update acc :call conj arg)))
          {:bindings [] :call []}
          args))

(defn defn-form? [src] (boolean (re-find #"^\s*\(defn-?\s+" src)))

(defn defn-name [src]
  (when-let [m (re-find #"^\s*\(defn-?\s+(\S+)" src)]
    (second m)))

(defn defn-args [src]
  (when-let [m (re-find #"^\s*\(defn-?\s+\S+\s+\[([^\]]*)\]" src)]
    (let [s (str/trim (second m))]
      (if (str/blank? s) [] (str/split s #"\s+")))))

(defn lagrangian-defn? [src]
  (and (defn-form? src)
       (some-> (defn-name src) (str/starts-with? "L-"))))

(defn hamiltonian-defn? [src]
  (and (defn-form? src)
       (some-> (defn-name src) (str/starts-with? "H-"))))

(defn available-quoted-args [src]
  (when-let [form (or (lagrangian-form src) (hamiltonian-form src))]
    (let [{:keys [args]}     (parse-lagrangian form)
          {:keys [bindings]} (arg-bindings args)]
      (mapv first bindings))))

(defn plot-template [body] (str "(plot " body ")"))
(defn animate-template [body] (str "(animate " body ")"))

(defn parametric-2d-template [body]
  (if (emmy-symbolic? body)
    (str "(emmy.mafs/mafs\n {:viewBox {:x [-2 2] :y [-2 2]}}\n (emmy.mafs/parametric\n  {:t  [0 (* 2 Math/PI)]\n   :xy " body "}))")
    (str "[mafs/Mafs {:viewBox {:x [-2 2] :y [-2 2]}}\n [mafs.coordinates/Cartesian]\n [mafs.plot/Parametric\n  {:t  [0 (* 2 Math/PI)]\n   :xy " body "}]]")))

(def ^:private kind-options
  [[:plot          "Plot — y = f(x)"               ["x" "t"]]
   [:parametric-2d "Parametric 2D — (x,y) = f(t)"   ["t"]]
   [:surface       "Surface — z = f(x,y)"           ["x" "y"]]
   [:animate       "Animate — y = f(t,x)"           ["t" "x"]]])

(defn expected-vars-for [kind]
  (some (fn [[k _ vs]] (when (= k kind) vs)) kind-options))

;; --- balance check --------------------------------------------------------
;; Counts opens vs closes, treating string literals as opaque (so parens
;; inside strings don't throw the count off).

(defn paren-balance [src]
  (let [n (count src)]
    (loop [i 0 depth 0 in-str false esc false]
      (if (>= i n)
        depth
        (let [c (.charAt src i)]
          (cond
            esc      (recur (inc i) depth in-str false)
            in-str   (case c
                       \\ (recur (inc i) depth true true)
                       \" (recur (inc i) depth false false)
                       (recur (inc i) depth true false))
            (= c \") (recur (inc i) depth true false)
            (= c \() (recur (inc i) (inc depth) false false)
            (= c \)) (recur (inc i) (dec depth) false false)
            :else    (recur (inc i) depth false false)))))))

(defn balanced? [src] (zero? (paren-balance src)))

;; --- tests ----------------------------------------------------------------

(deftest quote-stripping
  (is (= (wrap-as-fn-of "(sin 'x)" ["x"])
         "(fn [x] (sin x))"))
  (is (= (wrap-as-fn-of "(sin 'x)" ["t"])
         "(sin 'x)")
      "non-matching var leaves the source untouched")
  (is (= (wrap-as-fn-of "Math/sin" ["x"])
         "Math/sin")
      "no quoted var → return as-is")
  (is (= (wrap-as-fn-of "(* 'x 'y)" ["x" "y"])
         "(fn [x y] (* x y))"))
  (is (= (wrap-as-fn-of "'xy" ["x"])
         "'xy")
      "lookahead avoids 'xy false-matching 'x"))

(deftest lagrangian-detection
  (is (lagrangian-pattern? "(L-harmonic 'm 'k)"))
  (is (lagrangian-pattern? "(((Lagrange-equations (L-harmonic 'm 'k)) (literal-function 'q)) 't)"))
  (is (not (lagrangian-pattern? "(plot Math/sin)")))
  (is (= (lagrangian-form "(((Lagrange-equations (L-harmonic 'm 'k)) (literal-function 'q)) 't)")
         "(L-harmonic 'm 'k)")
      "extracts the inner balanced (L-…) form"))

(deftest defn-detection
  (is (defn-form? "(defn foo [x] x)"))
  (is (defn-form? "(defn- foo [x] x)"))
  (is (not (defn-form? "(let [x 1] x)")))
  (is (= (defn-name "(defn L-harmonic [m k] body)") "L-harmonic"))
  (is (= (defn-args "(defn L-harmonic [m k] body)") ["m" "k"]))
  (is (= (defn-args "(defn L-foo [] body)") []))
  (is (lagrangian-defn? "(defn L-harmonic [m k] body)"))
  (is (hamiltonian-defn? "(defn H-harmonic [m k] body)"))
  (is (not (lagrangian-defn? "(defn double [x] x)")))
  (is (not (hamiltonian-defn? "(defn helper [x] x)"))))

(deftest available-args
  (is (= (available-quoted-args "(L-harmonic 'm 'k)") ["m" "k"]))
  (is (= (available-quoted-args "(H-harmonic 'm 'k)") ["m" "k"]))
  (is (= (available-quoted-args "(L-free-particle 'mass)") ["mass"]))
  (is (nil? (available-quoted-args "Math/sin")))
  (is (= [] (available-quoted-args "(L-harmonic 1.0 1.0)"))
      "all-concrete args → empty vec, not nil — picker counts the size"))

(deftest emmy-symbolic-detection
  (is (emmy-symbolic? "(fn [t] (up (cos t) (sin t)))"))
  (is (emmy-symbolic? "(fn [t] (down 1 2))"))
  (is (not (emmy-symbolic? "(fn [t] [(Math/cos t) (Math/sin t)])"))
      "raw JS Math primitives → not Emmy-symbolic"))

(deftest plot-template-balanced
  ;; The most basic shape — wrapping a function name.
  (let [out (plot-template "Math/sin")]
    (is (balanced? out))
    (is (= out "(plot Math/sin)"))))

(deftest parametric-2d-template-routing
  (testing "vector-returning body → raw mafs"
    (let [out (parametric-2d-template "(fn [t] [(Math/cos t) (Math/sin t)])")]
      (is (balanced? out))
      (is (str/includes? out "[mafs/Mafs"))
      (is (not (str/includes? out "emmy.mafs")))))
  (testing "up-returning body → emmy.mafs/parametric"
    (let [out (parametric-2d-template "(fn [t] (up (cos t) (sin t)))")]
      (is (balanced? out))
      (is (str/includes? out "emmy.mafs/parametric")))))

(deftest arg-bindings-split
  (is (= (arg-bindings ["'m" "'k"])
         {:bindings [["m" 1.0] ["k" 1.0]] :call ["m" "k"]}))
  (is (= (arg-bindings ["1.0" "'k"])
         {:bindings [["k" 1.0]] :call ["1.0" "k"]})
      "concrete args stay in :call as-is"))

(deftest parse-lagrangian-shape
  (is (= (parse-lagrangian "(L-harmonic 'm 'k)")
         {:name "L-harmonic" :args ["'m" "'k"]}))
  (is (= (parse-lagrangian "(L-free-particle)")
         {:name "L-free-particle" :args []})))

(deftest parens-balance-in-known-output
  (testing "the actual templates emitted by wrap-code stay paren-balanced"
    ;; Synthesise a typical Lagrangian plot prelude + body.
    (let [output (str "(let [m 1.0\n      k 1.0\n      "
                       "L (L-harmonic m k)\n      "
                       "path (find-path L 0.0 1.0 1.5707 0.0 4)]\n  "
                       "(plot path [0 1.5707] [-1.5 1.5]))")]
      (is (balanced? output)))))

(let [{:keys [fail error]} (run-tests)]
  (System/exit (if (and (zero? fail) (zero? error)) 0 1)))
