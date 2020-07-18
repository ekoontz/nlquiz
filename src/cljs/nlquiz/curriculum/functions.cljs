(ns nlquiz.curriculum.functions
  (:require [cljs-http.client :as http]
            [cljslog.core :as log]
            [cljs.core.async :refer [<!]]
            [nlquiz.constants :refer [root-path]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(defn show-examples [specs]
  (let [expressions (r/atom [])]
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
               (range 0 (count @expressions))))]]])))
