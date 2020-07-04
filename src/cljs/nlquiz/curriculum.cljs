(ns nlquiz.curriculum
  (:require
   [reagent.session :as session]
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [cljslog.core :as log]
   [dag_unify.core :as u]
   [dommy.core :as dommy]))

(defn tree []
  [:div.curriculum
   [:h1 [:a {:href "/nlquiz/curriculum/adjectives"} "Adjectives"]]
   [:h1 [:a {:href "/nlquiz/curriculum/nouns"} "Nouns"]]
   [:ul
    [:li [:a {:href "/nlquiz/curriculum/nouns/art"} "Definite and indefinite articles"]]
    [:li [:a {:href "/nlquiz/curriculum/nouns/poss"} "Possessive articles"]]]
   [:h1 [:a {:href "/nlquiz/curriculum/verbs"} "Verbs"]]
   [:ul
    [:li "Present Tense"]
    [:li "Transitive"]
    [:li "Reflexive"]]])

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)]
      [:div.curr-major
       (tree)       
       [:h2
        "Choose a topic to study."]])))

(defn quiz-major []
  (fn []
    (let [routing-data (session/get :route)
          major (get-in routing-data [:route-params :major])]
      [:div.curr-major
       (tree)       
       [:h2
        "Let's study " major "!"]])))

(defn quiz-minor []
  (fn []
    [:div
     [:h1 "WELCOME TO DA CURRICULUM QUIZ (Minor)!"]
     ]))
