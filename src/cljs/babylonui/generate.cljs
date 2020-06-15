(ns babylonui.generate
  (:require
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.dropdown :as dropdown]
   [cljslog.core :as log]
   [reagent.core :as r]))

(declare do-the-source-expressions)
(declare update-target-expressions)
(declare controls)

(defn generate-component []
  (let [target-expressions (r/atom [])
        source-expressions (r/atom [])
        spec-atom (atom 0)]
    (fn []
      [:div.main
       [:div
        {:style {:float "left" :margin-left "10%"
                 :width "80%" :border "0px dashed green"}}

        [:h1 "Expression generator"]

        [dropdown/expressions spec-atom]
        [controls target-expressions source-expressions spec-atom]]
       
       [:div {:class ["expressions" "target"]}
        (doall
         (map (fn [i]
                (let [target-expression (:expression (nth @target-expressions i))]
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

(defn controls [target-expressions source-expressions spec-atom]
  (let [generated (r/atom 0)
        generate? (r/atom true)]
    (fn []
      (when @generate?
        (let [expression-index @spec-atom
              target-expression (nl/generate (nth nl/expressions expression-index))]
          (update-target-expressions! target-expressions {:expression target-expression})
          (do-the-source-expression target-expression source-expressions))
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

