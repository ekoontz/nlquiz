(ns nlquiz.speak
  (:require
   [cljslog.core :as log]))

(defn nederlands [say-this-in-nederlands]
  (let [synth (. js/window -speechSynthesis)
        utterance (new js/SpeechSynthesisUtterance say-this-in-nederlands)
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
      (do
        ;; 1. set the voice for the utterance:
        (aset utterance "voice" nl-voice)
        ;; 2. speak the utterance using synth:
        (.speak synth utterance))
      (log/warn (str "could not find a nl voice to speak Dutch on this device; will not attempt speech.")))))
