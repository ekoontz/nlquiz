(ns babylonui.language.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylonui.language :as lang]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(def nl-grammar (->> (nl/read-compiled-grammar)
                     (map dag_unify.serialization/deserialize)))
(def nl-lexicon (lang/deserialize-lexicon (nl/read-compiled-lexicon)))

(def foo (* 3 (count nl-grammar)))

(defn foo2 []
  (let [phrase
        (u/unify
         (first (shuffle (filter #(and
                                   (= :noun (u/get-in % [:cat]))
                                   (empty? (u/get-in % [:subcat])))
                                 nl-grammar)))
         {:head {:phrasal false}
          :comp {:phrasal false}
          :babylon.generate/started? true})

        noun (first (->> nl-lexicon
                         vals
                         flatten
                         (filter #(= :noun (u/get-in % [:cat])))
                         shuffle))
        det (first (->> nl-lexicon
                         vals
                         flatten
                         (filter #(= :det (u/get-in % [:cat])))
                         shuffle))]
    (nl/syntax-tree
     (u/unify phrase
              {:head noun}
              {:comp det}))))

(defn generate []
  (let [rule
        (u/unify
         (first (shuffle (filter #(and
                                    (= :noun (u/get-in % [:cat]))
                                    (empty? (u/get-in % [:subcat])))
                                 nl-grammar)))
         {:head {:phrasal false}
          :comp {:phrasal false}
          :babylon.generate/started? true})]
    (log/info (str "rule: " (u/get-in rule [:rule])))
    (u/get-in rule [:rule])))
