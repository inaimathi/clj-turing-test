(ns clj-turing-test.open-ai
  (:require [clojure.string :as str]

            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [cheshire.core :as json]))

;; Change default client for your whole application:
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def API_KEY "LOLNOPE")

(defn api-openai [endpoint & {:keys [body version method] :or {version "v1" method :get}}]
  (let [url (str "https://api.openai.com/" version "/" endpoint)
        method (if body :post method)
        auth {"Authorization" (str "Bearer " API_KEY)}
        headers (if body (merge auth {"Content-Type" "application/json"}) auth)
        callback (fn [{:keys [status headers body error]}]
                   (if error
                     (println "FAILED: " error)
                     (json/decode body)))
        min-opts {:url url :method method :headers headers}
        opts (if body (merge min-opts {:body (json/encode body)}) min-opts)]
    @(http/request opts callback)))

(defn models [] (get (api-openai "models") "data"))

(defn completion [prompt]
  (api-openai
   "completions"
   :body {:model "text-davinci-003"
          :prompt prompt
          :max_tokens 16
          :temperature 1}))

(defn chat [messages]
  (api-openai
   "chat/completions"
   :body {:model "gpt-3.5-turbo"
          :messages messages}))
