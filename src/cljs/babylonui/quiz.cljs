(ns babylonui.quiz
  (:require
   [accountant.core :as accountant]
   [babylonui.generate :as generate]
   [babylonui.dropdown :as dropdown]
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def expression-index (atom 0))
(def guess-text (r/atom ""))
(def ik-weet-niet-button-state (r/atom initial-button-state))
(def initial-state-is-enabled? true)
(def initial-button-state (if initial-state-is-enabled? "" "disabled"))
(def input-state (r/atom "disabled"))
(def possible-correct-semantics (r/atom []))
(def question-table (r/atom []))
(def question-html (r/atom ""))
(def show-answer (r/atom ""))
(def show-answer-display (r/atom "none"))
(def show-praise-text (r/atom ""))
(def show-praise-display (r/atom "none"))

(def praises ["precies!😁"
              "prima!!😎 "
              "geweldig!🇳🇱"
              "dat is leuk! 🚲"
              "oké! 🌷"
              ])

(defn new-question [expression-index question-html possible-correct-semantics]
  (go (let [response (<! (http/get (str "http://localhost:3449/generate/"
                                        @expression-index)))]
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

(defn quiz-component []
  (let [parse-html (r/atom "")
        semantics-of-guess (r/atom [])]
    (new-question expression-index question-html possible-correct-semantics)
    (fn []
      [:div.main
       [:div#answer {:style {:display @show-answer-display}} @show-answer]
       [:div#praise {:style {:display @show-praise-display}} @show-praise-text]       
       [:div {:style {:float "right"}}
        [dropdown/expressions expression-index]]
       [:div.question-and-guess
        [:div {:style {:float "left"}}
         @question-html]
        [:div {:style {:float "right"}}
         [:input {:type "text"
                  :id "input-guess"
                  :size 50
                  :value @guess-text
                  :disabled @input-state
                  :on-change (fn [input-element]
                               (submit-guess guess-text
                                             (-> input-element .-target .-value)
                                             parse-html
                                             semantics-of-guess
                                             possible-correct-semantics))}]
          [:button {:on-click (fn [input-element]
                                 (show-possible-answer))
                    :disabled @ik-weet-niet-button-state} "ik weet niet"]]]
          
       [:div {:style {:float "left" :width "100%"}}
        [:table
         [:tbody
          (doall
           (->> (range 0 (count @question-table))
                (map (fn [i]
                       [:tr {:key i}
                        [:th (+ 1 i)]
                        [:td.source (-> @question-table (nth i) :source)]
                        [:td.target (-> @question-table (nth i) :target)]
                        ]))))]]]

       ] ;; div.main
      )))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (let [result
        (->> guesses-semantics-set
             (mapcat (fn [guess]
                       (->> correct-semantics-set
                            (map (fn [correct-semantics]
                                   (let [result (u/unify correct-semantics guess)]
                                     (if (= result :fail)
                                       (log/info (str "guess was NOT correct: " (dag_unify.diagnostics/fail-path correct-semantics guess)))
                                       (log/info (str "guess was correct! " @guess-text)))
                                     result))))))
             (remove #(= :fail %)))]
    (when (not (empty? result))
      ;; got it right!
      (show-praise)
      (reset! input-state "disabled")
      (reset! question-table
              (concat
               [{:source @question-html :target @guess-text}]
               @question-table))
                      
      (new-question expression-index question-html possible-correct-semantics))))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess possible-correct-semantics]
  (reset! guess-text the-input-element)
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get "http://localhost:3449/parse"
                                     {:query-params {"q" guess-string}}))]
          (log/debug (str "sem1: " (-> response :body :sem)))
          (log/debug (str "sem2: " (->> (-> response :body :sem)
                                       (map cljs.reader/read-string))))
          (log/debug (str "sem3: " (->> (-> response :body :sem)
                                        (map cljs.reader/read-string)
                                        (map dag_unify.serialization/deserialize))))
          (reset! semantics-of-guess
                  (->> (-> response :body :sem)
                       (map cljs.reader/read-string)
                       (map dag_unify.serialization/deserialize)))
          (if (not (empty? @semantics-of-guess))
            (log/debug (str "comparing guess: " @semantics-of-guess " with correct answer:"
                            @possible-correct-semantics "; result:"
                            (evaluate-guess @semantics-of-guess
                                            @possible-correct-semantics eval-atom))))))))

