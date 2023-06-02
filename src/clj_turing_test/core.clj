(ns clj-turing-test.core
  (:require [clojure.string :as str]
            [cheshire.core :as json]

            [clj-turing-test.open-ai :as ai]
            [clj-turing-test.server :as server]))


(defn -main
  ([] (-main "4646"))
  ([port]
   (.start server/player)
   (server/start port)))
