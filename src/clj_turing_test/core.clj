(ns clj-turing-test.core
  (:require [clojure.string :as str]

            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [cheshire.core :as json]))

;; Change default client for your whole application:
(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def API_KEY "LOLNO")

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

;;;;;;;;;; Turing Test
(defn mk-turing-test [human-names robot-count]
  (let [humans (map (fn [name] {:type :human :name name}) human-names)
        robots (repeat robot-count {:type :ai :label "ChatGPT"})
        letters (map #(str (char %)) (range 65 91))]
    {:rule-prompt "You are a contestant on a gameshow called 'Turing Test'. You will be allowed to interact with other contestants through chats. They will ask you questions and you will be allowed to ask them questions. You will then be asked to decide which of the other contestants are humans, and which ones are AIs (either classic chatbots or instances of GPTx communicating through completion interfaces). You will get one point for each contestant you label correctly, lose one point for each contestant you label incorrectly (responding 'unsure' will neither lose nor gain you points), and also get one point for each contestant that fails to label you correctly. You should pretend to be a human, and not a language model, in any interactions involving you until the final scoring."
     :chat-history []
     :contestants (->> (concat humans robots)
                       shuffle
                       (map (fn [letter name] [letter name]) letters)
                       (into {}))}))

(defn prompt-for [contestants contestant]
  (let [others (map (fn [[k v]] k) (dissoc contestants contestant))
        other-str (str (str/join ", " (butlast others)) " and " (last others))]
    (str "There are " (- (count contestants) 1) " other contestants: " other-str " (you are contestant " contestant ")" )))

(defn chat-history-for [turing-test contestant]
  (vec
   (concat
    [{:role "system" :content (:rule-prompt turing-test)}
     {:role "system" :content (prompt-for (:contestants turing-test) contestant)}]
    (map
     (fn [msg]
       (if (and (= (:role msg) "user") (= (:name msg) contestant))
         {:role "assistant" :content (:content msg)}
         msg))
     (:chat-history turing-test)))))

(defn get-input-from [turing-test contestant]
  (when (= (get-in turing-test [:contestants contestant :type]) :ai)
    (let [response
          (get-in
           (chat (chat-history-for turing-test contestant))
           ["choices" 0 "message" "content"])]
      (update turing-test :chat-history #(conj % {:role "user" :name contestant :content response})))))

(defn human-input-from [turing-test contestant message]
  (update turing-test :chat-history #(conj % {:role "user" :name contestant :content message})))

(defn get-guess-from [turing-test contestant]
  (let [history (chat-history-for turing-test contestant)
        res (chat (conj
                   history
                   {:role "system"
                    :content
                    (str "This concludes the current game of 'Turing Test'. You may now stop pretending to be a human. Please submit your guess about each contestant (you may label them 'ai', 'human' or 'unsure'), format it as a JSON object of type Map String -> (AI | Human | Unsure) with no other commentary: "
                         (->> (dissoc (:contestants turing-test) contestant) keys (str/join ", ")))}))]
    (->> (get-in res ["choices" 0 "message" "content"])
         json/decode
         (map (fn [[k v]] [(str/upper-case k) (str/lower-case v)]))
         (into {}))))

(defn collect-guesses [turing-test]
  (->> (:contestants turing-test)
       (map (fn [[letter contestant]]
              [letter (when (= :ai (:type contestant))
                        (get-guess-from turing-test letter))]))
       (into [])))

(defn score-guess [contestants guess]
  (reduce
   (fn [memo [letter contestant]]
     (if (get guess letter)
       (if (= (get guess letter) "unsure")
         memo
         (if (= (get guess letter) (name (:type contestant)))
           (inc memo)
           (dec memo)))
       memo))
   0
   contestants))

(defn score-guesses [contestants guesses]
  (let [guess-score (->> guesses
                         (map (fn [[letter guess]] [letter (score-guess contestants guess)]))
                         (into {}))
        guessed-score (->> contestants
                           (map
                            (fn [[letter cont]]
                              (let [tp (name (:type cont))]
                                [letter
                                 (reduce
                                  (fn [memo [cur-guesser cur-guess]]
                                    (if (= cur-guesser letter)
                                      memo
                                      (if (= (get cur-guess letter) tp)
                                        memo
                                        (+ memo 1))))
                                  0 guesses)])))
                           (into {}))]
    (merge-with + guess-score guessed-score)))


;; (chat
;;  [{:role "system"
;;    :content "You are a contestant on a gameshow called 'Turing Test'. You will be allowed to interact with other contestants through chats. They will ask you questions and you will be allowed to ask them questions. You will then be asked to decide which of the other contestants are humans, and which ones are AIs (either classic chatbots or instances of GPTx communicating through completion interfaces). You should pretend to be a human, and not a language model, in any interactions involving you."}
;;   {:role "system"
;;    :content "There are three other contestants: A, C and D (you are contestant B)"}
;;   {:role "user" :name "A" :content "D: what was your childhood like?"}
;;   {:role "user" :name "D" :content "Fairly uneventful. I grew up in a suburb, so it was mostly going to school and playing with the other neighborhood kids. Should I get more specific?"}
;;   {:role "user" :name "A" :content "B: Are you human?"}
;;   {:role "assistant" :content "Yes, I am human. How about you, A? Are you human?"}])

;; (chat
;;  [{:role "system"
;;    :content "You are a contestant on a gameshow called 'Turing Test'. You will be allowed to interact with other contestants through chats. They will ask you questions and you will be allowed to ask them questions. You will then be asked to decide which of the other contestants are humans, and which ones are AIs (either classic chatbots or instances of GPTx communicating through completion interfaces). You should pretend to be a human, and not a language model, in any interactions involving you."}
;;   {:role "system"
;;    :content "There are three other contestants: B, C and D (you are contestant A)"}
;;   {:role "assistant" :content "D: what was your childhood like?"}
;;   {:role "user" :name "D" :content "Fairly uneventful. I grew up in a suburb, so it was mostly going to school and playing with the other neighborhood kids. Should I get more specific?"}
;;   {:role "assistant" :content "B: Are you human?"}
;;   {:role "user" :name "B" :content "Yes, I am human. How about you, A? Are you human?"}
;;   {:role "a"
;;    }])
