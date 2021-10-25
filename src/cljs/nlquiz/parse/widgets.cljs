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
(def ^:const vspace 30)

(defn draw-node [tree top hcenter]
  (if (and (u/get-in tree [:comp :canonical])
           (u/get-in tree [:head :surface]))
    (let [parent        {:x hcenter :y top}
          left-child  {:x (- hcenter 50) :y (+ top vline 40)}
          right-child {:x (+ hcenter 50) :y (+ top vline 40)}]
      [:g
       [:text {:x (:x parent)
               :y (:y parent)}
        (u/get-in tree [:rule])]
       
       [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x left-child) :y2 (:y left-child)}]
       [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x right-child) :y2 (:y right-child)}]
       
       [:text       {:x (:x left-child)
                     :y (:y left-child)}
        (u/get-in tree [:comp :canonical])]
       [:text       {:x (:x right-child)
                     :y (:y right-child)}
        (u/get-in tree [:head :surface])]])))

(defn draw-tree [tree]
  (if tree
    (log/info (str "drawing tree.."))
    (log/info (str "er is nog geen tree..?")))
  [:svg
   (draw-node tree 35 75)])

(defn nl-widget [text tree]
  [:div.debug {:style {:width "100%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
     @text
    [:h2 ":tree"]
    [:div.monospace
     (draw-tree @tree)]]])









