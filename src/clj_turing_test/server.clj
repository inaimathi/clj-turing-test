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

(def clients (atom {}))

(defn websocket [req]
  (server/with-channel req conn
    (swap! clients assoc conn true)
    (println conn " connected")
    (server/on-close conn (fn [status]
                            (swap! clients dissoc conn)
                            (println conn " disconnected")))))

(defn broadcast! [msg]
  (doseq [client @clients]
    (server/send! (key client) msg false)))

(defn home [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:head [:script {:src "/js/turing.js" :type "application/javascript"}]]
           [:body
            [:div {:id "screen"}
             [:h3 "Welcome to"]
             [:h1 "Turing Test"]]]])})

(def routes
  ["" [["" home]
       ["/" home]
       ["/socket" websocket]
       ["/js/turing.js" (serve-resource "turing.js" "application/javascript")]]])

(defn run [port]
  (println "Listening on port" port "...")
  (server/run-server
   (-> routes
       bring/make-handler
       wrap-params
       wrap-session)
   {:port (edn/read-string port)}))
