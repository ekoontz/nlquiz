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
    [show-examples (second content) (if (= 3 (count content))
                                      (nth content 2)
                                      ;; default to showing 5 examples:
                                      5)]

    (and (vector? content)
         (= (first content) :show-alternate-examples))
    [show-alternate-examples (nth content 1) (nth content 2)]
    
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
                                  :head {:phrasal? false}}}}]]])}})



