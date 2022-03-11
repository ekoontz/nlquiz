(ns nlquiz.quiz
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :refer [trim]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.parse :as parse]
   [menard.translate.spec :as tr]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.curriculum :as curriculum]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-morphology decode-parse
                          parses remove-duplicates]]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))

;; group 1
(def answer-count (atom 0))
(def expression-index (atom 0))
(def initial-state-is-enabled? true)
(def initial-button-state (if initial-state-is-enabled? "" "disabled"))
(def input-state (r/atom "disabled"))
(def possible-correct-semantics (r/atom nil))
(def question-table (r/atom nil))
(def question-html (r/atom spinner))
(def show-answer (r/atom nil))
(def show-praise-text (r/atom nil))
(def show-answer-display (r/atom "none"))
(def show-praise-display (r/atom "none"))
(def translation-of-guess (r/atom ""))
(def last-input-checked (atom ""))

;; group 2
(def ik-weet-niet-button-state (r/atom initial-button-state))

(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "goed gedaan! 🚲"
              "mooi!🌷"
              "oké! 🌷"
              "prachtig.🧇"
              "precies!😁"
              "prima!!😎 "])

(def parse-http (str (language-server-endpoint-url) "/parse"))
(def generate-http (str (language-server-endpoint-url) "/generate"))

(defn show-possible-answer []
  (reset! show-answer-display "block")
  (.focus (.getElementById js/document "input-guess"))
  (reset! translation-of-guess "")
  (js/setTimeout #(reset! show-answer-display "none") 3000)
  false)

(defn show-praise []
  (reset! show-praise-display "block")
  (reset! show-praise-text (-> praises shuffle first))
  (js/setTimeout #(reset! show-praise-display "none") 1000))

(def got-it-right? (atom nil))
(def get-question-fn-atom (atom (fn []
                                  (log/error (str "should not get here! - get-question-fn was not set correctly.")))))

(def major-atom (atom nil))
(def minor-atom (atom nil))

(defn on-submit [e]
  (log/debug (str "on-submit: start: show-answer is: " @show-answer))
  (.preventDefault e)
  (speak/nederlands @show-answer)
  (if (= true @got-it-right?)
    (let [correct-answer @show-answer
          question @question-html]
      ;; get the new expression now to save time, since this takes awhile..
      (if @minor-atom
        (get-expression @major-atom @minor-atom)
        (get-expression @major-atom))
      (show-praise)
      (swap! answer-count inc)
      (log/debug (str "ok, concatting the correct answer: " @show-answer))
      (reset! question-table
              (concat
               [{:source question :target @show-answer}]
               (take 4 @question-table))))

    ;; else
    (do (set-input-value "")    
        (show-possible-answer)))
  (.focus (.getElementById js/document "input-guess"))
  (.click (.getElementById js/document "input-guess")))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (when (seq guesses-semantics-set)
    (log/debug (str "guess count:  " (count guesses-semantics-set)))
    (log/debug (str "correct count:" (count correct-semantics-set)))
    (let [result
          (->> guesses-semantics-set
               (mapcat (fn [guess]
                         (log/debug (str "evaluating guess:          " (serialize guess)))
                         (->> correct-semantics-set
                              (map (fn [correct-semantics]
                                     (log/debug (str "correct semantics option: " (serialize correct-semantics)))
                                     ;; the guess is correct if and only if there is a semantic interpretation _guess_ of the guess where both of these are true:
                                     ;; - unifying _guess_ with some member _correct-semantics_ of the set of correct semantics is not :fail.
                                     ;; - this _correct_semantics_ is more general (i.e. subsumes) _guess_.
                                     (let [fails? (= :fail (u/unify correct-semantics guess))
                                           subsumes? (u/subsumes? correct-semantics guess)
                                           correct? (and (not fails?) subsumes?)
                                           fail-path (if fails? (select-keys (d/fail-path correct-semantics guess) [:path :arg1 :arg2]))]
                                       (log/debug (str "unifies?  " (not fails?)))
                                       (log/debug (str "subsumes? " subsumes?))
                                       (log/debug (str "correct?  " correct?))
                                       (when (not subsumes?)
                                         (log/debug (str "=== guess was not correct because semantics has something that guess semantics lacks. ==="))
                                         (log/debug (str "correct semantics: " (serialize correct-semantics)))
                                         (log/debug (str "guess semantics:   " (serialize guess)))
                                         (log/debug (str "correct subj: " (serialize (u/get-in correct-semantics [:subj]))))
                                         (log/debug (str "guess   subj: " (serialize (u/get-in guess [:subj]))))
                                         (log/debug (str "correct obj: " (serialize (u/get-in correct-semantics [:obj]))))
                                         (log/debug (str "guess   obj: " (serialize (u/get-in guess [:obj])))))
                                       correct?))))))
               (remove #(= false %)))]
      (not (empty? result)))))

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> get-expression (in scope of quiz-layout, but called from within evaluate-guess,
;;                and only called if guess is correct)

(def placeholder "wat is dit in Nederlands?")
(def initial-guess-input-size (count placeholder))
(def guess-input-size (r/atom initial-guess-input-size))
(def grammar (atom nil))
(def morphology (atom nil))

(defn get-input-value []
  (if (.getElementById js/document "input-guess")
    (trim (-> (.getElementById js/document "input-guess") .-value))))

(defn set-input-value []
  (set! (-> (.getElementById js/document "input-guess") .-value) ""))

(defn handle-correct-answer [correct-answer]
  (reset! got-it-right? true)
  (reset! question-html spinner)
  (log/debug (str "handle-correct-answer with: " correct-answer))
  (set-input-value)
  (.focus (.getElementById js/document "other-input"))
  (reset! translation-of-guess "")
  (log/debug (str "setting show-answer to correct-answer: " correct-answer))
  (reset! show-answer correct-answer)
  (if (.-requestSubmit (.getElementById js/document "quiz"))
    (.requestSubmit (.getElementById js/document "quiz"))
    (.dispatchEvent (.getElementById js/document "quiz")
                    (new js/Event "submit" {:cancelable true})))
  (.focus (.getElementById js/document "input-guess")))
  
(defn submit-guess [guess-string]
  (if (empty? @possible-correct-semantics)
    (log/error (str "there are no correct answers for this question."))
    ;; else, there are some correct answers:
    (let [guess-string (if guess-string (trim guess-string))]
      (if (not (empty? guess-string))
        (do
          (log/debug (str "submit-guess: your guess: " guess-string "; show-answer: " @show-answer))
          (if (= guess-string @show-answer)
            ;; user's answer was the same as the server-derived correct answer:
            (handle-correct-answer guess-string)

            ;; user's answer was not the same as the server-derived correct answer, but still might be correct:
            (do
              (reset! translation-of-guess spinner)
              (go (let [parse-response
                        (->
                         (<! (http/get (str (language-server-endpoint-url)
                                            "/parse-start/nl?q=" guess-string)))
                         :body decode-parse)
                        nl-parses (parses parse-response @grammar @morphology guess-string)
                        specs (->> nl-parses
                                   (map serialize)
                                   set
                                   vec
                                   (map deserialize)
                               (map tr/nl-to-en-spec)
                               remove-duplicates)
                        local-sem  (->> nl-parses
                                        (map #(u/get-in % [:sem])))
                        current-input-value (get-input-value)]
                    (if (not (= current-input-value guess-string))
                      (log/debug (str "submit-guess: ignoring guess-string: [" guess-string "] since it's older than current-input-value: [" current-input-value "]"))
                      
                      ;; else
                      (do
                        (log/debug (str "doing english generation with this many specs: " (count specs)))
                        (log/debug (str "doing english generation with specs: " (clojure.string/join "," specs)))
                        (doseq [en-spec specs]
                          (log/debug (str "en-spec to be used for /generate/en: " en-spec))
                          (go (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                                    "/generate/en?spec=" (-> en-spec
                                                                                             dag-to-string))))]
                                (log/debug (str "generating english: checking got-it-right?: " @got-it-right?))
                                ;; if user's already answered the question correctly, then
                                ;; @got-it-right? will be true. If true, then don't re-evaluate.
                                (if (false? @got-it-right?)
                                  
                                  ;; if false, then evaluate user's answer:
                                  (do
                                    (log/debug (str "english generation response to: '" guess-string "': " (-> gen-response :body :surface) " with got-it-right? " @got-it-right?))
                                    (if (not (nil? (-> gen-response :body :sem deserialize)))
                                      (reset! translation-of-guess (-> gen-response :body :surface))) ;; TODO: concatentate rather than overwrite.
                                    (reset! last-input-checked guess-string)
                                    (if (evaluate-guess local-sem
                                                        @possible-correct-semantics)
                                      ;; got it right!
                                      (handle-correct-answer guess-string))))))))))))))))))
  
(defn load-linguistics []
  (go
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))
          morphology-response (<! (http/get (str (language-server-endpoint-url)
                                                 "/morphology/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))
      (reset! morphology (-> morphology-response :body decode-morphology)))))

(def timer-ref (atom (.getTime (js/Date.))))

(defn quiz-layout []
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]
   [:div.question-and-guess
    [:form#quiz {:on-submit on-submit}
     [:div.guess
      [:div.question @question-html]
      [:div
       [:input {:type "text" :size "1"
                :input-mode "none"
                :id "other-input"}]
       [:input {:type "text"
                :placeholder placeholder
                :id "input-guess"
                :input-mode "text"
                :autoComplete "off"
                :size "20"
                :disabled @input-state
                :on-change (fn [input-element]
                             (let [old-timer-value @timer-ref
                                   guess-string (-> input-element .-target .-value)]
                               (log/debug (str "updating input element with: " guess-string))
                               (reset! timer-ref (.getTime (js/Date.)))
                               (log/debug (str "time since last check: " (- @timer-ref old-timer-value) "; currently: '" guess-string "'"))
                               (if (= @last-input-checked guess-string)
                                 (log/debug (str "nothing new: last thing checked was current input value which is: " guess-string))
                                 (do
                                   (reset! input-state "disabled")
                                   (log/debug (str "current guess size: " (-> input-element .-target .-value count)))
                                   (reset! guess-input-size (max initial-guess-input-size (+ 0 (-> input-element .-target .-value count))))
                                   (if (> (- @timer-ref old-timer-value) 200)
                                     (do
                                       (log/debug (str "it's been long enough to try parsing a new guess: " (-> input-element .-target .-value)))
                                       (submit-guess (-> input-element .-target .-value)))
                                     (log/info (str "too recent: not checking guess-string: " guess-string)))
                                   (reset! input-state "")
                                   (.focus (.getElementById js/document "input-guess"))))))
                }]]] ;; /div.guess
      
     [:div.english @translation-of-guess]

     [:div.dontknow
      [:input {:class "weetniet" :type "submit" :value "Ik weet het niet"
               :disabled @ik-weet-niet-button-state}]

      ;; reset button
      [:button {:class "weetniet"
                :on-click #(do
                             ;; this switching-around of focus is necessary
                             ;; for iOS Safari if I recall.
                             (.focus (.getElementById js/document "other-input"))
                             (reset! translation-of-guess "")
                             (set-input-value "")
                             (.focus (.getElementById js/document "input-guess"))
                             (.preventDefault %))} "Reset"]]]]
   [:div.answertable
    [:table
     [:tbody
      (doall
       (->> (range 0 (count @question-table))
            (map (fn [i]
                   (let [nederlandse-texte (-> @question-table (nth i) :target)]                   
                     [:tr {:key i :class (if (= 0 (mod i 2)) "even" "odd")}
                      [:th (- @answer-count i)]
                      [:th.speak [:button {:on-click #(speak/nederlands nederlandse-texte)} "🔊"]]
                      [:td.target [:a {:href (str "http://google.com/search?q=\"" nederlandse-texte "\"")} nederlandse-texte]]
                      [:td.source (-> @question-table (nth i) :source)]])))))]]]
   ] ;; div.main
  )

(def topic-name (r/atom ""))
(def specs-atom (r/atom nil))

(defn get-specs-from [content]
  (cond
    (and (keyword? (first content))
         (= :show-examples (first content)))
    (first (rest content))

    (and (keyword? (first content))
         (= :show-alternate-examples (first content)))
    (first (rest content))

    (keyword? (first content))
    (get-specs-from (rest content))

    (and (or (seq? content)
             (vector? content))
         (empty? content))
    nil

    (or (seq? content)
        (vector? content))
    (remove nil?
            (map get-specs-from
                 content))

    :else
    nil))

(defn get-specs [path]
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/specs.edn")))]
          (reset! specs-atom (-> response :body))
          (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
                (reset! specs-atom (->> response :body get-specs-from flatten (remove nil?) set vec))))))))

(defn find-matching-specs [major & [minor]]
  (get-specs (if minor
               (str major "/" minor)
               major))
  (->> @specs-atom
       (filter (fn [spec]
                 (not (empty? (filter #(= % major)
                                      (get spec :major-tags))))))
       (filter (fn [spec]
                 (or (nil? minor)
                     (not (empty? (filter #(= % minor)
                                          (get spec :minor-tags)))))))))

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      [:div.curr-major
       [:h4.normal
        "Welcome to nlquiz! Choose a topic to study."]
       [curriculum/tree path "curriculum full"]])))

(defn check-user-input []
  (let [current-input-value (get-input-value)]
    (log/debug (str "current-input: " current-input-value "; last-input-checked: " @last-input-checked))
    (if (not (= current-input-value @last-input-checked))
      (submit-guess current-input-value))
    (setup-timer)))

(defn setup-timer []
  (log/debug (str "starting timer.."))
  (js/setTimeout check-user-input 400))

(defn get-expression [major & [minor]]
  (log/debug (str "get-expression: major: " major))
  (log/debug (str "get-expression: minor: " minor))
  (setup-timer)
  (let [root-path (root-path-from-env)
        path (if minor
               (str major "/" minor)
               major)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          (reset! specs-atom (->> response :body get-specs-from flatten (remove nil?) set vec))
          (log/debug (str "specs-atom: " @specs-atom))
          (let [serialized-spec (-> @specs-atom shuffle first serialize str)]
            (let [response (<! (http/get generate-http {:query-params {"q" serialized-spec}}))]
              (reset! question-html (-> response :body :source))
              (log/debug (str "get-expression: setting got-it-right? to false."))
              (reset! got-it-right? false)
              (reset! show-answer (-> response :body :target))
              (reset! possible-correct-semantics
                      (->> (-> response :body :source-sem)
                           (map cljs.reader/read-string)
                           (map deserialize)))
              (reset! input-state "")
              (.focus (.getElementById js/document "input-guess"))))))))

(defn quiz-component []
  (load-linguistics)
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (reset! major-atom major)
    (reset! minor-atom minor)
    (reset! question-html spinner)
    (get-expression major minor)
    (fn []
      [:div.curr-major
       (quiz-layout)])))
