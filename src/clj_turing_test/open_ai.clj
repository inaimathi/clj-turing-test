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

(defn -api-audio [endpoint filename {:keys [prompt model response-format temperature language version] :or {prompt "" model "whisper-1" response-format :json temperature 1.0 language "en" version "v1"}}]
  (let [file (io/file filename)]
    (assert (.exists file))
    (assert (>= 1.0 temperature 0.0))
    (assert (#{:json :text :srt :verbose_json :vtt} response-format))
    (let [callback (fn [{:keys [status headers body error]}]
                     (if error
                       (println "FAILED: " error)
                       (json/decode body)))
          req-props {:url (str "https://api.openai.com/" version "/audio/" endpoint)
                     :headers {"Authorization" (str "Bearer " API_KEY)
                               "Content-Type" "multipart/form-data"}
                     :method :post
                     :multipart [{:name "language" :content language}
                                 {:name "temperature" :content (str temperature)}
                                 {:name "model" :content model}
                                 {:name "prompt" :content prompt}
                                 {:name "response_format" :content (name response-format)}
                                 {:name "file" :content file :filename (.getName file)}]}]
      @(http/request req-props callback))))

(defn transcription [filename & {:keys [prompt response-format temperature language] :as opts}]
  (-api-audio "transcriptions" filename opts))

(defn translation [filename & {:keys [prompt response-format temperature] :as opts}]
  (-api-audio "translations" filename opts))

(defn -image-urls [response]
  (map #(get % "url") (get response "data")))

(defn image [prompt & {:keys [count size response-format user] :or {count 1 size 1024 response-format :url}}]
  (assert (>= 10 count 1))
  (assert (#{256 512 1024} size))
  (assert (#{:url :b64_json} response-format))
  (let [res (-api-openai
             "images/generations"
             :body {:prompt prompt
                    :n count
                    :size (str size "x" size)
                    :response_format response-format})]
    (if (= :url response-format)
      (-image-urls res)
      res)))

(defn -api-image-file [endpoint filename {:keys [count version size response-format user mask prompt] :or {version "v1" size 1024 mask (str "mask-" size ".png") response-format :url count 1}}]
  (assert (#{:url :b64_json} response-format))
  (assert (#{256 512 1024} size))
  (assert (>= 10 count 1))
  (let [file (io/file filename)
        mfile (io/file (io/resource mask))]
    (assert (.exists file))
    (assert (.exists mfile))
    (let [callback (fn [{:keys [status headers body error]}]
                     (if error
                       (println "FAILED: " error)
                       (json/decode body)))
          multipart [{:name "n" :content (str count)}
                     {:name "size" :content (str size "x" size)}
                     {:name "response_format" :content (name response-format)}
                     {:name "mask" :content mfile :filename (.getName mfile)}
                     {:name "image" :content file :filename (.getName file)}]
          req-props {:url (str "https://api.openai.com/" version "/images/" endpoint)
                     :headers {"Authorization" (str "Bearer " API_KEY)
                               "Content-Type" "multipart/form-data"}
                     :method :post
                     :multipart (if prompt
                                  (conj multipart {:name "prompt" :content prompt})
                                  multipart)}]
      @(http/request req-props callback))))

(defn image-edit [image prompt & {:keys [count size response-format user mask] :as opts}]
  (let [res (-api-image-file "edits" image (assoc opts :prompt prompt))]
    (if (= :url response-format)
      (-image-urls res)
      res)))

(defn image-variations [image & {:keys [count size response-format user] :as opts}]
  (let [res (-api-image-file "variations" image opts)]
    (if (= :url response-format)
      (-image-urls res)
      res)))
