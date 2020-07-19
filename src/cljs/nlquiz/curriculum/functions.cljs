(ns nlquiz.curriculum.functions
  (:require [cljs-http.client :as http]
            [cljslog.core :as log]
            [cljs.core.async :refer [<!]]
            [nlquiz.constants :refer [root-path]]
            [nlquiz.quiz :refer [speak-dutch]]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn new-pair [spec]
  (let [input (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        get-pair-fn (fn [] (http/get (str root-path "generate")
                                     {:query-params {"q" serialized-spec
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
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
    (doall (take 3 (repeatedly #(add-one expressions (first (shuffle specs))))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i)}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak-dutch (:target expression))} "🔊"]]
                    [:td.target (:target expression)]
                    [:td.source (:source expression)]]))
               (range 0 (count @expressions))))]]])))
