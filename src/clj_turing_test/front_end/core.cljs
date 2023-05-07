(ns clj-turing-test.front-end.core
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(defonce username (r/atom "inaimathi"))

(defonce messages
  (r/atom []))

(defonce socket (r/atom nil))

(defonce ws-messages (r/atom []))

(defn ws-list [ms]
  (->> ms (map (fn [m] [:li [:code (.-data m)]]))
       (cons :ul) vec))

(defn ws-endpoint []
  (let [loc (.-location js/window)
        url (str (if (= "https:" (.-protocol loc)) "wss:" "ws:")
                 "//"
                 (.-host loc)
                 "/game/game001/socket")]
    (.log js/console "WS: " url)
    url))

(defonce current-message (r/atom ""))

(defn $ [selector]
  (.querySelector js/document selector))

(defn message-list [messages]
  (->> messages
       (map (fn [m] [:li (:name m) ": " (:content m)]))
       (cons :ul)
       vec))

(defn send-message! []
  (swap! messages #(conj % {:role "user" :name @username :content @current-message}))
  (if-let [s @socket]
    (.send s @current-message))
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
  (rd/render [game] ($ "#screen")))

(.log js/console "HELLO FROM CLJS")

(defn on-load [callback]
  (.addEventListener
   js/window
   "DOMContentLoaded"
   callback))

(on-load
 (fn []
   (.log js/console "DOMContentLoaded callback")
   (run)
   (let [ws (js/WebSocket. (ws-endpoint))]
     (reset! socket ws )
     (aset
      ws "onmessage"
      (fn [m]
        (.log js/console "Received " m)
        (swap! ws-messages #(conj % m)))))))
