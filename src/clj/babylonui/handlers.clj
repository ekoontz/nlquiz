(ns babylonui.handlers
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.middleware :refer [middleware]]
   [config.core :refer [env]]
   [clojure.data.json :as json :refer [write-str]]
   [dag_unify.core :as u]
   [hiccup.page :refer [include-js include-css html5]]))

(def nl-expressions
  (filter #(= true (u/get-in % [:menuable?] true))
          nl/expressions))

(defn generate [_request]
  (let [spec-index (-> _request :path-params :spec)
        spec (nth nl-expressions (Integer. spec-index))
        debug (log/info (str "generating a question with spec: " spec))
        target-expression (-> spec nl/generate)
        source-expression (-> target-expression tr/nl-to-en-spec en/generate)]
    {:source (-> source-expression en/morph)
     :target (-> target-expression nl/morph)}))

(defn parse [_request]
  (let [string-to-parse
        (get
         (-> _request :query-params) "q")]
    (log/info (str "parsing input: " string-to-parse))
    (let [parses (->> string-to-parse nl/parse)
          serialized (->> parses (map u/pprint))
          syntax-trees (->> parses (map nl/syntax-tree))]
      {:parses syntax-trees
       :serialized serialized})))




