(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string :refer [trim]]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-parse
                          nl-parses nl-parses-to-en-specs]]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url]]))

(defn on-change [nl-surface-atom en-surfaces-atom grammar]
  (fn [input-element]
    (let [nl-surface (-> input-element .-target .-value string/trim)
          fresh? (fn [] (= @nl-surface-atom nl-surface))]
      (when (not (fresh?))
        ;; only start the (go) if there is a difference between the input we are given (nl-surface)
        ;; and the last input that was processed (@nl-surface-atom).

        ;; change english output to spinner since it will be updated, if not by this (go), then by a subsequent (go):
        (reset! en-surfaces-atom spinner)

        (go
          (reset! nl-surface-atom nl-surface)

          ;; get the information necessary from the server about the NL expression to start parsing on the client side:
          (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                      "/parse-start?q=" nl-surface)))
                                   :body decode-parse)]
            (when (fresh?)

              ;; With this information ready, now do the parsing, and for that set of parses,
              ;; get the equivalent set of specifications for the english:
              (let [nl-parses (nl-parses parse-response @grammar nl-surface)
                    en-specs (nl-parses-to-en-specs nl-parses)]

                ;; generate an english expression for each spec in en-specs.
                ;; for each generated expression, add it to the 'update-to' atom.
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
                  ;; update the english UI element with a common-delimited string of all of
                  ;; the members of the 'update-to' atom:
                  (when (fresh?)
                    (reset! en-surfaces-atom (if (seq @update-to)
                                               (string/join "," @update-to)
                                               "??"))))))))))))

;; routed to by: core.cljs/(defn page-for)
(defn test []
  ;; 1. initialize some data structures that don't change (often).
  ;; for now, only NL grammar:
  (let [grammar (atom nil)]
    (go 
      (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                                "/grammar/nl")))]
        (reset! grammar (-> grammar-response :body decode-grammar))
        (log/info (str "finished loading the nl grammar."))))

    ;; UI and associated functionality
    ;; 2. atoms that link the UI and the functionality:
    (let [nl-surface-atom (r/atom spinner)
          en-surfaces-atom (r/atom spinner)]

      (fn []
        [:div ;; top
         [:div.debug
          [:input {:type "text"
                   :placeholder "type something in Dutch"
                   ;; 3. the functionality that take all the components (UI and linguistic resources) and does things with them:
                   :on-change (on-change nl-surface-atom en-surfaces-atom grammar)
                   }]]
         ;; 4. the UI:
         (nl-widget nl-surface-atom)
         (en-widget en-surfaces-atom)]))))

(defn en-widget [text]
  [:div.debug {:style {:width "40%" :float "right"}}
   [:h1 ":en"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]]])

(defn nl-widget [text]
  [:div.debug {:style {:width "40%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]]])

