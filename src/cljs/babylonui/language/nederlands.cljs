(ns babylonui.language.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylon.serialization :as s]
   [babylonui.language :as lang]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(def grammar (->> (nl/read-compiled-grammar)
                  (map dag_unify.serialization/deserialize)))
(def lexicon (atom nil))
(def morphology (nl/compile-morphology))

(defn syntax-tree [tree]
  (s/syntax-tree tree morphology))

(defn index-fn [spec]
  ;; for now a very bad index function: simply returns all the lexemes
  ;; no matter what the spec is.
  (filter #(or
            (and (= (u/get-in % [:cat])
                    (u/get-in spec [:cat]))
                 (or true (not (= :fail (u/unify spec %)))))
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

(defn morph
  ([tree]
   (cond
     (map? (u/get-in tree [:syntax-tree]))
     (s/morph (u/get-in tree [:syntax-tree]) morphology)

     true
     (s/morph tree morphology)))

  ([tree & {:keys [sentence-punctuation?]}]
   (if sentence-punctuation?
     (-> tree
         morph
         (nl/sentence-punctuation (u/get-in tree [:sem :mood] :decl))))))

(defn generate [spec]
  (let [attempt (g/generate-tiny spec grammar index-fn syntax-tree)]
    (if (= :fail attempt)
      (do
        (log/info (str "retry.."))
        (generate))
      {:structure attempt
       :syntax-tree (syntax-tree attempt)
       :surface (morph attempt)})))
