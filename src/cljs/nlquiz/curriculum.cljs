(ns nlquiz.curriculum
  (:require
   [cljslog.core :as log]
   [cljs-http.client :as http]
   [dag_unify.core :as u]
   [nlquiz.constants :refer [root-path]]
   [nlquiz.curriculum.guides :refer [guides]]
   [nlquiz.quiz :as quiz]
   [reagent.core :as r]
   [reagent.session :as session])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [inline-resource]]))

(def topic-name (r/atom ""))
(def curriculum-atom (r/atom nil))
(def specs-atom (r/atom
                 (-> "public/edn/specs.edn"
                     inline-resource 
                     cljs.reader/read-string)))

(defn get-curriculum []
  (go (let [response (<! (http/get "/edn/curriculum.edn"))]
        (reset! curriculum-atom (-> response :body)))))

(defn get-specs []
  (go (let [response (<! (http/get "/edn/specs.edn"))]
        (reset! specs-atom (-> response :body)))))

(defn find-matching-specs [major & [minor]]
  (get-specs)
  (->> @specs-atom
       (filter (fn [spec]
                 (not (empty? (filter #(= % major)
                                      (get spec :major-tags))))))
       (filter (fn [spec]
                 (or (nil? minor)
                     (not (empty? (filter #(= % minor)
                                          (get spec :minor-tags)))))))))

(defn tree-node [i node selected-path]
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
            (tree-node i child selected-path))
          (:child node)))]])

(defn tree [selected-path]
  (get-curriculum)
  (let [i (atom 0)]
    (fn []
      [:div.curriculum
       [:ul
        (doall (map (fn [node]
                      (tree-node i node selected-path))
                    @curriculum-atom))]])))

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
       (quiz/quiz-layout (get-expression major minor))
       (cond (and major minor guides (get guides major)
                  (-> guides (get major) (get minor)))
             [:div.guide [(-> guides (get major) (get minor))]]
             true "")])))




