(defproject babylonui "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring-server "0.5.0"]
                 [reagent "0.9.0-rc4"]
                 [reagent-utils "0.3.3"]
                 [ring "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1.6"]
                 [org.clojure/clojurescript "1.10.597"
                  :scope "provided"]
                 [metosin/reitit "0.3.7"]
                 [pez/clerk "1.0.0"]
                 [venantius/accountant "0.2.5"
                  :exclusions [org.clojure/tools.reader]]
                 [cljslog "0.1.0"]
                 [dag_unify "1.7.5"]
                 [babylon "0.0.1-SNAPSHOT"]

                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]]
  
  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.7"]
            [lein-asset-minifier "0.4.6"
             :exclusions [org.clojure/clojure]]
            [lein-ring "0.12.5"]]

  :ring {:handler babylonui.handler/app
         :uberwar-name "babylonui.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "babylonui.jar"
  :main babylonui.server
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  :minify-assets
  [[:css {:source "resources/public/css/site.css"
          :target "resources/public/css/site.min.css"}]
   [:css {:source "resources/public/css/lexeme.css"
          :target "resources/public/css/lexeme.min.css"}]]

  :cljsbuild
  {:builds {;; lein cljsbuild auto min
            :min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler

             ;; this value must be the same as what is used in: src/clj/babylonui/handler.clj:(defn loading-page).
             {:output-to        "target/cljsbuild/public/js/app-optimized.js"

              :output-dir       "target/cljsbuild/public/js"
              :source-map       "target/cljsbuild/public/js/app.js.map"
              :optimizations :advanced
              :infer-externs true
              :pretty-print  false}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :figwheel {:on-jsload "babylonui.core/mount-root"}
             :compiler
             {:main "babylonui.dev"
              :asset-path "/js/out"
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
   
   :css-dirs ["resources/public/css"]
   :ring-handler babylonui.handler/app}

  :profiles {:dev {:repl-options {:init-ns babylonui.repl}
                   :dependencies [[cider/piggieback "0.4.2"]
                                  [binaryage/devtools "0.9.11"]
                                  [ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.8.0"]
                                  [prone "2019-07-08"]
                                  [figwheel-sidecar "0.5.19"]
                                  [nrepl "0.6.0"]
                                  [pjstadig/humane-test-output "0.10.0"]]
                   
                   

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.19"]]
                   

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
