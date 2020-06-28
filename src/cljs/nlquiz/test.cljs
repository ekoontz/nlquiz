(ns nlquiz.test
  (:require
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [nlquiz.dropdown :as dropdown]
   [cljslog.core :as log]
   [reagent.core :as r]))


(defn test-component []
  (let [target-expression-history (r/atom [])
        source-expression-history (r/atom [])
        spec-atom (atom 0)]
    (fn []
      [:div {:style {:float "left"
                     :margin-left "10%"
                     :width "80%"
                     :border "1px dashed green"}}
       (let [target-expression (nl/generate (nth nl/expressions @spec-atom))
             source-expression (do-the-source-expression target-expression)]
         (log/info (str "target: " (-> target-expression nl/morph)))
         (log/info (str "source: " (-> source-expression en/morph)))
         (update-expressions! target-expression-history target-expression)
         (update-expressions! source-expression-history source-expression)
         [:table
          [:tbody
           (doall
            (map (fn [i]
                   (let [target-expression (nth @target-expression-history i)
                         source-expression (nth @source-expression-history i)]
                     [:tr {:key (str "target-" i)}
                      [:td (nl/morph target-expression)]
                      [:td (en/morph source-expression)]]))
                 (range 0 (count @target-expression-history))))]])])))

(defn update-expressions! [expression-history new-expression]
  (swap! expression-history
         (fn [existing-expression-history]
           (if (> (count existing-expression-history) 5)
             (cons new-expression (butlast existing-expression-history))
             (cons new-expression existing-expression-history)))))

(defn do-the-source-expression [target-expression]
  (try
    (-> target-expression
        tr/nl-to-en-spec
        en/generate)
    (catch js/Error e
      (do
        (log/warn (str "failed to generate: " e))
        "??"))))

