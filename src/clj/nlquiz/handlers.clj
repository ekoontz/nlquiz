(ns nlquiz.handlers
  (:require
   [clojure.tools.logging :as log]
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [dag_unify.core :as u]))

(def nl-expressions
  (filter #(= true (u/get-in % [:menuable?] true))
          nl/expressions))

(defn dag-to-string [dag]
  (-> dag dag_unify.serialization/serialize str))

(defn generate [spec]
  (let [debug (log/info (str "generating a question with spec: " spec))
        debug (log/info (str "input spec type: " (type spec)))
        target-expression (-> spec nl/generate)
        source-expression (-> target-expression tr/nl-to-en-spec en/generate)
        source-semantics (->> source-expression en/morph en/parse (map #(u/get-in % [:sem])))]
    (log/info (str "generated: '" (-> source-expression en/morph) "'"
                   " -> '"  (-> target-expression nl/morph) "'"))
    {:source (-> source-expression en/morph)
     :source-sem (map dag-to-string source-semantics)
     :target (-> target-expression nl/morph)}))

(defn generate-by-expression-index [_request]
  (let [expression-index (-> _request :path-params :expression-index)
        spec (nth nl-expressions (Integer. expression-index))]
    (generate spec)))

(defn generate-by-spec [_request]
  (let [spec (get (-> _request :query-params) "q")]
    (log/info (str "spec pre-decode: " spec))
    (let [spec (-> spec read-string dag_unify.serialization/deserialize)]
      (log/info (str "spec decoded: " spec))
      (generate spec))))

(defn parse-nl [_request]
  (let [string-to-parse
        (get
         (-> _request :query-params) "q")]
    (log/debug (str "parsing input: " string-to-parse))
    (let [parses (->> string-to-parse clojure.string/lower-case nl/parse
                      (filter #(or (= [] (u/get-in % [:subcat]))
                                   (= :top (u/get-in % [:subcat]))
                                   (= ::none (u/get-in % [:subcat] ::none))))
                      (filter #(= nil (u/get-in % [:mod] nil))))
          syntax-trees (->> parses (map nl/syntax-tree))]
      {:trees syntax-trees
       :sem (->> parses
                 (map #(u/get-in % [:sem]))
                 (map dag-to-string))})))

(defn parse-en [_request]
  (let [string-to-parse
        (get
         (-> _request :query-params) "q")]
    (log/debug (str "parsing input: " string-to-parse))
    (let [parses (->> string-to-parse clojure.string/lower-case en/parse
                      (filter #(or (= [] (u/get-in % [:subcat]))
                                   (= :top (u/get-in % [:subcat]))
                                   (= ::none (u/get-in % [:subcat] ::none))))
                      (filter #(= nil (u/get-in % [:mod] nil))))
          syntax-trees (->> parses (map en/syntax-tree))]
      {:trees syntax-trees
       :sem (->> parses
                 (map #(u/get-in % [:sem]))
                 (map dag-to-string))})))
