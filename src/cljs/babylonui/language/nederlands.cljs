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

(def lexicon-vals (flatten (vals nl-lexicon)))

(defn nl-index-fn [spec]
  ;; for now a very bad index function: simply returns all the lexemes
  ;; no matter what the spec is.
  (cond (= :noun (u/get-in spec [:cat]))
        (filter #(= :noun (u/get-in % [:cat]))
                lexicon-vals)
        (= :det (u/get-in spec [:cat]))
        (filter #(= :det (u/get-in % [:cat]))
                lexicon-vals)
        true
        lexicon-vals))

;; move to babylon
(defn generate-tiny [grammar lexicon-fn]
  (let [phrase
        (u/unify
         (first (shuffle (filter #(and
                                   (= :noun (u/get-in % [:cat]))
                                   (empty? (u/get-in % [:subcat])))
                                 grammar)))
         {:head {:phrasal false}
          :comp {:phrasal false}
          :babylon.generate/started? true})

        noun (first (shuffle (lexicon-fn {:cat :noun})))
        det (first (shuffle (lexicon-fn {:cat :det})))]
    (u/unify phrase
             {:head noun}
             {:comp det})))

(defn noun-phrase []
  (let [np-attempt (generate-tiny nl-grammar nl-index-fn)]
    (if (= :fail np-attempt)
      (do
        (log/info (str "retry.."))
        (noun-phrase))
      (nl/syntax-tree np-attempt))))
