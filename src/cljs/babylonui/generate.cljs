(ns babylonui.generate
  (:require
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.dropdown :as dropdown]
   [cljslog.core :as log]
   [reagent.core :as r]))

(defn generate-component []
  (let [target-expressions (r/atom [])
        source-expressions (r/atom [])
        spec-atom (atom 0)]
    (fn []
      [:div.main
       [:div {:style {:float "left" :margin-left "10%" :width "80%" :border "0px dashed green"}}
        [:h1 "Expression generator"]
        [dropdown/expressions spec-atom]
        [controls

         ;; function that will be called by (controls) to generate the pair
         ;; of expressions and insert them into the table of existing expressions:
         (fn [] 
           (let [target-expression (nl/generate (nth nl/expressions @spec-atom))
                 source-expression (do-the-source-expression target-expression)]
             (update-expressions! target-expressions target-expression)
             (update-expressions! source-expressions source-expression)))]]

       [:div {:class ["expressions" "target"]}
        (doall
         (map (fn [i]
                (let [target-expression (nth @target-expressions i)]
                  [:div.expression {:key (str "target-" i)}
                   [:span (nl/morph target-expression)]]))
              (range 0 (count @target-expressions))))]
       [:div {:class ["expressions" "source"]}
        (doall
         (map (fn [i]
                (let [expression-node (nth @source-expressions i)]
                  [:div.expression {:key (str "source-" i)}
                   [:span (en/morph expression-node)]]))
              (range 0 (count @source-expressions))))]])))

(defn controls [generate-pair-fn]
  (let [count-generated (r/atom 0)
        generate? (r/atom true)]
    (fn []
      (when @generate?
        (generate-pair-fn)
        (js/setTimeout #(swap! count-generated inc) 50))
      [:div {:style {:float "left" :width "100%" :padding "0.25em"}}
       [:div {:style {:float "left"}}
        (str "Generated: " (if @generate?
                             (inc @count-generated)
                             @count-generated) " pair" (if (not (= @count-generated 0)) "s") ".")]
       [:div {:style {:margin-left "1em"
                      :float :right
                      :text-align :right
                      :white-space "nowrap"}} "Generate:"
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

(defn update-expressions! [expressions new-expression]
  (swap! expressions
         (fn [existing-expressions]
           (if (> (count existing-expressions) 5)
             (cons new-expression (butlast existing-expressions))
             (cons new-expression existing-expressions)))))

(defn do-the-source-expression [target-expression]
  (try
    (-> target-expression
        tr/nl-to-en-spec
        en/generate)
    (catch js/Error e
      (do
        (log/warn (str "failed to generate: " e))
        "??"))))



