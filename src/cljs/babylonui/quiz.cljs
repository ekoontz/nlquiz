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

(def guess-text (r/atom ""))
(def question-table (r/atom []))
(def expression-index (atom 0))
(def question-html (r/atom ""))
(def possible-correct-semantics (r/atom []))

(defn new-question [expression-index question-html possible-correct-semantics]
  (go (let [response (<! (http/get (str "http://localhost:3449/generate/"
                                        @expression-index)))]
        (log/info (str "one correct answer to this question is: '"
                       (-> response :body :target) "'"))
        (reset! question-html (-> response :body :source))
        (reset! guess-text "")
        (reset! possible-correct-semantics
                (->> (-> response :body :source-sem)
                     (map cljs.reader/read-string)
                     (map dag_unify.serialization/deserialize))))))

(defn quiz-component []
  (let [parse-html (r/atom "")
        semantics-of-guess (r/atom [])]
    (new-question expression-index question-html possible-correct-semantics)
    (fn []
      [:div.main
       [dropdown/expressions expression-index]
       [:div {:style {:float "left" :width "100%"}}
        [:div {:style {:float "left" :width "50%"}}
         @question-html]
        [:div {:style {:float "right" :width "50%"}}
         [:div
          [:input {:type "text"
                   :size 50
                   :value @guess-text
                   :on-change (fn [input-element]
                                (submit-guess guess-text
                                              (-> input-element .-target .-value)
                                              parse-html
                                              semantics-of-guess
                                              possible-correct-semantics))}]]]]
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
                                   (log/info (str "ONE CORRECT:"
                                                  correct-semantics))
                                   (let [result (u/unify correct-semantics guess)]
                                     (if (= result :fail)
                                       (log/info (str "guess was NOT correct: " (dag_unify.diagnostics/fail-path correct-semantics guess)))
                                       (log/info (str "guess was correct! " @guess-text)))
                                     result))))))
             (remove #(= :fail %)))]
    (when (not (empty? result))
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
            (log/info (str "comparing guess: " @semantics-of-guess " with correct answer:"
                           @possible-correct-semantics " result:"
                           (evaluate-guess @semantics-of-guess
                                           @possible-correct-semantics eval-atom))))))))

