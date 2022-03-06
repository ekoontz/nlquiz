(ns nlquiz.quiz
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
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
(def guess-text (r/atom nil))
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
(def not-answered-yet? (atom true))

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
  (reset! guess-text "")
  (.focus (.getElementById js/document "input-guess"))
  (js/setTimeout #(reset! show-answer-display "none") 3000)
  false)

(defn show-praise []
  (reset! show-praise-display "block")
  (reset! show-praise-text (-> praises shuffle first))
  (js/setTimeout #(reset! show-praise-display "none") 1000))

(def got-it-right? (atom false))
(def get-question-fn-atom (atom (fn []
                                  (log/error (str "should not get here! - get-question-fn was not set correctly.")))))

(def major-atom (atom nil))
(def minor-atom (atom nil))

(defn on-submit [e]
  (.preventDefault e)
  (speak/nederlands @show-answer)
  (if (= true @got-it-right?)
    (let [correct-answer @show-answer
          question @question-html]
      (if @minor-atom
        (get-expression @major-atom @minor-atom)
        (get-expression @major-atom))
      (show-praise)
      (swap! answer-count inc)
      (reset! got-it-right? false)
      (reset! question-table
              (concat
               [{:source question :target correct-answer}]
               (take 4 @question-table))))

    ;; else
    (show-possible-answer))
  (.focus (.getElementById js/document "input-guess"))
  (.click (.getElementById js/document "input-guess")))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (when (seq guesses-semantics-set)
    (log/info (str "guess count:  " (count guesses-semantics-set)))
    (log/info (str "correct count:" (count correct-semantics-set)))
    (let [result
          (->> guesses-semantics-set
               (mapcat (fn [guess]
                         (log/info (str "evaluating guess:          " (serialize guess)))
                         (->> correct-semantics-set
                              (map (fn [correct-semantics]
                                     (log/info (str "correct semantics option: " (serialize correct-semantics)))
                                     ;; the guess is correct if and only if there is a semantic interpretation _guess_ of the guess where both of these are true:
                                     ;; - unifying _guess_ with some member _correct-semantics_ of the set of correct semantics is not :fail.
                                     ;; - this _correct_semantics_ is more general (i.e. subsumes) _guess_.
                                     (let [fails? (= :fail (u/unify correct-semantics guess))
                                           subsumes? (u/subsumes? correct-semantics guess)
                                           correct? (and (not fails?) subsumes?)
                                           fail-path (if fails? (select-keys (d/fail-path correct-semantics guess) [:path :arg1 :arg2]))]
                                       (log/info (str "unifies?  " (not fails?)))
                                       (log/info (str "subsumes? " subsumes?))
                                       (log/info (str "correct?  " correct?))
                                       (when (not subsumes?)
                                         (log/info (str "=== guess was not correct because semantics has something that guess semantics lacks. ==="))
                                         (log/info (str "correct semantics: " (serialize correct-semantics)))
                                         (log/info (str "guess semantics:   " (serialize guess)))
                                         (log/info (str "correct subj: " (serialize (u/get-in correct-semantics [:subj]))))
                                         (log/info (str "guess   subj: " (serialize (u/get-in guess [:subj]))))
                                         (log/info (str "correct obj: " (serialize (u/get-in correct-semantics [:obj]))))
                                         (log/info (str "guess   obj: " (serialize (u/get-in guess [:obj]))))
                                         )
                                         
                                       (if (not correct?)
                                         (log/info (str "semantics of guess: '" @guess-text "' are NOT correct: "
                                                         (if fail-path (str "fail-path: " fail-path)) "; "
                                                         "subsumes? " (u/subsumes? correct-semantics guess)))
                                         (log/info (str "Found an interpretation of the guess '" @guess-text "' which matched the correct semantics.")))
                                       correct?))))))
               (remove #(= false %)))]
      (not (empty? result)))))

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> new-question-fn (in scope of quiz-layout, but called from within evaluate-guess,
;;                and only called if guess is correct)

(def placeholder "wat is dit in Nederlands?")
(def initial-guess-input-size (count placeholder))
(def guess-input-size (r/atom initial-guess-input-size))
(def grammar (atom nil))
(def morphology (atom nil))

(defn submit-guess [guess-text the-input-element parse-html possible-correct-semantics if-correct-fn nl-parses-atom]
  (if (empty? @possible-correct-semantics)
    (log/error (str "there are no correct answers for this question.")))
  (reset! guess-text the-input-element)
  (let [guess-string @guess-text]
    (log/info (str "submitting your guess: " guess-string))
    (reset! translation-of-guess spinner)
    (go (let [parse-response
              (->
               (<! (http/get (str (language-server-endpoint-url)
                                  "/parse-start/nl?q=" guess-string)))
               :body decode-parse)
              nl-parses (parses parse-response @grammar @morphology @guess-text)
              specs (->> nl-parses
                         (map serialize)
                         set
                         vec
                         (map deserialize)
                         (map tr/nl-to-en-spec)
                         remove-duplicates)

              local-sem  (->> nl-parses
                              (map #(u/get-in % [:sem])))
              ]
          (reset! translation-of-guess "")
          (doseq [en-spec specs]
            (log/debug (str "en-spec to be used for /generate/en: " en-spec))
            (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                  "/generate/en?spec=" (-> en-spec
                                                                           dag-to-string))))]
              (log/debug (str "gen-response: " (-> gen-response :body :surface)))
              (reset! translation-of-guess (-> gen-response :body :surface)))) ;; TODO: concatentate rather than overwrite.
          ;; Show english translation of whatever
          ;; the person said, if it could be parsed as Dutch and
          ;; translated to English:
          ;; @not-answered-yet? *was* true when we fired off the request, but might not be true anymore,
          ;; if the user correctly answered this question, and another guess is being submitted
          ;; because they're still typing after that, so prevent re-evaluation in that case.
          (when (not (= guess-string @guess-text))
            (log/debug (str "guess-text changed: it's now: '" @guess-text "', so "
                           "we won't bother updating with responding to older "
                           "user guess: '" guess-string "'.")))
          (when (and @not-answered-yet?
                     (= guess-string @guess-text))
            (log/debug (str "*LOCAL* semantics of guess: " local-sem))
            (if (evaluate-guess local-sem
                                @possible-correct-semantics)
              ;; got it right!
              (if-correct-fn guess-string)

              ;; got it wrong:
              (do (log/info (str "sorry, your guess: '" guess-string "' was not right.")))))))))

(defn quiz-layout []
  ;; TODO: move this (go) somewhere else: (quiz-layout) should strictly be
  ;; just the layout of the page with all of the atoms.
  (go
    (let [grammar-response (<! (http/get (str (language-server-endpoint-url)
                                              "/grammar/nl")))
          morphology-response (<! (http/get (str (language-server-endpoint-url)
                                                 "/morphology/nl")))]
      (reset! grammar (-> grammar-response :body decode-grammar))
      (reset! morphology (-> morphology-response :body decode-morphology))))
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
                :size @guess-input-size
                :value @guess-text
                :disabled @input-state
                :on-change (fn [input-element]
                             (when @not-answered-yet?
                               (reset! input-state "disabled")
                               (reset! not-answered-yet? false)                               
                               (log/debug (str "current guess size: " (-> input-element .-target .-value count)))
                               (reset! guess-input-size (max initial-guess-input-size (+ 0 (-> input-element .-target .-value count))))
                               (submit-guess guess-text
                                             (-> input-element .-target .-value)
                                             parse-html
                                             possible-correct-semantics
                                             ;; function called if the user guessed correctly:
                                             (fn [correct-answer]
                                               (.focus (.getElementById js/document "other-input"))
                                               (reset! guess-text "")
                                               (reset! not-answered-yet? false)
                                               (reset! got-it-right? true)
                                               (reset! show-answer correct-answer)
                                               (reset! translation-of-guess "")
                                               (if (.-requestSubmit (.getElementById js/document "quiz"))
                                                 (.requestSubmit (.getElementById js/document "quiz"))
                                                 (.dispatchEvent (.getElementById js/document "quiz")
                                                                 (new js/Event "submit" {:cancelable true})))
                                               (.focus (.getElementById js/document "input-guess"))
                                               (reset! show-answer correct-answer)
                                               nl-parses-atom))
                               (reset! not-answered-yet? true)
                               (reset! input-state "")))}]]] ;; div.guess

     [:div.english @translation-of-guess]

     [:div.dontknow
      [:input {:class "weetniet" :type "submit" :value "Ik weet het niet"
               :disabled @ik-weet-niet-button-state}]
      [:button {:class "weetniet"
                :on-click #(do
                             ;; this switching-around of focus is necessary
                             ;; for iOS Safari if I recall.
                             (.focus (.getElementById js/document "other-input"))
                             (reset! guess-text "")
                             (reset! translation-of-guess "")
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
         (not (= :show-examples (first content))))
    (get-specs-from (rest content))

    (and (keyword? (first content))
         (= :show-examples (first content)))
    (first (rest content))

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

(defn get-expression [major & [minor]]
  (log/debug (str "get-expression: major: " major))
  (log/debug (str "get-expression: minor: " minor))
  (let [root-path (root-path-from-env)
        path (if minor
               (str major "/" minor)
               major)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          (reset! specs-atom (->> response :body get-specs-from flatten (remove nil?) set vec))
          (log/debug (str "specs-atom: " @specs-atom))
          (let [serialized-spec (-> @specs-atom shuffle first dag_unify.serialization/serialize str)]
            (let [response (<! (http/get generate-http {:query-params {"q" serialized-spec}}))]
              (reset! question-html (-> response :body :source))
              (reset! show-answer (-> response :body :target))
              (reset! possible-correct-semantics
                      (->> (-> response :body :source-sem)
                           (map cljs.reader/read-string)
                           (map dag_unify.serialization/deserialize)))
              (reset! not-answered-yet? true)
              (reset! input-state "")
              (.focus (.getElementById js/document "input-guess"))))))))

(defn quiz-component []
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (reset! major-atom major)
    (reset! minor-atom minor)    
    (get-expression major minor)
    (fn []
      [:div.curr-major
       (quiz-layout)])))



