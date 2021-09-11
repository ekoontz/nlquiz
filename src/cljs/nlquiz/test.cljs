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
(def en-surface-atom (r/atom ".."))
(defn en-surface []
  @en-surface-atom)

(defn nl-sem [input-map]
  (let [nl-parses (nl-parses input-map)]
    (->> nl-parses
         (map #(u/get-in % [:sem]))
         (map #(-> % dag_unify.serialization/serialize str)))))

(def nl-sem-atom (r/atom (str "..")))
(def nl-surface-atom (r/atom (str "..")))
(def nl-tokens-atom (r/atom (str "..")))
(def nl-trees-atom (r/atom (str "..")))
(def en-specs-atom (r/atom (str "..")))

;; [:a :b :c :d] -> "{:0 :a, :1 :b, :2 :c, :3 :d}"
(defn array2map [input]
  (str (zipmap (->> (range 0 (count input))
                    (map (fn [x] (-> x str keyword))))
               input)))

(defn test []
  (go 
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))
      ))
  (fn []
    [:div ;; top
     [:div.debug
      [:input {:type "text"
               :on-change (fn [input-element]
                            (reset! guess-text (-> input-element .-target .-value))
                            (go (let [parse-response (<! (http/get (str (language-server-endpoint-url)
                                                                        "/parse-start?q=" @guess-text)))
                                      input-map (-> parse-response :body decode-parse)
                                      nl-parses (nl-parses input-map)
                                      en-specs (->> nl-parses
                                                    (map tr/nl-to-en-spec))]
                                  ;; nl
                                  (reset! nl-surface-atom (nl-surface input-map))
                                  (reset! nl-tokens-atom (str (nl-tokens input-map)))
                                  (reset! nl-sem-atom (array2map (nl-sem input-map)))
                                  (reset! nl-trees-atom (array2map (nl-trees input-map)))
                                  ;; en
                                  (reset! en-specs-atom (array2map
                                                         (->> en-specs
                                                              (map dag-to-string))))
                                  (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                                        "/generate/en?spec=" (-> en-specs
                                                                                                 first
                                                                                                 dag-to-string))))]
                                    (log/info (str "gen-response::: " (-> gen-response :body :surface)))
                                    (reset! en-surface-atom (array2map [(-> gen-response :body :surface)]))

                                    gen-response)
                                  
                                ) ;; (let 

                                ) ;; (go
                            ) ;; :on-change (fn 
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
       [:h2 "sem"]
       [:div.monospace
        @nl-sem-atom]]
      [:div.debug
       [:h2 "trees"]
       [:div.monospace
        @nl-trees-atom]]]
     [:div.debug
      [:h1 "en"]
      [:div.debug
       [:h2 "specs"]
       [:div.monospace
        @en-specs-atom]]
      [:div.debug
       [:h2 "surface"]
       [:div.monospace
        @en-surface-atom]]]]
    )
  )
