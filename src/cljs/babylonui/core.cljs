(ns babylonui.core
  (:require
   [accountant.core :as accountant]
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
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
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(path-for :about)

(def expression-specification-atom (atom (nth nl/expressions 0)))
(def semantics-atom (r/atom nil))

(def target-expressions
  (r/atom []))

(def source-expressions
  (r/atom []))

(declare show-expressions-dropdown)

(defn do-the-source-expression [target-expression]
  (log/debug (str "GOT HERE!!! WITH A TARGET EXPRESSION:" target-expression))
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

(defn update-target-expressions! [expression-node]
  (swap! target-expressions
         (fn [existing-expressions]
           (log/debug (str "length of existing expressions: " (count existing-expressions)))
           (if (> (count existing-expressions) 5)
             (cons expression-node (butlast existing-expressions))
             (cons expression-node existing-expressions)))))

(defn generate []
  (log/debug (str "GENERATE!! THE EXPRESSION ATOM IS: " @expression-specification-atom))
  (let [target-expression
        (nl/generate @expression-specification-atom)]
    (update-target-expressions!
     {:expression target-expression})
    (do-the-source-expression target-expression)))

(def source-node (r/atom []))
(def target-node (r/atom []))

(defn generate-from-server []
  (go (let [response (<! (http/get (str "http://localhost:3449/language/" 0)))]
        (reset! source-node (-> response :source))
        (reset! target-node (-> response :target)))))

(set! (.-onload js/window)
      (fn []))

(defn timer-component []
  (let [generated (r/atom 0)
        generate? (r/atom true)]
    (fn []
      (when @generate?
        (generate)
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

(defn home-page []
  (fn []
    [:div.main
     [:div
      {:style {:float "left" :margin-left "10%"
               :width "80%" :border "0px dashed green"}}
      [show-expressions-dropdown]
      [timer-component]]
     
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
             (range 0 (count @source-expressions))))]]))

(defn show-expressions-dropdown []
  (let [show-these-expressions
        (filter #(= true (u/get-in % [:menuable?] true))
                nl/expressions)]
    [:div {:style {:float "left" :border "0px dashed blue"}}
     [:select {:id "expressionchooser"
               :on-change #(reset! expression-specification-atom
                                   (nth show-these-expressions
                                        (js/parseInt
                                         (dommy/value (dommy/sel1 :#expressionchooser)))))}
      (map (fn [item-id]
             (let [expression (nth show-these-expressions item-id)]
               [:option {:name item-id
                         :value item-id
                         :key (str "item-" item-id)}
                (:note expression)]))
           (range 0 (count show-these-expressions)))]]))

(defn about-page []
(fn [] [:span.main
        [:h1 "About babylon UI"]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "About babylon UI"]]]
       [page]
       [:footer
        [:p "Babylon UI was generated by the "
         [:a {:href "https://github.com/reagent-project/reagent-template"}
          "Reagent Template"] "."]]])))

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
