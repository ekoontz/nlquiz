(ns nlquiz.test.menard
  (:require [clojure.test :refer [deftest is]]
            [dag_unify.serialization :as serialization]
            [dag_unify.core :as u]
            [menard.handlers :as handlers]))
  
(defn decode-lookup [encoded-lookup]
  ;; encoded-lookup is a map between:
  ;; keys: each key is a span e.g. [0 1]
  ;; vals: each val is a sequence of lexemes, each of which spans the span: [i i+1] indicated by the key.
  ;; This returns a similar map, except the sequence of lexemes is decoded. 
  (into {}
        (->> (keys encoded-lookup)
             (map (fn [k]
                    [(read-string (clojure.string/join (rest (str k))))
                     (map (fn [serialized-lexeme]
                            (-> serialized-lexeme read-string serialization/deserialize))
                          (get encoded-lookup k))])))))

(deftest can-understand-server-lookups
  (let [decoded-lookups
        (->> "de kat"
             handlers/parse-nl-start
             (map decode-lookup))]
    (doall
     (->>
      decoded-lookups
      (take 1)
      (map (fn [decoded-lookup]
             (is (map? decoded-lookup))
             (is (= '(0 1) (keys decoded-lookup)))))))))


  
