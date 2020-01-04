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
(def lexicon (-> (nl/deserialize-lexicon (nl/read-compiled-lexicon))
                 vals
                 flatten))

(defn nl-index-fn [spec]
  ;; for now a very bad index function: simply returns all the lexemes
  ;; no matter what the spec is.
  (filter #(or
            (and (= (u/get-in % [:cat])
                    (u/get-in spec [:cat]))
                 (not (= :fail (u/unify spec %))))
            (= ::unspec (u/get-in % [:cat] ::unspec)))
          (-> (nl/deserialize-lexicon (nl/read-compiled-lexicon))
              vals
              flatten)))

(defn noun-phrase []
  (let [np-attempt (g/generate-tiny nl-grammar nl-index-fn)]
    (if (= :fail np-attempt)
      (do
        (log/info (str "retry.."))
        (noun-phrase))
      (nl/syntax-tree np-attempt))))
