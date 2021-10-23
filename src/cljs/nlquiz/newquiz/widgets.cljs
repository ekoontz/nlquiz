(ns nlquiz.newquiz.widgets
  (:require
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

(defn draw-tree [tree]
  (if tree
    (log/info (str "DRAW TREE WITH: " @tree))
    (log/info (str "er is nog geen tree")))
  [:svg
   
   [:text {:x "75" :y "50"} "np"]
   [:text {:x "50" :y "100"} "de"]
   [:text {:x "150" :y "100"} "hond"]
   
   [:line.thick {:x1 "95" :y1 "55" :x2 "60" :y2 "80"}]
   [:line.thick {:x1 "95" :y1 "55" :x2 "160" :y2 "80"}]
   ])

(defn nl-widget [text tree]
  [:div.debug {:style {:width "40%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]
    
    [:h2 ":tree"]
    [:div.monospace
     (draw-tree tree)]]])









