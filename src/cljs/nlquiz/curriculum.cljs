(ns nlquiz.curriculum
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [cljslog.core :as log]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.quiz :refer [get-title-for get-curriculum]]
   [nlquiz.speak :as speak]
   [reagent.session :as session]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url
                                           root-path-from-env]]))

(def curriculum-content-atom (r/atom ""))
(def this-many-examples 5)

(def generate-http (str (language-server-endpoint-url) "/generate"))
(def generate-with-alts-http (str (language-server-endpoint-url) "/generate-with-alts"))

(defn set-content [path]
  (get-curriculum)
  (let [root-path (root-path-from-env)]
    (go
      (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
        (if (= 200 (-> response :status))
          (reset! curriculum-content-atom (rewrite-content (-> response :body)))
          (log/error (str "unexpected response for path:"
                          path "; response was: " 
                          response)))))))

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

(defn major []
  (let [routing-data (session/get :route)
        major (get-in routing-data [:route-params :major])]
    (set-content major)
    (fn []
      [:div.curr-major
       [:div.guide
        [:div.h4 [:h4 (get-title-for major)]]
        [:div.content @curriculum-content-atom]]])))

(defn major-minor []
  (let [routing-data (session/get :route)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (set-content (str major "/" minor))
    (fn []
      [:div.curr-major
       [:div.guide
        [:div.h4 [:h4 (get-title-for major minor)]]
        [:div.content @curriculum-content-atom]]])))

(defn new-pair [spec]
  (let [input (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        get-pair-fn (fn [] (http/get generate-http
                                     {:query-params {"q" serialized-spec
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input
                  {:source (-> response :body :source)
                   :target (-> response :body :target)})))
    input))

(defn new-pair-alternate-set [spec alternates]
  (let [input-alternate-a (r/atom nil)
        input-alternate-b (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        serialized-alternates (->> alternates
                                   (map dag_unify.serialization/serialize)
                                   str)
        get-pair-fn (fn [] (http/get generate-with-alts-http
                                     {:query-params {"spec" serialized-spec
                                                     "alts" serialized-alternates
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input-alternate-a
                  {:source (-> response :body (nth 0) :source)
                   :target (-> response :body (nth 0) :target)})
          (reset! input-alternate-b
                  {:source (-> response :body (nth 1) :source)
                   :target (-> response :body (nth 1) :target)})))
    [input-alternate-a input-alternate-b]))

(defn add-one [expressions spec]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   [(new-pair spec)]))))

(defn add-one-alternates [expressions spec alternates]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   (new-pair-alternate-set spec alternates)))))

(defn show-examples [specs & [supply-this-many-examples]]
  (let [expressions (r/atom [])
        this-many-examples (or supply-this-many-examples this-many-examples)]
    (doall (take this-many-examples
                 (repeatedly #(add-one expressions (first (shuffle specs))))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or [:a {:href (str "http://google.com/search?q=\"" (:target expression) "\"")} (:target expression)] spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

(defn show-alternate-examples [spec alternates]
  (let [expressions (r/atom [])
        specs [spec]]
    (log/info (str "show-alternate-examples: spec: " spec))
    (doall (take this-many-examples
                 (repeatedly #(add-one-alternates
                               expressions (first (shuffle specs)) alternates))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or (:target expression) spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

