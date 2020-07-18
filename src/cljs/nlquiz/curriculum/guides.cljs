(ns nlquiz.curriculum.guides
  (:require
   [nlquiz.curriculum.functions :refer [show-examples]]
   [reagent.core :as r]))

(def guides
  {"adjectives"
   (fn []
     [:div
      [:p "Adjectives modify nouns. Adverbs, in turn, modify adjectives. Here are some examples of an adjective modified by an adverb:"]
      [show-examples
       [{:cat :adjective
         :mod nil
         :subcat []
         :phrasal true
         :head {:phrasal false}
         :comp {:phrasal false}}]]])
   "nouns"
   {:general
    (fn []
      [:div
       [:p "A noun usually refers to a person, place, thing, or event. The quiz for this section cover all of the noun-related subsections, but "
        " you might want to first practice specific subsections before trying this more general section."]])
    "definite-articles"
    (fn []
      [:div
       [:p "Unlike in English, but as in German or Romance languages, nouns"
        " in Dutch have a gender. There are two genders: " [:b "common"] " and " [:b "neuter"] "."]
       [:p "There are two definite articles in Dutch: " [:i "de"]
        " and " [:i "het"] ". A noun will always use " [:i "de"] " when it's plural, regardless of the noun's gender:"]
       [show-examples
        [{:cat :noun
          :phrasal true
          :subcat []
          :sem {:quant :the}
          :agr {:number :plur}}]]
       [:p "But when the noun is singular, the article used depends on the noun's gender."]
       [:ul
        [:li "If the noun is of " [:b "common"] " gender, then the definite article is " [:i "de"] ", for example:"
         [show-examples
          [{:cat :noun
            :subcat []
            :sem {:quant :the}
            :agr {:gender :common
                  :number :sing}
            :phrasal true
            :head {:phrasal false}}]]]
        [:li "If the noun is of " [:b "neuter"] " gender, then the definite article is " [:i "het"] ", for example:"
         [show-examples
          [{:cat :noun
            :subcat []
            :sem {:quant :the}
            :agr {:gender :neuter
                  :number :sing}
            :phrasal true
            :head {:phrasal false}}]]]]])
    "poss" 
    (fn []
      [:div
       [:p "A noun's article can be a " [:b "possessive article"] ", for example:"]
       [show-examples
        [{:cat :noun
          :subcat []
          :phrasal true
          :head {:phrasal false
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal false
                 :sem {:pred :he}}}
         {:cat :noun
          :subcat []
          :phrasal true
          :head {:phrasal false
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal false
                 :sem {:pred :she}}}          
         {:cat :noun
          :subcat []
          :phrasal true
          :head {:phrasal false
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal false
                 :sem {:pred :i}}}]]
       [:p "The article varies by number and gender."]])

    "indef-adj"
    (fn []
      [:div
       [:p "In most cases, when an adjective modifies a noun, the adjective will"
        " have an " [:i "-e"] " at the end. For example:"]

       [show-examples
        [{:cat :noun
          :agr {:number :plur}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :some}
          :subcat []
          :phrasal true
          :head {:phrasal true}
          :comp {:phrasal false}}

         {:cat :noun
          :agr {:number :sing
                :gender :common}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :some}
          :subcat []
          :phrasal true
          :head {:phrasal true}
          :comp {:phrasal false}}

         {:cat :noun
          :agr {:number :sing
                :gender :common}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :the}
          :subcat []
          :phrasal true
          :head {:phrasal true}
          :comp {:phrasal false}}

         ]]
         

       [:p "However, if:"]
       [:ul
        [:li "the noun is singular,"]
        [:li "the noun is of " [:b "neuter"] " gender, and"]
        [:li "the article is indefinite (" [:i "een"] "),"]]
       [:p "then the adjective will " [:b "not"] " have an -e ending, "
        "for example: "]

       [show-examples
        [{:cat :noun
          :agr {:number :sing
                :gender :neuter}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :some}
          :subcat []
          :phrasal true
          :head {:phrasal true}
          :comp {:phrasal false}}]]])}})


