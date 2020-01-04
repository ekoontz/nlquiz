(ns babylonui.language.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylonui.language :as lang]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(def grammar (->> (nl/read-compiled-grammar)
                  (map dag_unify.serialization/deserialize)))

(def lexicon (atom nil))

(defn index-fn [spec]
  ;; for now a very bad index function: simply returns all the lexemes
  ;; no matter what the spec is.
  (filter #(or
            (and (= (u/get-in % [:cat])
                    (u/get-in spec [:cat]))
                 (not (= :fail (u/unify spec %))))
            (= ::unspec (u/get-in % [:cat] ::unspec)))
          (if (nil? @lexicon)
            (do (swap! lexicon
                       (fn []
                         (-> (nl/read-compiled-lexicon)
                             babylon.lexiconfn/deserialize-lexicon              
                             vals
                             flatten)))
                @lexicon)
            @lexicon)))

(defn generate [spec]
  (let [attempt (g/generate-tiny spec grammar index-fn nl/syntax-tree)]
    (if (= :fail attempt)
      (do
        (log/info (str "retry.."))
        (generate))
      (nl/syntax-tree attempt))))
