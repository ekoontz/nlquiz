(ns nlquiz.speak
  (:require
   [cljslog.core :as log]))

(defn nederlands [input]
  (let [synth (. js/window -speechSynthesis)
        utterThis (new js/SpeechSynthesisUtterance input)
        nl-voice
        (->> (.getVoices synth)
             (filter #(or (= "nl" (-> % .-lang))
                          (= "nl-BE" (-> % .-lang))
                          (= "nl-NL" (-> % .-lang))))

             ;; remove duplicates, if any:
             set
             vec

             ;; pick one
             shuffle
             first)]
    (if nl-voice
      (do (aset utterThis "voice" nl-voice)
          (.speak synth utterThis))
      (log/warn (str "could not find a nl-NL voice to speak Dutch on this device; will not attempt speech.")))))
