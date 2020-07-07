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
    :href "adjectives"}
   {:name "Nouns"
    :href "nouns"
    :child [{:name "Definite and indefinite articles"
             :href "nouns/articles"}
            {:name "Possessive articles"
             :href "nouns/poss"}]}
   {:name "Verbs"
    :child [{:name "Present Tense"} ;; also intransitive
            {:name "Transitive Verbs"}
            {:name "Reflexive Verbs"}]}])

(def n (atom 0))

(defn tree-node [node selected-path]
  (log/info (str "selected-path: " selected-path))
  [:li {:key (str "node-" (swap! n inc))}
   [:h1
    (if (:href node)
      (let [url (str "/nlquiz/curriculum/" (:href node))]
        [:a {:class (if (= url selected-path)
                      "selected" "")
             :href (str "/nlquiz/curriculum/" (:href node))}
         (:name node)])
      (:name node))]
   [:ul
    (doall
     (map (fn [child]
            (tree-node child selected-path))
          (:child node)))]])

(def curriculum-width (r/atom "2em"))

(defn toggle-width []
  (if (= @curriculum-width "100%")
    (reset! curriculum-width "2em")
    (reset! curriculum-width "100%")))

(defn tree [selected-path & [class]]
  (let [show-carats (nil? class)
        class (or class "curriculum minimized")]
    (log/info (str "tree: selected-path: " selected-path))
    (fn []
      [:div {:class class :style {:width @curriculum-width}}
       [:ul
        (doall (map (fn [node]
                      (tree-node node selected-path))
                    curriculum))]])))

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      (log/info (str "curriculum quiz with path:" path))
      (reset! curriculum-width "100%")
      [:div.curr-major
       [:h4.normal
        "Choose a topic to study."]
       [tree path "curriculum full"]])))

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
    (reset! curriculum-width "2em")
    (quiz/new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       [:div.button
        [:button {:on-click (fn [input-element] (toggle-width))}
         (if (= @curriculum-width "100%")
           ">>"
           "<<")]]
       [tree path]
       [:h4 (str major (if minor (str " : " minor)))]
       (quiz/quiz-layout (get-expression major minor))])))



