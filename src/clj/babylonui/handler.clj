(ns babylonui.handler
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [babylon.english :as en]
   [babylon.nederlands :as nl]
   [babylon.translate :as tr]
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
   :body (handler _request)})

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/" {:get {:handler html-response}}]

     ["/generate/:spec" {:get {:handler generate
                               ;; :spec is an index in an array of expressions;
                               ;; the index is the expression specification that we want to
                               ;; use to generate.
                               :parameters {:path {:spec int?}}}}]
     ["/parse" {:get {:handler (fn [request] (json-response request parse))}}]
     ["/quiz" {:get {:handler html-response}}]
     ["/about" {:get {:handler html-response}}]])
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
