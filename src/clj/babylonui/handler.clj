;; top-level, mostly-generic http handler configuration;
;; See babylonui.handlers for more domain-specific http handlers.
(ns babylonui.handler
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [babylonui.handlers :refer [generate parse]]
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

;; see: src/cljs/babylonui/core.cljs for the subset of
;; these routes below that are handled by html-response:
;; i.e. "/","/quiz", and "/about":
(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [;; routes which return a html response:
     ["/"      {:get {:handler html-response}}]
     ["/about" {:get {:handler html-response}}]
     ["/quiz"  {:get {:handler html-response}}]
     
     ;; routes which return a json response:
     ["/generate/:spec" {:get {:handler (fn [request] (json-response request generate))}}]
     ["/parse"          {:get {:handler (fn [request] (json-response request parse))}}]])

   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
