(ns nlquiz.speak
  (:require
   [accountant.core :as accountant]
   [nlquiz.constants :refer [root-path]]
   [nlquiz.dropdown :as dropdown]
   [dag_unify.core :as u]
   [dag_unify.serialization :as s]
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [reagent.core :as r]
   [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn nederlands [input]
  (let [synth (. js/window -speechSynthesis)
        utterThis (new js/SpeechSynthesisUtterance input)
        nl-voice
        (first
         (->> (.getVoices synth)
              (filter #(= "nl-NL" (-> % .-lang)))))]
    (if nl-voice
      (do (aset utterThis "voice" nl-voice)
          (.speak synth utterThis))
      (log/warn (str "could not find a nl-NL voice to speak Dutch on this device; will not attempt speech.")))))

