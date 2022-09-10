(ns nlquiz.test.menard
  (:require [clojure.test :refer [deftest is]]
            [dag_unify.serialization :as serialization]
            [dag_unify.core :as u]))

(def encoded-lookup
  '({"[0 1]"
     '("[[[] {:cat :det, :phrasal? false, :null? false, :inflected? true, :possessive? false, :curriculum :user/none, :agr {:gender :common, :number :sing}, :definite? true, :sem {:pred :the}, :canonical \"de\"}]]"
       "[[[] {:cat :det, :phrasal? false, :null? false, :inflected? true, :possessive? false, :curriculum :user/none, :agr {:number :plur}, :definite? true, :sem {:pred :the}, :canonical \"de\"}]]"),
   "[1 2]"
   '("[[[] {:pronoun? false, :cat :noun, :phrasal? false, :regular true, :null? false, :mod [], :curriculum :user/none, :subcat {:1 {:cat :det, :agr :top, :sem {:countable? :top, :arg1 :top, :arg2 :top, :pred :top}}, :2 []}, :agr :top, :propernoun? false, :sem {:countable? :top, :pred :cat, :arg2 :top, :ref {:canine? false, :human? false, :number :top}, :mod [], :existential? false, :quant :top, :number? false, :context :none, :arg1 :top}, :canonical \"kat\", :inflection :repeated-consonant, :reflexive? false, :surface \"kat\"}] [[[:agr] [:subcat :1 :agr]] {:gender :common, :number :top, :person :3rd}] [[[:sem :ref :number] [:agr :number] [:subcat :1 :agr :number]] :sing] [[[:sem :countable?] [:subcat :1 :sem :countable?]] true] [[[:subcat :1 :sem :arg1] [:sem :arg1]] :top] [[[:sem :arg2] [:subcat :1 :sem :arg2]] :top] [[[:subcat :1 :sem :pred] [:sem :quant]] :top]]")}))

(defn decode-lookup [encoded-lookup]
  ;; encoded-lookup is a map between:
  ;; keys: each key is a span e.g. [0 1]
  ;; vals: each val is a sequence of lexemes, each of which spans the span: [i i+1] indicated by the key.
  ;; This returns a similar map, except the sequence of lexemes is decoded. 
  (into {}
        (->> (keys encoded-lookup)
             (map (fn [k]
                    [(read-string (clojure.string/join (rest (str k))))
                     (map (fn [serialized-lexeme]
                            (-> serialized-lexeme read-string serialization/deserialize))
                          (get encoded-lookup k))])))))

(deftest foo
  (let [decoded-lookups (map decode-lookup encoded-lookup)]
    (let [decoded-lookup (first decoded-lookups)]
      (is (map? decoded-lookup))
      (is (= '(0 1) (keys decoded-lookup))))))


  
