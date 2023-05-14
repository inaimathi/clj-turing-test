(ns clj-turing-test.server
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [hiccup.core :as hic]
            [org.httpkit.server :as server]
            [bidi.ring :as bring]

            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]))

(defn serve-resource
  [name content-type]
  (fn [req]
    {:status 200
     :headers {"Content-Type" (str content-type "; charset=utf-8")}
     :body (slurp (io/resource name))}))

(def games (atom {}))

(defn game-socket [req]
  (server/with-channel req conn
    (let [game-id (get-in req [:route-params :game-id])]
      (swap! games #(assoc-in % [game-id :sockets conn] true))
      (println conn " connected")
      (server/on-close
       conn
       (fn [status]
         (swap! games #(update-in % [game-id :sockets] dissoc conn))
         (println conn " disconnected")))
      (server/on-receive
       conn
       (fn [data]
         (println "RECEIVED: " conn data req))))))

(defn broadcast! [game-id msg]
  (doseq [game (get @games game-id)]
    (server/send! (key game) msg false)))

(defn public-address! [msg]
  (doseq [game-id (keys @games)]
    (broadcast! game-id msg)))

(defn game-page [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:head [:script {:src "/js/turing.js" :type "application/javascript"}]]
           [:body
            [:div {:id "screen"}
             [:h3 "Welcome to"]
             [:h1 "Turing Test"]]]])})

(defn home-page [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:body
            [:div {:id "games"}
             [:ul
              (map
               (fn [[game-id struct]]
                 [:li
                  [:a {:href (str "/game/" game-id "/")} game-id]
                  [:pre (str [game-id struct])]])
               @games)]]]])})

(defn tap [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:head]
           [:body
            [:pre (str req)]]])})

(def routes
  ["" [["" home-page]
       ["/" home-page]
       [["/game/" :game-id] {"/" game-page
                             "/socket" game-socket
                             "/history" tap
                             "/message" tap}]
       ["/js/turing.js" (serve-resource "turing.js" "application/javascript")]]])


(defonce +server+ (atom nil))

(defn start
  [port]
  (println "Listening on port" port "...")
  (server/run-server
   (-> routes
       bring/make-handler
       wrap-params
       wrap-session)
   {:port (edn/read-string port)}))

(defn stop
  []
  (when-let [s @+server+]
    (s :timeout 100)
    (reset! +server+ nil)
    (println "Stopped...")))

(defn restart
  [port]
  (stop)
  (start port))
