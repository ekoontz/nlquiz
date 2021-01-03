(ns nlquiz-local.server
    (:require
     [clojure.tools.logging :as log]
     [nlquiz-local.handler :refer [app]]
     [ring.adapter.jetty :refer [run-jetty]])
    (:gen-class))

(defn -main [& args]
  (let [port (or (env :port) 3000)]
    (run-jetty app {:port port :join? false})))

