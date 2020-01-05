(ns babylonui.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylon.serialization :as s]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(def grammar-atom (atom nil))
(def lexicon-atom (atom nil))
(def morphology-atom (atom nil))

(defn grammar []
  (->> (nl/read-compiled-grammar)
       (map dag_unify.serialization/deserialize)))

(defn morphology []
  (or @morphology-atom
      (do (swap! morphology-atom (fn [] (nl/compile-morphology)))
          @morphology-atom)))

(defn lexicon []
  (if (nil? @lexicon-atom)
    (do (swap! lexicon-atom
               (fn []
                 (-> (nl/read-compiled-lexicon)
                     babylon.lexiconfn/deserialize-lexicon              
                     vals
                     flatten)))
        @lexicon-atom)
    @lexicon-atom))

(defn syntax-tree [tree]
  (s/syntax-tree tree (morphology)))

(defn index-fn [spec]
  ;; for now a somewhat bad index function: simply returns
  ;; lexemes which match the spec's :cat, or, if the :cat isn't
  ;; defined, just return all the lexemes.
  (let [lexicon (lexicon)]
    (filter #(= (u/get-in % [:cat] :top)
                (u/get-in spec [:cat] :top))
            lexicon)))

(defn morph
  ([tree]
   (cond
     (map? (u/get-in tree [:syntax-tree]))
     (s/morph (u/get-in tree [:syntax-tree]) (morphology))

     true
     (s/morph tree (morphology))))

  ([tree & {:keys [sentence-punctuation?]}]
   (if sentence-punctuation?
     (-> tree
         morph
         (nl/sentence-punctuation (u/get-in tree [:sem :mood] :decl))))))

(defn generate [spec & [times]]
  (let [attempt (first (g/generate-small [spec]
                                         (grammar)
                                         (fn [spec]
                                           (shuffle (index-fn spec)))
                                         syntax-tree))]
    (cond
      (and (not (nil? times))
           (< times 5)
           (= :fail attempt))
      (do
        (log/info (str "retry.." times))
        (generate spec (if (nil? times) 1 (+ 1 times))))
      (= :fail attempt)
      (log/error (str "giving up generating after 5 times; sorry."))
      true
      {:structure attempt
       :syntax-tree (syntax-tree attempt)
       :surface (morph attempt)})))

