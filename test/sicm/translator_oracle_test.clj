(ns sicm.translator-oracle-test
  "Run Emmy's own SICM chapter tests
  (test/emmy/sicm/chN_test.cljc, vendored from
  github.com/mentat-collective/emmy) as a regression oracle for our
  compat shim + scittle plugin surface.

  Each `(is (= 'X Y))` in those files is a canonical assertion that
  passes on a stock Emmy install. We re-run them under our test-time
  environment so that any change to sicm.compat — added stubs,
  reshuffled requires, a missing dispatch — that would silently
  break Emmy's own examples shows up as a test failure here. It also
  serves as a 'known-good' oracle for our scrape→translate pipeline:
  if the equivalence-test's :expected values diverge from Emmy's
  authoritative answers in a chapter Emmy covers, that's a translator
  regression worth chasing."
  (:require [clojure.test :as t :refer [deftest]]
            ;; Force-load each chapter test ns. Each one carries its
            ;; own deftests; clojure.test picks them up when we
            ;; explicitly run their namespaces.
            emmy.sicm.ch1-test
            emmy.sicm.ch2-test
            emmy.sicm.ch3-test
            emmy.sicm.ch5-test
            emmy.sicm.ch6-test
            emmy.sicm.ch7-test))

(def ^:private chapter-test-namespaces
  '[emmy.sicm.ch1-test
    emmy.sicm.ch2-test
    emmy.sicm.ch3-test
    emmy.sicm.ch5-test
    emmy.sicm.ch6-test
    emmy.sicm.ch7-test])

(defn -main [& _]
  ;; Use apply so run-tests sees each ns as a separate argument.
  (let [{:keys [fail error]} (apply t/run-tests chapter-test-namespaces)]
    (shutdown-agents)
    (System/exit (if (or (pos? fail) (pos? error)) 1 0))))
