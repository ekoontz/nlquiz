(ns nlquiz.parse
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.menard :refer [decode-grammar decode-morphology]]
   [nlquiz.parse.widgets :refer [en-widget nl-widget]]
   [nlquiz.parse.functions :refer [do-analysis on-change new-question]]
   [reagent.core :as r]
   [cemerick.url :as url])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

;; routed to by: core.cljs/(defn page-for)
(defn component []
  (let [;; linguistic atoms:
        en-grammar (atom nil)
        en-morphology (atom nil)
        nl-grammar (atom nil)
        nl-morphology (atom nil)
        language-models-loaded? (atom false)

        ;; UI atoms:
        surface-atom (r/atom "")
        nl-trees-atom (r/atom " ")
        nl-lexemes-atom (r/atom " ")
        nl-rules-atom (r/atom " ")
        en-trees-atom (r/atom " ")
        en-lexemes-atom (r/atom " ")
        en-rules-atom (r/atom " ")]
        
    ;; 1. initialize linguistic resources from server:
    (go
      (let [en-grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                   "/grammar/en")))
            en-morphology-response (<! (http/get (str (language-server-endpoint-url)
                                                      "/morphology/en")))
            nl-grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                   "/grammar/nl")))
            nl-morphology-response (<! (http/get (str (language-server-endpoint-url)
                                                      "/morphology/nl")))]
        (reset! en-grammar (-> en-grammar-response :body decode-grammar))
        (reset! en-morphology (-> en-morphology-response :body decode-morphology))

        (reset! nl-grammar (-> nl-grammar-response :body decode-grammar))
        (reset! nl-morphology (-> nl-morphology-response :body decode-morphology))

        (reset! language-models-loaded? true)

        (log/info (str "finished loading the en grammar: " (count @en-grammar) " rule"
                       (if (not (= (count @en-grammar) 1)) "s") "."))
        (log/info (str "finished loading the en morphology: " (count @en-morphology) " rule"
                       (if (not (= (count @en-morphology) 1)) "s") "."))

        (log/info (str "finished loading the nl grammar: " (count @nl-grammar) " rule"
                       (if (not (= (count @nl-grammar) 1)) "s") "."))
        (log/info (str "finished loading the nl morphology: " (count @nl-morphology) " rule"
                       (if (not (= (count @nl-morphology) 1)) "s") "."))
        
        (let [q (-> (:query (url/url (-> js/window .-location .-href)))
                    (get "q")
                    ((fn [x] (if (seq x) (trim (string/replace x #"\+" " "))))))]
          (when (seq q)
            (reset! surface-atom q)
            (if true (set! (.-value (.getElementById js/document "parse-input")) q))
            (when true
              (do-analysis @surface-atom
                           {:input surface-atom
                            :nl {:trees nl-trees-atom
                                 :lexemes nl-lexemes-atom
                                 :rules nl-rules-atom
                                 :grammar nl-grammar
                                 :morphology nl-morphology}
                            :en {:trees en-trees-atom
                                 :lexemes en-lexemes-atom
                                 :rules en-rules-atom
                                 :grammar en-grammar
                                 :morphology en-morphology}}))))))

    ;; UI and associated functionality
    ;; 2. atoms that link the UI and the functionality:
    (let []
      
      ;; 4. render the UI:
      (fn []
        [:div.parse
         [:div.input [:input {:type "text"
                              :size 50
                              :id "parse-input"
                              :placeholder "type something in Dutch or English"
                              ;; 5. attach the function that take all the components (UI and linguistic resources) and does things with them to the on-change attribute:
                              
                              :on-change (on-change {:input surface-atom
                                                     :nl {:trees nl-trees-atom
                                                          :lexemes nl-lexemes-atom
                                                          :rules nl-rules-atom
                                                          :grammar nl-grammar
                                                          :morphology nl-morphology}
                                                     :en {:trees en-trees-atom
                                                          :lexemes en-lexemes-atom
                                                          :rules en-rules-atom
                                                          :grammar en-grammar
                                                          :morphology en-morphology}})}]]
                                           
         (nl-widget nl-trees-atom nl-lexemes-atom nl-rules-atom)
         (en-widget en-trees-atom en-lexemes-atom en-rules-atom)

         ]))))







