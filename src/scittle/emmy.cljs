(ns scittle.emmy
  {:no-doc true}
  (:require [emmy.sci]
            [scittle.core :as scittle]))

(defn init []
  (scittle/register-plugin! ::emmy emmy.sci/config)
  ;; Pre-refer emmy.env so D, square, Lagrangian, etc. resolve at analysis
  ;; time without the user needing a (require ...) at the top of every snippet.
  (scittle/eval-string "(require '[emmy.env :refer :all])"))
