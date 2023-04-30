(ns clj-turing-test.front-end.core
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(defonce messages (r/atom []))

(defonce current-message (r/atom ""))

(defn $ [selector]
  (.querySelector js/document selector))

(defn message-list [messages]
  (->> messages
       (map (fn [m] [:li m]))
       (cons :ul)
       vec))

(defn send-message! []
  (swap! messages #(conj % @current-message))
  (reset! current-message ""))

(defn game []
  [:div {}
   [:h3 "Welcome to"]
   [:h1 "Turing Test!!!"]
   [:h5 "(but from the FE though...)"]
   (message-list @messages)
   [:input {:type "text"
            :value @current-message
            :class "messageInput"
            :on-change #(reset! current-message (.. % -target -value))
            :onKeyDown (fn [ev]
                         (when (= (.-keyCode ev) 13)
                           (.preventDefault ev)
                           (send-message!)))}]
   [:button {:on-click send-message! } "Submit"]])

(defn ^:export run []
  (rd/render [game] ($ "body")))

(.log js/console "HELLO FROM CLJS")

(defn on-load [callback]
  (.addEventListener
   js/window
   "DOMContentLoaded"
   callback))

(on-load
 (fn []
   (.log js/console "DOMContentLoaded callback")
   (run)))
