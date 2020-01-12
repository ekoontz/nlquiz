(ns babylonui.nederlands
  (:require-macros [babylon.nederlands])
  (:require
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [babylon.nederlands.cljs_support :as nl-cljs]
   [babylon.serialization :as s]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(defn generate [spec & [times]]
  (let [attempt
        (try
          (g/generate spec
                      (nl-cljs/grammar)
                      (fn [spec]
                        (shuffle (nl-cljs/index-fn spec)))
                      nl-cljs/syntax-tree)
          (catch js/Error e
            (cond
              (or (nil? times)
                  (< times 2))
              (do
                (log/info (str "retry #" (if (nil? times) 1 (+ 1 times))))
                (generate spec (if (nil? times) 1 (+ 1 times))))
              true nil)))]
      (cond
        (and (or (nil? times)
                 (< times 2))
             (or (= :fail attempt)
                 (nil? attempt)))
        (do
          (log/info (str "retry #" (if (nil? times) 1 (+ 1 times))))
          (generate spec (if (nil? times) 1 (+ 1 times))))
        (or (nil? attempt) (= :fail attempt))
        (log/error (str "giving up generating after 2 times; sorry."))
        true
        {:structure attempt
         :syntax-tree (nl-cljs/syntax-tree attempt)
         :surface (nl-cljs/morph attempt)})))
