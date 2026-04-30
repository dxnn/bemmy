(ns scittle.emmy
  {:no-doc true}
  (:require [emmy.sci]
            [scittle.core :as scittle]))

(defn init []
  (scittle/register-plugin! ::emmy emmy.sci/config))
