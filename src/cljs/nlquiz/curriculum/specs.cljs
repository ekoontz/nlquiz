(ns nlquiz.curriculum.specs)

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
           " have an -e at the end."]
       [:p "However, if:"]
       [:ul
        [:li "the noun is singular,"]
        [:li "the noun is of " [:b "neuter"] " gender, and"]
        [:li "the article is indefinite (" [:i "een"] ")"]]
       [:p "then the adjective will " [:b "not"] " have an -e ending."]])}})

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

