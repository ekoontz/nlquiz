(ns nlquiz.curriculum.specs)

(def curriculum
  [{:name "Adjectives"
    :href "adjectives"}
   {:name "Nouns"
    :href "nouns"
    :child [{:name "Definite articles"
             :href "nouns/definite-articles"}
            {:name "Possessive articles"
             :href "nouns/poss"}]}])

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
    "poss"
    (fn []
      [:div
       [:p "Here's some stuff about possessive articles."]])}})

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
           :sem {:pred :she}}}])


