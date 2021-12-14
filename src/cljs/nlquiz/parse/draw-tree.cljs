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
  (let [;; hide {k v=:top} pairs since
        ;; they aren't very interesting:
        uninteresting-val? (fn [v] (or (= v :top)
                                       (= v :none)
                                       (= v [])))
        uninteresting-key? (fn [k] (or (= k :phrasal?)
                                       (= k :np?)
                                       (= k :menard.nesting/only-one-allowed-of)
                                       (= k :menard.generate/started?)))]
    (when (map? parse-node)
      [:table.treenode
       [:tbody
        (map (fn [k]
               (let [val
                     (u/get-in parse-node [k])]
                 (if (not (uninteresting-val? val)) 
                   [:tr
                    {:key k}
                    [:th k]
                    [:td
                     [:div.index "1"]
                     (draw-node-value k val)]])))
             ;; remove uninteresting keys:
             (->> parse-node keys (remove uninteresting-key?) sort))]])))

(defn draw-node-value [k v]
  [:div.node
   (cond
     (map? v) (draw-node-html v)
     (= v :menard.nederlands/none) "none"
     (= :rule k) v
     (string? v) [:i v]
     (keyword? v) v
     (boolean? v) [:b (if (true? v) "+" "-")]
     (nil? v) [:tt "NULL"]
     (= v []) [:tt "[ ]"]
     :else (str v))])

(defn draw-node [tree x y is-head?]
  (let [left-is-head? (= (get tree :head) (get tree :1))
        rule (u/get-in tree [:rule] nil)
        surface (u/get-in tree [:surface] nil)
        canonical (u/get-in tree [:canonical] nil)
        show (or rule surface canonical)
        parent {:x (* x h-unit) :y (+ vspace (* y v-unit))}
        parent-class (r/atom (str "rule"
                                  (if is-head? " rule-head")))
        ;; left
        left-rule (u/get-in tree [:1 :rule])
        left-class (r/atom (str "leaf"
                                (if left-is-head?
                                  " leaf-head")))
        left-surface (u/get-in tree [:1 :surface])
        left-canonical (u/get-in tree [:1 :canonical])
        left-show (or left-rule left-surface left-canonical)
        left-child-xy-units {:x (- x 1) :y (+ y 1)}
        left-child-xy-pixels {:x (* (:x left-child-xy-units) h-unit)
                              :y (+ vspace (* (:y left-child-xy-units) v-unit))}
        left-node
        (if left-rule
          (draw-node (u/get-in tree [:1]) (- x 1) (+ y 1)
                     left-is-head?)
          ;; left child is a leaf:
          {:x (:x left-child-xy-units)
           :y (:y left-child-xy-units)
           :g [:text {:class @left-class
                      :x (:x left-child-xy-pixels)
                      :y (+ vspace (:y left-child-xy-pixels))}
               left-show]})

        ;; right:
        right-rule (u/get-in tree [:2 :rule])
        right-class (r/atom (str "leaf"
                                 (if (not left-is-head?)
                                   " leaf-head")))
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
                     (:y right-child-xy-units) (not left-is-head?))

          ;; else, right child is a leaf:
          {:x (:x right-child-xy-units)
           :y (:y right-child-xy-units)
           :g [:text {:class @right-class
                      :x (:x right-child-xy-pixels)
                      :y (+ vspace (:y right-child-xy-pixels))}
               right-show]})]
    {:x (:x right-node)
     :y (:y right-node)
     :max-x (max (:x left-node)
                 (:x right-node)
                 (:max-x left-node)
                 (:max-x right-node))
     :max-y (max (:y left-node)
                 (:y right-node)
                 (:max-y left-node)
                 (:max-y right-node))
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
    (let [tree (draw-node tree 2 1 false)
          x-scale 3
          y-scale 2.5]
      [:svg {:style {:height (str (* (:max-y tree) y-scale) "em")
                     :width (str (* (:max-x tree) x-scale) "em")}}
       (:g tree)])))


