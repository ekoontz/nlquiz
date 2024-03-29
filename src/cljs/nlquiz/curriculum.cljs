(ns nlquiz.curriculum
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [nlquiz.log :as log]
   [dag_unify.core :as u]
   [dag_unify.serialization :refer [deserialize serialize]]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.speak :as speak]
   [reagent.session :as session]
   [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [language-server-endpoint-url
                                           root-path-from-env]]))

(def curriculum-atom (r/atom nil))
(def curriculum-content-atom (r/atom ""))
(def default-count-of-examples 5)

(def generate-http (str (language-server-endpoint-url) "/generate/nl"))
(def generate-with-alts-http (str (language-server-endpoint-url) "/generate-with-alts"))

(defn tree-node [i node selected-path]
  [:div {:key (str "node-" (swap! i inc))}
   [:h1
    (if (:href node)
      (let [url (str "/nlquiz/curriculum/" (:href node))]
        (if (and (not (empty? selected-path))
                 (= url selected-path))
         (reset! topic-name (:name node)))
        [:a {:class (if (= url selected-path)
                      "selected" "")
             :href (str "/nlquiz/curriculum/" (:href node))}
         (:name node)])
      (:name node))]
   (doall
    (map (fn [child]
           (tree-node i child selected-path))
         (:child node)))])

(defn tree [selected-path]
  (get-curriculum)
  (let [i (atom 0)]
    (fn []
      [:div.curriculum
       (doall (map (fn [node]
                     (tree-node i node selected-path))
                   @curriculum-atom))])))

(defn get-curriculum []
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/toc.edn")))]
          (reset! curriculum-atom (-> response :body))))))

(defn get-name-or-children [node]
  (cond (and (:child node) (:name node))
        (cons node (map get-name-or-children (:child node)))
        (:child node)
        (map get-name-or-children (:child node))
        true node))

(defn get-title-for [major & [minor]]
  (->> @curriculum-atom
       (map get-name-or-children)
       flatten (filter #(or (and minor (= (:href %) (str major "/" minor)))
                            (and (nil? minor) (= (:href %) major))))
       (map :name) first))

(defn set-content [path]
  (get-curriculum)
  (let [root-path (root-path-from-env)]
    (go
      (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
        (if (= 200 (-> response :status))
          (do
            (set-meta-defaults)
            (-> response :body interpret-meta)            
            (reset! curriculum-content-atom (-> response :body
                                                  process-show-examples
                                                  remove-meta)))
          (log/error (str "unexpected response for path:"
                          path "; response was: " 
                          response)))))))

(defn process-show-examples
  "transform all instances of '[:show-examples ...]' with '[show-examples ...]'
   Input can be of the form of either: 
      - '[:show-examples <spec>]' or:
      - '[:show-examples <spec> <number>]'.
   If the latter, then <number> of examples will be returned.
   If the former, then the default number of examples will be shown."
  [content]
  (cond
    (and (vector? content)
         (= (first content) :show-examples))
    [show-examples (second content) (if (= 3 (count content))
                                      (nth content 2)
                                      ;; default number of examples:
                                      default-count-of-examples)]

    (and (vector? content)
         (= (first content) :show-alternate-examples))
    [show-alternate-examples (nth content 1) (nth content 2)]
    
    (vector? content)
    (vec (map (fn [x]
                (process-show-examples x))
              content))
    :else
    content))

(defn interpret-meta
  "set state (e.g. which language models to use) for this curriculum item."
  [content]
  (cond
    (and (map? content)
         (u/get-in content [:meta :use-model]))
    (let [use-model
          (u/get-in content [:meta :use-model])]
      (if use-model
        (do (log/debug (str "curriculum/interpret-meta: setting model to: " use-model " for this quiz."))
            (reset! model-name-atom use-model)
            (log/debug (str "curriculum/interpret-meta: ok, set it to: " @model-name-atom)))))
    (vector? content)
    (vec (map (fn [x]
                (interpret-meta x))
              content))
    :else
    content))

(def default-model-name "complete")
(def model-name-atom (atom default-model-name))
(defn set-meta-defaults []
  (reset! model-name-atom default-model-name))

(defn remove-meta
  "remove meta section, if any"
  [content]
  (cond
    (and (map? content)
         (get content :meta))
    ""
    
    (vector? content)
    (vec (map (fn [x]
                (remove-meta x))
              content))
    :else
    content))

(defn major []
  (let [routing-data (session/get :route)
        major (get-in routing-data [:route-params :major])]
    (set-content major)
    (fn []
      [:div.curr-major
       [:div.guide
        [:div.h4 [:h4 (get-title-for major)]]
        [:div.content @curriculum-content-atom]]])))

(defn major-minor []
  (let [routing-data (session/get :route)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (set-content (str major "/" minor))
    (fn []
      [:div.curr-major
       [:div.guide
        [:div.h4 [:h4 (get-title-for major minor)]]
        [:div.content @curriculum-content-atom]]])))

(defn new-pair [spec]
  (let [input (r/atom nil)
        model (or (u/get-in spec [:model]) "complete")
        spec (dissoc spec :model)
        serialized-spec (-> spec serialize str)
        get-pair-fn (fn [] (http/get generate-http
                                     {:query-params {"q" serialized-spec
                                                     "model" model
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))
                                                     }}))]
    (go (let [response (<! (get-pair-fn))]
          (reset! input
                  {:source (-> response :body :source)
                   :target (-> response :body :target)})))
    input))

(defn new-pair-alternate-set [spec alternates]
  (let [input-alternate-a (r/atom nil)
        input-alternate-b (r/atom nil)
        model (or (u/get-in spec [:model]) "complete")
        spec (dissoc spec :model)
        serialized-spec (-> spec serialize str)
        serialized-alternates (->> alternates
                                   (map serialize)
                                   str)
        get-pair-fn (fn [] (http/get generate-with-alts-http
                                     {:query-params {"spec" serialized-spec
                                                     "alts" serialized-alternates
                                                     "model" model
                                                     ;; append a cache-busting argument: some browsers don't support 'Cache-Control:no-cache':
                                                     "r" (hash (str (.getTime (js/Date.)) (rand-int 1000)))}}))]
    (go (let [response (<! (get-pair-fn))]
          (if (true? (-> response :success))
            (do
              (reset! input-alternate-a
                      {:source (-> response :body (nth 0) :source)
                       :target (-> response :body (nth 0) :target)})
              (reset! input-alternate-b
                      {:source (-> response :body (nth 1) :source)
                       :target (-> response :body (nth 1) :target)}))
            (log/error (str "failed to get alternate set for spec:    " serialized-spec " and alts: " serialized-alternates "; response was: " response ".")))))

    ;; Note that we're returning the *refererences*, not the responses themselves.
    ;; The responses will be set asynchronously by the reset!s immediately above.
    [input-alternate-a input-alternate-b]))

(defn add-one [expressions spec]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   [(new-pair spec)]))))

(defn add-one-alternates [expressions spec alternates]
  (swap! expressions
         (fn [expressions]
           (concat expressions
                   (new-pair-alternate-set spec alternates)))))

(defn show-examples [specs & [supply-this-many-examples]]
  (let [expressions (r/atom [])
        this-many-examples (or supply-this-many-examples default-count-of-examples)]
    (doall (take this-many-examples
                 (repeatedly #(add-one expressions (first (shuffle specs))))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or [:a {:href (str "http://google.com/search?q=\"" (:target expression) "\"")} (:target expression)] spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

(defn show-alternate-examples [spec alternates]
  (let [expressions (r/atom [])
        specs [spec]]
    (log/debug (str "show-alternate-examples: spec: " spec))
    (doall (take default-count-of-examples
                 (repeatedly #(add-one-alternates
                               expressions (first (shuffle specs)) alternates))))
    (fn []
      [:div.exampletable
       [:table
        [:tbody
         (doall
          (map (fn [i]
                 (let [expression @(nth @expressions i)]
                   [:tr {:key (str "row-" i) :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th.index (+ i 1)]
                    [:th.speak [:button {:on-click #(speak/nederlands (:target expression))} "🔊"]]
                    [:td.target (or (:target expression) spinner)]
                    [:td.source (or (:source expression) spinner)]]))
               (range 0 (count @expressions))))]]])))

