(ns nlquiz.quiz
  (:require
   [accountant.core :as accountant]

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
(def possible-correct-semantics (r/atom nil))
(def question-table (r/atom nil))
(def question-html (r/atom nil))
(def semantics-of-guess (r/atom nil))
(def show-answer (r/atom nil))
(def show-praise-text (r/atom nil))
(def show-answer-display (r/atom "none"))
(def show-praise-display (r/atom "none"))

(defonce root-path "/nlquiz/")

(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "mooi..🌷"
              "oké! 🌷"
              "prachtig.."
              "precies!😁"
              "prima!!😎 "])

(defn expression-based-get []
  (log/debug (str "returning a function from the expression index: " @expression-index))
  (http/get (str root-path "generate/" @expression-index)))

(defn new-question [specification-fn]
  (go (let [response (<! (specification-fn))]
        (log/debug (str "new-expression response: " reponse))
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

(defn choose-question-from-dropdown [get-question-fn]
  (if (nil? @expression-index)
    (reset! expression-index 0))
  [:div {:style {:float "right"}}
   [dropdown/expressions expression-index
    
    ;; what to call if dropdown's choice is changed (generate a new question):
    (fn [] (new-question get-question-fn))]])

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> new-question-fn (in scope of quiz-layout, but called from within evaluate-guess, and only called if guess is correct)
(defn quiz-layout [get-question-fn question-type-chooser-fn]
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]       
   (question-type-chooser-fn get-question-fn)
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
                                         possible-correct-semantics

                                         ;; function that will called if the user guessed correctly:
                                         (fn []
                                           (let [correct-answer @guess-text]
                                             (show-praise)
                                             (reset! input-state "disabled")
                                             (reset! question-table
                                                     (concat
                                                      [{:source @question-html :target correct-answer}]
                                                      (take 4 @question-table))))
                                           (new-question get-question-fn))))}]]
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

(defn expression-list-quiz-component [get-question-fn chooser]
  (new-question get-question-fn)
  #(quiz-layout get-question-fn chooser))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (let [result
        (->> guesses-semantics-set
             (mapcat (fn [guess]
                       (->> correct-semantics-set
                            (map (fn [correct-semantics]
                                   ;; the guess is correct if and only if there is a semantic interpretation _guess_ of the guess where both of these are true:
                                   ;; - unifying _guess_ with some member _correct-semantics_ of the set of correct semantics is not :fail.
                                   ;; - this _correct_semantics_ is more general (i.e. subsumes) _guess_.
                                   (let [correct? (and (not (= :fail (u/unify correct-semantics guess)))
                                                       (u/subsumes? correct-semantics guess))]
                                     (if (not correct?)
                                       (log/info (str "semantics of guess: '" @guess-text "' are NOT correct: "
                                                      "fail-path: "
                                                      (dag_unify.diagnostics/fail-path correct-semantics guess) "; "
                                                      "subsumes? " (u/subsumes? correct-semantics guess)))
                                       (log/info (str "Found an interpretation of the guess '" @guess-text "' which matched the correct semantics.")))
                                     correct?))))))
             (remove #(= false %)))]
    (not (empty? result))))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess possible-correct-semantics if-correct-fn]
  (log/debug (str "submitting guess: " guess-text))
  (reset! guess-text the-input-element)
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get (str root-path "parse/nl")
                                     {:query-params {"q" guess-string}}))]
          (log/debug (str "parse response: " response))
          (log/debug (str "semantics of guess: " semantics-of-guess))
          (reset! semantics-of-guess
                  (->> (-> response :body :sem)
                       (map cljs.reader/read-string)
                       (map dag_unify.serialization/deserialize)))
          (when (evaluate-guess @semantics-of-guess
                                @possible-correct-semantics)
            ;; got it right!
            (if-correct-fn))))))