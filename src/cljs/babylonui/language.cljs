(ns babylonui.language
  (:require-macros [babylon.english]
                   [babylon.nederlands])
  (:require
   [babylon.english :as en]
   [babylon.generate :as g]
   [babylon.nederlands :as nl]
   [cljslog.core :as log]
   [dag_unify.core :as u]))

(defn deserialize-lexicon [map-with-serializations]
  (zipmap
   (keys map-with-serializations)
   (map (fn [serializations]
          (vec (map dag_unify.serialization/deserialize
                    serializations)))
        (vals map-with-serializations))))
