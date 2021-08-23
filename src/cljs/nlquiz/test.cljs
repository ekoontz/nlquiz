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

(def tokens-a (r/atom spinner))
(def grammar-a (r/atom spinner))
(def span-pairs-a (r/atom spinner))

(defn decode-parse [response-body]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of serialized lexemes
   (into {}
         (->> (keys response-body)
              (map (fn [k]
                     (log/info (str "K: " (cljs.reader/read-string (clojure.string/join "" (rest (str k))))))
                     [(cljs.reader/read-string (clojure.string/join "" (rest (str k))))
                      (map (fn [serialized-lexeme]
                             (-> serialized-lexeme cljs.reader/read-string deserialize))
                           (get response-body k))])))))

(defn decode-grammar [response-body]
  (map (fn [rule] (-> rule cljs.reader/read-string deserialize))
       response-body))

(defn test []
  (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                              "/parse-start?q=" "de grote pinda")))
            grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))]
        (let [grammar (-> grammar-response :body decode-grammar)
              input-map (-> parse-response :body decode-parse)
              input-length (count (keys input-map))]
          (log/info (str "TEST (get [0 1]: "
                         (-> input-map (get
                                        [0 1]))))
          (let [next-stage (binding [parse/syntax-tree (fn [x] "test:syntax-tree:"
                                                         (or (u/get-in x [:rule])
                                                             (u/get-in x [:canonical])))]
                             (log/info (str "INPUT-LENGTH: " input-length))
                             (log/info (str "GRAMMAR: " grammar))                     
                             (parse/parse-next-stage input-map input-length 2 grammar))]
            (log/info (str "next-stage: " (keys next-stage)))
            (reset! grammar-a grammar)))))
  (fn []
    [:div
     (comment
     [:div.debug
      [:h2 "tokens"]
      (if (map? @tokens-a)
        [:table
         [:thead
          [:tr [:th "span"] [:th "tokens"]]]
         [:tbody
          (->> (keys @tokens-a)
               (map (fn [k]
                      [:tr {:key (str k)}
                       [:td k]
                       [:td (get @tokens-a k)]]))
               doall)]])])

     [:div.debug
      [:h2 "grammar"]
      (if (seq @grammar-a)
        (->> @grammar-a
             (map dag_unify.core/pprint)
             (map (fn [rule]
                    [:div.debug {:key (md5/string->md5-hex (str rule))} (str rule)]))))]
     [:div.debug
      [:h2 "span pairs.."]
      (str @span-pairs-a)]
     ]))
