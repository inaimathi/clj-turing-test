(ns clj-turing-test.server
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [hiccup.core :as hic]
            [org.httpkit.server :as server]
            [bidi.ring :as bring]

            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]

            [clj-turing-test.model :as model]))

(defn -sess [req]
  (if (get-in req [:session :user-id])
    (:session req)
    (assoc (:session req) :user-id (str (java.util.UUID/randomUUID)))))

(defn -uid [req] (get-in req [:session :user-id]))

(defn serve-resource
  [name content-type]
  (fn [req]
    {:status 200
     :headers {"Content-Type" (str content-type "; charset=utf-8")}
     :body (slurp (io/resource name))
     :session (-sess req)}))

(def games (atom {}))

(defn game-socket [req]
  (if-let [user-id (-uid req)]
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
           (println "RECEIVED: "
                    (with-out-str
                      (pp/pprint
                       {:conn conn
                        :user-id user-id :game-id game-id
                        :data data :req req})))))))))

(defn broadcast! [game-id msg & {:keys [ignore] :or {ignore #{}}}]
  (doseq [entry (get @games game-id)]
    (if (not (ignore (val entry)))
      (server/send! (key entry) msg false))))

(defn public-address! [msg]
  (doseq [game-id (keys @games)]
    (broadcast! game-id msg)))

(defn new-game-page [req]
  (println (str req))
  (let [game-id (str (java.util.UUID/randomUUID))]
    (swap! games #(update-in % [game-id :model] (fn [_] (model/mk-turing-test ["inaimathi"] 3))))
    {:status 302
     :headers {"Location" (str "/game/" game-id "/")}
     :session (-sess req)}))

(defn game-page [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:head [:script {:src "/js/turing.js" :type "application/javascript"}]]
           [:body
            [:div {:id "screen"}
             [:h3 "Welcome to"]
             [:h1 "Turing Test"]]]])
   :session (-sess req)})

(defn home-page [req]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (hic/html
            [:html
             [:body
              [:h1 "Turing Test"]
              [:div {:id "games"}
               [:ul
                (map
                 (fn [[game-id struct]]
                   [:li
                    [:a {:href (str "/game/" game-id "/")} game-id]
                    [:pre (with-out-str (pp/pprint [game-id struct]))]])
                 @games)]]
              [:a {:href "/new-game"} "New Game"]]])
     :session (-sess req)})

(defn tap [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hic/html
          [:html
           [:head]
           [:body
            [:pre (str req)]]])
   :session (-sess req)})

(def routes
  ["" [["" home-page]
       ["/" home-page]
       ["/new-game" new-game-page]
       [["/game/" :game-id] {"" game-page
                             "/" game-page
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
