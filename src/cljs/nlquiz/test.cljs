(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [md5.core :as md5]
   [nlquiz.constants :refer [root-path spinner]]
   [nlquiz.curriculum.content :refer [curriculum]]
   [nlquiz.menard :refer [array2map dag-to-string decode-grammar decode-parse
                          nl-parses nl-parses-to-en-specs nl-sem nl-tokens nl-trees
                          remove-duplicates submit-guess]]
   [menard.parse :as parse]
   [menard.serialization :as s]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session]
   )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

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

(defn update-english [en-specs en-surfaces-atom fresh?]
  (go
    (let [update-to (atom [])]
      (doseq [en-spec en-specs]
        (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                              "/generate/en?spec=" (-> en-spec
                                                                       dag-to-string))))]
          (if (fresh?)
            (do
              (reset! update-to (-> (cons (-> gen-response :body :surface)
                                          @update-to)
                                    set
                                    vec))
              (reset! en-surfaces-atom (if (seq @update-to)
                                         (string/join "," @update-to)
                                         "??")))))))))

(defn test []
  (let [grammar (atom nil)]
  (go 
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))))
  (let [guess-text (r/atom "")
        spinner [:i {:class "fas fa-stroopwafel fa-spin"}]
        
        ;; nl-related
        nl-surface-atom (r/atom spinner)
        
        ;; en-related
        en-surfaces-atom (r/atom spinner)]
    (fn []
      [:div ;; top
       [:div.debug
        [:input {:type "text"
                 :placeholder "type something in Dutch"
                 :on-change (fn [input-element]
                              (let [nl-surface (-> input-element .-target .-value)
                                    fresh? (fn [] (= @nl-surface-atom nl-surface))]
                                (go
                                  (reset! nl-surface-atom nl-surface)
                                  (reset! en-surfaces-atom spinner)
                                  (let [parse-response (-> (<! (http/get (str (language-server-endpoint-url)
                                                                              "/parse-start?q=" nl-surface)))
                                                           :body decode-parse)]
                                    (if (fresh?)
                                      (let [nl-parses (nl-parses parse-response @grammar nl-surface)
                                            en-specs (nl-parses-to-en-specs nl-parses)]
                                        (update-english en-specs en-surfaces-atom fresh?)))))))
                                          
                 }]]
       (nl-widget nl-surface-atom)
       (en-widget en-surfaces-atom)
       ]))))

