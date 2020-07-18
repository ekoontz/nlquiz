;; top-level, mostly-generic http handler configuration;
;; See nlquiz.handlers for more domain-specific http handlers.
(ns nlquiz.handler
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [nlquiz.handlers :refer [generate-by-expression-index
                            generate-by-spec
                            parse-en parse-nl]]
   [nlquiz.middleware :refer [middleware]]
   [config.core :refer [env]]
   [clojure.data.json :as json :refer [write-str]]
   [dag_unify.core :as u]
   [hiccup.page :refer [include-js include-css html5]]
   [menard.model :as model]
   [menard.english :as en]
   [menard.nederlands :as nl]))

(def optimized? true)

(defonce root-path (or (env :root-path) "/"))

(def mount-target
  [:div#app
   [:h2 "Welcome to nlquiz"]
   [:p "please wait while Figwheel is waking up ..."]
   [:p "(Check the js console for hints if nothing exciting happens.)"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (str root-path (if (env :dev) "css/site.css" "css/site.min.css")))
   (include-css (str root-path (if (env :dev) "css/debug.css" "css/debug.min.css")))
   (include-css (str root-path (if (env :dev) "css/expression.css" "css/expression.min.css")))])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (if (env :dev)
      (include-js (str root-path "js/app.js"))
      (include-js (str root-path "js/app-optimized.js")))]))

(defn html-response
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn json-response
  [_request handler]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (write-str (handler _request))})

;; see: src/cljs/nlquiz/core.cljs for the subset of
;; these routes below that are handled by html-response:
;; i.e. "/","/quiz", and "/about":
(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [;; routes which return a html response:

     ;; TODO: redirect 302 to /nlquiz (or maybe it's not necessary)
     ["/"                                    {:get {:handler html-response}}]
     ["/nlquiz"                              {:get {:handler html-response}}]
     ["/nlquiz/about"                        {:get {:handler html-response}}]
     ["/nlquiz/curriculum"                   {:get {:handler html-response}}]
     ["/nlquiz/curriculum/:major"            {:get {:handler html-response}}]
     ["/nlquiz/curriculum/:major/:minor"     {:get {:handler html-response}}]
     ["/nlquiz/test"                         {:get {:handler html-response}}]
     ;; routes which return a json response:
     ["/nlquiz/parse/nl"                     {:get {:handler (fn [request] (json-response request parse-nl))}}]
     ["/nlquiz/parse/en"                     {:get {:handler (fn [request] (json-response request parse-en))}}]
     ["/nlquiz/generate"                     {:get {:handler (fn [request] (json-response request generate-by-spec))}}]
     ["/nlquiz/generate/pair"                {:get {:handler (fn [request] (json-response request generate-by-spec))}}]



     ["/about"                               {:get {:handler html-response}}]
     ["/curriculum"                          {:get {:handler html-response}}]
     ["/curriculum/:major"                   {:get {:handler html-response}}]
     ["/curriculum/:major/:minor"            {:get {:handler html-response}}]
     ["/test"                                {:get {:handler html-response}}]
     ;; routes which return a json response:
     ["/parse/nl"                            {:get {:handler (fn [request] (json-response request parse-nl))}}]
     ["/parse/en"                            {:get {:handler (fn [request] (json-response request parse-en))}}]
     ["/generate"                            {:get {:handler (fn [request] (json-response request generate-by-spec))}}]
     ["/generate/pair"                       {:get {:handler (fn [request] (json-response request generate-by-spec))}}]])

   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path root-path :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))

(defmacro inline-resource [resource-path]
  (slurp (clojure.java.io/resource resource-path)))
