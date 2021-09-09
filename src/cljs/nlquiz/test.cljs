(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.parse :as parse]
   [menard.serialization :as s]
   [menard.translate.spec :as tr]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session]
   [md5.core :as md5]
   )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

(defn syntax-tree [tree]
  (s/syntax-tree tree morphology))

(defn decode-parse [response-body]
   ;; a map between:
   ;; keys: each key is a span e.g. [0 1]
   ;; vals: each val is a sequence of serialized lexemes
   (into {}
         (->> (keys response-body)
              (map (fn [k]
                     [(cljs.reader/read-string (clojure.string/join (rest (str k))))
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

(defn parse-in-stages [input-map input-length i grammar]
  (if (>= input-length i)
    (-> input-map
        (parse/parse-next-stage input-length i grammar)
        (parse-in-stages input-length (+ 1 i) grammar))
    input-map))

(defn submit-guess [guess-text the-input-element]
  (log/info (str "submit-guess: " guess-text)))

(defn dag-to-string [dag]
  (-> dag dag_unify.serialization/serialize str))

(def grammar (atom nil))

(defn nl-surface []
  @guess-text)

(defn nl-tokens [input-map]
  (into
   {}
   (->>
    (-> input-map keys)
    (map (fn [k]
           [(-> k first str keyword)
            (-> input-map
                (get k)
                first
                ((fn [x]
                   (or (u/get-in x [:surface])
                       (u/get-in x [:canonical])))))])))))

(defn nl-trees [input-map]
  (map syntax-tree (nl-parses input-map)))

(defn nl-parses [input-map]
  (let [input-length (count (keys input-map))]
    (binding [parse/syntax-tree syntax-tree]
      (->
       (parse-in-stages input-map input-length 2 @grammar)
       (get [0 input-length])))))

(def guess-text (r/atom "de hond"))

(defn assemble-nl-data [nl-parses input-map]
  (let [nl-sems (->> nl-parses
                     (map #(u/get-in % [:sem]))
                     (map #(-> % dag_unify.serialization/serialize str)))
        nl-tokens (into
                   {}
                   (->>
                    (-> input-map keys)
                    (map (fn [k]
                           [(-> k first str keyword)
                            (-> input-map
                                (get k)
                                first
                                ((fn [x]
                                   (or (u/get-in x [:surface])
                                       (u/get-in x [:canonical])))))]))))]
    (-> (map syntax-tree nl-parses)
        ((fn [x] {:sem nl-sems
                  :tokens nl-tokens
                  :surface @guess-text
                  :trees (vec x)})))))

(def nl-surface-atom (r/atom (str "..")))
(def nl-tokens-atom (r/atom (str "..")))
(def nl-trees-atom (r/atom (str "..")))

(def parse-nl-atom (r/atom (str {})))

(defn test []
  (go 
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))
      ))
  (fn []
    [:div
     [:div.debug
      [:input {:type "text"
               :on-change (fn [input-element]
                            (reset! guess-text (-> input-element .-target .-value))
                            (reset! parse-nl-atom spinner)
                            (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                                                        "/parse-start?q=" @guess-text)))
                                      input-map (-> parse-response :body decode-parse)
                                      nl-parses (nl-parses input-map)
                                      nl (assemble-nl-data nl-parses input-map)
                                      en-specs (map (fn [nl-parse]
                                                      (tr/nl-to-en-spec nl-parse))
                                                    nl-parses)]
                                  (log/info (str "nl: " (str nl)))
                                  (log/info (str "en specs: " (map dag-to-string en-specs)))
                                  (if false
                                    ;; doesn't work yet (hence use of 'false' above):
                                    (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                                          "/generate/en?q=" (str (map dag-to-string en-specs)))))]
                                      (log/info (str "gen-response: " gen-response))))
                                  (reset! nl-surface-atom (nl-surface input-map))
                                  (reset! nl-tokens-atom (str (nl-tokens input-map)))
                                  (reset! nl-trees-atom (str (nl-trees input-map)))
                                  (reset! parse-nl-atom (-> {:nl nl
                                                             :en {:specs (map dag-to-string en-specs)}
                                                             :sem (-> nl :sem dag-to-string)}
                                                            str)))))
               :value @guess-text}]]
     [:div.debug
      [:h1 "nl"]
      [:div.debug
       [:h2 "surface"]
       [:div.monospace
        @nl-surface-atom]]
      [:div.debug
       [:h2 "tokens"]
       [:div.monospace
        @nl-tokens-atom]]
      [:div.debug
       [:h2 "trees"]
       [:div.monospace
        @nl-trees-atom]]
      [:div.debug
       [:h2 "parse"]
       [:div.monospace
        @parse-nl-atom]]]]))




