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
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

;; routed to by: core.cljs/(defn page-for)
(defn component []
  ;; 1. initialize some data structures that don't change (often).
  ;; for now, only NL grammar:

  ;; TODO: have to synchronize on this to prevent generation before linguistic resources are done loading:
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
                   :on-change (when language-models-loaded? (on-change nl-surface-atom en-surfaces-atom grammar))
                   }]]
         (nl-widget nl-surface-atom)
         (en-widget en-surfaces-atom)]))))

(defn on-change [nl-surface-atom en-surfaces-atom grammar]
  (fn [input-element]
    (let [nl-surface (-> input-element .-target .-value string/trim)
          fresh? (fn [] (= @nl-surface-atom nl-surface))]
      (when (not (fresh?))
        ;; Only start the (go) if there is a difference between the input we are given (nl-surface)
        ;; and the last input that was processed (@nl-surface-atom).

        ;; Change english output to spinner since it will be updated, if not by this (go), then by a subsequent (go):
        (reset! en-surfaces-atom spinner)

        (go
          (reset! nl-surface-atom nl-surface)

          ;; 1. Get the information necessary from the server about the NL expression to start parsing on the client side:
          (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                      "/parse-start?q=" nl-surface)))
                                   :body decode-parse)]
            (when (fresh?)

              ;; 2. With this information ready, now do the NL parsing:
              (let [nl-parses (nl-parses parse-response @grammar nl-surface)
                    ;; And for that set of NL parses, get the equivalent
                    ;; set of specifications for the english:
                    en-specs (nl-parses-to-en-specs nl-parses)]

                ;; 3. For each such spec, generate an english expression, and
                ;;    for each generated expression, add it to the 'update-to' atom.
                (let [update-to (atom [])]
                  (doseq [en-spec en-specs]
                    (when (fresh?)
                      (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                            "/generate/en?spec=" (-> en-spec
                                                                                     dag-to-string))))]
                        (when (fresh?)
                          (reset! update-to (-> (cons (-> gen-response :body :surface)
                                                      @update-to)
                                                set
                                                vec))))))
                  ;; 4. Update the english UI element with a common-delimited string of all of
                  ;; the members of the 'update-to' atom:
                  (when (fresh?)
                    (reset! en-surfaces-atom (if (seq @update-to)
                                               (string/join "," @update-to)
                                               "??"))))))))))))

(defn new-question [en-question-atom]
  (go
    (let [generation-response
          (<! (http/get
               (str (language-server-endpoint-url)
                    "/generate/en?spec=" (-> {:phrasal true
                                              :sem {:pred :dog}
                                              :cat :noun
                                              :subcat []}
                                             dag-to-string))))]
      (reset! en-question-atom (-> generation-response
                                   :body :surface)))))

