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
(def curriculum
  [{:name "Adjectives"
    :href "/nlquiz/curriculum/adjectives"}
   {:name "Nouns"
    :child [{:name "Definite and indefinite articles"
             :href "/nlquiz/curriculum/nouns/articles"}
            {:name "Possessive articles"
             :href "/nlquiz/curriculum/nouns/poss"}]}
   {:name "Verbs"
    :child [{:name "Present Tense"} ;; also intransitive
            {:name "Transitive Verbs"}
            {:name "Reflexive Verbs"}]}])

(defn tree [selected-path]
  (log/info (str "tree: selected-path: " selected-path))
  [:div.curriculum
   (doall (map (fn [node]
                 [:h1 [:a {:href (:href node)}
                       (:name node)]])
               curriculum))])

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      (log/info (str "curriculum quiz with path:" path))
      [:div.curr-major
       (tree path)
       [:h4
        "Choose a topic to study."]])))

(defn get-expression [major & [minor]]
  (log/info (str "get-expression: major: " major))
  (log/info (str "get-expression: minor: " minor))
  (fn []
    (let [specs (find-matching-specs major minor)
          spec (-> specs shuffle first)
          serialized-spec (-> spec dag_unify.serialization/serialize str)]
      (log/info (str "generating with spec: " spec))
      (http/get (str root-path "generate") {:query-params {"q" serialized-spec}}))))

(defn quiz-component []
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (quiz/new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       (tree path)
       [:h4 (str major (if minor (str " : " minor)))]
       (quiz/quiz-layout (get-expression major minor))])))



