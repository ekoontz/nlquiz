(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [dag_unify.serialization :refer [deserialize]]
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
        source (r/atom "")
        source-semantics (r/atom [])
        target-semantics (r/atom [])
        possible-answer (r/atom "")
        possible-answer-parses (r/atom [])]
    (get-generation-tuple expression-index generation-tuple
                          source source-semantics
                          possible-answer
                          (fn [] (parse-possible-answer
                                  @possible-answer possible-answer-parses target-semantics)))
    (fn []
      [:div#test
       [:button
        {:on-click (fn [input-element]
                     (get-generation-tuple
                      expression-index
                      generation-tuple source possible-answer
                      (fn [] (parse-possible-answer
                              @possible-answer
                              possible-answer-parses))))}
        "Regenerate"]
       [:div [:h4 "source"] @source]
       [:div [:h4 "source semantics"]
        [:p.code (clojure.string/join "," @source-semantics)]]
       [:div [:h4 "possible answer"] @possible-answer]
       [:div [:h4 "parses of possible-answer"]
        [:p.code (clojure.string/join "," @possible-answer-parses)]]
       [:div [:h4 "semantics of possible-answer parses"]
        [:p.code (clojure.string/join "," @target-semantics)]]])))

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

(defn parse-possible-answer [possible-answer put-parses-here put-semantics-here]
  (go (let [response (<! (http/get (str root-path "parse/nl")
                                   {:query-params {"q" possible-answer}}))]
        (reset! put-parses-here (-> response :body :trees))
        (reset! put-semantics-here
                (->> (-> response :body :sem)
                     (map cljs.reader/read-string)
                     (map deserialize)
                     (map dag_unify.core/pprint)
                     (map str))))))


        


