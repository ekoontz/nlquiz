(ns nlquiz.parse.functions
  (:require
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]   
   [cemerick.url :as url]
   [cljs-http.client :as http]
   [nlquiz.log :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.menard :refer [dag-to-string decode-analyze decode-grammar
                          decode-parse decode-rules
                          nl-parses-to-en-specs
                          parses]]
   [nlquiz.parse.draw-tree :refer [draw-node-html draw-tree]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

(def server-side-parsing? true)

(defn display-linguistics-content [{do-each-fn :do-each-fn
                                    if-none-message :if-none-message
                                    input-value :input-value
                                    language-flag :language-flag
                                    of :of
                                    plural :plural
                                    singular :singular
                                    where :where
                                    which-is :which-is}]
  (if (not (empty? which-is))
    (reset! where
            (vec
             (cons
              :div.section
              (mapv (fn [elem]
                      [:div.parse-cell
                       [:div.number [:span (str (u/get-in elem [::i])
                                                " " of " "
                                                (count which-is) " " language-flag " "
                                                (if (not (= 1 (count which-is))) plural singular) " for input: '")] [:i input-value] [:span "'."]]
                       (if do-each-fn (do-each-fn elem))
                       (draw-node-html
                        (-> elem
                            (dissoc :1)
                            (dissoc :2)
                            (dissoc :head)
                            (dissoc :comp)))])
                    (map merge
                         (sort (fn [a b]
                                 (compare (str a) (str b)))
                               which-is)
                         (->> (range 1 (+ 1 (count which-is)))
                              (map (fn [i] {::i i}))))))))
  (reset! where [:div.section [:b if-none-message]])))


(defn on-change [{input :input
                  link-atom :link-atom
                  {nl-trees-atom :trees
                   nl-lexemes-atom :lexemes
                   nl-rules-atom :rules
                   nl-grammar :grammar
                   nl-morphology :morphology} :nl
                  {en-trees-atom :trees
                   en-lexemes-atom :lexemes
                   en-rules-atom :rules
                   en-grammar :grammar
                   en-morphology :morphology} :en}]
  (fn [input-element]
    (let [input-value (-> input-element .-target .-value string/trim)
          fresh? (fn [] (= @input input-value))]
      (reset! link-atom (str "?q=" (url/url-encode input-value)))
      (when (not (fresh?))
        ;; Only start the (go) if there is a difference between the input we are given (input-value)
        ;; and the last input that was processed (@input).

        (do-analysis input-value
                     {:input input
                      :nl {:trees nl-trees-atom
                           :lexemes nl-lexemes-atom
                           :rules nl-rules-atom
                           :grammar nl-grammar
                           :morphology nl-morphology}
                      :en {:trees en-trees-atom
                           :lexemes en-lexemes-atom
                           :rules en-rules-atom
                           :grammar en-grammar
                           :morphology en-morphology}})))))

(defn do-analysis [input-value
                   {{nl-trees-atom :trees
                     nl-lexemes-atom :lexemes
                     nl-rules-atom :rules
                     nl-grammar :grammar
                     nl-morphology :morphology} :nl
                    {en-trees-atom :trees
                     en-lexemes-atom :lexemes
                     en-rules-atom :rules
                     en-grammar :grammar
                     en-morphology :morphology} :en}]
  (go
          
    ;; 1. Get the information necessary from the server about the NL expression to start parsing on the client side:
    (let [nl-parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                   "/parse-start/nl?q=" input-value (when server-side-parsing? "&all"))))
                                :body decode-parse)
          nl-lexemes (-> (<! (http/get (str (language-server-endpoint-url)
                                            "/analyze/nl?q=" input-value)))
                         :body decode-analyze)
          nl-rules (-> (<! (http/get (str (language-server-endpoint-url)
                                          "/rule/nl?q=" input-value)))
                       :body decode-rules)
          nl-flag "🇳🇱"
          en-flag "🇬🇧"
          en-parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                   "/parse-start/en?q=" input-value (when server-side-parsing? "&all"))))
                                :body decode-parse)
          en-lexemes (-> (<! (http/get (str (language-server-endpoint-url)
                                            "/analyze/en?q=" input-value)))
                         :body decode-analyze)
          en-rules (-> (<! (http/get (str (language-server-endpoint-url)
                                          "/rule/en?q=" input-value)))
                       :body decode-rules)]
      ;; 2. With this information ready,
      (let [;; 2 do the NL and EN parsing. (we specified "&all" above in the query so actually these (parses) calls doesn't do anything much):
            nl-parses (->> (parses nl-parse-response @nl-grammar @nl-morphology
                                   input-value))
            en-parses (->> (parses en-parse-response @en-grammar @en-morphology
                                   input-value))]
        (display-linguistics-content
         {:do-each-fn draw-tree
          :if-none-message (str "geen " nl-flag " boom")
          :input-value input-value
          :language-flag nl-flag
          :of "van"
          :plural "bomen"
          :singular "boom"
          :which-is nl-parses
          :where nl-trees-atom})
        
        (display-linguistics-content
         {:if-none-message (str "geen " nl-flag " woord")
          :input-value input-value
          :language-flag nl-flag
          :of "van"
          :plural "woorden"
          :singular "woord"
          :which-is nl-lexemes
          :where nl-lexemes-atom})
        
        (display-linguistics-content
         {:if-none-message (str "geen " nl-flag " regel")
          :input-value input-value
          :language-flag nl-flag
          :of "van"
          :plural "regels"
          :singular "regel"
          :which-is nl-rules
          :where nl-rules-atom})
        
        (display-linguistics-content
         {:do-each-fn draw-tree
          :if-none-message (str "no " en-flag " tree")
          :input-value input-value
          :language-flag en-flag
          :of "of"
          :plural "trees"
          :singular "tree"
          :which-is en-parses
          :where en-trees-atom})
        
        (display-linguistics-content
         {:if-none-message (str "no " en-flag " word")
          :input-value input-value
          :language-flag en-flag
          :of "of"
          :plural "words"
          :singular "word"
          :which-is en-lexemes
          :where en-lexemes-atom})
        
        (display-linguistics-content
         {:if-none-message (str "no " en-flag " rule")
          :input-value input-value
          :language-flag en-flag
          :of "of"
          :plural "rules"
          :singular "rule"
          :which-is en-rules
          :where en-rules-atom})))))

(defn new-question [en-question-atom]
  (let [spec {:phrasal true
              :sem {:pred :dog}
              :cat :noun
              :subcat []}]
    (go
      (let [generation-response
            (<! (http/get
                 (str (language-server-endpoint-url)
                      "/generate/en?spec=" (-> spec
                                               dag-to-string))))]
        (reset! en-question-atom (-> generation-response
                                     :body :surface))))))
