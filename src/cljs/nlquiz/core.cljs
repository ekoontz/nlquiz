(ns nlquiz.core
  (:require
   [accountant.core :as accountant]
   [nlquiz.generate :as generate]
   [nlquiz.quiz :as quiz]
   [clerk.core :as clerk]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn about-component []
  (fn []
    [:div {:style {:float "left" :margin "0.5em"}}
     [:h3 "About nlquiz"]
     [:p "This is a way to drill some short phrases in Dutch. Choose a phrase from the dropdown menu, and you'll get similar phrases to what you've chosen.
By default, phrases like the first are shown: 'ongewoon slim' which means 'unusually smart'."]
     [:p "Problems or questions? Please create an issue on " [:a {:href "https://github.com/ekoontz/nlquiz/issues"} "github"]
      " or " [:a {:href "mailto:ekoontz@hiro-tan.org"} "email me."]]]))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:a {:href (path-for :index)} "Quiz"] " | "
        [:a {:href (path-for :about)} "About"]]
       [page]
       [:footer
        [:p [:a {:href "https://github.com/ekoontz/nlquiz"} "nlquiz"] " is powered by "
         [:a {:href "https://github.com/ekoontz/menard"}
          "menard"] " | "
         [:a {:href "https://github.com/ekoontz/dag-unify"}
          "dag-unify"] " | "
         [:a {:href "https://github.com/reagent-project/reagent-template"}
          "Reagent Template"] " | "
         [:a {:href "https://clojure.org"}
          "Clojure"][:a {:href "https://clojurescript.org"}
          "/script"]]]])))


;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/nlquiz" :index]
    ["/nlquiz/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Translate routes -> page components
(defn page-for [route]
  (case route
    nil #'quiz/quiz-component
    :index #'quiz/quiz-component
    :about #'about-component))

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
        (r/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

(set! (.-onload js/window)
      (fn []))


