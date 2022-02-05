(ns nlquiz.parse
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-morphology decode-parse
                          nl-parses nl-parses-to-en-specs]]
   [nlquiz.parse.widgets :refer [en-widget nl-widget]]
   [nlquiz.parse.functions :refer [on-change new-question]]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

;; routed to by: core.cljs/(defn page-for)
(defn component []
  ;; 1. initialize some data structures that don't change (often).
  ;; for now, only NL grammar:
  (let [nl-grammar (atom nil)
        nl-morphology (atom nil)
        language-models-loaded? (atom false)]
    ;; 1. initialize linguistic resources from server:
    (go
      (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))
            morphology-response (<! (http/get (str (language-server-endpoint-url)
                                                   "/morphology/nl")))]
        (reset! nl-grammar (-> grammar-response :body decode-grammar))
        (reset! nl-morphology (-> morphology-response :body decode-morphology))
        
        (reset! language-models-loaded? true)
        (log/info (str "finished loading the nl grammar: " (count @nl-grammar) " rule"
                       (if (not (= (count @nl-grammar) 1)) "s") "."))
        (log/info (str "finished loading the nl morphology: " (count @nl-morphology) " rule"
                       (if (not (= (count @nl-morphology) 1)) "s") "."))))

    ;; UI and associated functionality
    ;; 2. atoms that link the UI and the functionality:
    (let [surface-atom (r/atom "")

          nl-trees-atom (r/atom " ")
          nl-lexemes-atom (r/atom " ")
          nl-rules-atom (r/atom " ")

          en-trees-atom (r/atom " ")
          en-lexemes-atom (r/atom " ")
          en-rules-atom (r/atom " ")]

      ;; 3. initialize the UI: e.g. a new question:

      ;; 4. render the UI:
      (fn []
        [:div.parse
         [:div.input [:input {:type "text"
                              :size 50
                              :placeholder "type something in Dutch"
                              ;; 5. attach the function that take all the components (UI and linguistic resources) and does things with them to the on-change attribute:
                              
                              :on-change (when language-models-loaded?
                                           ;;^ we do the above (when) check because,
                                           ;; unless language-models-loaded? is true,
                                           ;; we can't parse user's guess.
                                           (on-change {:input surface-atom
                                                       :nl {:trees nl-trees-atom
                                                            :lexemes nl-lexemes-atom
                                                            :rules nl-rules-atom
                                                            :grammar nl-grammar
                                                            :morphology nl-morphology}
                                                       :en {:trees en-trees-atom
                                                            :lexemes en-lexemes-atom
                                                            :rules en-rules-atom
                                                            :grammar en-grammar
                                                            :morphology en-morphology}}))}]]
         (nl-widget nl-trees-atom nl-lexemes-atom nl-rules-atom)
         (en-widget en-trees-atom en-lexemes-atom en-rules-atom)]))))





