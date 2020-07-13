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
  (log/info (str "initializing.."))
  (log/info (str "loading nl model.."))
  (nl/load-model)
  (log/info (str "loaded."))
  (log/info (str "loading en model.."))
  (en/load-model)
  (log/info (str "loaded.")))

(use 'ruiyun.tools.timer)
(if (System/getenv "MODEL_URL")
  (run-task! initialize-language-models
             :period (* 5 60 1000)) ;; reload every 5 minutes.
  (log/warn (str "MODEL_URL was not defined in the environment: will not be able to refresh language models. In the future, please set your environment's MODEL_URL to 'file:///Users/ekoontz/menard/src/'")))

(defn -main [& args]
  (let [port (or (env :port) 3000)]
    (if true
      (initialize-language-models))
    (run-jetty app {:port port :join? false})))
