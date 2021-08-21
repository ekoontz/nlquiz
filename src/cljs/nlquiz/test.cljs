(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.parse :as parse]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

(def tokens (r/atom spinner))
(def grammar (r/atom spinner))
(def span-pairs (r/atom spinner))

(defn test []
  (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                              "/parse-start?q=" "de grote pinda")))
            grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))
            ]
        (log/info (str "one dag: " (-> parse-response :body vals first first deserialize)))
        (log/info (str "first grammar rule: " (-> grammar-response :body first deserialize)))
        (reset! tokens (-> parse-response :body vals first first deserialize))
        (reset! grammar (-> grammar-response :body first deserialize))
        (log/info (str "COUNT: " (-> parse-response :body keys count)))
        (reset! span-pairs (parse/span-pairs (-> parse-response :body keys count) 2))
        ))
  (fn []
    [:div [:h2 "test.."]
     [:div.debug (if (map? @tokens) (str @tokens) @tokens)]
     [:div.debug (if (map? @grammar) (-> @grammar dag_unify.core/pprint str) @grammar)]
     [:div.debug (str @span-pairs)]
     ]))













