(ns nlquiz.parse.widgets
  (:require
   [dag_unify.core :as u]
   [cljslog.core :as log]
   [md5.core :as md5]
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

(defn nl-widget [text trees node-html]
  [:div {:width "100%"}
   (doall (map (fn [tree]
                 [:div.tree
                  {:key (md5/string->md5-hex (str tree))}
                  (draw-tree tree node-html)])
               @trees))
   [:div.treenode
    (draw-node-html @node-html)]])



