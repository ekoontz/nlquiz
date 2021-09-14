(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]
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
  (log/info (str "parse-in-stages: " (- i 1) "/" input-length))
  (if (>= input-length i)
    (-> input-map
        (parse/parse-next-stage input-length i grammar)
        (parse-in-stages input-length (+ 1 i) grammar))
    input-map))

(defn submit-guess [guess-text the-input-element]
  (log/info (str "submit-guess: " guess-text)))

(defn dag-to-string [dag]
  (-> dag dag_unify.serialization/serialize str))

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

(defn nl-trees [nl-parses]
  (map syntax-tree nl-parses))

(defn nl-parses [input-map grammar]
  (let [input-length (count (keys input-map))]
    (binding [parse/syntax-tree syntax-tree]
      (->
       (parse-in-stages input-map input-length 2 grammar)
       (get [0 input-length])))))

(defn nl-sem [nl-parses]
  (->> nl-parses
       (map #(u/get-in % [:sem]))
       (map #(-> % dag_unify.serialization/serialize str))))

;; [:a :b :c :d] -> "{:0 :a, :1 :b, :2 :c, :3 :d}"
(defn array2map [input]
  (str (zipmap (->> (range 0 (count input))
                    (map (fn [x] (-> x str keyword))))
               input)))

(def en-surfaces-atom (r/atom))
(def input-map (atom {}))

(defn english-widget []
  [:div.debug {:style {:width "40%"}}
   [:h1 "en"]
   [:div.monospace
     @en-surfaces-atom]])

(defn update-english [nl-parses-atom en-surfaces-atom nl-surface-atom]
  (let [old-nl @nl-surface-atom
        old-en @en-surfaces-atom]
    (go
      (let [specs (->> @nl-parses-atom (map tr/nl-to-en-spec))
            update-to (atom [])]
        (log/info (str "adding this many specs: " (count specs)))
        (doseq [en-spec (->> @nl-parses-atom (map tr/nl-to-en-spec))]
          (if (= old-nl @nl-surface-atom)
            (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                  "/generate/en?spec=" (-> en-spec
                                                                           dag-to-string))))]
              (reset! update-to (cons (-> gen-response :body :surface)
                                      @update-to)))
            (log/info (str "AVOIDING UNNECESSARY GENERATE CALL!"))))
        (let [test @nl-surface-atom]
          (if (= old-nl test)
            (reset! en-surfaces-atom (string/join "," @update-to))
            (do
              (reset! en-surfaces-atom old-en)
              (log/info (str "**** NOT *** updating english (2) since nl: [" old-nl "] != [" test "]")))))))))

(defn test []
  (let [grammar (atom nil)]
  (go 
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))
      ))
  (let [nl-sem-atom (r/atom (str ".."))
        nl-surface-atom (r/atom (str ".."))
        nl-tokens-atom (r/atom (str ".."))
        nl-trees-atom (r/atom (str ".."))
        nl-parses-atom (atom nil)
        guess-text (r/atom "de hond")]
    (fn []
      [:div ;; top
       [:div.debug
        [:input {:type "text"
                 :on-change (fn [input-element]
                              (log/debug (str "input changed."))
                              (reset! guess-text (-> input-element .-target .-value))
                              (reset! en-surfaces-atom "..")
                              (go
                                (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                                            "/parse-start?q=" @guess-text)))
                                                         :body decode-parse)
                                      nl-parses (nl-parses parse-response @grammar)]
                                  (reset! nl-parses-atom nl-parses)
                                  (reset! input-map parse-response)
                                  (reset! nl-surface-atom @guess-text)
                                  (reset! nl-tokens-atom (str (nl-tokens @input-map)))
                                  (reset! nl-sem-atom (array2map (nl-sem nl-parses)))
                                  (reset! nl-trees-atom (array2map (nl-trees nl-parses)))
                                  (update-english nl-parses-atom en-surfaces-atom nl-surface-atom)
                              )))
                 
                 ;; :on-change (fn 
                 :value @guess-text}]]
       [:div.debug {:style {:width "45%"}}
      [:h1 "nl"]
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
       (english-widget)
       ]))))


