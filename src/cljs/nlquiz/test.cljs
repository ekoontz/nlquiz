(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.serialization :as s]
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

(def stage-0-atom (r/atom spinner))
(def stage-1-atom (r/atom spinner))

(defn decode-parse [response-body]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of serialized lexemes
   (into {}
         (->> (keys response-body)
              (map (fn [k]
                     [(cljs.reader/read-string (clojure.string/join "" (rest (str k))))
                      (map (fn [serialized-lexeme]
                             (-> serialized-lexeme cljs.reader/read-string deserialize))
                           (get response-body k))])))))

(defn decode-grammar [response-body]
  (map (fn [rule] (-> rule cljs.reader/read-string deserialize))
       response-body))

(defn print-stage [stage-map]
  [:table
   [:thead
    [:tr
     [:th [:h2 "span"]]
     [:th [:h2 "expressions"]]]]
   [:tbody
    (->> (sort (keys stage-map))
         (map (fn [k]
                (let [v (get stage-map k)]
                  [:tr {:key (md5/string->md5-hex (str k))}
                   [:td (str k)]
                   [:td (->> v
                             (map (fn [each-expression]
                                    [:div.debug {:key (md5/string->md5-hex (str each-expression))}
                                     (str each-expression)
                                     ])))]]))))]])
   
(defn test []
  (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                              "/parse-start?q=" "de grote pinda")))
            grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))]
        (let [grammar (-> grammar-response :body decode-grammar)
              input-map (-> parse-response :body decode-parse)
              input-length (count (keys input-map))]
          (reset! stage-0-atom (print-stage input-map))
          (let [next-stage (binding [parse/syntax-tree (fn [x] "??")]
                             (parse/parse-next-stage input-map input-length 2 grammar))]
            (reset! stage-1-atom (print-stage next-stage))))))
  (fn []
    [:div
     [:div.debug
      [:h2 "stage-1"]
      @stage-1-atom]

     [:div.debug
      [:h2 "stage-0"]
      @stage-0-atom]

     ]))


     




     

     
