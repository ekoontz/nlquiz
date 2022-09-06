(ns nlquiz.test.menard
  (:require [clojure.test :refer [deftest is]]
            [dag_unify.serialization :as serialization]))

(def encoded-parse
  {"[0 1]"
   '("[[[] {:cat :det, :phrasal? false, :null? false, :inflected? true, :possessive? false, :curriculum :user/none, :agr {:gender :common, :number :sing}, :definite? true, :sem {:pred :the}, :canonical \"de\"}]]"
    "[[[] {:cat :det, :phrasal? false, :null? false, :inflected? true, :possessive? false, :curriculum :user/none, :agr {:number :plur}, :definite? true, :sem {:pred :the}, :canonical \"de\"}]]"),
   "[1 2]"
   '("[[[] {:pronoun? false, :cat :noun, :phrasal? false, :regular true, :null? false, :mod [], :curriculum :user/none, :subcat {:1 {:cat :det, :agr :top, :sem {:countable? :top, :arg1 :top, :arg2 :top, :pred :top}}, :2 []}, :agr :top, :propernoun? false, :sem {:countable? :top, :pred :cat, :arg2 :top, :ref {:canine? false, :human? false, :number :top}, :mod [], :existential? false, :quant :top, :number? false, :context :none, :arg1 :top}, :canonical \"kat\", :inflection :repeated-consonant, :reflexive? false, :surface \"kat\"}] [[[:agr] [:subcat :1 :agr]] {:gender :common, :number :top, :person :3rd}] [[[:sem :ref :number] [:agr :number] [:subcat :1 :agr :number]] :sing] [[[:sem :countable?] [:subcat :1 :sem :countable?]] true] [[[:subcat :1 :sem :arg1] [:sem :arg1]] :top] [[[:sem :arg2] [:subcat :1 :sem :arg2]] :top] [[[:subcat :1 :sem :pred] [:sem :quant]] :top]]")})

(defn decode-parse [response-body]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of trees, each of which spans the span indicated by the key.
   (into {}
         (->> (keys response-body)
              (map (fn [k]
                     [(read-string (clojure.string/join (rest (str k))))
                      (map (fn [serialized-lexeme]
                             (-> serialized-lexeme read-string serialization/deserialize))
                           (get response-body k))])))))

(deftest foo
  (is (= 0 0)))

