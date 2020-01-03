(ns babylonui.language
  (:require-macros [babylon.english]
                   [babylon.nederlands])
  (:require
   [babylon.english :as en]
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(defn deserialize-lexicon [map-with-serializations]
  (zipmap
   (keys map-with-serializations)
   (map (fn [serializations]
          (vec (map dag_unify.serialization/deserialize
                    serializations)))
        (vals map-with-serializations))))

(def en-lexicon (deserialize-lexicon (en/read-compiled-lexicon)))

(def nl-grammar (->> (nl/read-compiled-grammar)
                     (map dag_unify.serialization/deserialize)))
(def nl-lexicon (deserialize-lexicon (nl/read-compiled-lexicon)))

(defn generate-a-np [grammar lexicon index-fn]                            
  (let [rule
        (u/unify
         (first (shuffle (filter #(and
                                    (= :noun (u/get-in % [:cat]))
                                    (empty? (u/get-in % [:subcat])))
                                 grammar)))
         {:head {:phrasal false}
          :comp {:phrasal false}
          :babylon.generate/started? true})
        syntax-tree nl/syntax-tree
        morph nl/morph]
    (log/debug (str "showing noun-type rule: " (u/get-in rule [:rule])))
    (let [phrase
          (first
           (-> rule
               (g/add grammar index-fn syntax-tree)
               (g/add grammar index-fn syntax-tree)))]
      (log/debug (str "after add: " (syntax-tree phrase)))
      (log/debug (str "head: " (u/get-in phrase [:head])))
      {:tree (syntax-tree phrase)
       :rule (u/get-in phrase [:rule])
       :surface (morph phrase)})))

