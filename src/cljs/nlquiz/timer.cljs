(ns nlquiz.timer
  (:require
   [cljslog.core :as log]))

(def default-check-input-every 300)

(defn setup-timer [get-input-value-fn submit-guess-fn & [last-input-ref check-input-every]]
  (let [last-input-ref (or last-input-ref (atom ""))
        check-input-every (or check-input-every default-check-input-every)
        check-user-input
        (fn []
          (let [current-input-value (get-input-value-fn)]
            (if (and (not (empty? current-input-value))
                     (not (= current-input-value @last-input-ref)))
              (do
                (log/info (str "submitting guess after timeout=" check-input-every  ": '" current-input-value "'"))
                (reset! last-input-ref current-input-value)
                (submit-guess-fn current-input-value)))
            (setup-timer get-input-value-fn submit-guess-fn last-input-ref check-input-every)))]
    (js/setTimeout check-user-input check-input-every)))

