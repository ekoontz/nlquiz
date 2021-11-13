;; interface with menard api
(ns nlquiz.menard
  (:require
   [cljslog.core :as log]
   [clojure.string :as string]
   [dag_unify.core :as u]
   [dag_unify.serialization :refer [deserialize serialize]]
   [md5.core :as md5]
   [menard.parse :as parse]
   [menard.serialization :as s]
   [menard.translate.spec :as tr]))

;; [:a :b :c :d] -> "{:0 :a, :1 :b, :2 :c, :3 :d}"
(defn array2map [input]
  (str (zipmap (->> (range 0 (count input))
                    (map (fn [x] (-> x str keyword))))
               input)))

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

(defn nl-parses [input-map grammar morphology surface]
  (let [input-length (count (keys input-map))
        syntax-tree (fn [tree] (s/syntax-tree tree morphology))
        morph (fn [tree] (s/morph tree morphology))]
    (binding [parse/syntax-tree syntax-tree
              parse/morph morph
              parse/truncate? true
              parse/truncate-fn (fn [tree]
                                  (log/info (str "**** NON-DEFAULT TRUNCATION FUNCTION!!!"))
                                  (-> tree
                                      (assoc :syntax-tree (syntax-tree tree))
                                      (assoc :surface (morph tree))
                                      (dissoc :head)
                                      (dissoc :comp)
                                      (dissoc :1)
                                      (dissoc :2)
                                      ))
              ]
      (->
       (parse-in-stages input-map input-length 2 grammar surface)
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

(defn parse-in-stages [input-map input-length i grammar surface]
  (if (>= input-length i)
    (-> input-map
        (parse/parse-next-stage input-length i grammar)
        (parse-in-stages input-length (+ 1 i) grammar surface))
    input-map))

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
