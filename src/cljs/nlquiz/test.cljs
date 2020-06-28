(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [dag_unify.diagnostics :refer [fail-path]]
   [dag_unify.serialization :refer [deserialize]]
   [dag_unify.core :as u]
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [nlquiz.dropdown :as dropdown]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce root-path "/nlquiz/")

(defn test-component []
  (let [expression-index (r/atom 0)
        generation-tuple (r/atom nil)
        source (r/atom nil)
        source-semantics (r/atom nil)
        target-semantics (r/atom nil)
        possible-answer (r/atom nil)
        possible-answer-parses (r/atom nil)]
    (get-generation-tuple expression-index generation-tuple
                          source source-semantics
                          possible-answer
                          (fn [] (parse-possible-answer
                                  @possible-answer possible-answer-parses target-semantics)))
    (fn []
      [:div#test
       [:button
        {:on-click (fn [input-element]
                     (get-generation-tuple expression-index generation-tuple
                                           source source-semantics
                                           possible-answer
                                           (fn [] (parse-possible-answer
                                                   @possible-answer
                                                   possible-answer-parses
                                                   target-semantics
                                                   evaluations))))}
        "Regenerate"]
       [:div [:h4 "source"] @source]
       [:div [:h4 "source semantics"]
        [:ul.code
         (doall
          (->> (range 0 (count @source-semantics))
               (map (fn [i]
                      [:li {:key i}
                       (-> (nth @source-semantics i)
                           dag_unify.core/pprint
                           str)]))))]]
       [:div [:h4 "possible answer"] @possible-answer]
       [:div [:h4 "parses of possible-answer"]
        [:ul.code
         (doall
          (->> (range 0 (count @possible-answer-parses))
               (map (fn [i]
                      [:li {:key i}
                       (nth @possible-answer-parses i)]))))]]
       [:div [:h4 "semantics of possible-answer parses"]
        [:ul.code
         (doall
          (->> (range 0 (count @target-semantics))
               (map (fn [i]
                      [:li {:key i}
                       (-> (nth @target-semantics i)
                           dag_unify.core/pprint
                           str
                           )]))))]]
       [:div [:h4 "evaluations"]
        [:table.eval
         [:tbody
          [:tr
           [:th]
           (doall
            (->> (range 0 (count @source-semantics))
                 (map (fn [i]
                         [:th {:key (str "thc" i)}
                          (str "s" i)]))))]
          (doall
           (->> (range 0 (count @target-semantics))
                (map (fn [i]
                       [:tr {:key (str "tr" i)}
                        [:th {:key (str "thr" i)}
                         (str "t" i)]
                        (doall
                         (->> (range 0 (count @source-semantics))
                              (map (fn [j]
                                     (let [u? (u/unify (nth @target-semantics i)
                                                       (nth @source-semantics j))
                                           the-fp (if (= u? :fail)
                                                (fail-path
                                                 (nth @target-semantics i)
                                                 (nth @source-semantics j)))]
                                       (if the-fp (log/info (str "FP: " the-fp)))
                                       (if the-fp (log/info (str "path(fp): " (-> the-fp :path str))))
                                       [:td {:key (str "td" i "-" j)}
                                        [:table.eval
                                         [:tbody
                                          [:tr [:th "u?"]
                                           (if (= u? :fail)
                                             [:th "fp"])
                                           (if (= u? :fail)
                                             [:th "v(t)"])
                                           (if (= u? :fail)
                                             [:th "v(s)"])]

                                          [:tr
                                           (if (= u? :fail)
                                             [:td.code
                                              (-> u? u/pprint str)]
                                             [:td {:class "code ok"}
                                              (-> u? u/pprint str)])
                                           (if (= u? :fail)
                                             [:td.code
                                              (-> the-fp :path str)])
                                           (if the-fp
                                             [:td.code
                                              (-> the-fp :arg1 deserialize u/pprint str)])
                                           (if the-fp
                                             [:td.code
                                              (-> the-fp :arg2 deserialize u/pprint str)])]]]])))))]))))]]]])))

(defn get-generation-tuple [expression-index generation-tuple
                            source source-semantics possible-answer next-step-fn]
  (go (let [response (<! (http/get (str root-path "generate/"
                                        @expression-index)))]
        (reset! generation-tuple (-> response :body))
        (reset! source (-> response :body :source))
        (reset! source-semantics
                (->> (-> response :body :source-sem)
                     (map cljs.reader/read-string)
                     (map deserialize)))
        (reset! possible-answer (-> response :body :target))
        (next-step-fn))))

(defn parse-possible-answer [possible-answer put-parses-here put-semantics-here put-evaluation-here]
  (go (let [response (<! (http/get (str root-path "parse/nl")
                                   {:query-params {"q" possible-answer}}))]
        (reset! put-parses-here (-> response :body :trees))
        (reset! put-semantics-here
                (->> (-> response :body :sem)
                     (map cljs.reader/read-string)
                     (map deserialize))))))


        


