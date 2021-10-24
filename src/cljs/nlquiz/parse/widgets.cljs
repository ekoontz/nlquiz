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

(def ^:const vline 20)
(def ^:const vspace 30 )

(defn draw-node [tree top]
  [:g
   [:text {:x (str 75) :y (str top)} (u/get-in tree [:rule])]
   [:line.thick {:x1 "95" :y1 (str top) :x2 "60" :y2 (str (+ top vline vspace))}]
   [:line.thick {:x1 "95" :y1 (str top) :x2 "160" :y2 (str (+ top vline vspace))}]

   [:text {:x "50" :y (+ top vline)} (u/get-in tree [:comp :canonical])]
   [:text {:x "150" :y (+ top vline)} (u/get-in tree [:head :surface])]])


(defn draw-tree [tree]
  (if tree
    (log/info (str "drawing tree.."))
    (log/info (str "er is nog geen tree..?")))
  [:svg
   (draw-node tree 35)])

(defn nl-widget [text tree]
  [:div.debug {:style {:width "100%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
     @text
    [:h2 ":tree"]
    [:div.monospace
     (draw-tree @tree)]]])









