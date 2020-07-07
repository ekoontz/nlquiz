(ns nlquiz.core
  (:require
   [accountant.core :as accountant]
   [clerk.core :as clerk]
   [cljs.core.async :refer [<!]]
   [cljslog.core :as log]
   [cljs-http.client :as http]
   [nlquiz.curriculum :as curriculum]
   [nlquiz.generate :as generate]
   [nlquiz.quiz :as quiz]
   [nlquiz.test :as test]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn about-component []
  (fn []
    [:div {:style {:float "left" :margin "0.5em"}}
     [:h3 "About nlquiz"]
     [:p "This is a way to drill some short phrases in Dutch. Choose a 
topic in the curriculum to drill on that type of phrase."]
     [:p "Problems or questions? Please create an issue on " [:a {:href "https://github.com/ekoontz/nlquiz/issues"} "github"]
      " or " [:a {:href "mailto:ekoontz@hiro-tan.org"} "email me."]]]))

;; -------------------------
;; Page mounting component

(defn prefix?
  "return true iff a is a prefix of b"
  [a b]
  (= (subs b 0 (count a)) a))

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))
          path (session/get :path)]
      [:div
       [:header
        [:a {:class (if (or (= path "/")
                            (prefix? (path-for :curriculum) path)) "selected" "")
             :href (path-for :curriculum)} "Curriculum"] " "
        [:a {:class (if (prefix? (path-for :about) path) "selected" "")
             :href (path-for :about)} "About"] " "
        [:a.debug
         {:class (if (prefix? (path-for :test) path) "selected" "")
          :href (path-for :test)} "Debug"]]
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
;; Routes

(def router
  (reitit/router
   [["/nlquiz"                          :index]
    ["/nlquiz/test"                     :test]
    ["/nlquiz/about"                    :about]
    ["/nlquiz/curriculum"               
     ["" {:name :curriculum}]
     ["/:major" {:name :curriculum-major}]]
    ["/nlquiz/curriculum/:major/:minor" :curriculum-minor]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Translate routes -> page components
(defn page-for [route]
  (case route
    nil #'curriculum/quiz
    :index #'curriculum/quiz
    :about #'about-component
    :test #'test/test-component
    :curriculum #'curriculum/quiz
    :curriculum-major #'curriculum/quiz-component
    :curriculum-minor #'curriculum/quiz-component))

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data match))
            route-params (:path-params match)]
        (log/info (str "PATH: " path))
        (r/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (session/put! :path path)
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

(set! (.-onload js/window)
      (fn []))


