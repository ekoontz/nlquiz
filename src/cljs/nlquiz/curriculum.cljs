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
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; TODO: move root-path to core:
(defonce root-path "/nlquiz/")

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
    :minor-tags ["articles"]
    :example "de kat"
    :cat :noun
    :subcat []
    :phrasal true
    :head {:phrasal false
           :subcat {:1 {:cat :det}}}
    :comp {:phrasal false}}])

(defn find-matching-specs [major & [minor]]
  (->> specs
       (filter (fn [spec]
                 (not (empty? (filter #(= % major)
                                      (get spec :major-tags))))))
       (filter (fn [spec]
                 (or (nil? minor)
                     (not (empty? (filter #(= % minor)
                                          (get spec :minor-tags)))))))))
  
(defn tree []
  [:div.curriculum
   [:h1 [:a {:href "/nlquiz/curriculum/adjectives"} "Adjectives"]]
   [:h1 [:a {:href "/nlquiz/curriculum/nouns"} "Nouns"]]
   [:ul
    [:li [:a {:href "/nlquiz/curriculum/nouns/articles"}
          "Definite and indefinite articles"]]
    [:li [:a {:href "/nlquiz/curriculum/nouns/poss"}
          "Possessive articles"]]]
   [:h1 [:a {:href "/nlquiz/curriculum/verbs"}
         "Verbs"]]
   [:ul
    [:li "Present Tense"]
    [:li "Transitive"]
    [:li "Reflexive"]]])

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)]
      [:div.curr-major
       (tree)
       [:h4
        "Choose a topic to study."]])))

(defn get-expression [major & [minor]]
  (log/info (str "creating a function for getting an expression.."))
  (log/info (str "MAJOR: " major))
  (log/info (str "MINOR: " minor))
  (fn []
    (let [specs (find-matching-specs major minor)
          spec (-> specs shuffle first)
          serialized-spec (-> spec dag_unify.serialization/serialize str)]
      (log/info (str "generating with spec: " spec))
      (http/get (str root-path "generate") {:query-params {"q" serialized-spec}}))))

(defn quiz-component []
  (let [routing-data (session/get :route)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (quiz/new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       (tree)
       [:h4 (str major (if minor (str " : " minor)))]
       (quiz/quiz-layout (get-expression major minor))])))



