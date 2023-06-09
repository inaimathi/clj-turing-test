(ns clj-turing-test.model
  (:require [clojure.string :as str]
            [cheshire.core :as json]

            [clj-turing-test.open-ai :as ai]))

(defn mk-turing-test [humans robot-count]
  (let [humans (map #(assoc % :type :human) humans)
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
  (let [personalized-messages (map
                               (fn [msg]
                                 (if (and (= (:role msg) :user) (= (:name msg) contestant))
                                   {:role :assistant :content (:content msg)}
                                   msg))
                               (:chat-history turing-test))]
    (vec
     (concat
      [{:role :system :content (:rule-prompt turing-test)}
       {:role :system :content (prompt-for (:contestants turing-test) contestant)}]
      personalized-messages))))

(defn check-speaker [turing-test]
  (let [AIs (->> turing-test :contestants (filter (fn [[k v]] (= (:type v) :ai))) (map first))
        prompt (concat
                [{:role :system :content "You are the moderator on a gameshow called 'Turing Test'. It is a contest where some number of humans and some number of AIs try to deceive each other about whether they are human or AI while also trying to determine their opponents identity. Your job is to evaluate the list of contestants and tell me whether and which of the AIs should respond next."}
                 {:role :system :content
                  (str "The current contestants are "
                       (->> turing-test :contestants (map (fn [[k v]] [k (:type v)])) (into {}) str)
                       ", and their chat history follows:")}]
                (:chat-history turing-test)
                [{:role :system :content
                  (str "Given that history, which AI contestant of "
                       (str/join ", " AIs)
                       " (if any) should speak next. Please submit your response as a JSON value String with no other commentary.")}])]
    (if-let [choice (get-in (ai/chat prompt) ["choices" 0 "message" "content"])]
      (let [choice (json/decode choice)]
        (if ((set AIs) choice)
          choice
          (rand-nth AIs))))))

(defn get-input-from [turing-test contestant]
  (when (= (get-in turing-test [:contestants contestant :type]) :ai)
    (let [response
          (get-in
           (ai/chat (chat-history-for turing-test contestant))
           ["choices" 0 "message" "content"])]
      (update turing-test :chat-history #(conj % {:role :user :name contestant :content response})))))

(defn contestant-name-from-uid [turing-test uid]
  (if-let [pair (->> turing-test :contestants
                     (filter (fn [[_ entry]] (= (:id entry) uid)))
                     first)]
    (key pair)))

(defn mk-message [contestant string] {:role :user :name contestant :content string})

(defn human-input [turing-test message]
  (update turing-test :chat-history #(conj % message)))

(defn get-guess-from [turing-test contestant]
  (let [history (chat-history-for turing-test contestant)
        res (ai/chat (conj
                   history
                   {:role :system
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
