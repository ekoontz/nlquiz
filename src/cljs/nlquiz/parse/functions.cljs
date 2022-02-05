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

(defn display-linguistics-content [{nl-trees-atom :where
                                    nl-parses :which-is}]
  (if (not (empty? nl-parses))
    (reset! nl-trees-atom
            (vec
             (cons
              :div.section
              (cons (when (= (count nl-parses) 0)
                      [:h4
                       (str "geen bomen")])
                    (mapv (fn [parse]
                            [:div.parse-cell
                             [:div.number (str (u/get-in parse [::i]) " van " (count nl-parses) " 🇳🇱 "
                                               (if (not (= 1 (count nl-parses))) "bomen" "boom"))]
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
    (reset! nl-trees-atom [:div.section [:b "geen boometje owe!"]])))

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
                             :body decode-rules)

                en-lexemes (-> (<! (http/get (str (language-server-endpoint-url)
                                                  "/analyze/en?q=" input-value)))
                               :body decode-analyze)
                ]
            (when (fresh?)
              ;; 2. With this information ready,
              (let [;; 2.a. do the NL parsing. (we specified "&all" above in the query so actually this nl-parses call doesn't do anything much):
                    nl-parses (->> (nl-parses nl-parse-response @nl-grammar @nl-morphology
                                              input-value))
                    ;; 2.b. do the EN parsing:
                    ]
                (display-linguistics-content
                 {:which-is nl-parses
                  :where nl-trees-atom})
                
                (if (not (empty? nl-lexemes))
                  (reset! nl-lexemes-atom
                          (vec
                           (cons
                            :div.section
                            (cons (when (= (count nl-lexemes) 0)
                                    [:h4 (str "geen worden")])
                                  (mapv (fn [lexeme]
                                          [:div.lexeme
                                           [:div.number (str (u/get-in lexeme [::i]) " van " (count nl-lexemes) " 🇳🇱 "
                                                             (if (not (= 1 (count nl-lexemes))) "worden" "woord"))]
                                           (draw-node-html lexeme)])
                                        
                                        (map merge
                                             (sort (fn [a b]
                                                     (compare (str a) (str b)))
                                                   nl-lexemes)
                                             (->> (range 1 (+ 1 (count nl-lexemes)))
                                                  (map (fn [i] {::i i})))))))))
                  (reset! nl-lexemes-atom [:div.section [:b "geen woord"]]))
                  
                (if (not (empty? nl-rules))
                  (reset! nl-rules-atom
                          (vec
                           (cons
                            :div.section
                            (cons (when (= (count nl-rules) 0)
                                    [:h4 (str "geen regels")]) 
                                  (mapv (fn [rule]
                                          [:div.rule 
                                           [:div.number (str (u/get-in rule [::i]) " van " (count nl-rules) " 🇳🇱 "
                                                             (if (not (= 1 (count nl-rules))) "regels" "regel"))]
                                           (draw-node-html rule)])
                                        
                                        ;; add an 'i' index to each rule: e.g. first rule has {:i 0}, second rule has {:i 1}, etc.
                                        (map merge ;; map will use 'merge' as the function to map over.
                                             
                                             ;; sort the rules. This is the first sequence: each member of *this* sequence .. 
                                             (sort (fn [a b]
                                                     (compare (str a) (str b)))
                                                   nl-rules)
                                             (->> (range 1 (+ 1 (count nl-rules))) ;; .. is merged with the member in this second sequence
                                                  (map (fn [i] {::i i})))))))))
                  (reset! nl-rules-atom [:div.section [:b "geen regel"]]))))))))))

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
