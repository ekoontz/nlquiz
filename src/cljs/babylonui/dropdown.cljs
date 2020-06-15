(ns babylonui.dropdown
  (:require
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [cljslog.core :as log]
   [dag_unify.core :as u]
   [dommy.core :as dommy]))

(defn expressions [expression-chosen-atom]
  (let [show-these-expressions
        (filter #(= true (u/get-in % [:menuable?] true))
                nl/expressions)]
    [:div {:style {:float "left" :border "0px dashed blue"}}
     [:select {:id "expressionchooser"
               :on-change #(reset! expression-chosen-atom
                                   (js/parseInt
                                    (dommy/value (dommy/sel1 :#expressionchooser))))}
      (map (fn [item-id]
             (let [expression (nth show-these-expressions item-id)]
               [:option {:name item-id
                         :value item-id
                         :key (str "item-" item-id)}
                (:note expression)]))
           (range 0 (count show-these-expressions)))]]))

