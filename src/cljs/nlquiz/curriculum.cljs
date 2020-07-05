(ns nlquiz.curriculum
  (:require
   [cljs-http.client :as http]
   [reagent.session :as session]
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [cljslog.core :as log]
   [dag_unify.core :as u]
   [dommy.core :as dommy]
   [nlquiz.quiz :as quiz]
   [reagent.core :as r]))

;; TODO: move to core.
(defonce root-path "/nlquiz/")

(def curriculum
  [{:adjectives
    [{:note "intensifier adjective"
      :example "ongewoon slim"
      :cat :adjective
      :mod nil
      :subcat []
      :phrasal true
      :head {:phrasal false}
      :comp {:phrasal false}}]}])

(defn tree []
  [:div.curriculum
   [:h1 [:a {:href "/nlquiz/curriculum/adjectives"} "Adjectives"]]
   [:h1 [:a {:href "/nlquiz/curriculum/nouns"} "Nouns"]]
   [:ul
    [:li [:a {:href "/nlquiz/curriculum/nouns/art"} "Definite and indefinite articles"]]
    [:li [:a {:href "/nlquiz/curriculum/nouns/poss"} "Possessive articles"]]]
   [:h1 [:a {:href "/nlquiz/curriculum/verbs"} "Verbs"]]
   [:ul
    [:li "Present Tense"]
    [:li "Transitive"]
    [:li "Reflexive"]]])

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)]
      [:div.curr-major
       (tree)
       [:h2
        "Choose a topic to study."]])))

(defn quiz-major []
  (fn []
    (let [routing-data (session/get :route)
          major (get-in routing-data [:route-params :major])]
      [:div.curr-major
       (tree)       
       [:h2
        "Let's study " major "!"]
       (quiz-component [{:cat :noun}])])))

(defn quiz-minor []
  (fn []
    (let [routing-data (session/get :route)
          major (get-in routing-data [:route-params :major])
          minor (get-in routing-data [:route-params :minor])]
      (log/info (str "NEW QUESTION-FN: " quiz/expression-based-get))
      (log/info (str "QUESTION-HTML: " quiz/question-html))
      (log/info (str "PCS: " quiz/possible-correct-semantics))
;;      (quiz/new-question quiz/expression-based-get quiz/question-html quiz/possible-correct-semantics)
      [:div.curr-major
       (tree)       
       [:h2
        "Let's study " major " and, in particular, " minor "!"]
       (log/info (str "ok..."))])))

(defn get-expression [expression-index]
  (log/info (str "creating a function from: " @quiz/expression-index))
  (fn []
    (log/info (str "returning a function from the expression index: " @quiz/expression-index))
    (http/get (str root-path "generate/" @quiz/expression-index))))

(defn quiz-component [get-question-fn]
  (quiz/new-question get-question-fn)
  #(quiz/quiz-layout get-question-fn))

(defn choose-question-from-dropdown [get-question-fn]
  (if (nil? @expression-index)
    (reset! expression-index 0))
  [:div {:style {:float "right"}}
   [dropdown/expressions expression-index
    
    ;; what to call if dropdown's choice is changed (generate a new question):
    (fn [] (new-question get-question-fn))]])

(defn quiz-component-old [spec-set]
  (log/info (str "got here..." spec-set))
  (log/info (str "THE FOO IS: " foo))
  
  (let [parse-html (r/atom "")
        semantics-of-guess (r/atom [])]
    [:div
     [:div (str "HELLO: " spec-set)]
     [:h4 (str "some stuff.."
               (-> curriculum (nth 0) :adjectives))]]))

(defn curriculum-based-get [curriculum-key]
  (log/info (str "returning a function from the curriculum-key: " curriculum-key))
  (if false
    (let [spec {:cat :noun}]
      (http/get (str root-path "generate") {:query-params {"q" spec}}))
    quiz/quiz-choose-question-from-dropdown))

      



