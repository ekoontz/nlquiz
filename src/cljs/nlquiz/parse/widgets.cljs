(ns nlquiz.parse.widgets
  (:require
   [dag_unify.core :as u]
   [cljslog.core :as log]
   [nlquiz.parse.draw-tree :refer [draw-node-html
                                   draw-tree]]))

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

(defn nl-widget [text tree node-html]
  [:div {:width "100%"}
   [:div.tree
    (draw-tree @tree node-html)]
   [:div.treenode
    (draw-node-html @node-html)]])



