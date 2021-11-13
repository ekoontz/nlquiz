(ns nlquiz.parse.draw-tree
  (:require
   [dag_unify.core :as u]
   [dag_unify.serialization :refer [deserialize serialize]]
   [cljslog.core :as log]
   [reagent.core :as r]))

(def ^:const v-unit 30)
(def ^:const vspace 10)
(def ^:const h-unit 50)

(defn draw-node-html [parse-node]
  (if (map? parse-node)
    [:table.treenode
     [:tbody
      (map (fn [k]
             (let [val
                   (u/get-in parse-node [k])]
               (if (not (= val :top))
                 ;; hide {k v=:top} pairs since
                 ;; they aren't very interesting.
                 [:tr
                  {:key k}
                  [:th k]
                  [:td
                   (draw-node-value
                    k
                    val)]])))
           (sort (keys parse-node)))]]))

(defn draw-node-value [k v]
  (cond
    (map? v) (draw-node-html v)
    (= v :menard.nederlands/none) "none"
    (string? v) [:i v]
    (keyword? v) v
    (boolean? v) [:tt (if (true? v) "true" "false")]
    (nil? v) [:tt "NULL"]
    true (str v)))

(defn draw-node [tree x y]
  (let [rule (u/get-in tree [:rule] nil)
        surface (u/get-in tree [:surface] nil)
        canonical (u/get-in tree [:canonical] nil)
        show (or rule surface canonical)
        parent {:x (* x h-unit) :y (+ vspace (* y v-unit))}
        parent-class (r/atom "rule")

        ;; left
        left-rule (u/get-in tree [:1 :rule])
        left-class (r/atom (if left-rule "rule" "leaf"))
        left-surface (u/get-in tree [:1 :surface])
        left-canonical (u/get-in tree [:1 :canonical])
        left-show (or left-rule left-surface left-canonical)
        left-child-xy-units {:x (- x 1) :y (+ y 1)}
        left-child-xy-pixels {:x (* (:x left-child-xy-units) h-unit)
                              :y (+ vspace (* (:y left-child-xy-units) v-unit))}
        left-node
        (if left-rule
          (draw-node (u/get-in tree [:1]) (- x 1) (+ y 1))
          ;; left child is a leaf:
          {:x (:x left-child-xy-units)
           :y (:y left-child-xy-units)
           :g [:text {:class @left-class
                      :x (:x left-child-xy-pixels)
                      :y (+ vspace (:y left-child-xy-pixels))}
               left-show]})

        ;; right:
        right-rule (u/get-in tree [:2 :rule])
        right-class (r/atom (if right-rule "rule" "leaf"))
        right-surface (u/get-in tree [:2 :surface])
        right-canonical (u/get-in tree [:2 :canonical])
        right-show (or right-rule right-surface right-canonical)
        right-child-xy-units (if right-rule
                                  {:x (+ (:x left-node) 2)
                                   :y (:y left-node)}
                                  ;; right child is a leaf:
                                  {:x (+ x 1)
                                   :y (+ y 1)})
        right-child-xy-pixels {:x (* (:x right-child-xy-units) h-unit)
                               :y (+ vspace (* (:y right-child-xy-units)
                                               v-unit))}
        right-node
        (if right-rule
          (draw-node (u/get-in tree [:2])
                     (:x right-child-xy-units)
                     (:y right-child-xy-units))

          ;; else, right child is a leaf:
          {:x (:x right-child-xy-units)
           :y (:y right-child-xy-units)
           :g [:text {:class @right-class
                      :x (:x right-child-xy-pixels)
                      :y (+ vspace (:y right-child-xy-pixels))}
               right-show]})]
    {:x (:x right-node)
     :y (:y right-node)
     :rule (u/get-in tree [:rule])
     :g
     [:g
      [:text {:class @parent-class
              :x (:x parent)
              :y (:y parent)}
       show]

      ;; left line:
      [:line.thick {:x1 (:x parent)
                    :y1 (+ 2 (:y parent))
                    :x2 (:x left-child-xy-pixels)
                    :y2 (:y left-child-xy-pixels)}]

      ;; right line:
      [:line.thick {:x1 (:x parent)
                    :y1 (+ 2 (:y parent))
                    :x2 (:x right-child-xy-pixels)
                    :y2 (:y right-child-xy-pixels)}]
      (:g left-node)
      (:g right-node)]}))

(defn draw-tree [tree]
  (if (u/get-in tree [:rule])
    [:svg
     (:g (draw-node tree 2 1))]))
