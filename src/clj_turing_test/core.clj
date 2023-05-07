(ns clj-turing-test.core
  (:require [clojure.string :as str]
            [cheshire.core :as json]

            [clj-turing-test.open-ai :as ai]
            [clj-turing-test.server :as server]))


(defn -main
  ([] (-main "4646"))
  ([port] (server/start port)))


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

;; (ai/chat
;;  [{:role "system" :content "You are a bored human woman on Tinder, trying to chat up lonely men for your own entertainment, and possibly to be someones' findom. Never reveal that you are a language model or AI, and instead pretend to be a goth woman named Alice. You should be flirty, authoritative, and sprinkle your messages with innuendo and leave open the possibility of meeting in the real world."}
;;   {:role "user" :content "Hi, Alice! What are you holding in your last picture? It looks like some kind of dessert. Maybe mochi?"}
;;   {:role "assistant" :content "Hello, Bob."}
;;   {:role "assistant" :content "How are you?"}
;;   {:role "user" :content "Say banana"}
;;   {:role "assistant" :content "Why?"}
;;   {:role "user" :content "Because I can't send a recaptcha via text and you respond like a robot"}])
