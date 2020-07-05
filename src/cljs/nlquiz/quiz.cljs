(ns nlquiz.quiz
  (:require
   [accountant.core :as accountant]
   [nlquiz.generate :as generate]
   [nlquiz.dropdown :as dropdown]
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def expression-index (atom nil))
(def guess-text (r/atom nil))
(def ik-weet-niet-button-state (r/atom initial-button-state))
(def initial-state-is-enabled? true)
(def initial-button-state (if initial-state-is-enabled? "" "disabled"))
(def input-state (r/atom "disabled"))
(def possible-correct-semantics (r/atom []))
(def question-table (r/atom []))
(def question-html (r/atom ""))
(def semantics-of-guess (r/atom nil))
(def show-answer (r/atom ""))
(def show-answer-display (r/atom "none"))
(def show-praise-text (r/atom ""))
(def show-praise-display (r/atom "none"))

(defonce root-path "/nlquiz/")

(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "mooi..🌷"
              "oké! 🌷"
              "prachtig.."
              "precies!😁"
              "prima!!😎 "])

(defn expression-based-get [expression-index]
  (log/info (str "returning a function from the expression index: " expression-index))
  (fn []
    (http/get (str root-path "generate/" expression-index))))

(defn curriculum-based-get [curriculum-key]
  (log/info (str "returning a function from the curriculum-key: " curriculum-key))
  (let [spec {:cat :noun}]
    (fn []
      (http/get (str root-path "generate") {:query-params {"q" spec}}))))

(defn new-question [expression-index question-html possible-correct-semantics]
  (log/info (str "THE EXPRESSION INDEX IS: " @expression-index))
  (go (let [response (<! ((expression-based-get @expression-index)))]
        (log/info (str "GOT BACK RESPONSE: " reponse))
        (log/debug (str "one possible correct answer to this question is: '"
                        (-> response :body :target) "'"))
        (reset! question-html (-> response :body :source))
        (reset! guess-text "")
        (reset! show-answer (-> response :body :target))
        (reset! show-answer-display "none")
        (reset! input-state "")
        (reset! possible-correct-semantics
                (->> (-> response :body :source-sem)
                     (map cljs.reader/read-string)
                     (map dag_unify.serialization/deserialize)))
        (.focus (.getElementById js/document "input-guess")))))

(defn show-possible-answer []
  (reset! show-answer-display "block")
  (reset! guess-text "")
  (.focus (.getElementById js/document "input-guess"))  
  (js/setTimeout #(reset! show-answer-display "none") 1000))

(defn show-praise []
  (reset! show-praise-display "block")
  (reset! show-praise-text (-> praises shuffle first))
  (js/setTimeout #(reset! show-praise-display "none") 1000))

(defn quiz-layout []
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]       
   [:div {:style {:float "right"}}
    [dropdown/expressions expression-index
     (fn [] (new-question expression-index question-html possible-correct-semantics))]]
   [:div.question-and-guess
    [:div.question
     @question-html]
    [:div.guess
     [:input {:type "text"
              :placeholder "wat is dit in Nederlands?"
              :id "input-guess"
              :size 20
              :value @guess-text
              :disabled @input-state
              :on-change (fn [input-element]
                           (submit-guess guess-text
                                         (-> input-element .-target .-value)
                                         parse-html
                                         semantics-of-guess
                                         possible-correct-semantics))}]]
    [:button {:on-click (fn [input-element]
                          (show-possible-answer))
              :disabled @ik-weet-niet-button-state} "ik weet het niet"]]
   
   [:div {:style {:float "left" :width "100%"}}
    [:table
     [:tbody
      (doall
       (->> (range 0 (count @question-table))
            (map (fn [i]
                   [:tr {:key i :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th (+ 1 i)]
                    [:td.source (-> @question-table (nth i) :source)]
                    [:td.target (-> @question-table (nth i) :target)]
                    ]))))]]]
   
   ] ;; div.main
  )

(defn quiz-component []
  (let [parse-html (r/atom "")]
    (if (nil? @expression-index)
      (reset! expression-index 0))
    (new-question expression-index question-html possible-correct-semantics)
    quiz-layout))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (log/info (str "guess-text: '" @guess-text "' has " (count correct-semantics-set) " semantic interpretation" (if (= 1 (count correct-semantics)) "s") "."))
  (let [result
        (->> guesses-semantics-set
             (mapcat (fn [guess]
                       (->> correct-semantics-set
                            (map (fn [correct-semantics]
                                   (let [correct? (and (not (= :fail (u/unify correct-semantics guess)))
                                                       (u/subsumes? correct-semantics guess))]
                                     (if (not correct?)
                                       (log/info (str "semantics of guess: '" @guess-text "' are NOT correct: "
                                                      "fail-path: "
                                                      (dag_unify.diagnostics/fail-path correct-semantics guess) "; "
                                                      "subsumes? " (u/subsumes? correct-semantics guess)))
                                       (log/info (str "This interpretation of the guess matched the correct semantics! " @guess-text)))
                                     correct?))))))
             (remove #(= false %)))]
    (when (not (empty? result))
      ;; got it right!
      (let [correct-answer @guess-text]
        (show-praise)
        (reset! input-state "disabled")
        (reset! question-table
                (concat
                 [{:source @question-html :target correct-answer}]
                 (take 4 @question-table))))
                      
      (new-question expression-index question-html possible-correct-semantics))))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess possible-correct-semantics]
  (log/info (str "SUBMITTING THE GUESS: " guess-text))
  (reset! guess-text the-input-element)
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get (str root-path "parse/nl")
                                     {:query-params {"q" guess-string}}))]
          (log/info (str "PARSE RESPONSE: " response))
          (log/info (str "SEMANTICS OF GUESS: " semantics-of-guess))
          (log/info (str "SEMANTICS OF GUESS TYPE: " (type semantics-of-guess)))
          (reset! semantics-of-guess
                  (->> (-> response :body :sem)
                       (map cljs.reader/read-string)
                       (map dag_unify.serialization/deserialize)))
          (log/info (str "GOT HERE (1)"))
          (if (not (empty? @semantics-of-guess))
            (log/debug (str "comparing guess: " @semantics-of-guess " with correct answer:"
                            @possible-correct-semantics "; result:"
                            (evaluate-guess @semantics-of-guess
                                            @possible-correct-semantics))))))))


