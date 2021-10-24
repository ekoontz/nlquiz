(ns nlquiz.parse.widgets
  (:require
   [dag_unify.core :as u]
   [dag_unify.serialization :refer [deserialize serialize]]
   [cljslog.core :as log]))

(defn en-question-widget [text]
  [:div.debug {:style {:width "40%" :float "right"}}
   [:h1 ":question"]
   @text])

(defn en-widget [text]
  [:div.debug {:style {:width "40%" :float "right"}}
   [:h1 ":en"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]]])

(defn draw-node [tree top]
  [:g
   [:text {:x (str 75) :y (str (+ top 50))} (u/get-in tree [:rule])]
   [:text {:x "50" :y (+ top 100)} (u/get-in tree [:comp :canonical])]
   [:text {:x "150" :y (+ top 100)} (u/get-in tree [:head :surface])]
   
   [:line.thick {:x1 "95" :y1 "55" :x2 "60" :y2 "80"}]
   [:line.thick {:x1 "95" :y1 "55" :x2 "160" :y2 "80"}]])
  
(defn draw-tree [tree]
  (if tree
    (log/info (str "drawing tree.."))
    (log/info (str "er is nog geen tree..?")))
  [:svg
   (draw-node tree 0)])

(defn nl-widget [text tree]
  [:div.debug {:style {:width "100%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
     @text
    [:h2 ":tree"]
    [:div.monospace
     (draw-tree @tree)]]])









