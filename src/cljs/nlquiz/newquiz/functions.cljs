(ns nlquiz.newquiz.functions
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-parse
                          nl-parses nl-parses-to-en-specs]]
   [nlquiz.parse.draw-tree :refer [draw-node-html draw-tree]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

(def server-side-parsing? true)

(defn on-change [{{nl-surface-atom :surface
                   nl-tree-atom :tree
                   nl-grammar :grammar
                   nl-morphology :morphology} :nl
                  {en-surfaces-atom :surfaces} :en}]
  (fn [input-element]
    (let [nl-surface (-> input-element .-target .-value string/trim)
          fresh? (fn [] (= @nl-surface-atom nl-surface))]
      (when (not (fresh?))
        ;; Only start the (go) if there is a difference between the input we are given (nl-surface)
        ;; and the last input that was processed (@nl-surface-atom).

        ;; Change english output to spinner since it will be updated, if not by this (go), then by a subsequent (go):
        (if en-surfaces-atom (reset! en-surfaces-atom spinner))

        (go
          (reset! nl-surface-atom nl-surface)

          ;; 1. Get the information necessary from the server about the NL expression to start parsing on the client side:
          (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                      "/parse-start?q=" nl-surface (when server-side-parsing? "&all"))))
                                   :body decode-parse)]
            (when (fresh?)

              ;; 2. With this information ready,
              (let [;; 2.a. do the NL parsing:
                    nl-parses (nl-parses parse-response @nl-grammar @nl-morphology
                                         nl-surface)
                    ;; 2.b. For that set of NL parses in 2.a., get the equivalent
                    ;; set of specifications for the english:
                    en-specs (when en-surfaces-atom (nl-parses-to-en-specs nl-parses))]
                (when (and nl-parses (seq nl-parses))
                  (reset! nl-tree-atom
                          (vec
                           (cons
                            :div
                            (mapv (fn [parse]
                                    [:div
                                     (draw-tree parse)
                                     (draw-node-html
                                      (-> parse
                                          (dissoc :1)
                                          (dissoc :2)
                                          (dissoc :head)
                                          (dissoc :comp)))])
                                  nl-parses)))))

                (when en-surfaces-atom
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
                                                 "??")))))))))))))

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
