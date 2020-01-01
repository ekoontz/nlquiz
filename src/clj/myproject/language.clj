(ns myproject.language
  (:require [babylon.english :as en]
            [babylon.nederlands :as nl]))

(defmacro get-en-lexicon []
  `~(en/compiled-lexicon))
