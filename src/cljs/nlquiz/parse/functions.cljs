(ns nlquiz.parse.functions
  (:require
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]   
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.menard :refer [dag-to-string decode-analyze decode-grammar
                          decode-parse decode-rules
                          nl-parses nl-parses-to-en-specs]]
   [nlquiz.parse.draw-tree :refer [draw-node-html draw-tree]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

(def server-side-parsing? true)

(defn helemaal-niks [& atoms]
  (log/info (str "doing helemaal.."))
  (doall (map (fn [atom]
                (reset! atom [:b "helemaal niks"]))
              atoms)))

(defn on-change [{input :input
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
      (when (not (fresh?))
        ;; Only start the (go) if there is a difference between the input we are given (input-value)
        ;; and the last input that was processed (@input).

        (go
          (reset! input input-value)

          ;; 1. Get the information necessary from the server about the NL expression to start parsing on the client side:
          (let [nl-parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                      "/parse-start/nl?q=" input-value (when server-side-parsing? "&all"))))
                                   :body decode-parse)
                nl-lexemes (-> (<! (http/get (str (language-server-endpoint-url)
                                               "/analyze/nl?q=" input-value)))
                            :body decode-analyze)
                nl-rules (-> (<! (http/get (str (language-server-endpoint-url)
                                             "/rule/nl?q=" input-value)))
                          :body decode-rules)]
            (when (fresh?)
              ;; 2. With this information ready,
              (let [;; 2.a. do the NL parsing:
                    nl-parses (->> (nl-parses nl-parse-response @nl-grammar @nl-morphology
                                              input-value))
                    ;; 2.b. do the EN parsing:
                    ]
                (cond (and nl-parses (seq nl-parses))
                      (reset! nl-trees-atom
                              (vec
                               (cons
                                :div.section
                                (cons (when (= (count nl-parses) 0)
                                        [:h4
                                         (str "no parses.")])
                                      (mapv (fn [parse]
                                              [:div.parse-cell
                                               [:div.number (str (u/get-in parse [::i]) " of " (count nl-parses) " 🇳🇱 parse"
                                                                 (when (not (= 1 (count nl-parses))) "s") "")]
                                               (draw-tree parse)
                                               (draw-node-html
                                                (-> parse
                                                    (dissoc :1)
                                                    (dissoc :2)
                                                    (dissoc :head)
                                                    (dissoc :comp)))])
                                            (map merge
                                                 (sort (fn [a b]
                                                         (compare (str a) (str b)))
                                                       nl-parses)
                                                 (->> (range 1 (+ 1 (count nl-parses)))
                                                      (map (fn [i] {::i i})))))))))
                      
                      (seq nl-lexemes)
                      (reset! nl-lexemes-atom
                              (vec
                               (cons
                                :div.section
                                (cons (when (= (count nl-lexemes) 0)
                                        [:h4 (str "no lexemes.")])
                                      (mapv (fn [lexeme]
                                              [:div.lexeme
                                               [:div.number (str (u/get-in lexeme [::i]) " of " (count nl-lexemes) "  🇳🇱 lexeme"
                                                                 (when (not (= 1 (count nl-lexemes))) "s") "")]
                                               (draw-node-html lexeme)])

                                            (map merge
                                                 (sort (fn [a b]
                                                         (compare (str a) (str b)))
                                                       nl-lexemes)
                                                 (->> (range 1 (+ 1 (count nl-lexemes)))
                                                      (map (fn [i] {::i i})))))))))

                      (seq nl-rules)
                      (reset! nl-rules-atom
                              (vec
                               (cons
                                :div.section
                                (cons (when (= (count nl-rules) 0)
                                        [:h4 (str "no rules")]) 
                                      (mapv (fn [rule]
                                              [:div.rule 
                                               [:div.number (str (u/get-in rule [::i]) " of " (count nl-rules) " 🇳🇱 rule"
                                                                 (when (not (= 1 (count nl-rules))) "s") "")]
                                               (draw-node-html rule)])

                                            ;; add an 'i' index to each rule: e.g. first rule has {:i 0}, second rule has {:i 1}, etc.
                                            (map merge ;; map will use 'merge' as the function to map over.

                                                 ;; sort the rules. This is the first sequence: each member of *this* sequence .. 
                                                 (sort (fn [a b]
                                                         (compare (str a) (str b)))
                                                       nl-rules)
                                                 (->> (range 1 (+ 1 (count nl-rules))) ;; .. is merged with the member in this second sequence
                                                      (map (fn [i] {::i i})))))))))

                      :else (do
                              (log/info (str "DOING HELEMAAL!!"))
                              (helemaal-niks nl-trees-atom nl-rules-atom nl-lexemes-atom)
                              (helemaal-niks en-trees-atom en-rules-atom en-lexemes-atom)))))))))))
                              
                              

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
