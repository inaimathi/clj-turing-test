(ns clj-turing-test.open-ai
  (:require [clojure.string :as str]
            [clojure.java.io :as io]

            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [cheshire.core :as json]))

;; Change default client for your whole application:
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def API_KEY "LOLNOPE")

(defn map->multipart [m]
  (->> m
       (map
        (fn [[k v]]
          (let [res {:name (if (keyword? k) (name k) (str k))
                     :content (cond (keyword? v) (name v)
                                    (= java.io.File (class v)) v
                                    :else (str v))}]
            (if (= java.io.File (class v))
              (assoc res :filename (.getName v))
              res))))
       (into [])))

(defn -api-openai [endpoint & {:keys [body multipart version method] :or {version "v1" method :get}}]
  (assert (or (and body (not multipart))
              (and (not body) multipart)
              (and (not body) (not multipart))))
  (let [url (str "https://api.openai.com/" version "/" endpoint)
        method (if body :post method)
        auth {"Authorization" (str "Bearer " API_KEY)}
        content-type (cond body {"Content-Type" "application/json"}
                           multipart {"Content-Type" "multipart/form-data"}
                           :else {})
        callback (fn [{:keys [status headers body error]}]
                   (if error
                     (println "FAILED: " error)
                     body))
        min-opts {:url url :method method :headers (merge auth content-type)}
        opts (cond body (merge min-opts {:body (json/encode body)})
                   multipart (merge min-opts {:multipart multipart})
                   :else min-opts)]
    @(http/request opts callback)))

(defn models []
  (-> (-api-openai "models")
      json/decode
      (get "data")))

(defn completion [prompt]
  (json/decode
   (-api-openai
    "completions"
    :body {:model "text-davinci-003"
           :prompt prompt
           :max_tokens 2048
           :temperature 1})))

(defn chat [messages]
  (json/decode
   (-api-openai
    "chat/completions"
    :body {:model "gpt-3.5-turbo"
           :messages messages})))

(defn -api-audio [endpoint filename {:keys [prompt model response-format temperature language version] :or {prompt "" model "whisper-1" response-format :json temperature 1.0 language "en" version "v1"}}]
  (let [file (io/file filename)]
    (assert (.exists file))
    (assert (>= 1.0 temperature 0.0))
    (assert (#{:json :text :srt :verbose_json :vtt} response-format))
    (let [res (-api-openai
               (str "audio/" endpoint)
               :method :post
               :multipart (map->multipart
                           {:language language
                            :temperature temperature
                            :model model
                            :prompt prompt
                            :response_format response-format
                            :file file}))]
      (if (#{:json :verbose_json} response-format)
        (json/decode res)
        res))))

(defn transcription [filename & {:keys [prompt response-format temperature language] :as opts}]
  (-api-audio "transcriptions" filename opts))

(defn translation [filename & {:keys [prompt response-format temperature] :as opts}]
  (-api-audio "translations" filename opts))

(defn -image-resp [format response]
  (let [res-slot (name format)
        decoded (json/decode response)
        eget (fn [k] (get-in decoded ["error" k]))]
    (if (contains? decoded "error")
      (cond (str/starts-with? (eget "message") "Your request was rejected as a result of our safety system")
            {:state :error
             :type :safety-error}

            (= "server_error" (eget "type"))
            {:state :error :type :server-error}

            :else
            {:state :error
             :type (keyword (eget "type"))
             :code (eget "code")
             :param (eget "param")
             :message (eget "message")})
      (map #(get % res-slot) (get decoded "data")))))

(defn image-url->file [url path]
  @(http/get
    url {:as :byte-array}
    (fn [{:keys [status headers body error opts]}]
      (if (= 200 status)
        (with-open [w (java.io.BufferedOutputStream. (java.io.FileOutputStream. path))]
          (.write w body))
        error))))

(defn image [prompt & {:keys [count size response-format user] :or {count 1 size 1024 response-format :url}}]
  (assert (>= 10 count 1))
  (assert (#{256 512 1024} size))
  (assert (#{:url :b64_json} response-format))
  (-image-resp
   response-format
   (-api-openai
    "images/generations"
    :body {:prompt prompt
           :n count
           :size (str size "x" size)
           :response_format response-format})))

(defn image-edit [filename prompt & {:keys [mask count size response-format user] :or {size 1024 response-format :url count 1} :as opts}]
  (assert (#{:url :b64_json} response-format))
  (assert (#{256 512 1024} size))
  (assert (>= 10 count 1))
  (let [file (io/file filename)
        mfile (io/file (io/resource (or mask (str "mask-" size ".png"))))]
    (assert (.exists file))
    (assert (.exists mfile))
    (let [props {:n count :size (str size "x" size)
                 :response_format response-format
                 :image file :mask mfile}
          multipart (map->multipart (if prompt (assoc props :prompt prompt) props))]
      (-image-resp
       response-format
       (-api-openai "images/edits"
        :method :post :multipart multipart)))))

(defn image-variations [filename & {:keys [count size response-format user] :or {size 1024 response-format :url count 1} :as opts}]
  (assert (#{:url :b64_json} response-format))
  (assert (#{256 512 1024} size))
  (assert (>= 10 count 1))
  (let [file (io/file filename)]
    (assert (.exists file))
    (let [props {:n count :size (str size "x" size) :response_format response-format :image file}
          multipart (map->multipart props)]
      (-image-resp response-format (-api-openai "images/variations" :method :post :multipart multipart)))))
