(ns nlquiz.quiz
  (:require
   [accountant.core :as accountant]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]
   [nlquiz.constants :refer [root-path]]
   [nlquiz.dropdown :as dropdown]
   [nlquiz.speak :as speak]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def answer-count (atom 0))
(def expression-index (atom 0))
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

(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "mooi..🌷"
              "oké! 🌷"
              "prachtig.."
              "precies!😁"
              "prima!!😎 "])

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
  (js/setTimeout #(reset! show-answer-display "none") 1000)
  false)

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

(def got-it-right? (atom false))
(def get-question-fn-atom (atom (fn [] (log/error (str "should not get here! - get-question-fn was not set correctly.")))))

(defn on-submit [e]
  (.preventDefault e)
  (speak/nederlands @show-answer)
  (if (= true @got-it-right?)
    (do
      (show-praise)
      (swap! answer-count inc)
      (reset! got-it-right? false)
      (reset! question-table
              (concat
               [{:source @question-html :target @show-answer}]
               (take 4 @question-table)))
      (new-question @get-question-fn-atom))
    ;; else
    (show-possible-answer))
    
  (.focus (.getElementById js/document "input-guess"))
  (.click (.getElementById js/document "input-guess")))

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> new-question-fn (in scope of quiz-layout, but called from within evaluate-guess, and only called if guess is correct)
(defn quiz-layout [get-question-fn & [question-type-chooser-fn]]
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]       
   (if question-type-chooser-fn (question-type-chooser-fn get-question-fn))
   [:div.question-and-guess
    [:form#quiz {:on-submit on-submit}
     [:div.guess
      [:div.question
       @question-html]
      [:div
       [:input {:type "text"
                :placeholder "wat is dit in Nederlands?"
                :id "input-guess"
                :autoComplete "off"
                :size 25
                :value @guess-text
                :disabled @input-state
                :on-change (fn [input-element]
                             (submit-guess guess-text
                                           (-> input-element .-target .-value)
                                           parse-html
                                           semantics-of-guess
                                           possible-correct-semantics
                                           
                                           ;; function called if the user guessed correctly:
                                           (fn [correct-answer]
                                             (reset! got-it-right? true)
                                             (reset! get-question-fn-atom get-question-fn)
                                             (reset! show-answer correct-answer)
                                             (if (.-requestSubmit (.getElementById js/document "quiz"))
                                               (.requestSubmit (.getElementById js/document "quiz"))
                                               (.dispatchEvent (.getElementById js/document "quiz") (new js/Event "submit" {:cancelable true})))
                                             (reset! input-state "disabled")
                                             (reset! show-answer correct-answer))))}]]]
     [:div.dontknow
      [:input {:class "weetniet" :type "submit" :value "Ik weet het niet"
               :disabled @ik-weet-niet-button-state}] ;; </div.question-and-guess>
      [:button {:class "weetniet" :style {:float :right}
                :on-click #(do (reset! guess-text "")
                               (.preventDefault %))} "Reset"]]]]
   [:div.answertable
    [:table
     [:tbody
      (doall
       (->> (range 0 (count @question-table))
            (map (fn [i]
                   [:tr {:key i :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th (- @answer-count i)]
                    [:th.speak [:button {:on-click #(speak/nederlands (-> @question-table (nth i) :target))} "🔊"]]
                    [:td.target (-> @question-table (nth i) :target)]
                    [:td.source (-> @question-table (nth i) :source)]
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
  (reset! input-state "disabled")
  (reset! guess-text the-input-element)
  (reset! input-state "")
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
            (if-correct-fn guess-string))))))
