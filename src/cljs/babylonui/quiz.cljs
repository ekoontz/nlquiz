(ns babylonui.quiz
  (:require
   [accountant.core :as accountant]
   [babylonui.generate :as generate]
   [babylonui.dropdown :as dropdown]
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]
   [clerk.core :as clerk]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare submit-guess)

(def eval-atom (r/atom "UNDEFINED...???"))
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
        (reset! possible-correct-semantics (-> response :body :source-sem)))))

(defn quiz-component []
  (let [guess-text (r/atom "")
        parse-html (r/atom "")
        semantics-of-guess (r/atom [])]
    (new-question expression-index question-html possible-correct-semantics)
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%" :width "80%" :border "0px dashed green"}}
        [:h3 "Quiz"]

        [:div {:style {:float "right" :border "5px dashed blue"}}
         @eval-atom]

        [:div {:style {:float "left" :width "100%" :border "2px dashed yellow"}}

         [:table
          [:thead [:tr [:th] [:th "source"] [:th "target"]]]
          [:tbody
           (doall
            (->> (range 0 (count @question-table))
                 (map (fn [i]
                        [:tr {:key i}
                         [:th i]
                         [:td (-> @question-table (nth i) :source)]
                         [:td (-> @question-table (nth i) :target)]
                         ]))))
           ]
          ]
         
         ]
         
        
        [dropdown/expressions expression-index]
        [:div {:style {:margin-top "1em" :float "left" :width "100%"}}

         [:div {:style {:float "left" :width "auto"}}
          @question-html]

         [:div {:style {:float "right"}}
          [:div
           [:input {:type "text"
                    :size 50
                    :value @guess-text
                    :on-change (fn [input-element]
                                 (submit-guess guess-text
                                               (-> input-element .-target .-value)
                                               parse-html semantics-of-guess possible-correct-semantics))}]]]

         [:div {:style {:float "left" :width "100%"}}
          [:div {:style {:float "left" :width "40%" :border "1px dashed blue"}}
           [:h3 "possible correct semantics"]
           [:ul
            (doall
             (->> (range 0 (count @possible-correct-semantics))
                  (map (fn [i]
                         (let [sem (nth @possible-correct-semantics i)]
                           [:li {:key i}
                            (str sem)])))))]]

          [:div {:style {:float "right" :width "40%" :border "1px dashed green"}}
           [:h3 "guess semantics"]
           [:ul
            (doall
             (map (fn [i]
                    (let [sem (nth @semantics-of-guess i)]
                      [:li {:key i}
                       (str sem)]))
                  (range 0 (count @semantics-of-guess))))]]]

         [:div {:style {:float "left" :width "100%"}} @parse-html]
         ]

        ]
       ]

      )))

(defn evaluate-guess [guesses corrects]
  (let [result
        (not (empty?
              (remove #(= :fail %)
                      (->> guesses
                           (mapcat (fn [g]
                                     (->>
                                      corrects
                                      (map (fn [c]
                                             (let [result (u/unify c g)]
                                               (if (= result :fail)
                                                 (log/info (str "fail: " (dag_unify.diagnostics/fail-path c g)))
                                                 (log/info (str "guess was correct: " g)))
                                               result))))))))))]
    (reset! eval-atom (if result "GOOOD!!!" "BAD!!!"))
    (when result
      (reset! question-table
              (cons {:source "foo" :target "bar"}
                    @question-table))
      (new-question expression-index question-html possible-correct-semantics))))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess possible-correct-semantics]
  (reset! guess-text the-input-element)
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get "http://localhost:3449/parse"
                                     {:query-params {"q" guess-string}}))]
          (reset! semantics-of-guess (-> response :body :sem))
          (if (not (empty? @semantics-of-guess))
            (log/info (str "comparing guess: " @semantics-of-guess " with correct answer:"
                           @possible-correct-semantics " result:"
                           (evaluate-guess @semantics-of-guess @possible-correct-semantics eval-atom))))))))

