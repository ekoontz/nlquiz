(ns babylonui.quiz
  (:require
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.handlers :as handlers]
   [cljslog.core :as log]
   [reagent.core :as r]))

(defn atom-input [value]
  [:div
   [:input {:type "text"
            :size 50
            :value @value
            :on-change #(submit-guess value %)}]])
  
(defn quiz-component []
  (fn []
    [:div {:style {:margin-top "1em"
                   :float "left" :width "100%"}}

     [:div {:style {:float "left" :width "100%"}}
      @question-html]

     [:div {:style {:float "right" :width "100%"}}
      (atom-input guess-html)]

     [:div {:style {:float "left" :width "100%"}}
      @parse-html]

     [:div {:style {:float "left" :width "100%"}}
      @sem-html]]))

(defn quiz-page []
  (let [spec-atom (atom 0)]
    (get-a-question @spec-atom)
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}

        [:h3 "Quiz"]

        [handlers/show-expressions-dropdown spec-atom]
        [quiz-component]]])))
