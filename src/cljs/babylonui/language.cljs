(ns babylonui.language
  (:require-macros [babylon.english]
                   [babylon.nederlands])
  (:require
   [babylon.english :as en]
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
(defn nl-index-fn [spec]
  ;; for now a very bad index function: simply returns all the lexemes
  ;; no matter what the spec is.
  (vals nl-lexicon))

(defn generate-a-np [grammar lexicon]                            
  (let [rule (first (shuffle (filter #(= :noun (u/get-in % [:cat]))
                                     grammar)))]
    (log/info (str "showing noun-type rule: " (u/get-in rule [:rule])))
    rule))

