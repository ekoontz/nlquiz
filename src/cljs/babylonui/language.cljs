(ns babylonui.language
  (:require-macros [babylon.english]
                   [babylon.nederlands])
  (:require
   [babylon.english :as en]
   [babylon.nederlands :as nl]))

(defn deserialize-lexicon [map-with-serializations]
  (zipmap
   (keys map-with-serializations)
   (map (fn [serializations]
          (vec (map dag_unify.serialization/deserialize
                    serializations)))
        (vals map-with-serializations))))

(def en-lexicon (deserialize-lexicon (en/read-compiled-lexicon)))
(def nl-lexicon (deserialize-lexicon (nl/read-compiled-lexicon)))

