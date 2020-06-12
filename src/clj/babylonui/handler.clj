(ns babylonui.handler
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
   [babylonui.middleware :refer [middleware]]
   [config.core :refer [env]]
   [clojure.data.json :as json :refer [write-str]]
   [dag_unify.core :as u]
   [hiccup.page :refer [include-js include-css html5]]))

(def optimized? false)

(def mount-target
  [:div#app
   [:h2 "Welcome to babylonui"]
   [:p "please wait while Figwheel is waking up ..."]
   [:p "(Check the js console for hints if nothing exciting happens.)"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css (if (env :dev) "/css/debug.css" "/css/debug.min.css"))
   (include-css (if (env :dev) "/css/expression.css" "/css/expression.min.css"))])

;; TODO: use environment to control which version of the js (normal or optimized) is included.
(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (if optimized?

      ;; the following include-js path must be the
      ;; same as in: project.clj:cljsbuild:builds:min:compiler:output-to :
      (include-js "/js/app-optimized.js")

      (include-js "/js/app.js"))]))

(defn index-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn quiz-handler [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(def nl-expressions
  (filter #(= true (u/get-in % [:menuable?] true))
          nl/expressions))

(defn generate [_request]
  (let [spec-index (-> _request :path-params :spec)
        spec (nth nl-expressions (Integer. spec-index))
        debug (log/info (str "generating a question with spec: " spec))
        target-expression (-> spec nl/generate)
        source-expression (-> target-expression tr/nl-to-en-spec en/generate)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (write-str {:source (-> source-expression en/morph)
                       :target (-> target-expression nl/morph)})}))

(defn parse [_request]
  (let [string-to-parse
        (get
         (-> _request :query-params) "q")]
    (log/info (str "parse: your input was: " string-to-parse))
    (let [parses
          (->> string-to-parse nl/parse (map nl/syntax-tree))]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (write-str {:parses parses})})))

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/" {:get {:handler index-handler}}]
     ["/generate/:spec" {:get {:handler generate
                               :parameters {:path {:spec int?}}}}]
     ["/parse" {:get {:handler parse}}]
     ["/quiz" {:get {:handler quiz-handler}}]
     ["/about" {:get {:handler index-handler}}]])
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
