(ns nlquiz.quiz
  (:require
   [cljs-http.client :as http]
   [nlquiz.log :as log]
   [cljs.core.async :refer [<!]]
   [clojure.string :refer [trim]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [dag_unify.serialization :refer [deserialize serialize]]
   [menard.parse :as parse]
   [menard.translate.spec :as tr]
   [nlquiz.constants :refer [spinner]]
   [nlquiz.curriculum :as curriculum]
   [nlquiz.menard :refer [dag-to-string decode-grammar decode-morphology
                          decode-parses parses remove-duplicates]]
   [nlquiz.speak :as speak]
   [nlquiz.timer :refer [setup-timer]]
   [reagent.core :as r]
   [reagent.session :as session])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource language-server-endpoint-url]]))


;; we create a timer loop:
;;   (setup-timer get-input-value submit-guess)
;; which calls (defn submit-guess) below.
;; 

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
(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "goed gedaan! 🚲"
              "mooi!🌷"
              "oké! 🌷"
              "prachtig.🧇"
              "precies!😁"
              "prima!!😎 "])

(def parse-http (str (language-server-endpoint-url) "/parse"))
(def generate-http (str (language-server-endpoint-url) "/generate/nl"))

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
(def get-question-fn-atom
  (atom (fn []
          (log/error (str "should not get here! - get-question-fn was not set correctly.")))))

(def major-atom (atom nil))
(def minor-atom (atom nil))
(def show-this-many-answers 5)

(defn add-new-row [row]
  (reset! question-table
          (concat
           row
           (take (- show-this-many-answers 1) @question-table))))

(defn volgende [& [e]]
  (when e
    (.preventDefault e))
  (speak/nederlands @show-answer)
  (swap! answer-count inc)
  ;; Show only last 5 questions answered:
  (add-new-row [{:source @question-html :target @show-answer}])
  (get-next-expression))

(defn get-next-expression []
  (if @minor-atom
    (get-expression @major-atom @minor-atom)
    (get-expression @major-atom)))

(defn on-submit [e]
  (log/debug (str "on-submit!"))
  (.preventDefault e)
  (speak/nederlands @show-answer)
  (cond
    (= true @got-it-right?)
    (let [correct-answer @show-answer
          question @question-html]

      ;; get the new expression now to save time, since this takes awhile..
      (get-next-expression)
      (show-praise)
      (swap! answer-count inc)

      ;; Show only last 5 questions answered:
      (add-new-row [{:source @save-question :target @show-answer}]))
    :else
    (do (set-input-value)
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

;; setup-timer -> submit-guess -> evaluate-guess
;;             -> get-expression (in scope of quiz-layout, but called from within evaluate-guess,
;;                and only called if guess is correct)

(def placeholder "wat is dit in het Nederlands?")
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

;; TODO: split this huge (submit-guess) function into smaller, readable pieces:
(defn submit-guess [guess-string]
  (if (empty? @possible-correct-semantics)
    (do (log/error (str "there are no correct answers for this question."))
        (volgende))
    ;; else, there are some correct answers:
    (let [guess-string (if guess-string (trim guess-string))]
      (if (not (empty? guess-string))
        (do
          (if (= (clojure.string/lower-case guess-string) (clojure.string/lower-case (str @show-answer)))
            ;; case 1: user's answer was the same as the server-derived correct answer. We can avoid doing
            ;; expensive parsing of guess-string to see the answer could have been correct.
            (handle-correct-answer guess-string)

            ;; case 2: user's answer was not the same as the server-derived correct answer, 
            ;; but still might be correct: we have to analyze it to find out.
            (do
              (reset! translation-of-guess spinner)
              (or (seq @curriculum/model-name-atom)
                  (do
                    (log/error (str "curriculum/model-name-atom not set: resetting to " curriculum/default-model-name))
                    (reset! curriculum/model-name-atom curriculum/default-model-name)))
              (if (empty? (deref curriculum/model-name-atom))

                ;; TODO: handle this somehow..
                (log/error (str "No model was found!!")) 

                (log/debug (str "doing nl parsing with model: '" (deref curriculum/model-name-atom) "'")))
              (go (let [parse-response
                        (->
                         (<! (http/get (str (language-server-endpoint-url)
                                            "/parse-start/nl?q=" guess-string "&model=" (deref curriculum/model-name-atom))))
                         :body decode-parses)
                        debug (if (seq parse-response)
                                (log/debug (str "found one or more parses for: "
                                                "'" guess-string "'"))
                                (log/debug (str "no parses for '" guess-string "' found in response.")))
                        nl-parses
                        (->>
                         parse-response
                         (mapcat (fn [each-parse]
                                   (parses each-parse @grammar @morphology guess-string))))
                        debug (log/debug (if (empty? nl-parses)
                                           (str "no parses found.")
                                           (str "at least one parse found.")))
                        specs (->> nl-parses
                                   (map serialize)
                                   (map deserialize)
                                   (map tr/nl-to-en-spec)
                                   remove-duplicates)

                        debug (log/debug (if (empty? specs)
                                           (str "no specs found.")
                                           (str "at least one spec found.")))
                        local-sem  (->> nl-parses
                                        (map #(u/get-in % [:sem])))
                        current-input-value (get-input-value)]
                    (when (empty? specs)
                      (do (log/debug (str "couldn't parse: '" guess-string "'"))
                          (reset! translation-of-guess (str "'" guess-string "'..?"))))

                    (if (not (= current-input-value guess-string))
                      (log/debug (str "input changed: will not try to generate english for input: " guess-string " since it's now changed to: " current-input-value)))

                    (if (= current-input-value guess-string)
                      (do
                        (doseq [en-spec specs]
                          (log/debug (str "going to try to generate english with spec: "
                                         en-spec))
                          (go (let [gen-response (<! (http/get (str (language-server-endpoint-url)
                                                                    "/generate/en?spec=" (-> en-spec
                                                                                             dag-to-string)
                                                                    "&model=" (deref curriculum/model-name-atom))))]
                                ;; if user's already answered the question correctly, then
                                ;; @got-it-right? will be true. If true, then don't re-evaluate.
                                (if (false? @got-it-right?)
                                  ;; if false, then evaluate user's answer:
                                  (do


                                    (if (not (nil? (-> gen-response :body :sem deserialize)))
                                      ;; we got a (or another) translation to English, so concatenate it to the
                                      ;; any existing translations:
                                      (let [new-guess-response (-> gen-response :body :surface)
                                            update-guess-text
                                            (if (= @translation-of-guess [:i {:class "fas fa-stroopwafel fa-spin"}])
                                              ;; no exisiting answer (just a spinning stroopwafel), 
                                              ;; so simply replace with this single new translation:
                                              (-> gen-response :body :surface)

                                              ;; else, there was already a guess, so concatenate it to the end:
                                              ;; TODO: sort them by whether they have a '_' in them
                                              ;; (those go to the end, since they aren't as accurate).
                                              (str @translation-of-guess ", " guess-response))]
                                        (reset! translation-of-guess update-guess-text)))

                                    (log/debug (str "semantics: " local-sem "; possible correct-semantics: " @possible-correct-semantics))
                                    (if @translation-of-guess
                                      (log/debug (str "non-empty english translation: " @translation-of-guess)))
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

(defn do-a-submit []
  (if (.-requestSubmit (.getElementById js/document "quiz"))
    (.requestSubmit (.getElementById js/document "quiz"))
    (.dispatchEvent (.getElementById js/document "quiz")
                    (new js/Event "submit" {:cancelable true}))))

(defn reset-button []
  ;; this switching-around of focus is necessary
  ;; for iOS Safari if I recall.
  (.focus (.getElementById js/document "other-input"))
  (reset! translation-of-guess "")
  (set-input-value)  
  (.focus (.getElementById js/document "input-guess")))

(defn quiz-layout []
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]
   [:div.question-and-guess
    [:form#quiz {:on-submit on-submit}
     [:div.guess
      [:div.question @question-html]
      [:div

       ;; this is a blank input that we use to try to fool the
       ;; speech input into focusing on to get out of
       ;; speech input mode:
       [:input {:type "text" :size "1"
                :input-mode "none"
                :id "other-input"}]

       [:input {:type "text"
                :size (* 1 (count placeholder))
                :placeholder placeholder
                :id "input-guess"
                :input-mode "text"
                :autoComplete "off"
                :disabled @input-state
                ;; Note that we don't do anything here:
                ;;  it's the timer (the use of (setup-timer) in
                ;;  (defn get-expression) that does it.
                :on-change (fn [input-element])
                }]]] ;; /div.guess

     [:div.english @translation-of-guess]

     [:div.dontknow
      [:button {:class "weetniet" :title "Ik weet het niet"
               :disabled @ik-weet-niet-button-state} "⁉️"]

      ;; 'reset' button
      [:button {:class "weetniet" :title "reset"
                :on-click #(do
                             (reset-button)
                             (.preventDefault %))} "❌"]

      ;; 'next' button
      [:button {:class "weetniet" :title "volgende"
                :on-click #(volgende %)
                } "⏩"]
      
      ] ;; /div.dontknow

     ] ;; /form#quiz

    ] ;; /div.question-and-guess

      
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

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      [:div.curr-major
       [:h4.normal
        "Welcome to nlquiz! Choose a topic to study."]
       [curriculum/tree path "curriculum full"]])))

(defn get-expression [major & [minor]]
  (let [root-path (root-path-from-env)
        path (if minor
               (str major "/" minor)
               major)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum/" path ".edn")))]
          (reset! specs-atom (->> response :body get-specs-from flatten (remove nil?) set vec))
          (let [spec (-> @specs-atom shuffle first)
                model (or (u/get-in spec [:model]) "complete")
                spec (dissoc spec :model)
                serialized-spec (-> spec serialize str)]
            (let [response (<! (http/get generate-http {:query-params {"model" model
                                                                       "q" serialized-spec}}))]
              (log/debug (str "nlquiz.quiz: get-expression: got response: " (-> response :body)))
              (reset! question-html (-> response :body :source))
              (reset! got-it-right? false)
              (reset! show-answer (-> response :body :target))
              (reset! possible-correct-semantics
                      (->> (-> response :body :source-sem)
                           (map cljs.reader/read-string)
                           (map deserialize)))
              (reset! input-state "")
              (reset-button)
              (.focus (.getElementById js/document "input-guess"))))))))

(defn quiz-component []
  (load-linguistics)
  (setup-timer get-input-value submit-guess)
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
