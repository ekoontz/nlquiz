(ns nlquiz.timer
  (:require
   [cljslog.core :as log]))

(defn setup-timer [get-input-value-fn last-input-ref submit-guess-fn]
  (let [check-input-every 400
        check-user-input
        (fn []
          (let [current-input-value (get-input-value-fn)]
            (if (and (not (empty? current-input-value))
                     (not (= current-input-value @last-input-ref)))
              (do
                (log/info (str "submitting guess after timeout=" check-input-every  ": '" current-input-value "'"))
                (submit-guess-fn current-input-value)))
            (setup-timer get-input-value-fn last-input-ref submit-guess-fn)))]
    (js/setTimeout check-user-input check-input-every)))

