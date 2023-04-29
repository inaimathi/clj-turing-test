(ns clj-turing-test.core
  (:require [clojure.string :as str]
            [cheshire.core :as json]

            [clj-turing-test.open-ai :as ai]
            [clj-turing-test.server :as server]))


(defn -main
  ([] (-main "4646"))
  ([port] (server/run port)))


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
