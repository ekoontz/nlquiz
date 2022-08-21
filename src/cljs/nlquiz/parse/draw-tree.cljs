(ns nlquiz.parse.draw-tree
  (:require
   [dag_unify.core :as u :refer [ref?]]
   [dag_unify.serialization :refer [deserialize final-reference-of serialize]]
   [nlquiz.log :as log]
   [reagent.core :as r]))

(def ref-counter (atom 0))
(def ^:dynamic html-index-map)
(declare draw-node-html-with-binding)

(defn draw-node-html [parse-node]
  (binding [html-index-map (atom {})]
    (swap! ref-counter (fn [_] 0))
    (-> parse-node
        serialize ;; serialize and deserialize to remove singleton references.
        deserialize
        draw-node-html-with-binding)))

(defn display-derivation [deriv]
  (->> (seq (zipmap (vals deriv) (keys deriv)))
       (map (fn [x] {(-> x first :menard.lexiconfn/order)
                     (if (-> x first :sense)
                       {:rule (-> x second)
                        :sense (-> x first :sense)}
                       (-> x second))}))
       (reduce merge)))
  
(defn draw-node-html-with-binding [parse-node]
  (if (nil? html-index-map)
    (log/error (str "html-index-map is NULL :(; parse-node: "
                    (u/pprint parse-node))))
  (let [interesting-key? (fn [k] (or (= k :agr)
                                     (= k :definite?)
                                     (= k :number)
                                     (= k :ref)
                                     (= k :obj)
                                     (= k :subcat)
                                     (= k :top)))

        ;; hide {k v=:top} pairs since
        ;; they aren't very interesting:
        uninteresting-val? (fn [v k] (or (and (= v :top)
                                              (not (interesting-key? k)))
                                         (and (= v :none)
                                              (not (interesting-key? k)))
                                         (and (= v [])
                                              (not (interesting-key? k)))))
        uninteresting-key? (fn [k] (or (= k :phrasal?)
                                       (= k :np?)
                                       (= k :exceptions)
                                       (= k :nlquiz.parse.functions/i)
                                       (= k :menard.nesting/only-one-allowed-of)
                                       (= k :menard.generate/started?)))
        interesting-keys
        ;; remove uninteresting keys:
        (->> parse-node keys (remove uninteresting-key?) sort)]
    (when (and (map? parse-node)
               (seq interesting-keys))
      ;; TODO: currently, it's possible that we'll have an empty [:table.treenode],
      ;; if there are interesting keys, but all values are uninteresting.
      (let [retval
            [:table.treenode
             [:tbody
              (map (fn [k]
                     (let [val (u/get-in parse-node [k])]
                       (if (not (uninteresting-val? val k)) 
                         (let [v (if (ref? (get parse-node k))
                                   (final-reference-of (get parse-node k)))
                               entry-if-any (get @html-index-map v)
                               ref-index 
                               (if v
                                 (or entry-if-any
                                     ;; if v is not in html-index-map,
                                     ;; add it now, with its key being
                                     ;; the next value of ref-counter
                                     ;; (which was initialized at 0 at the
                                     ;;  beginning of draw-node-html).
                                     (do (swap! ref-counter (fn [x] (+ 1 x)))
                                         (swap! html-index-map
                                                (fn [x] (assoc x v @ref-counter)))
                                         @ref-counter)))]
                               [:tr
                            {:key k}
                            [:th k]
                            [:td
                             (if ref-index
                               [:div.index ref-index])
                             [:div.node
                              (cond

                                ;; already seen index:
                                (and ref-index entry-if-any) ""

                                (or (= k :menard.lexiconfn/derivation)
                                    (= k :head-derivation)
                                    (= k :comp-derivation))
                                (draw-node-html-with-binding (display-derivation val))
                                
                                (map? val)
                                (draw-node-html-with-binding val)
                                (= val :menard.nederlands/none) "none"
                                (= :rule k) val
                                (string? val) [:i val]
                                (= val :top) [:span "⊤"]
                                (= val :fail) [:span "⊥"]
                                (keyword? val) val
                                (boolean? val) [:b (if (true? val) "+" "-")]
                                (nil? val) [:tt "NULL"]
                                (= val []) [:tt "[ ]"]
                                :else (str val))]]]))))
                   interesting-keys)]]]

        ;; this is needed for some reason: otherwise the dynamic variable
        ;; won't be bound in recursive calls to draw-node-html-with-binding.
        (log/debug (count (subs (str retval) 0 1)))

        retval))))

(def ^:const v-unit 30)
(def ^:const vspace 10)
(def ^:const h-unit 50)

(defn draw-node [tree x y is-head?]
  (let [left-is-head? (or (= (get tree :head) (get tree :1))
                          (true? (get tree :left-is-head?)))
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
          x-scale 2.5
          y-scale 2.5]
      [:div.outer
       [:div.inner
        [:svg {:style {:height (str (* (:max-y tree) y-scale) "em")
                       :width (str (* (:max-x tree) x-scale) "em")}}
         (:g tree)]]])))

