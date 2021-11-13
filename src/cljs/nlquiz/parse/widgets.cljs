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

(defn nl-widget [trees]
  [:div.nl_widget
   ;;                   removes duplicates
   ;;                          |
   ;;                          v
   ;;
   (nl-widget-trees (-> @trees set vec))])

(defn nl-widget-trees [trees]
  (if (seq trees)
    (let [tree (first trees)]
      (if (map? tree)
        (cons
         [:div.tree
          {:key (md5/string->md5-hex (str tree))}
          (draw-tree tree)]
         (cons
          (let [html (draw-node-html
                      (-> tree
                          (dissoc :1)
                          (dissoc :2)
                          (dissoc :head)
                          (dissoc :comp)))]
            [:div.treenode
             {:key (md5/string->md5-hex (str html))}
             html])
          (nl-widget-trees (rest trees))))
        (nl-widget-trees (rest trees))))))
