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
   [nlquiz.parse.widgets :refer [en-question-widget en-widget nl-widget]]
   [nlquiz.newquiz.functions :refer [on-change new-question]]
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
    (let [nl-surface-atom (r/atom "")
          nl-tree-atom (r/atom "")
          nl-node-html-atom (r/atom "")

          en-question-atom (r/atom spinner)

          en-surface-atom (r/atom "")
          en-tree-atom (r/atom "")
          en-node-html-atom (r/atom "")]

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
                                           (on-change {:nl {:surface nl-surface-atom
                                                            :tree nl-tree-atom
                                                            :node-html nl-node-html-atom
                                                            :grammar nl-grammar
                                                            :morphology nl-morphology}
                                                       :en {:surface en-surface-atom
                                                            :tree en-tree-atom
                                                            :node-html en-node-html-atom
                                                            :grammar en-grammar
                                                            :morphology en-morphology}}))}]]
         (nl-widget nl-tree-atom)]))))




