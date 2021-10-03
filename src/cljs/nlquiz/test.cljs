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

(def en-surfaces-atom (r/atom))
(def input-map (atom {}))

(defn en-widget [text specs sems trees]
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
    [:h2 ":sem"]
    [:div.monospace
     @sems]]
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

(defn update-english [nl-parses-atom en-surfaces-atom nl-surface-atom en-specs-atom en-sem-atom en-trees-atom]
  (let [old-nl @nl-surface-atom
        old-en @en-surfaces-atom]
    (let [specs (->> @nl-parses-atom
                     (map dag_unify.serialization/serialize)
                     set
                     vec
                     (map dag_unify.serialization/deserialize)
                     (map tr/nl-to-en-spec)
                     remove-duplicates)
          update-to (atom [])]
      (reset! en-specs-atom (->> specs
                                 (map dag_unify.serialization/serialize)
                                 (map str)
                                 array2map))
      (reset! en-sem-atom (->> specs (map #(u/get-in % [:sem])) (map dag_unify.serialization/serialize) (map str) array2map))
      (log/info (str "generating this many english expressions: " (count specs)))
      (doseq [en-spec specs]
        (if (= old-nl @nl-surface-atom)
          (go
            (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                  "/generate/en?spec=" (-> en-spec
                                                                           dag-to-string))))]
              (reset! update-to (-> (cons (-> gen-response :body :surface)
                                          @update-to)
                                    set
                                    vec))
              
              (reset! en-surfaces-atom (if (seq @update-to)
                                         (string/join "," @update-to)
                                         "??"))
              (reset! en-trees-atom (->> [@en-surfaces-atom] array2map))))
          (log/info (str "*** avoiding updating after processing old data.")))))))

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
        last-parse-of (atom "")
        
        ;; en-related
        en-specs-atom (r/atom spinner)
        en-sems-atom (r/atom spinner)
        en-trees-atom (r/atom spinner)]
    (fn []
      [:div ;; top
       [:div.debug
        [:input {:type "text"
                 :placeholder "type something in Dutch"
                 :on-change (fn [input-element]
                              (reset! guess-text (-> input-element .-target .-value))
                              (log/info (str "input changed to: " @guess-text))
                              (reset! en-surfaces-atom spinner)
                              (if (not (= @guess-text @last-parse-of))
                                (do
                                  (reset! last-parse-of @guess-text)
                                  (go
                                    (let [parse-of @guess-text
                                          parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                                                "/parse-start?q=" parse-of)))
                                                             :body decode-parse)
                                          nl-parses (nl-parses parse-response @grammar @guess-text)]
                                      (reset! nl-parses-atom nl-parses)
                                      (reset! input-map parse-response)
                                      (reset! nl-surface-atom @guess-text)
                                      (reset! nl-tokens-atom (str (nl-tokens @input-map)))
                                      (reset! nl-sem-atom (array2map (nl-sem nl-parses)))
                                      (reset! nl-trees-atom (array2map (nl-trees nl-parses)))
                                      (update-english nl-parses-atom en-surfaces-atom nl-surface-atom
                                                      en-specs-atom en-sems-atom en-trees-atom))))
                                (log/info (str "NOT DOING REDUNDANT PARSE OF: " @last-parse-of))))
         :value @guess-text}]]
       (nl-widget guess-text nl-tokens-atom nl-sem-atom nl-trees-atom)
       (en-widget en-surfaces-atom en-specs-atom en-sems-atom en-trees-atom)
       (backwards-compat-widget nl-sem-atom en-surfaces-atom)
       ]))))
