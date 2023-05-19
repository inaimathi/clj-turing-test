(ns clj-turing-test.open-ai
  (:require [clojure.string :as str]
            [clojure.java.io :as io]

            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [cheshire.core :as json]))

;; Change default client for your whole application:
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def API_KEY "LOLNOPE")

(defn -api-openai [endpoint & {:keys [body version method] :or {version "v1" method :get}}]
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

(defn models [] (get (-api-openai "models") "data"))

(defn completion [prompt]
  (-api-openai
   "completions"
   :body {:model "text-davinci-003"
          :prompt prompt
          :max_tokens 16
          :temperature 1}))

(defn chat [messages]
  (-api-openai
   "chat/completions"
   :body {:model "gpt-3.5-turbo"
          :messages messages}))

(defn -image-urls [response]
  (map #(get % "url") (get response "data")))

(defn image [prompt & {:keys [count size response-format user] :or {count 1 size 1024 response-format :url}}]
  (assert (>= 10 count 1))
  (assert (#{256 512 1024} size))
  (assert (#{:url :b64_json} response-format))
  (-image-urls
   (-api-openai
    "images/generations"
    :body {:prompt prompt
           :n count
           :size (str size "x" size)
           :response_format response-format})))

;; (defn -api-openai-file [endpoint filename {:keys [count version size response-format user mask prompt] :or {version "v1"}}]
;;   (let [callback (fn [{:keys [status headers body error]}]
;;                    (if error
;;                      (println "FAILED: " error)
;;                      (json/decode body)))
;;         req-props {:url (str "https://api.openai.com/" version "/images/" endpoint)
;;                    :headers {"Authorization" (str "Bearer " API_KEY)}
;;                    :method :post
;;                    :multipart [{:name "n" :content (str count)}
;;                                {:name "size" :content (str size "x" size)}
;;                                {:name "response_format" :content (name response-format)}
;;                                {:name "image" :content (io/file filename) :filename "test.png"}]}]
;;     ;; (println (str req-props))
;;     @(http/request req-props callback)))

;; (defn image-edit [image prompt & {:keys [count size response-format mask user] :or {count 1 size 1024 response-format :url} :as props}]
;;   (assert (>= 10 count 1))
;;   (assert (#{256 512 1024} size))
;;   (assert (#{:url :b64_json} response-format))
;;   (-api-openai-file
;;    "edits" image
;;    {:mask mask
;;     :prompt prompt
;;     :n count
;;     :size (str size "x" size)
;;     :response_format response-format}))

(defn image-variations [image & {:keys [count size response-format user] :or {count 1 size 1024 response-format :url} :as props}]
  (assert (>= 10 count 1))
  (assert (#{256 512 1024} size))
  (assert (#{:url :b64_json} response-format))
  (let [callback (fn [{:keys [status headers body error]}]
                   (if error
                     (println "FAILED: " error)
                     (json/decode body)))
        req-props {:url (str "https://api.openai.com/v1/images/variations")
                   :headers {"Authorization" (str "Bearer " API_KEY)}
                   :method :post
                   :multipart [{:name "n" :content (str count)}
                               {:name "size" :content (str size "x" size)}
                               {:name "response_format" :content (name response-format)}
                               {:name "image" :content (io/file image) :filename image}]}]
    ;; (println (str req-props))
    (-image-urls @(http/request req-props callback))))
