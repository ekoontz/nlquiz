(defproject nlquiz "0.1.0-SNAPSHOT"
  :description "web app for studying Dutch"
  :url "https://hiro-tan.org/nlquiz"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [thedavidmeister/cljc-md5 "0.0.2"]
                 [ring-server "0.5.0"]
                 [prismatic/dommy "1.1.0"]
                 [reagent "0.10.0"]
                 [reagent-utils "0.3.3"]
                 [ring "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [yogthos/config "1.2.0"]
                 [org.clojure/clojurescript "1.11.60"
                  :scope "provided"]
                 [metosin/reitit "0.5.10"]
                 [venantius/accountant "0.2.5"
                  :exclusions [org.clojure/tools.reader]]
                 [clj-http "3.10.2"]
                 [clj-http "3.12.3"]
                 [cljs-http "0.1.46"]
                 [org.clojure/tools.logging "1.1.0"]
                 [dag_unify "1.10.3-SNAPSHOT"]
                 [org.clojure/tools.logging "1.2.4"]
                 [menard "1.5.0"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/core.async "1.5.648"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [com.cemerick/url "0.1.1"]]
  
  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.8"]
            [lein-asset-minifier "0.4.6"
             :exclusions [org.clojure/clojure]]
            [lein-ring "0.12.5"]]
  
  :ring {:handler nlquiz.handler/app
         :uberwar-name "nlquiz.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "nlquiz.jar"
  :main nlquiz.server
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  ;; if you want to add new css files, you also need to add them to: src/clj/nlquiz/handler:head.
  :minify-assets
  [[:css {:source "resources/public/css/site.css"
          :target "resources/public/css/site.min.css"}]
   [:css {:source "resources/public/css/debug.css"
          :target "resources/public/css/debug.min.css"}]
   [:css {:source "resources/public/css/expression.css"
          :target "resources/public/css/expression.min.css"}]
   [:css {:source "resources/public/css/fa.css"
          :target "resources/public/css/fa.min.css"}]]

  :cljsbuild
  ;; build with:
  ;; - rm target/cljsbuild/public/js/app-optimized.js;
  ;; - lein cljsbuild once min
  {:builds {;; "lein cljsbuild auto min"
            ;; "lein cljsbuild once min"
            :min
            {:source-paths ["src/cljs" "env/prod/cljs"]
             :compiler

             ;; this value must be the same as what is used in the (if optimized? ..)
             ;; part of src/clj/nlquiz/handler.clj:(defn loading-page).
             {:output-to        "target/cljsbuild/public/js/app-optimized.js"

              :output-dir       "target/cljsbuild/public/js"
              :source-map       "target/cljsbuild/public/js/app.js.map"
              :optimizations :advanced
              :infer-externs true
              :pretty-print  false}}
            ;; "lein cljsbuild once app"
            ;; "lein cljsbuild auto app"
            :app
            {:source-paths ["src/cljs" "env/dev/cljs"]
             :figwheel {:on-jsload "nlquiz.core/mount-root"}
             :compiler
             {:main "nlquiz.dev"
              :asset-path "/js/out"
              ;; this value must be the same as what is used in the
              ;; else part of the (if optimized? ..)
              ;; part of src/clj/nlquiz/handler.clj:(defn loading-page).
              :output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/cljsbuild/public/js/out"
              :source-map true
              :verbose true
              :optimizations :none
              :pretty-print  true}}}}

  :figwheel
  {:http-server-root "public"
   :server-port 3449
   :nrepl-port 7002
   :nrepl-middleware [cider.piggieback/wrap-cljs-repl]
   :hawk-options {:watcher :polling}
   :css-dirs ["resources/public/css"]
   :ring-handler nlquiz.handler/app}

  :profiles {:dev {:repl-options {:init-ns nlquiz.repl
                                  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
                   :dependencies [[cider/piggieback "0.5.3"]
                                  [binaryage/devtools "1.0.6"]
                                  [ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.9.5"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [prone "2021-04-23"]
                                  [figwheel-sidecar "0.5.20"]
                                  [nrepl "0.9.0"]
                                  [pjstadig/humane-test-output "0.10.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.20"]]
                   
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
