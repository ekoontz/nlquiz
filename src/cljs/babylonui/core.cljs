(ns babylonui.core
  (:require
   [accountant.core :as accountant]
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.handlers :as handlers]
   [clerk.core :as clerk]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [clojure.string :as string]
   [dag_unify.core :as u]
   [dommy.core :as dommy]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(path-for :about)

(defn update-target-expressions! [target-expressions expression-node]
  (swap! target-expressions
         (fn [existing-expressions]
           (log/debug (str "length of existing expressions: "
                           (count existing-expressions)))
           (if (> (count existing-expressions) 5)
             (cons expression-node (butlast existing-expressions))
             (cons expression-node existing-expressions)))))

(defn do-the-source-expression [target-expression source-expressions]
  (let [source-expression-node {:morph
                                (try
                                  (-> target-expression
                                      tr/nl-to-en-spec
                                      en/generate
                                      en/morph)
                                  (catch js/Error e
                                    (do
                                      (log/warn (str "failed to generate: " e))
                                      "??")))}]
    (log/debug (str "source-expression: " (:morph source-expression-node)))
    (swap! source-expressions
           (fn [existing-expressions]

             (log/debug (str "length of existing expressions: " (count existing-expressions)))
             (if (> (count existing-expressions) 5)
               (cons source-expression-node (butlast existing-expressions))
               (cons source-expression-node existing-expressions))))))

(defn generate [target-expressions source-expressions expression-index]
  (log/info (str "doing generate with specification: " (nth nl/expressions expression-index)))
  (let [target-expression
        (nl/generate (nth nl/expressions expression-index))]
    (update-target-expressions! target-expressions {:expression target-expression})
    (do-the-source-expression target-expression source-expressions)))

(defn timer-component [target-expressions source-expressions spec-atom]
  (let [generated (r/atom 0)
        generate? (r/atom true)]
    (fn []
      (when @generate?
        (generate target-expressions source-expressions @spec-atom)
        (js/setTimeout #(swap! generated inc) 50))
      [:div {:style {:float "left" :width "100%" :padding "0.25em"}}

       [:div {:style {:float "left"}}
        (str "Generated: " (if @generate?
                             (inc @generated)
                             @generated)
             " pairs")]

       [:div {:style {:margin-left "1em"
                      :float :right
                      :text-align :right
                      :white-space "nowrap"}}
        "Generate:"
        [:input {:type "radio" :value "Generate"
                 :name "generate-switch"
                 :checked @generate?
                 :on-click #(reset! generate? true)
                 :on-change #(reset! generate? true)
                 :id "switch-on"}]
        [:label {:for "switch-on"} "On"]
        [:input {:type "radio" :value "Generate"
                 :name "generate-switch"
                 :checked (not @generate?)
                 :on-click #(reset! generate? false)
                 :on-change #(reset! generate? false)
                 :id "switch-off"}]
        [:label {:for "switch-off"} "Off"]]])))

(defn generate-page []
  (let [target-expressions (atom [])
        source-expressions (r/atom [])
        spec-atom (atom 0)]
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}

        [:h1 "Expression generator"]

        [handlers/show-expressions-dropdown spec-atom]
        [timer-component target-expressions source-expressions spec-atom]]
       
       [:div {:class ["expressions" "target"]}
        (doall
         (map (fn [i]
                (let [expression-node (nth @target-expressions i)
                      target-spec (:spec expression-node)
                      target-expression (:expression expression-node)]
                  (log/debug (str "target expression: " (nl/morph target-expression)))
                  [:div.expression {:key (str "target-" i)}
                   [:span (nl/morph target-expression)]]))
              (range 0 (count @target-expressions))))]
       
       [:div {:class ["expressions" "source"]}
        (doall
         (map (fn [i]
                (let [expression-node (nth @source-expressions i)]
                  [:div.expression {:key (str "source-" i)}
                   [:span (:morph expression-node)]]))
              (range 0 (count @source-expressions))))]])))

(def source-node (r/atom []))
(def target-node (r/atom []))
(defn generate-from-server []
  (go (let [response (<! (http/get (str "http://localhost:3449/generate/" 0)))]
        (reset! source-node (-> response :source))
        (reset! target-node (-> response :target)))))

(set! (.-onload js/window)
      (fn []))


(defonce guess-html (r/atom ""))
(defonce question-html (r/atom ""))
(defonce parse-html (r/atom ""))
(defonce sem-html (r/atom ""))

(defn get-a-question [spec-index]
  (go (let [response (<! (http/get (str "http://localhost:3449/generate/" spec-index)))]
        (log/info (str "one correct answer to this question is: '"
                       (-> response :body :target) "'"))
        (reset! question-html (-> response :body :source)))))

(defn submit-guess [the-atom the-input-element]
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

(defn atom-input [value]
  [:div
   [:input {:type "text"
            :size 50
            :value @value
            :on-change #(submit-guess value %)}]])
  
(defn quiz-component []
  (fn []
    [:div {:style {:margin-top "1em"
                   :float "left" :width "100%"}}

     [:div {:style {:float "left" :width "100%"}}
      @question-html]

     [:div {:style {:float "right" :width "100%"}}
      (atom-input guess-html)]

     [:div {:style {:float "left" :width "100%"}}
      @parse-html]

     [:div {:style {:float "left" :width "100%"}}
      @sem-html]]))

(defn quiz-page []
  (let [spec-atom (atom 0)]
    (get-a-question @spec-atom)
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}

        [:h3 "Quiz"]

        [handlers/show-expressions-dropdown spec-atom]
        [quiz-component]]])))

(defn about-page []
(fn [] [:span.main
        [:h1 "About babylon UI"]]))

;; -------------------------
;; Translate routes -> page components
(defn page-for [route]
  (case route
    :index #'generate-page
    :quiz #'quiz-page
    :about #'about-page))

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
