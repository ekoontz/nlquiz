(ns nlquiz.curriculum.content
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [nlquiz.curriculum.functions
    :refer [show-alternate-examples
            show-examples]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env]]))

(def path-to-content (atom {}))

(defn set-content [path]
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          ;; TODO: check for server errors
          (if (= 200 (-> response :status))
            (reset! path-to-content
                    (merge {path (-> response :body)}
                           @path-to-content))

            (log/error (str "unexpected response for path:"
                            path "; response was: " 
                            response)))))))

(defn get-content [path]
  (fn [] (rewrite-content (or (get @path-to-content path)
                              (do (set-content path)
                                  (get @path-to-content path))))))

(defn rewrite-content
  "transform all instances of '[:show-examples ...]' with '[show-examples ...]'"
  [content]
  (cond
    (and (vector? content)
         (= (first content) :show-examples))
    [show-examples (second content) (if (= 3 (count content))
                                      (nth content 2)
                                      ;; default to showing 5 examples:
                                      5)]

    (and (vector? content)
         (= (first content) :show-alternate-examples))
    [show-alternate-examples (nth content 1) (nth content 2)]
    
    (vector? content)
    (vec (map (fn [x]
                (rewrite-content x))
              content))
    :else
    content))


