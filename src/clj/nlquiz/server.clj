(ns nlquiz.server
    (:require
     [clojure.tools.logging :as log]
     [config.core :refer [env]]
     [menard.english :as en]
     [menard.nederlands :as nl]
     [nlquiz.handler :refer [app]]
     [ring.adapter.jetty :refer [run-jetty]])
    (:gen-class))

(defn initialize-language-models []
   (log/info (str "It's time for the reloading-of-the-models.."))
   (log/info (str "loading nl model.."))
   (let [new-model (nl/create-model)]
     (log/info (str "model loaded; replacing."))    
     (dosync
      (ref-set nl/model new-model)))
   (log/info (str "loaded."))
   (log/info (str "loading en model.."))
   (let [new-model (en/create-model)]
     (log/info (str "model loaded; replacing."))
     (dosync
      (ref-set en/model new-model)))
   (log/info (str "Done with all the reloading-of-the-models 'til next time!")))

(use 'ruiyun.tools.timer)
;; TODO: check if we got here are running as 'lein uberjar' here somehow (?)
;; or in other words, only run this task if we're running the the -main of the jar.
(if (System/getenv "MODEL_URL")
  (run-task! initialize-language-models
             :period (* 5 60 1000)) ;; reload every 5 minutes.
  (log/warn (str "MODEL_URL was not defined in the environment: will not be able to "
                 "refresh language models. In the future, please set "
                 "your environment's MODEL_URL to 'file:///Users/ekoontz/menard/resources/', "
                 "unless you are seeing this warning when you running 'lein uberjar', in which "
                 "case, you can safely ignore this warning since it's not relevant.")))
                 

(defn -main [& args]
  (let [port (or (env :port) 3000)]
    (if true
      (initialize-language-models))
    (run-jetty app {:port port :join? false})))
