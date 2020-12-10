(ns nlquiz.server
    (:require
     [clojure.tools.logging :as log]
     [config.core :refer [env]]
     [nlquiz.handler :refer [app]]
     [ring.adapter.jetty :refer [run-jetty]])
    (:gen-class))

(defn -main [& args]
  (let [port (or (env :port) 3000)]
    (run-jetty app {:port port :join? false})))

