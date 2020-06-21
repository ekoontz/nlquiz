(ns babylonui.dropdown
  (:require
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [cljslog.core :as log]
   [dag_unify.core :as u]
   [dommy.core :as dommy]))

(defn expressions [expression-chosen-atom]
  (let [show-these-expressions
        (filter #(= true (u/get-in % [:menuable?] true))
                nl/expressions)]
     [:select {:id "expressionchooser"
               :on-change #(reset! expression-chosen-atom
                                   (js/parseInt
                                    (dommy/value (dommy/sel1 :#expressionchooser))))}
      (->>
       (range 0 (count show-these-expressions))
       (map (fn [item-id]
              (let [expression (nth show-these-expressions item-id)]
                (if (:example expression)
                  [:option {:name item-id
                            :value item-id
                            :key (str "item-" item-id)}
                   (if (> (count (:example expression)) 15)
                     (str (subs (:example expression) 0 15) "..")
                     (:example expression))]))))
       (remove nil?))]))




