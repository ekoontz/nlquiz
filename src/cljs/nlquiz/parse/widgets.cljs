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


(def ^:const vline 75)
(def ^:const vspace 10)
(def ^:const h-unit 50)

(defn draw-node [tree x y]
  (log/info (str "draw-node: x=" x "; y=" y "; rule: " (u/get-in tree [:rule])))
  (let [rule (u/get-in tree [:rule] nil)
        surface (u/get-in tree [:surface] nil)
        canonical (u/get-in tree [:canonical] nil)
        show (or rule surface canonical)

        left-rule (u/get-in tree [:1 :rule])
        left-surface (u/get-in tree [:1 :surface])
        left-canonical (u/get-in tree [:1 :canonical])
        left-show (or left-rule left-surface left-canonical)
        
        right-rule (u/get-in tree [:2 :rule])
        right-surface (u/get-in tree [:2 :surface])
        right-canonical (u/get-in tree [:2 :canonical])
        right-show (or right-rule right-surface right-canonical)]
    (if rule
      (let [parent        {:x (* x       h-unit) :y (+ vspace (* y       vline))}
            left-child    {:x (* (- x 1) h-unit) :y (+ vspace (* (+ y 1) vline))}
            right-child   {:x (* (+ x 1) h-unit) :y (+ vspace (* (+ y 1) vline))}
            parent-class "rule"
            left-class   (if left-rule "rule" "leaf")
            right-class  (if right-rule "rule" "leaf")]
        [:g
         [:text {:class parent-class
                 :x (:x parent)
                 :y (:y parent)}
          show]
         
         [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x left-child)  :y2 (:y left-child)}]
         [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x right-child) :y2 (:y right-child)}]
         
         (if left-rule
           (draw-node (u/get-in tree [:1]) (- x 1) (+ y 1))
           [:text       {:class left-class
                         :x (:x left-child)
                         :y (:y left-child)} left-show])

         (if right-rule
           (draw-node (u/get-in tree [:2]) (+ x 1) (+ y 1))
           [:text       {:class right-class
                         :x (:x right-child)
                         :y (:y right-child)} right-show])]))))

(defn draw-tree [tree]
  (if tree
    (log/info (str "drawing tree.."))
    (log/info (str "er is nog geen tree..?")))
  [:svg
   (draw-node tree 2 0)])

(defn nl-widget [text tree]
  [:div.debug {:style {:width "100%" :float "left"}}
   [:div.monospace {:style {:min-height "20em"}}
    (draw-tree @tree)]])










