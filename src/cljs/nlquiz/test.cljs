(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.parse :as parse]
   [menard.serialization :as s]
   [menard.translate.spec :as tr]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.menard :refer [array2map dag-to-string decode-grammar decode-parse
                          nl-parses nl-sem nl-tokens nl-trees
                          remove-duplicates submit-guess]]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session]
   [md5.core :as md5]
   )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

(def input-map (atom {}))

(defn en-widget [text specs trees]
  [:div.debug {:style {:width "40%" :float "right"}}
   [:h1 ":en"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]]
   [:div.debug
    [:h2 ":specs"]
     [:div.monospace
      @specs]]
   [:div.debug
    [:h2 ":trees"]
    [:div.monospace
     @trees]]])

(defn nl-widget [text tokens sem trees]
  [:div.debug {:style {:width "40%" :float "left"}}
   [:h1 ":nl"]
   [:div.debug
    [:h2 ":surface"]
    [:div.monospace
     @text]]
   [:div.debug
    [:h2 ":tokens"]
    [:div.monospace
     @tokens]]
   [:div.debug
    [:h2 ":sem"]
    [:div.monospace
     @sem]]
   [:div.debug
    [:h2 ":trees"]
    [:div.monospace
     @trees]]])

(defn backwards-compat-widget [nl-sem en-surfaces]
  [:div.debug
   [:h2 ":sem"]
   [:div.monospace
    @nl-sem]
   [:h2 ":english"]
   [:div.monospace
    @en-surfaces]])

(defn update-english [en-specs en-surfaces-atom en-trees-atom nl-surface-when-called nl-surface-atom]
  (log/info (str "generating this many english expressions: " (count en-specs) " with nl-surface being:"
                 nl-surface-when-called))
  (reset! en-surfaces-atom spinner)
  (go
    (let [update-to (atom [])]
      (doseq [en-spec en-specs]
        (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                              "/generate/en?spec=" (-> en-spec
                                                                       dag-to-string))))]
          (if (not (= nl-surface-when-called @nl-surface-atom))
            (log/info (str " CHANGE DETECTED: (update-english): " nl-surface-when-called " != " @nl-surface-atom))
            (do
              (log/info (str "ok to update with: " (-> gen-response :body :surface)))
              (reset! update-to (-> (cons (-> gen-response :body :surface)
                                          @update-to)
                                    set
                                    vec))
              (reset! en-surfaces-atom (if (seq @update-to)
                                         (string/join "," @update-to)
                                         "??"))
              (reset! en-trees-atom (->> [@en-surfaces-atom] array2map)))))))))

(defn nl-parses-to-en-specs [nl-parses]
  (->> nl-parses
       (map dag_unify.serialization/serialize)
       set
       vec
       (map dag_unify.serialization/deserialize)
       (map tr/nl-to-en-spec)
       remove-duplicates))

(defn test []
  (let [grammar (atom nil)]
  (go 
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))))
  (let [guess-text (r/atom "")
        spinner [:i {:class "fas fa-stroopwafel fa-spin"}]
        
        ;; nl-related
        nl-sem-atom (r/atom spinner)
        nl-surface-atom (r/atom spinner)
        nl-tokens-atom (r/atom spinner)
        nl-trees-atom (r/atom spinner)
        nl-parses-atom (atom spinner)
        
        ;; en-related
        en-specs-atom (r/atom spinner)
        en-trees-atom (r/atom spinner)
        en-surfaces-atom (r/atom spinner)]
    (fn []
      [:div ;; top
       [:div.debug
        [:input {:type "text"
                 :placeholder "type something in Dutch"
                 :on-change (fn [input-element]
                              (let [nl-surface (-> input-element .-target .-value)]
                                (go
                                  (reset! nl-surface-atom nl-surface)
                                  (reset! nl-sem-atom spinner)
                                  (reset! nl-tokens-atom spinner)
                                  (reset! nl-trees-atom spinner)
                                  (reset! en-specs-atom spinner)
                                  (reset! en-trees-atom spinner)
                                  (reset! en-surfaces-atom spinner)
                                  (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                                              "/parse-start?q=" nl-surface)))
                                                           :body decode-parse)]
                                    (if (= @nl-surface-atom nl-surface)
                                      (let [nl-parses (nl-parses parse-response @grammar nl-surface)
                                            en-specs (nl-parses-to-en-specs nl-parses)]
                                        (update-english en-specs en-surfaces-atom en-trees-atom
                                                        nl-surface nl-surface-atom)
                                        (reset! en-specs-atom en-specs)
                                        (reset! nl-parses-atom nl-parses)
                                        (reset! input-map parse-response)
                                        (reset! nl-tokens-atom (str (nl-tokens @input-map)))
                                        (reset! nl-sem-atom (array2map (nl-sem nl-parses)))
                                        (reset! nl-trees-atom (array2map (nl-trees nl-parses))))
                                      (log/info (str "CHANGE DETECTED (test):"
                                                     nl-surface " != " @nl-surface-atom)))))))
                                          
                 }]]
       (nl-widget nl-surface-atom nl-tokens-atom nl-sem-atom nl-trees-atom)
       (en-widget en-surfaces-atom en-specs-atom en-trees-atom)
       (backwards-compat-widget nl-sem-atom en-surfaces-atom)
       ]))))

