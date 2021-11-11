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

(defn nl-widget [text trees]
  [:div {:width "100%"}
   [:div {:width "48%" :float "left"}
    (doall (map (fn [tree]
                  [:div.tree
                   {:key (md5/string->md5-hex (str tree))}
                   (draw-tree tree node-html)])
                @trees))]
   [:div {:width "48%" :float "right"}
    (doall (map (fn [tree]
                  (let [html-node
                        (if (map? tree)
                          (draw-node-html
                           (->
                            tree
                            (dissoc :1)
                            (dissoc :2)
                            (dissoc :head)
                            (dissoc :comp)))
                          (str "node-" tree))]
                   [:div.treenode
                    {:key (md5/string->md5-hex (str html-node))}
                    html-node]))
                @trees))]])










