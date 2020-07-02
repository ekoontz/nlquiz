(ns nlquiz.curriculum
  (:require
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [cljslog.core :as log]
   [dag_unify.core :as u]
   [dommy.core :as dommy]))

(defn tree []
  [:div.curriculum
   [:h1 [:a {:href "/curriculum/adjectives"} "Adjectives"]]
   [:h1 [:a {:href "/curriculum/nouns"} "Nouns"]]
   [:ul
    [:li [:a {:href "/curriculum/nouns/art"} "Definite and indefinite articles"]]
    [:li [:a {:href "/curriculum/nouns/poss"} "Possessive articles"]]]
   [:h1 "Verbs"]
   [:ul
    [:li "Present Tense"]
    [:li "Transitive"]
    [:li "Reflexive"]]])


    
