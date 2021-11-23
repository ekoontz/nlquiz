;; interface with menard api
(ns nlquiz.menard
  (:require
   [cljslog.core :as log]
   [clojure.string :as string]
   [dag_unify.core :as u]
   [dag_unify.dissoc :refer [dissoc-in]]
   [dag_unify.serialization :refer [deserialize serialize]]
   [md5.core :as md5]
   [menard.parse :as parse]
   [menard.serialization :as s]
   [menard.translate.spec :as tr]))

(defn dag-to-string [dag]
  (-> dag dag_unify.serialization/serialize str))

(defn decode-grammar [response-body]
  (map (fn [rule]
         (-> rule
             cljs.reader/read-string
             deserialize))
       response-body))

(defn decode-morphology [response-body]
  ;; see menard/server.clj: "/morphology/:lang":
  ;; we have to convert the regex to a str so that it can be serialized,
  ;; and here we do the inverse: str->regex using re-pattern:
  (map (fn [{[generate-from generate-to] :g
             [parse-from parse-to] :p
             u :u}]
         {:g [(re-pattern generate-from) generate-to]
          :p [(re-pattern parse-from)    parse-to]
          :u u})
       response-body))
  
(defn decode-parse [response-body]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of serialized lexemes
   (into {}
         (->> (keys response-body)
              (map (fn [k]
                     [(cljs.reader/read-string (string/join (rest (str k))))
                      (map (fn [serialized-lexeme]
                             (-> serialized-lexeme cljs.reader/read-string deserialize))
                           (get response-body k))])))))

(defn strip-map [m]
  (select-keys m [:1 :2 :canonical :rule :surface]))

;; copied from menard/nederlands.cljc:
;; TODO: use menard/nederlands.cljc's version
;; instead of duplicating here.
(defn tokenize [input-string]
  (binding [parse/split-on #"[ ]"]
    (parse/tokenize input-string)))

(defn nl-parses [input-map grammar morphology surface]
  (let [input-length (count (tokenize surface))
        syntax-tree (fn [tree] (s/syntax-tree tree morphology))
        morph (fn [tree] (s/morph tree morphology))]
    (binding [parse/syntax-tree syntax-tree
              parse/morph morph
              parse/truncate? true
              parse/truncate-fn (fn [tree]
                                  (-> tree
                                      (dissoc :head)
                                      (dissoc :comp)
                                      (assoc :1 (strip-map (u/get-in tree [:1])))
                                      (assoc :2 (strip-map (u/get-in tree [:2])))))]
      (->
       (parse/parse-in-stages input-map input-length 2 grammar surface)
       (get [0 input-length])
       remove-duplicates))))

(defn nl-sem [nl-parses]
  (->> nl-parses
       (map #(u/get-in % [:sem]))
       remove-duplicates))

(defn nl-tokens [input-map]
  (into
   {}
   (->>
    (-> input-map keys)
    (map (fn [k]
           [(-> k first str keyword)
            (-> input-map
                (get k)
                first
                ((fn [x]
                   (or (u/get-in x [:surface])
                       (u/get-in x [:canonical])))))])))))

(defn nl-trees [nl-parses]
  (map syntax-tree nl-parses))

(defn print-stage [stage-map]
  [:table
   [:thead
    [:tr
     [:th [:h2 "span"]]
     [:th [:h2 "expressions"]]]]
   [:tbody
    (->> (sort (keys stage-map))
         (map (fn [k]
                (let [v (get stage-map k)]
                  [:tr {:key (md5/string->md5-hex (str k))}
                   [:td (str k)]
                   [:td (->> v
                             (map (fn [each-expression]
                                    [:div.debug {:key (md5/string->md5-hex (str each-expression))}
                                     (str each-expression)
                                     ])))]]))))]])

(defn remove-duplicates [input]
  (->> input
       (map dag_unify.serialization/serialize)
       set
       vec
       (map dag_unify.serialization/deserialize)))

(defn submit-guess [guess-text the-input-element]
  (log/info (str "submit-guess: " guess-text)))

(defn nl-parses-to-en-specs [nl-parses]
  (->> nl-parses
       (map dag_unify.serialization/serialize)
       set
       vec
       (map dag_unify.serialization/deserialize)
       (map tr/nl-to-en-spec)
       remove-duplicates))
