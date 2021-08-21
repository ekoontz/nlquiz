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
   [reagent.session :as session]
   [md5.core :as md5]
   )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

(def tokens (r/atom spinner))
(def grammar (r/atom spinner))
(def span-pairs (r/atom spinner))

(defn decode-tokens [response-body]
  ;; a map between:
  ;; keys: each key is a span e.g. [0 1]
  ;; vals: each val is a sequence of serialized lexemes
  (into {}
        (->> (keys response-body)
             (map (fn [k]
                    [k (map deserialize (get response-body k))])))))

(defn decode-grammar [response-body]
  (map deserialize response-body))

(defn test []
  (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                              "/parse-start?q=" "de grote pinda")))
            grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))]
        (let [grammar-decoded (-> grammar-response :body decode-grammar)
              tokens-decoded (-> parse-response :body decode-tokens)]
          (reset! grammar grammar-decoded)
          (reset! tokens tokens-decoded)
          (reset! span-pairs (parse/span-pairs (-> tokens-decoded keys count) 2)))))
  (fn []
    [:div

     [:div.debug
      [:h2 "tokens"]
      (if (map? @tokens)
        [:table
         [:thead
          [:tr [:th "span"] [:th "tokens"]]]
         [:tbody
          (->> (keys @tokens)
               (map (fn [k]
                      [:tr {:key (str k)}
                       [:td k]
                       [:td (get @tokens k)]]))
               doall)]])]
     [:div.debug
      [:h2 "grammar"]
      (if (seq @grammar)
                   (->> @grammar
                        (map dag_unify.core/pprint)
                        (map (fn [rule]
                               [:div.debug {:key (md5/string->md5-hex (str rule))} (str rule)]))))]
     [:div.debug
      [:h2 "span pairs"]
      (str @span-pairs)]
     ]))














