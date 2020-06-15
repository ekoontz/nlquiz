(ns babylonui.quiz
  (:require
   [accountant.core :as accountant]
   [babylonui.generate :as generate]
   [babylonui.dropdown :as dropdown]
   [clerk.core :as clerk]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare submit-guess)

(defn quiz-component []
  (let [expression-index (atom 0)
        guess-html (r/atom "")
        parse-html (r/atom "")
        sem-html (r/atom "")
        question-html (r/atom "")
        spec-index @expression-index]
    (go (let [response (<! (http/get (str "http://localhost:3449/generate/"
                                          spec-index)))]
          (log/info (str "one correct answer to this question is: '"
                         (-> response :body :target) "'"))
          (reset! question-html (-> response :body :source))))
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}
        [:h3 "Quiz"]
        [dropdown/expressions expression-index]
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
          @sem-html]]]])))

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

