(ns nlquiz.core
  (:require
   [accountant.core :as accountant]
   [cljs.core.async :refer [<!]]
   [cljslog.core :as log]
   [cljs-http.client :as http]
   [nlquiz.about :as about]   
   [nlquiz.quiz :as quiz]
   [nlquiz.parse :as parse]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit])

  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -------------------------
;; Page mounting component

(defn prefix?
  "return true iff a is a prefix of b"
  [a b]
  (= (subs b 0 (count a)) a))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/nlquiz"                          :index]
    ["/nlquiz/about"                    :about]
    ["/nlquiz/parse"                    :parse]
    ["/nlquiz/curriculum"
     ["" {:name :curriculum}]
     ["/:major" {:name :curriculum-major}]]
    ["/nlquiz/curriculum/:major/:minor" :curriculum-minor]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))
          path (session/get :path)]
      (log/debug (str "current path: " path))
      [:div#page
       [:header
        [:a {:class (if (or (= path "/")
                            (= path "/nlquiz")
                            (= path "/nlquiz/")
                            (prefix? (path-for :curriculum) path)) "selected" "")
             :href (path-for :curriculum)} "Curriculum"] " "

        [:a {:class (if (prefix? (path-for :parse) path) "selected" "")
          :href (path-for :parse)} "Parse"]
        
        [:a {:class (if (prefix? (path-for :about) path) "selected" "")
             :href (path-for :about)} "About"] " "

        ]
       [page]
       [:footer
        [:p
         [:a {:href "https://github.com/ekoontz/nlquiz"}
          "nlquiz"] " | "
         [:a {:href "https://github.com/ekoontz/menard"}
          "menard"] " | "
         [:a {:href "https://github.com/ekoontz/dag-unify"}
          "dag-unify"] " | "
         [:a {:href "https://github.com/reagent-project"}
          "reagent"] " | "
         [:a {:href "https://clojure.org"}
          "clojure"] "/" [:a {:href "https://clojurescript.org"}
          "script"]]]])))

;; -------------------------
;; Translate routes -> page components
(defn page-for [route]
  (case route
    nil #'quiz/quiz
    :about #'about/component
    :index #'quiz/quiz
    :curriculum #'quiz/quiz
    :curriculum-major #'quiz/quiz-component
    :curriculum-minor #'quiz/quiz-component
    :parse  #'parse/component))

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data match))
            route-params (:path-params match)]
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (session/put! :path path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

(set! (.-onload js/window)
      (fn []))
