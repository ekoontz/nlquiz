(ns nlquiz.curriculum.content
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [nlquiz.curriculum.functions
    :refer [show-alternate-examples
            show-examples]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env]]))

(def path-to-content (r/atom {}))

(defn set-content [path]
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          ;; TODO: check for server errors
          (if (= 200 (-> response :status))
            (reset! path-to-content
                    (merge {path (-> response :body)}
                           @path-to-content))

            (log/error (str "unexpected response for path:"
                            path "; response was: " 
                            response)))))))

(defn get-content [path]
  (fn [] (rewrite-content (or (get @path-to-content path)
                              (do (set-content path)
                                  (get @path-to-content path))))))

(defn rewrite-content
  "transform all instances of '[:show-examples ...]' with '[show-examples ...]'"
  [content]
  (cond
    (and (vector? content)
         (= (first content) :show-examples))
    [show-examples (second content) 5]

    (vector? content)
    (vec (map (fn [x]
                (rewrite-content x))
              content))
    :else
    content))
  
(def curriculum
  {"verbs"
   {"subject-pronouns-and-present-tense"
    (fn []
      [:div
       [:p "The first verb construction we will look at is a pronoun subject with a
      present tense verb."]
      [show-examples
       [{:head {:curriculum :basic
                :phrasal? false}
         :cat :verb,
         :infl :present
         :phrasal? true
         :subcat []
         :comp {:pronoun? true
                :interrogative? false}}]]])

    "past-simple-regular"
    (fn []
      [:div
       [:p "The regular past simple in Dutch is created by taking the verb stem (the verb minus the " [:i "en"] " suffix), and adding:"]
       [:ul
        [:li "-te or -de for the singular, or"]
        [:li "-ten or -den for the plural."]
        ]
       [show-examples
        [{:major-tags ["verbs"]
          :minor-tags ["past-simple-regular"]
          :phrasal? true
          :subcat []
          :cat :verb
          :infl :past-simple
          :sem {:obj {:top :top}}
          :head {:modal false
                 :head {:phrasal? false
                        :irregular-past-simple? false
                        :subcat {:2 {:cat :noun}}}}}

         {:major-tags ["verbs"]
          :minor-tags ["past-simple-regular"]
          :phrasal? true
          :subcat []
          :cat :verb
          :infl :past-simple
          :sem {:obj :none}
          :head {:modal false
                 :irregular-past-simple? false
                 :phrasal? false}}]]])

    "past-simple-irregular"
    (fn []
      [:div
       [:p "As with English, the simple past has many exceptions, which must be "
        "learned by rote."]
       [show-examples
        [{:major-tags ["verbs"]
          :minor-tags ["past-simple-regular"]
          :phrasal? true
          :subcat []
          :cat :verb
          :infl :past-simple
          :sem {:obj {:top :top}}
          :head {:modal false
                 :head {:phrasal? false
                        :irregular-past-simple? true
                        :subcat {:2 {:cat :noun}}}}}
         {:major-tags ["verbs"]
          :minor-tags ["past-simple-irregular"]
          :phrasal? true
          :subcat []
          :cat :verb
          :infl :past-simple
          :sem {:obj :none}
          :head {:modal false
                 :irregular-past-simple? true
                 :phrasal? false}}]]])

    "nodig"
    (fn []
      [:div 
       [:p "How to say 'I need...'?"]

       [show-examples
        [
         {:phrasal? true
          :head {:rule "adverb-nodig"
                 :comp {:rule "vp"
                        :head {:infl :present :phrasal? false}
                        :comp {:pronoun? true}}}
          :comp {:pronoun? true}
          :subcat []
          :cat :verb
          :infl :present
          :sem {:tense :present
                :aspect :simple
                :pred :need}}
         ]]
       ]
      )

    "reflexive"
    (fn []

      [:div
       [:p "Reflexive verbs"]

       [show-examples
        [{:major-tags ["verbs"]
          :minor-tags ["reflexive" "present"]
          :note "Sentence with reflexive object"
          :example "ik zie me"
          :generic true
          :max-depth 3
          :cat :verb
          :subcat []
          :phrasal? true
          :reflexive? true
          :comp {:pronoun? true
                 :phrasal? false}
          :sem {:tense :present
                :aspect :simple
                :pred :see
                :obj {:top :top}}}
         ]]]
      )
    }

   "pronouns"
   {
   "object-3" ;; object-3
   (fn []
     [:div
      
      [:h3 "Third person singular"]
      
      [show-examples
       [
        {:major-tags ["pronouns"]
         :minor-tags ["object"]
         :example "Ik zie haar niet"
         :max-depth 3
         :reflexive? false
         :cat :verb
         :head {:comp {:pronoun? true
                       :agr {:person :3rd
                             :number :sing}}}
         :subcat []
         :sem {:pred :see
               :obj {:obj :none}}
         :training-wheels {:head {:rule "vp"
                                  :head {:phrasal? false}}}}
        ]]
      
      [:h3 "Third person plural"]
      
      [show-examples
       [
        {:major-tags ["pronouns"]
         :minor-tags ["object"]
         :example "Ik zie haar niet"
         :max-depth 3
         :reflexive? false
         :cat :verb
         :head {:comp {:pronoun? true
                       :agr {:person :3rd
                             :number :plur}}}
         :subcat []
         :sem {:pred :see
               :obj {:obj :none}}
         :training-wheels {:head {:rule "vp"
                                  :head {:phrasal? false}}}}]]])}


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
          :rule "np:2"
          :phrasal? true
          :subcat []
          :sem {:quant :the}
          :agr {:number :plur}}]]
       [:p "But when the noun is singular, the article used depends on the noun's gender."]
       [:ul
        [:li "If the noun is of " [:b "common"] " gender, then the definite article is " [:i "de"] ", for example:"
         [show-examples
          [{:cat :noun
            :rule "np:2"
            :subcat []
            :sem {:quant :the}
            :agr {:gender :common
                  :number :sing}
            :phrasal? true
            :head {:phrasal? false}}]]]
        [:li "If the noun is of " [:b "neuter"] " gender, then the definite article is " [:i "het"] ", for example:"
         [show-examples
          [{:cat :noun
            :rule "np:2"
            :subcat []
            :sem {:quant :the}
            :agr {:gender :neuter
                  :number :sing}
            :phrasal? true
            :head {:phrasal? false}}]]]]])

    "demonstratives"
    (fn []
      [:div
       [:p "There are four demonstrative articles:"]
       [:ul
        [:li [:i "deze"] " - used for 'this' with " [:b "common"] " nouns, and for 'these' with all nouns"]
        [:li [:i "dit"] " - used for 'this' with " [:b "neuter"] " nouns"]
        [:li [:i "die"] " - used for 'that' with " [:b "common"] " nouns, and for 'those' with all nouns"]
        [:li [:i "dat"] " - used for 'that' with " [:b "neuter"] " nouns"]]

       [show-examples
        [{:cat :noun
          :subcat []
          :phrasal? true
          :comp {:phrasal? false
                 :sem {:pred :this}}}
         {:cat :noun
          :subcat []
          :phrasal? true
          :comp {:phrasal? false
                 :sem {:pred :that}}}]
        10]])
    
    "poss" 
    (fn []
      [:div
       [:p "A noun's article can be a " [:b "possessive article"] ", for example:"]
       [show-examples
        [{:cat :noun
          :subcat []
          :phrasal? true
          :head {:phrasal? false
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal? false
                 :possessive? true}}]]
       [:p "The possessive has two forms for 1st person plural: it can either " [:i "ons"] ", if the noun is both singular and of neuter gender (a 'het' word):"]
       [show-examples
        [{:cat :noun
          :subcat []
          :phrasal? true
          :head {:phrasal? false
                 :agr {:gender :neuter
                       :number :sing}
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal? false
                 :sem {:pred :we}
                 :possessive? true}}]]

       [:p "Or otherwise " [:i "onze"] ":"]
       [show-examples
        [{:cat :noun
          :subcat []
          :phrasal? true
          :head {:phrasal? false
                 :agr {:gender :common
                       :number :sing}
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal? false
                 :sem {:pred :we}                 
                 :possessive? true}}
         {:cat :noun
          :subcat []
          :phrasal? true
          :head {:phrasal? false
                 :agr {:number :plur}
                 :subcat {:1 {:cat :det}}}
          :comp {:phrasal? false
                 :sem {:pred :we}                 
                 :possessive? true}}]]])
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
          :phrasal? true
          :head {:phrasal? true}
          :comp {:phrasal? false}}

         {:cat :noun
          :agr {:number :sing
                :gender :common}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :some}
          :subcat []
          :phrasal? true
          :head {:phrasal? true}
          :comp {:phrasal? false}}

         {:cat :noun
          :agr {:number :sing
                :gender :common}
          :sem {:mod {:first {:number? false}
                      :rest []}
                :quant :the}
          :subcat []
          :phrasal? true
          :head {:phrasal? true}
          :comp {:phrasal? false}}

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
          :phrasal? true
          :head {:phrasal? true}
          :comp {:phrasal? false}}]]])

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
             :phrasal? true
           :head {:phrasal? false
                  :inflection :s}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns where the plural is formed by adding " [:i "en"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :en}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns with a repeated vowel"
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :repeated-vowel}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "n"] ", " [:i "p"] " or " [:i "t"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :repeated-consonant}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "k"] " (sometimes, like in these examples)"
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :repeated-k}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]
        
        [:li "Nouns that end in " [:i "f"] " or " [:i "s"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :f2v}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "heid"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :heid}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        [:li "Nouns that end in " [:i "y"] " or a vowel other than " [:i "e"]
         [show-alternate-examples
          {:cat :noun
           :mod nil
           :sem {:quant :the}
           :phrasal? true
           :head {:phrasal? false
                  :inflection :apostrophe-s}}
          [{:sem {:ref {:number :sing}}}
           {:sem {:ref {:number :plur}}}]]]

        ]])
       
    "numbers"
    (fn []
      [:div
       [:p "In English, a number like 'twenty four' is expressed first with the base-10 word: 'twenty', and " [:i "then"] " the base-1 word: 'four'. But in Dutch, the word order is reversed, so 'twenty four' is " [:i "vier en twintig"] "."]
       [show-examples
        [{:example "de vier kliene vogels"
          :cat :noun
          :mod nil
          :sem {:quant :the
                :mod {:first {:number? true}
                      :rest {:first {:number? false}
                             :rest []}}}
          :phrasal? true
          :training-wheels {:head {:comp {:phrasal? false}
                                   :head {:comp {:phrasal? false}
                                          :head {:phrasal? false}}}
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
          :phrasal? true
          :training-wheels {:comp {:cat :det}
                            :head {:comp {:comp {:phrasal? false}
                                          :head {:head {:phrasal? false
                                                        :sem {:number? true}}
                                                 :comp {:phrasal? false}}}
                                   :head {:head {:phrasal? false}
                                          :comp {:phrasal? false}}}}}]]])}})
