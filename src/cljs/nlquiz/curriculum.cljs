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
   [:h1 [:a {:href "/nlquiz/curriculum"} "(whole curriculum)"]]
   [:h1 [:a {:href "/nlquiz/curriculum/adjectives"} "Adjectives"]]
   [:h1 [:a {:href "/nlquiz/curriculum/nouns"} "Nouns"]]
   [:ul
    [:li [:a {:href "/nlquiz/curriculum/nouns/art"} "Definite and indefinite articles"]]
    [:li [:a {:href "/nlquiz/curriculum/nouns/poss"} "Possessive articles"]]]
   [:h1 "Verbs"]
   [:ul
    [:li "Present Tense"]
    [:li "Transitive"]
    [:li "Reflexive"]]])

(defn quiz []
  (fn []
    [:div
     [:h1 "WELCOME TO DA CURRICULUM QUIZ!"]
     ]))

(defn quiz-major []
  (fn []
    [:div
     [:h1 "WELCOME TO DA CURRICULUM QUIZ (Major)!"]
     ]))

(defn quiz-minor []
  (fn []
    [:div
     [:h1 "WELCOME TO DA CURRICULUM QUIZ (Minor)!"]
     ]))


    
