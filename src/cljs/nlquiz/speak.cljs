(ns nlquiz.speak
  (:require
   [nlquiz.log :as log]))

(def nl-languages (set ["nl" "nl-BE" "nl-NL"]))

(defn nederlands [say-this-in-nederlands]
  (let [synth (. js/window -speechSynthesis)
        utterance (new js/SpeechSynthesisUtterance say-this-in-nederlands)
        nl-voice
        (->> (.getVoices synth)
             (filter #(let [lang (-> % .-lang)]
                        (contains? nl-languages lang)))
             ;; remove duplicates, if any:
             set

             ;; pick one:
             shuffle
             first)]
    (if nl-voice
      (do
        (log/info (str "speaking with voice with lang: " (-> nl-voice .-lang)))
        ;; 1. set the voice for the utterance:
        (aset utterance "voice" nl-voice)
        ;; 2. speak the utterance using synth:
        (.speak synth utterance))
      (log/warn (str "could not find a nl voice to speak Dutch on this device; will not attempt speech.")))))
