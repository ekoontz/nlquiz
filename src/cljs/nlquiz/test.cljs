(ns nlquiz.test
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [menard.english :as en]
   [menard.nederlands :as nl]
   [menard.translate :as tr]
   [nlquiz.dropdown :as dropdown]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce root-path "/nlquiz/")

(defn test-component []
  (let [target-expression-history (r/atom [])
        source-expression-history (r/atom [])
        spec-atom (atom 0)]
    (fn []
      [:div {:style {:float "left"
                     :margin-left "10%"
                     :width "80%"
                     :border "1px dashed green"}}
       (if false
         (do
           (js/setTimeout
            #(generate-new-pair spec-atom target-expression-history source-expression-history)
            1000)
           nil))
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [target-expression (nth @target-expression-history i)
                       source-expression (nth @source-expression-history i)
                       tree-atom (r/atom [])]
                   [:tr {:key (str "target-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th (str (+ 1 i))]
                    [:td (nl/morph target-expression)]
                    [:td (en/morph source-expression)]
                    [:td
                     (do (-> source-expression en/morph (source-parse tree-atom))
                         (log/info (str "tree-atom: " @tree-atom))
                         (clojure.string/join " " @tree-atom))]]))
               (range 0 (count @target-expression-history))))]]])))

(defn source-parse [source-string tree-atom]
  (go (let [response (<! (http/get (str root-path "parse/en")
                                   {:query-params {"q" source-string}}))]
        (log/info (str "the parse trees are: " (-> response :body :trees)))
        (reset! tree-atom (-> response :body :trees))))
  (log/info (str "and now the tree-atom is: " @tree-atom)))
        
(defn generate-new-pair [spec-atom target-expression-history source-expression-history]
  (let [target-expression (nl/generate (nth nl/expressions @spec-atom))
        source-expression (do-the-source-expression target-expression)]
    (log/info (str "target: " (-> target-expression nl/morph)))
    (log/info (str "source: " (-> source-expression en/morph)))
    (update-expressions! target-expression-history target-expression)
    (update-expressions! source-expression-history source-expression)))
    
(defn update-expressions! [expression-history new-expression]
  (swap! expression-history
         (fn [existing-expression-history]
           (if (> (count existing-expression-history) 5)
             (cons new-expression (butlast existing-expression-history))
             (cons new-expression existing-expression-history))))
  nil)

(defn do-the-source-expression [target-expression]
  (try
    (-> target-expression
        tr/nl-to-en-spec
        en/generate)
    (catch js/Error e
      (do
        (log/warn (str "failed to generate: " e))
        "??"))))

