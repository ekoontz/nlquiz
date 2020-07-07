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
(def n (atom 0))
(def topic-name (r/atom ""))

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

(defonce curriculum-minimized-width "3%")
(defonce curriculum-minimized-height "10em")
(def curriculum-width (r/atom "auto"))
(def curriculum-height (r/atom "auto"))

(defn toggle-width []
  (if (= @curriculum-width "auto")
    (reset! curriculum-width curriculum-minimized-width)
    (reset! curriculum-width "auto"))
  (if (= @curriculum-height "auto")
    (reset! curriculum-height curriculum-minimized-height)
    (reset! curriculum-heigth "auto")))

(defn tree [selected-path & [class]]
  (let [show-carats (nil? class)
        class (or class "curriculum")]
    (log/debug (str "tree: selected-path: " selected-path))
    (fn []
      [:div {:on-click (fn [input-element] (toggle-width))
             :class class :style {:width @curriculum-width :height @curriculum-height}}
       [:ul
        (doall (map (fn [node]
                      (tree-node node selected-path))
                    curriculum))]])))

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      (log/info (str "curriculum quiz with path:" path))
      (reset! curriculum-width "auto")
      (reset! curriculum-height "auto")
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
      (log/info (str "generating with spec: " spec))
      (http/get (str root-path "generate") {:query-params {"q" serialized-spec}}))))

(defn quiz-component []
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (reset! curriculum-width curriculum-minimized-width)
    (reset! curriculum-height curriculum-minimized-height)
    (quiz/new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       [tree path]
       [:h4 (str major (if minor (str " : " minor)))]
       (quiz/quiz-layout (get-expression major minor))])))



