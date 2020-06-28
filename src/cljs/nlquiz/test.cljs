(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
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
        possible-answer (r/atom "")
        possible-answer-parses (r/atom [])]
    (get-generation-tuple expression-index generation-tuple source possible-answer
                          (fn [] (parse-possible-answer @possible-answer possible-answer-parses)))
    (fn []
      [:div#test
       [:div [:h4 "source"] @source]
       [:div [:h4 "possible answer"] @possible-answer]
       [:div [:h4 "parses of possible-answer"]
        (clojure.string/join "," @possible-answer-parses)]])))

(defn get-generation-tuple [expression-index generation-tuple source possible-answer next-step-fn]
  (go (let [response (<! (http/get (str root-path "generate/"
                                        @expression-index)))]
        (log/debug (str "one possible correct answer to this question is: '" (-> response :body :target) "'"))
        (reset! generation-tuple (-> response :body))
        (reset! source (-> response :body :source))
        (reset! possible-answer (-> response :body :target))
        (next-step-fn))))

(defn parse-possible-answer [possible-answer put-parses-here]
  (log/info (str "GOT HERE IN PARSING WITH POSSIBLE ANSWER: " possible-answer))
  (go (let [response (<! (http/get (str root-path "parse/nl")
                                   {:query-params {"q" possible-answer}}))]
        (log/info (str "response: " (-> response :body :trees)))
        (reset! put-parses-here (-> response :body :trees))
        (doall
         (->> (-> response :body :trees)
              (map (fn [tree]
                     (log/info (str "tree: " tree)))))))))





        


