(ns nlquiz.curriculum.guides
  (:require
   [nlquiz.curriculum.functions :refer [show-examples]]
   [reagent.core :as r]))

(def guides
  {"nouns"
   {"definite-articles"
    (fn []
      [:div
       [:p "There are two definite articles in Dutch: " [:i "de"]
        " and " [:i "het"] ". A noun will always use " [:i "de"] " when it's plural:"]
       [show-examples
        [{:cat :noun
          :phrasal true
          :subcat []
          :sem {:quant :the}
          :agr {:number :plur}}]]
       [:p " but when singular, the article used depends on the noun's gender:"]
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


