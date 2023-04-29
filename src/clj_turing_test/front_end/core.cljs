(ns clj-turing-test.front-end.core
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(.log js/console "HELLO FROM CLJS")

(defn on-load [callback]
  (.addEventListener
   js/window
   "DOMContentLoaded"
   callback))

(on-load
 (fn []
   (.log js/console "DOMContentLoaded callback")))
