(ns nlquiz.curriculum.fns
  (:require))

(defn new-pair [spec]
  (let [input (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        get-pair-fn (fn [] (http/get (str root-path "generate")
                                     {:query-params {"q" serialized-spec}}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input
                  {:source (-> response :body :source)
                   :target (-> response :body :target)})))
    input))

(defn add-one [expressions spec]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   [(new-pair spec)]))))

(defn show-examples [expressions specs]
  (doall (take 5 (repeatedly #(add-one expressions (first (shuffle specs))))))
  (fn []
    [:div.exampletable
     [:table
      [:tbody
       (doall
        (map (fn [i]
               (let [expression @(nth @expressions i)]
                 [:tr {:key (str "row-" i)}
                  [:th (+ i 1)]
                  [:td.target (:target expression)]
                  [:td.source (:source expression)]]))
             (range 0 (count @expressions))))]]]))

   
  
