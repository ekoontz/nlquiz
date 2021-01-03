(ns nlquiz-local.middleware
  (:require
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(def middleware
  [#(wrap-defaults % (assoc site-defaults :session false))])

