(ns babylonui.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylon.serialization :as s]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(declare grammar)
(declare index-fn)
(declare morph)
(declare syntax-tree)

(defn generate [spec & [times]]
  (let [attempt
        (try
          (g/generate spec
                      (grammar)
                      (fn [spec]
                        (shuffle (index-fn spec)))
                      syntax-tree)
          (catch js/Error e
            (cond
              (or (nil? times)
                  (< times 5))
              (do
                (log/info (str "retry #" (or times 1)))
                (generate spec (if (nil? times) 2 (+ 1 times))))
              true
              (log/error (str "giving up generating after 5 times; sorry.")))))]
      (cond
        (and (not (nil? times))
             (< times 5)
             (or (= :fail attempt)
                 (= :fail attempt)))
        (do
          (log/info (str "retry #" times))
          (generate spec (if (nil? times) 1 (+ 1 times))))
        (= :fail attempt)
        (log/error (str "giving up generating after 5 times; sorry."))
        true
        {:structure attempt
         :syntax-tree (syntax-tree attempt)
         :surface (morph attempt)})))

(declare morphology)

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

(def grammar-atom (atom nil))
(def lexicon-atom (atom nil))
(def morphology-atom (atom nil))
(def expressions-atom (atom nil))
(def lexeme-map-atom (atom nil))

(defn grammar []
  (->> (nl/read-compiled-grammar)
       (map dag_unify.serialization/deserialize)))

(defn morphology []
  (or @morphology-atom
      (do (swap! morphology-atom (fn [] (nl/compile-morphology)))
          @morphology-atom)))

(defn expressions []
  (or @expressions-atom
      (do (swap! expressions-atom (fn [] (nl/read-expressions))))))

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

(defn lexeme-map []
  (if (nil? @lexeme-map-atom)
    (do (swap! lexeme-map-atom
               (fn []
                 {:verb (->> (lexicon)
                             (filter #(= :verb (u/get-in % [:cat]))))
                  :det (->> (lexicon)
                            (filter #(= :det (u/get-in % [:cat]))))
                  :intensifier (->> (lexicon)
                                    (filter #(= :intensifier (u/get-in % [:cat]))))
                  :noun (->> (lexicon)
                             (filter #(= :noun (u/get-in % [:cat]))))
                  :top (lexicon)
                  :adjective (->> (lexicon)                                                          
                                  (filter #(= :adjective (u/get-in % [:cat]))))})))
    @lexeme-map-atom))

(defn syntax-tree [tree]
  (s/syntax-tree tree (morphology)))

(defn index-fn [spec]
  ;; for now a somewhat bad index function: simply returns
  ;; lexemes which match the spec's :cat, or, if the :cat isn't
  ;; defined, just return all the lexemes.
  (log/debug (str "looking for key: " (u/get-in spec [:cat] ::none)))
  (let [result (get (lexeme-map) (u/get-in spec [:cat] :top) nil)]
    (if (not (nil? result))
        (shuffle result)
        (do
          (log/info (str "no entry from cat: " (u/get-in spec [:cat] ::none) " in lexeme-map: returning all lexemes."))
          (lexicon)))))
