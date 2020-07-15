(ns nlquiz.curriculum.specs
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def curriculum
  [{:name "Adjectives"
    :href "adjectives"}
   {:name "Nouns"
    :href "nouns"
    :child [{:name "Definite articles"
             :href "nouns/definite-articles"}
            {:name "Possessive articles"
             :href "nouns/poss"}
            {:name "Nouns with indefinite articles and adjectives"
             :href "nouns/indef-adj"}]}])

(def answer-count (atom 0))
(def question-table (r/atom nil))

(defn show-examples [specs]
  (reset! question-table
          [{:target "rot" :source "red"}
           {:target "blauw" :source "blue"}])
  (reset! answer-count (count @question-table))
  (fn []
    [:div.answertable
     [:table
      [:tbody
       (doall
        (->> (range 0 (count @question-table))
             (map (fn [i]
                    [:tr {:key i :class (if (= 0 (mod i 2)) "even" "odd")}
                     [:th (+ 1 i)]
                     [:td.target (-> @question-table (nth i) :target)]
                     [:td.source (-> @question-table (nth i) :source)]
                     ]))))]]]))

(def guides
  {"nouns"
   {"definite-articles"
    (fn []
      [:div
       [:p "There are two definite articles in Dutch: " [:i "de"]
        " and " [:i "het"] ". A noun will always use " [:i "de"] " when it's plural,"
        " but when singular, the article used depends on the noun's gender:"]
       [:ul
        [:li "If the noun is of " [:b "common"] " gender, then the definite article is " [:i "de"] "."]
        [:li "If the noun is of " [:b "neuter"] " gender, then the definite article is " [:i "het"] "."]]])
    "poss-not-shown" ;; change to "poss" when this is ready to show.
    (fn []
      [:div
       [:p "Here's some stuff about possessive articles."]
       [:table
        [:tr
         [:th "fruit"][:th "count"]]
        [:tr
         [:td "appel"][:th "3"]]
        [:tr
         [:td "sinasappel"][:th "8"]]]])

    "indef-adj"
    (fn []
      [:div
       [:p "Normally, when an adjective modifies a noun, the adjective will"
        " have an -e at the end. For example:"]

       [show-examples [{:cat :noun
                        :agr {:number :plur}
                        :sem {:mod {:first {:number? false}
                                    :rest []}
                              :quant :some}
                        :subcat []
                        :phrasal true
                        :head {:phrasal true}
                        :comp {:phrasal false}}]]

       [:p "However, if:"]
       [:ul
        [:li "the noun is singular,"]
        [:li "the noun is of " [:b "neuter"] " gender, and"]
        [:li "the article is indefinite (" [:i "een"] "),"]]
       [:p "then the adjective will " [:b "not"] " have an -e ending, "
        "for example: "]
       [show-examples [{:cat :noun
                        :agr {:number :sing
                              :gender :neuter}
                        :sem {:mod {:first {:number? false}
                                    :rest []}
                              :quant :some}
                        :subcat []
                        :phrasal true
                        :head {:phrasal true}
                        :comp {:phrasal false}}]]])}})
        

(def specs
  [{:note "intensifier adjective"
    :major-tags ["adjectives"]
    :example "ongewoon slim"
    :cat :adjective
    :mod nil
    :subcat []
    :phrasal true
    :head {:phrasal false}
    :comp {:phrasal false}}
   
   {:note "article+noun"
    :major-tags ["nouns"]
    :minor-tags ["definite-articles"]
    :example "de kat"
    :cat :noun
    :subcat []
    :phrasal true
    :agr {:number :sing}
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :the}}}
   
   {:note "article+noun"
    :major-tags ["nouns"]
    :minor-tags ["articles"]
    :example "de kat"
    :cat :noun
    :subcat []
    :phrasal true
    :agr {:number :plur}
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :the}}}
   
   {:note "article+noun"
    :major-tags ["nouns"]
    :minor-tags ["articles"]
    :example "de kat"
    :cat :noun
    :subcat []
    :phrasal true
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :some}}}
   
   {:note "article+noun"
    :major-tags ["nouns"]
    :minor-tags ["articles"]
    :example "de kat"
    :cat :noun
    :subcat []
    :phrasal true
    :agr {:number :sing}
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :some}}}
   
   {:major-tags ["nouns"]
    :minor-tags ["poss"]
    :example "zijn kat"
    :cat :noun
    :subcat []
    :phrasal true
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :he}}}
   
   {:major-tags ["nouns"]
    :minor-tags ["poss"]
    :example "zijn kat"
    :cat :noun
    :subcat []
    :phrasal true
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false
           :sem {:pred :she}}}

   {:major-tags ["nouns"]
    :minor-tags ["indef-adj"]
    :example "een oud huis"
    :sem {:mod {:first {:number? false}
                :rest []}
          :quant :some
          :ref {:number :sing}}
    :subcat []
    :phrasal true
    :cat :noun
    :head {:phrasal true}
    :comp {:phrasal false}}])

