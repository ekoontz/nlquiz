;; interface with menard api
(ns nlquiz.menard
  (:require
   [nlquiz.log :as log]
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
  
(defn decode-analyze [response-body]
  ;; a sequence of lexemes.
  (->> response-body
       (map cljs.reader/read-string)
       (map deserialize)
       (filter (fn [lexeme]
                 (not (= "_" (u/get-in lexeme [:canonical])))))))

(defn decode-rules [response-body]
  ;; a sequence of rules.
  (->> response-body
       (map cljs.reader/read-string)
       (map deserialize)))

(defn decode-parse [input-map]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of trees, each of which spans the span indicated by the key.
  (log/debug (str "decode-parse: input-map: " input-map))
  (into {}
         (->> (keys input-map)
              (map (fn [k]
                     (log/debug (str "decode-parse: got a key: " k))
                     [(cljs.reader/read-string (string/join (rest (str k))))
                      (map (fn [serialized-lexeme]
                             (-> serialized-lexeme cljs.reader/read-string deserialize))
                           (get input-map k))])))))

(defn decode-parses [response-body]
  (log/debug (str "decode-parses: response-body: " response-body))
  (if (seq response-body)
    (cons
     (decode-parse (first response-body))
     (decode-parses (rest response-body)))))

(defn strip-map [m]
  (select-keys m [:1 :2 :canonical :rule :surface]))

;; copied from menard/nederlands.cljc:
;; TODO: use menard/nederlands.cljc's version
;; instead of duplicating here.
(defn tokenize [input-string]
  (let [split-on #"[ ]"

        ;; this analyze-fn simply returns true for every possible
        ;; sequence of words combined as a token:
        analyze-fn (fn [token] [true])]

    (parse/tokenize input-string split-on analyze-fn)))

(defn parses [input-map grammar morphology surface & [truncate?]]
  (let [truncate? (if (false? truncate?)
                    false
                    true)
        syntax-tree (fn [tree] (s/syntax-tree tree morphology))
        morph (fn [tree] (s/morph tree morphology))
        input-length (-> input-map keys count)]
    (log/debug (str "nlquiz.menard parses begin with input-map: " input-map
                    " of length: " input-length " and truncate? " truncate?))
    (if (seq (get input-map [0 input-length]))
      ;; supplied input-map has the whole parse, all the way from [0, input-length]:
      (do
        (get input-map [0 input-length]))

      ;; else, input-map supplied less than the full parse (e.g. just the
      ;; tokenizations with all the lexemes looked up for the tokenizations),
      ;; so parse is not complete yet: we need to parse ourselves.
      (->
       (parse/parse-in-stages input-map input-length 2 grammar syntax-tree morph truncate?)
       (get [0 input-length])
       ;; not sure if this is necessary:
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
