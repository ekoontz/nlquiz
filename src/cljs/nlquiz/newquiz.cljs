(ns nlquiz.newquiz
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-parse
                          nl-parses nl-parses-to-en-specs]]
   [nlquiz.newquiz.widgets :refer [en-question-widget en-widget nl-widget]]
   [nlquiz.newquiz.functions :refer [on-change new-question]]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

;; routed to by: core.cljs/(defn page-for)
(defn component []
  ;; 1. initialize some data structures that don't change (often).
  ;; for now, only NL grammar:
  (let [grammar (atom nil)
        language-models-loaded? (atom false)]
    ;; 1. initialize linguistic resources from server:
    (go
      (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))]
        (reset! grammar (-> grammar-response :body decode-grammar))
        (reset! language-models-loaded? true)
        (log/info (str "finished loading the nl grammar."))))

    ;; UI and associated functionality
    ;; 2. atoms that link the UI and the functionality:
    (let [nl-surface-atom (r/atom spinner)
          en-surfaces-atom (r/atom spinner)
          en-question-atom (r/atom spinner)]

      ;; 3. initialize the UI: e.g. a new question:
      (new-question en-question-atom)

      ;; 4. render the UI:
      (fn []
        [:div ;; top

         (en-question-widget en-question-atom)

         [:div.debug
          [:input {:type "text"
                   :placeholder "type something in Dutch"
                   ;; 5. attach the function that take all the components (UI and linguistic resources) and does things with them to the on-change attribute:

                   ;; unless language-models-loaded? is true,
                   ;; can't parse user's guess:
                   :on-change (when language-models-loaded?
                                (on-change nl-surface-atom en-surfaces-atom grammar))}]]
         (nl-widget nl-surface-atom)
         (en-widget en-surfaces-atom)]))))

