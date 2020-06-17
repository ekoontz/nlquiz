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

(defn quiz-component []
  (let [expression-index (atom 0)
        guess-text (r/atom "")
        parse-html (r/atom "")
        question-html (r/atom "")
        semantics-of-guess (r/atom [])
        possible-correct-semantics (r/atom [])]
    (go (let [response (<! (http/get (str "http://localhost:3449/generate/"
                                          @expression-index)))]
          (log/info (str "one correct answer to this question is: '"
                         (-> response :body :target) "'"))
          (reset! question-html (-> response :body :source))))
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%" :width "80%" :border "0px dashed green"}}
        [:h3 "Quiz"]
        [dropdown/expressions expression-index]
        [:div {:style {:margin-top "1em" :float "left" :width "100%"}}
         [:div {:style {:float "left" :width "100%"}}
          @question-html]
         [:div {:style {:float "right" :width "100%"}}
          [:div
           [:input {:type "text"
                    :size 50
                    :value @guess-text
                    :on-change #(submit-guess guess-text % parse-html semantics-of-guess)}]]]

         [:div {:style {:float "left" :width "100%"}} @parse-html]]

        [:div {:style {:float "left" :width "100%" :border "1px dashed green"}}
         [:h3 "semantics list:"]
         [:ul
          (doall
           (map (fn [i]
                  (let [sem (nth @semantics-of-guess i)]
                    [:li {:key i}
                     (str sem)]))
                (range 0 (count @semantics-of-guess))))]]]])))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess]
  (reset! guess-text (-> the-input-element .-target .-value))
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get "http://localhost:3449/parse"
                                     {:query-params {"q" guess-string}}))
              trees (-> response :body :trees)
              trees (->> (range 0 (count trees))
                         (map (fn [index]
                                {:tree (nth trees index)
                                 :index index})))]
          (log/debug (str "trees with indices: " trees))
          (reset! semantics-of-guess (-> response :body :sem))))))

