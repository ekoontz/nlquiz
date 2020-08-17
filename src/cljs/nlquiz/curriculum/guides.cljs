;; TODO: move this up a level and rename it to curriculum.cljs,
;; and rename the old curriculum.cljs to curriculumfns.cljs or
;; something similar.
(ns nlquiz.curriculum.guides
  (:require
   [nlquiz.curriculum.functions
    :refer [show-alternate-examples
            show-examples]]
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
          :comp {:phrasal false}}]]])

    "number"
    (fn []
      [:div
       [:p "A noun's pluralization depends on its singular form, as shown in "
        "the following examples."]
       [:ul

        [:li "Nouns where the plural is formed by adding " [:i "s"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
             :phrasal true
           :head {:phrasal false
                  :inflection :s}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns where the plural is formed by adding " [:i "en"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :en}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns with a repeated vowel"
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :repeated-vowel}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "n"] ", " [:i "p"] " or " [:i "t"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :repeated-consonant}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "k"] " (sometimes, like in these examples)"
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :repeated-k}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]
        
        [:li "Nouns that end in " [:i "f"] " or " [:i "s"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :f2v}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "heid"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :heid}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "y"] " or a vowel other than " [:i "e"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal true
           :head {:phrasal false
                  :inflection :apostrophe-s}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        ]])

        
       
    "numbers"
    (fn []
      [:div
       [:p "In English, when we want to say a number between 19 and 100, we give the base-10 word: 'twenty', 'seventy', etc, and then the base-1 word: the 'four' in 'twenty four'. But in Dutch, the word order is reversed, so 'twenty four' is " [:i "vier en twintig"] "."]
       [show-examples
        [{:example "de vier kliene vogels"
          :cat :noun
          :mod nil
          :sem {:quant :the
                :mod {:first {:number? true}
                      :rest {:first {:number? false}
                             :rest []}}}
          :phrasal true
          :training-wheels {:head {:comp {:phrasal false}
                                   :head {:comp {:phrasal false}
                                          :head {:phrasal false}}}
                            :comp {:cat :det}}}
         
         {:major-tags ["nouns"]
          :minor-tags ["numbers"]
          :example "de vier en twintig kleine vogels"
          :cat :noun
          :subcat []
          :sem {:quant :the
                :ref {:number :plur}
                :mod {:first {:number? true}
                      :rest {:first {:number? false
                                     :rest []}}}}
          :phrasal true
          :training-wheels {:comp {:cat :det}
                            :head {:comp {:comp {:phrasal false}
                                          :head {:head {:phrasal false
                                                        :sem {:number? true}}
                                                 :comp {:phrasal false}}}
                                   :head {:head {:phrasal false}
                                          :comp {:phrasal false}}}}}]]])}})
  



