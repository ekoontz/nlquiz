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


(def ^:const v-unit 75)
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
        right-show (or right-rule right-surface right-canonical)
        parent        {:x (* x       h-unit) :y (+ vspace (* y       v-unit))}
        left-child-coordinates {:x (- x 1)
                                :y (+ y 1)}
        left-child    {:x (* (:x left-child-coordinates) h-unit)
                       :y (+ vspace (* (:y left-child-coordinates) v-unit))}
        parent-class "rule"
        left-class   (if left-rule "rule" "leaf")
        right-class  (if right-rule "rule" "leaf")
        
        left-node
        (if left-rule
          (draw-node (u/get-in tree [:1]) (- x 1) (+ y 1))
          {;;:x (:x left-child-coordinates)
           ;;:y (:y left-child-coordinates)
           :g [:text       {:class left-class
                            :x (:x left-child)
                            :y (+ vspace (:y left-child))} left-show]})
        left-contents (:g left-node)
        left-x (:x left-node)
        left-y (:y left-node)
        left-rule (:rule left-node)
        
        debug (if (:rule left-node)
                (log/info (str "LEFT X: " left-x "; LEFT Y: " left-y "; RULE: " left-rule)))

        right-x-increment (Math/max 1 left-x)
        right-y-increment (Math/max 1 left-y)
        right-child-coordinates {:x (+ x right-x-increment)
                                 :y (+ y right-y-increment)}
        right-child {:x (* (:x right-child-coordinates) h-unit)
                     :y (+ vspace (* (:y right-child-coordinates) v-unit))}
        
        right-contents
        (if right-rule
          (do
            (log/info (str "ok, let's draw the right rule: " right-rule
                           " with right-coords: " right-child-coordinates))
                           
            (:g (draw-node (u/get-in tree [:2])
                           (:x right-child-coordinates)
                           (:y right-child-coordinates))))
          (do
            (log/info (str "ok let's draw the right child leaf: " right-show
                           " with right-coords: " right-child-coordinates))
            [:text       {:class right-class
                          :x (:x right-child)
                          :y (+ vspace (:y right-child))} right-show]))]
    (log/info (str "returning from rule: " (u/get-in tree [:rule])
                   " with right-child-coordinates: " right-child-coordinates))
    {:x (or (:x right-child-coordinates) x)
     :y (or (:y right-child-coordinates) y)
     :rule (u/get-in tree [:rule])
     :g
     [:g
      [:text {:class parent-class
              :x (:x parent)
              :y (:y parent)}
       show]
      [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x left-child)  :y2 (:y left-child)}]
      [:line.thick {:x1 (:x parent) :y1 (:y parent) :x2 (:x right-child) :y2 (:y right-child)}]
      
      left-contents
      right-contents]}))

(defn draw-tree [tree]
  (if (u/get-in tree [:rule])
    [:svg
     (:g (draw-node tree 2 0))]))

(defn nl-widget [text tree]
  [:div.debug {:style {:width "100%" :float "left"}}
   [:div.monospace {:style {:min-height "20em"}}
    (draw-tree @tree)]])










