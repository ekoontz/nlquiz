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

(def answer-count (atom 0))
(def expression-index (atom 0))
(def ik-weet-niet-button-state (r/atom initial-button-state))
(def initial-state-is-enabled? true)
(def initial-button-state (if initial-state-is-enabled? "" "disabled"))
(def input-state (r/atom "disabled"))
(def possible-correct-semantics (r/atom nil))
(def question-table (r/atom nil))
(def question-html (r/atom spinner))
(def save-question (atom ""))
(def show-answer (r/atom nil))
(def show-praise-text (r/atom nil))
(def show-answer-display (r/atom "none"))
(def show-praise-display (r/atom "none"))
(def translation-of-guess (r/atom ""))
(def last-input-checked (atom ""))

;; <typing and timeouts>
;; typing and timeouts: trying to preserve
;; a good balance between responsiveness
;; and wasted effort:
(defn setup-timer [get-input-value-fn last-input-ref submit-guess-fn]
  (log/debug (str "starting timer.."))
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

;; <typing and timeouts>

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
      (reset! question-table
              (concat
               [{:source @save-question :target @show-answer}]
               (take 4 @question-table))))

    ;; else
    (do (set-input-value "")
        (show-possible-answer)))
  (.focus (.getElementById js/document "input-guess"))
  (.click (.getElementById js/document "input-guess")))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (when (seq guesses-semantics-set)
    (let [result
          (->> guesses-semantics-set
               (mapcat (fn [guess]
                         (->> correct-semantics-set
                              (map (fn [correct-semantics]
                                     ;; the guess is correct if and only if there is a semantic interpretation _guess_ of the guess where both of these are true:
                                     ;; - unifying _guess_ with some member _correct-semantics_ of the set of correct semantics is not :fail.
                                     ;; - this _correct_semantics_ is more general (i.e. subsumes) _guess_.
                                     (let [fails? (= :fail (u/unify correct-semantics guess))
                                           subsumes? (u/subsumes? correct-semantics guess)
                                           correct? (and (not fails?) subsumes?)
                                           fail-path (if fails? (select-keys (d/fail-path correct-semantics guess) [:path :arg1 :arg2]))]
                                       correct?))))))
               (remove #(= false %)))]
      (not (empty? result)))))

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> get-expression (in scope of quiz-layout, but called from within evaluate-guess,
;;                and only called if guess is correct)

(def placeholder "wat is dit in Nederlands?")
(def grammar (atom nil))
(def morphology (atom nil))

(defn get-input-value []
  (if (.getElementById js/document "input-guess")
    (trim (-> (.getElementById js/document "input-guess") .-value))))

(defn set-input-value []
  (set! (-> (.getElementById js/document "input-guess") .-value) ""))

(defn handle-correct-answer [correct-answer]
  (reset! got-it-right? true)
  (reset! save-question @question-html)
  (reset! question-html spinner)
  (set-input-value)
  (.focus (.getElementById js/document "other-input"))
  (reset! translation-of-guess "")
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
          (reset! last-input-checked guess-string)
          (if (= guess-string @show-answer)
            ;; user's answer was the same as the server-derived correct answer:
            (handle-correct-answer guess-string)

            ;; user's answer was not the same as the server-derived correct answer, 
            ;; but still might be correct: we have to analyze it to find out.
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
                    (if (= current-input-value guess-string)
                      (do
                        (doseq [en-spec specs]
                          (go (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                                    "/generate/en?spec=" (-> en-spec
                                                                                             dag-to-string))))]
                                ;; if user's already answered the question correctly, then
                                ;; @got-it-right? will be true. If true, then don't re-evaluate.
                                (if (false? @got-it-right?)
                                  ;; if false, then evaluate user's answer:
                                  (do
                                    (if (not (nil? (-> gen-response :body :sem deserialize)))
                                      (reset! translation-of-guess (-> gen-response :body :surface))) ;; TODO: concatentate rather than overwrite.
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
                :disabled @input-state
                :on-change (fn [input-element]
                             (let [guess-string (-> input-element .-target .-value)]
                               (log/info (str "not doing anything with input: " guess-string))
                               (.focus (.getElementById js/document "input-guess"))))
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

(defn get-expression [major & [minor]]
  (setup-timer get-input-value last-input-checked submit-guess)
  (let [root-path (root-path-from-env)
        path (if minor
               (str major "/" minor)
               major)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          (reset! specs-atom (->> response :body get-specs-from flatten (remove nil?) set vec))
          (let [serialized-spec (-> @specs-atom shuffle first serialize str)]
            (let [response (<! (http/get generate-http {:query-params {"q" serialized-spec}}))]
              (reset! question-html (-> response :body :source))
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
