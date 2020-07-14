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
   [nlquiz.curriculum.specs :refer [curriculum specs]]
   [nlquiz.quiz :as quiz]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; TODO: move root-path to core:
(defonce root-path "/nlquiz/")
(def topic-name (r/atom ""))

;; this atom is used to add :key values to list items and table rows:
(def i (atom 0))

(defn find-matching-specs [major & [minor]]
  (->> specs
       (filter (fn [spec]
                 (not (empty? (filter #(= % major)
                                      (get spec :major-tags))))))
       (filter (fn [spec]
                 (or (nil? minor)
                     (not (empty? (filter #(= % minor)
                                          (get spec :minor-tags)))))))))

(defn tree-node [node selected-path]
  (log/debug (str "selected-path: " selected-path))
  [:li {:key (str "node-" (swap! i inc))}
   [:h1
    (if (:href node)
      (let [url (str "/nlquiz/curriculum/" (:href node))]
        (if (and (not (empty? selected-path))
                 (= url selected-path))
         (reset! topic-name (:name node)))
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

(defn tree [selected-path]
  (log/debug (str "tree: selected-path: " selected-path))
  (fn []
    [:div.curriculum
     [:ul
      (doall (map (fn [node]
                    (tree-node node selected-path))
                  curriculum))]]))
(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      (log/info (str "curriculum quiz with path:" path))
      [:div.curr-major
       [:h4.normal
        "Choose a topic to study."]
       [tree path "curriculum full"]])))

(defn get-expression [major & [minor]]
  (log/debug (str "get-expression: major: " major))
  (log/debug (str "get-expression: minor: " minor))
  (fn []
    (let [specs (find-matching-specs major minor)
          spec (-> specs shuffle first)
          serialized-spec (-> spec dag_unify.serialization/serialize str)]
      (log/debug (str "generating with spec: " spec))
      (http/get (str root-path "generate") {:query-params {"q" serialized-spec}}))))

(defn quiz-component []
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (quiz/new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       [tree path]
       [:h4 @topic-name]
       (quiz/quiz-layout (get-expression major minor))])))

