(ns nlquiz.curriculum.content
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [nlquiz.curriculum.functions
    :refer [show-alternate-examples
            show-examples]]
   [nlquiz.quiz :refer [get-title-for get-curriculum]]
   [reagent.session :as session]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env]]))

(def the-content (r/atom ""))

(defn set-content [path]
  (get-curriculum)
  (let [root-path (root-path-from-env)]
    (go
      (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
        (if (= 200 (-> response :status))
          (reset! the-content (rewrite-content (-> response :body)))
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
        [:div.content @the-content]]])))

