(ns nlquiz.curriculum.functions
  (:require [cljs-http.client :as http]
            [cljslog.core :as log]
            [cljs.core.async :refer [<!]]
            [nlquiz.constants :refer [root-path spinner]]
            [nlquiz.speak :as speak]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; functions used by curriculum.cljs

(def this-many-examples 5)

(def generate-http-lambda "https://lambda.hiro-tan.org/generate")
(def generate-http-local "/generate")
(def generate-http generate-http-lambda)

(def generate-with-alts-http-lambda "https://lambda.hiro-tan.org/generate-with-alts")
(def generate-with-alts-http-local "https://lambda.hiro-tan.org/generate-with-alts")
(def generate-with-alts-http generate-with-alts-http-lambda)

(defn new-pair [spec]
  (let [input (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        get-pair-fn (fn [] (http/get generate-http
                                     {:query-params {"q" serialized-spec
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input
                  {:source (-> response :body :source)
                   :target (-> response :body :target)})))
    input))

(defn new-pair-alternate-set [spec alternates]
  (let [input-alternate-a (r/atom nil)
        input-alternate-b (r/atom nil)
        serialized-spec (-> spec dag_unify.serialization/serialize str)
        serialized-alternates (->> alternates
                                   (map dag_unify.serialization/serialize)
                                   str)
        get-pair-fn (fn [] (http/get generate-with-alts-http
                                     {:query-params {"spec" serialized-spec
                                                     "alts" serialized-alternates
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input-alternate-a
                  {:source (-> response :body (nth 0) :source)
                   :target (-> response :body (nth 0) :target)})
          (reset! input-alternate-b
                  {:source (-> response :body (nth 1) :source)
                   :target (-> response :body (nth 1) :target)})))
    [input-alternate-a input-alternate-b]))

(defn add-one [expressions spec]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   [(new-pair spec)]))))

(defn add-one-alternates [expressions spec alternates]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   (new-pair-alternate-set spec alternates)))))

(defn show-examples [specs]
  (let [expressions (r/atom [])]
    (doall (take this-many-examples
                 (repeatedly #(add-one expressions (first (shuffle specs))))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or (:target expression) spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

(defn show-alternate-examples [spec alternates]
  (let [expressions (r/atom [])
        specs [spec]]
    (log/info (str "show-alternate-examples: spec: " spec))
    (doall (take this-many-examples
                 (repeatedly #(add-one-alternates
                               expressions (first (shuffle specs)) alternates))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or (:target expression) spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

