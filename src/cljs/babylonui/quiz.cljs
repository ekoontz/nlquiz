(ns babylonui.quiz
  (:require
   [accountant.core :as accountant]
   [babylonui.generate :as generate]
   [babylonui.handlers :as handlers]
   [clerk.core :as clerk]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn get-a-question [spec-index question-html]
  (go (let [response (<! (http/get (str "http://localhost:3449/generate/" spec-index)))]
        (log/info (str "one correct answer to this question is: '"
                       (-> response :body :target) "'"))
        (reset! question-html (-> response :body :source)))))

(defn submit-guess [the-atom the-input-element parse-html sem-html]
  (reset! the-atom (-> the-input-element .-target .-value))
  (let [guess-string @the-atom]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get "http://localhost:3449/parse"
                                     {:query-params {"q" guess-string}}))
              trees (-> response :body :trees)
              trees (->> (range 0 (count trees))
                         (map (fn [index]
                                {:tree (nth trees index)
                                 :index index})))
              sems (-> response :body :sem)
              sems (->> (range 0 (count sems))
                        (map (fn [index]
                               {:sem (nth sems index)
                                :index index})))]
          (log/debug (str "trees with indices: " trees))
          (log/debug (str "sems: " sems))

          (reset! sem-html
                  [:ul
                   (->>
                    sems
                    (map (fn [sem]
                           [:li {:key (str "sem-" (:index sem))}
                            (:sem sem)

                            ])))])

          (reset! parse-html
                  [:ul
                   (->>
                    trees
                    (map (fn [parse]
                           [:li
                            {:key (str "tree-" (:index parse))}
                            (:tree parse)])))])))))

(defonce guess-html (r/atom ""))

(defn quiz-component [question-html parse-html sem-html]
  (fn []
    [:div {:style {:margin-top "1em"
                   :float "left" :width "100%"}}

     [:div {:style {:float "left" :width "100%"}}
      @question-html]

     [:div {:style {:float "right" :width "100%"}}
      [:div
       [:input {:type "text"
                :size 50
                :value @guess-html
                :on-change #(submit-guess guess-html % parse-html sem-html)}]]]

     [:div {:style {:float "left" :width "100%"}}
      @parse-html]

     [:div {:style {:float "left" :width "100%"}}
      @sem-html]]))

(defn quiz-page []
  (let [parse-html (r/atom "")
        sem-html (r/atom "")
        spec-atom (atom 0)
        question-html (r/atom "")]
    (get-a-question @spec-atom question-html)
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}

        [:h3 "Quiz"]

        [handlers/show-expressions-dropdown spec-atom]
        [quiz-component question-html parse-html sem-html]]])))

(defn about-page []
(fn [] [:span.main
        [:h1 "About babylon UI"]]))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p
         [:a {:href (path-for :index)} "Generate"] " | "
         [:a {:href (path-for :quiz)} "Quiz"] " | "
         [:a {:href (path-for :about)} "About"]]]
       [page]
       [:footer
        [:p "Powered by:"
         [:a {:href "https://github.com/reagent-project/reagent-template"}
          "Reagent Template"] " | "
         [:a {:href "https://github.com/ekoontz/menard"}
          "Menard"] ""]]])))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/quiz" :quiz]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Translate routes -> page components
(defn page-for [route]
  (case route
    :index #'generate/generate-page
    :quiz #'quiz-page
    :about #'about-page))

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
            current-page (:name (:data  match))
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
