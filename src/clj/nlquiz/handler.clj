;; top-level, mostly-generic http handler configuration;
;; See nlquiz.handlers for more domain-specific http handlers.
(ns nlquiz.handler
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [nlquiz.handlers :refer [generate-by-expression-index
                            generate-by-spec
                            generate-by-spec-with-alts
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

(defonce root-path
  (do (log/info (str "environment ROOT_PATH: " (env :root-path)))
      (or (env :root-path) "/")))

;; this macro is supposed to let clojurescript to know the server's root-path
;; so that it can properly create URLs to do HTTP requests to the server,
;; but it doesn't work right yet: it thinks the environment doesn't have the value
;; defined, even though it is.
(defmacro root-path-from-env []
  (or (System/getenv "ROOT_PATH") "/nlquiz/"))

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
   [:title "nlquiz"] ;; TODO: title should be more context-specific about each page.
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
  "Call a handler on a request, which returns a clojure data structure.
   Then call clojure.data.json/write-str to turn that structure into JSON
   so the client's browser can parse it."
  [_request handler]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (-> _request handler write-str)})

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
     ["/nlquiz/generate-with-alts"           {:get {:handler (fn [request] (json-response request generate-by-spec-with-alts))}}]

     ;; unfortunately we have to add every one of the routes above AGAIN (so that the app works at a non-empty path within an existing domain):
     ["/about"                               {:get {:handler html-response}}]
     ["/curriculum"                          {:get {:handler html-response}}]
     ["/curriculum/:major"                   {:get {:handler html-response}}]
     ["/curriculum/:major/:minor"            {:get {:handler html-response}}]
     ["/test"                                {:get {:handler html-response}}]
     ;; routes which return a json response:
     ["/parse/nl"                            {:get {:handler (fn [request] (json-response request parse-nl))}}]
     ["/parse/en"                            {:get {:handler (fn [request] (json-response request parse-en))}}]
     ["/generate"                            {:get {:handler (fn [request] (json-response request generate-by-spec))}}]
     ["/generate-with-alts"                  {:get {:handler (fn [request] (json-response request generate-by-spec-with-alts))}}]
     ])

   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path root-path :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))

(defmacro inline-resource [resource-path]
  (slurp (clojure.java.io/resource resource-path)))
