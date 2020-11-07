(ns nlquiz.quiz
  (:require
   [cljs-http.client :as http]
   [cljslog.core :as log]
   [cljs.core.async :refer [<!]]
   [dag_unify.core :as u]
   [dag_unify.diagnostics :as d]
   [nlquiz.constants :refer [root-path]]
   [nlquiz.curriculumcontent :refer [guides]]
   [nlquiz.speak :as speak]
   [reagent.core :as r]
   [reagent.session :as session])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [nlquiz.handler :refer [root-path-from-env inline-resource]]))

(def answer-count (atom 0))
(def expression-index (atom 0))
(def guess-text (r/atom nil))
(def ik-weet-niet-button-state (r/atom initial-button-state))
(def initial-state-is-enabled? true)
(def initial-button-state (if initial-state-is-enabled? "" "disabled"))
(def input-state (r/atom "disabled"))
(def possible-correct-semantics (r/atom nil))
(def question-table (r/atom nil))
(def question-html (r/atom nil))
(def semantics-of-guess (r/atom nil))
(def show-answer (r/atom nil))
(def show-praise-text (r/atom nil))
(def show-answer-display (r/atom "none"))
(def show-praise-display (r/atom "none"))
(def translation-of-guess (r/atom nil))

(def praises ["dat is leuk! 🚲"
              "geweldig!🇳🇱"
              "goed gedaan! 🚲"
              "mooi!🌷"
              "oké! 🌷"
              "prachtig.🧇"
              "precies!😁"
              "prima!!😎 "])

(defn new-question [specification-fn]
  (go (let [response (<! (specification-fn))]
        (log/debug (str "new-expression response: " reponse))
        (log/debug (str "one possible correct answer to this question is: '"
                        (-> response :body :target) "'"))
        (reset! question-html (-> response :body :source))
        (reset! guess-text "")
        (reset! show-answer (-> response :body :target))
        (reset! show-answer-display "none")
        (reset! input-state "")
        (reset! possible-correct-semantics
                (->> (-> response :body :source-sem)
                     (map cljs.reader/read-string)
                     (map dag_unify.serialization/deserialize)))
        (.focus (.getElementById js/document "input-guess")))))

(defn show-possible-answer []
  (reset! show-answer-display "block")
  (reset! guess-text "")
  (.focus (.getElementById js/document "input-guess"))  
  (js/setTimeout #(reset! show-answer-display "none") 1000)
  false)

(defn show-praise []
  (reset! show-praise-display "block")
  (reset! show-praise-text (-> praises shuffle first))
  (js/setTimeout #(reset! show-praise-display "none") 1000))

(def got-it-right? (atom false))
(def get-question-fn-atom (atom (fn [] (log/error (str "should not get here! - get-question-fn was not set correctly.")))))

(defn on-submit [e]
  (.preventDefault e)
  (speak/nederlands @show-answer)
  (if (= true @got-it-right?)
    (do
      (show-praise)
      (swap! answer-count inc)
      (reset! got-it-right? false)
      (reset! question-table
              (concat
               [{:source @question-html :target @show-answer}]
               (take 4 @question-table)))
      (new-question @get-question-fn-atom))
    ;; else
    (show-possible-answer))
    
  (.focus (.getElementById js/document "input-guess"))
  (.click (.getElementById js/document "input-guess")))

;; quiz-layout -> submit-guess -> evaluate-guess
;;             -> new-question-fn (in scope of quiz-layout, but called from within evaluate-guess, and only called if guess is correct)
(defn quiz-layout [get-question-fn & [question-type-chooser-fn]]
  [:div.main
   [:div#answer {:style {:display @show-answer-display}} @show-answer]
   [:div#praise {:style {:display @show-praise-display}} @show-praise-text]       
   (if question-type-chooser-fn (question-type-chooser-fn get-question-fn))
   [:div.question-and-guess
    [:form#quiz {:on-submit on-submit}
     [:div.guess
      [:div.question
       @question-html]
      [:div
       [:input {:type "text"
                :placeholder "wat is dit in Nederlands?"
                :id "input-guess"
                :autoComplete "off"
                :size 25
                :value @guess-text
                :disabled @input-state
                :on-change (fn [input-element]
                             (submit-guess guess-text
                                           (-> input-element .-target .-value)
                                           parse-html
                                           semantics-of-guess
                                           possible-correct-semantics
                                           
                                           ;; function called if the user guessed correctly:
                                           (fn [correct-answer]
                                             (reset! got-it-right? true)
                                             (reset! get-question-fn-atom get-question-fn)
                                             (reset! show-answer correct-answer)
                                             (if (.-requestSubmit (.getElementById js/document "quiz"))
                                               (.requestSubmit (.getElementById js/document "quiz"))
                                               (.dispatchEvent (.getElementById js/document "quiz") (new js/Event "submit" {:cancelable true})))
                                             (reset! input-state "disabled")
                                             (reset! show-answer correct-answer))))}]]]

     [:div.english @translation-of-guess]

     [:div.dontknow
      [:input {:class "weetniet" :type "submit" :value "Ik weet het niet"
               :disabled @ik-weet-niet-button-state}] ;; </div.question-and-guess>
      [:button {:class "weetniet" :style {:float :right}
                :on-click #(do (reset! guess-text "")
                               (.preventDefault %))} "Reset"]]]]
   [:div.answertable
    [:table
     [:tbody
      (doall
       (->> (range 0 (count @question-table))
            (map (fn [i]
                   [:tr {:key i :class (if (= 0 (mod i 2)) "even" "odd")}
                    [:th (- @answer-count i)]
                    [:th.speak [:button {:on-click #(speak/nederlands (-> @question-table (nth i) :target))} "🔊"]]
                    [:td.target (-> @question-table (nth i) :target)]
                    [:td.source (-> @question-table (nth i) :source)]
                    ]))))]]]  
   ] ;; div.main
  )

(defn expression-list-quiz-component [get-question-fn chooser]
  (new-question get-question-fn)
  #(quiz-layout get-question-fn chooser))

(defn evaluate-guess [guesses-semantics-set correct-semantics-set]
  (let [result
        (->> guesses-semantics-set
             (mapcat (fn [guess]
                       (->> correct-semantics-set
                            (map (fn [correct-semantics]
                                   ;; the guess is correct if and only if there is a semantic interpretation _guess_ of the guess where both of these are true:
                                   ;; - unifying _guess_ with some member _correct-semantics_ of the set of correct semantics is not :fail.
                                   ;; - this _correct_semantics_ is more general (i.e. subsumes) _guess_.
                                   (let [correct? (and (not (= :fail (u/unify correct-semantics guess)))
                                                       (u/subsumes? correct-semantics guess))]
                                     (if (not correct?)
                                       (log/info (str "semantics of guess: '" @guess-text "' are NOT correct: "
                                                      "fail-path: "
                                                      (d/fail-path correct-semantics guess) "; "
                                                      "subsumes? " (u/subsumes? correct-semantics guess)))
                                       (log/info (str "Found an interpretation of the guess '" @guess-text "' which matched the correct semantics.")))
                                     correct?))))))
             (remove #(= false %)))]
    (not (empty? result))))

(defn submit-guess [guess-text the-input-element parse-html semantics-of-guess possible-correct-semantics if-correct-fn]
  (log/debug (str "submitting guess: " guess-text))
  (reset! input-state "disabled")
  (reset! guess-text the-input-element)
  (reset! input-state "")
  (let [guess-string @guess-text]
    (log/debug (str "submitting your guess: " guess-string))
    (go (let [response (<! (http/get (str root-path "parse/nl")
                                     {:query-params {"q" guess-string}}))]
          (log/debug (str "parse response: " response))
          (log/debug (str "semantics of guess: " semantics-of-guess))
          (reset! semantics-of-guess
                  (->> (-> response :body :sem)
                       (map cljs.reader/read-string)
                       (map dag_unify.serialization/deserialize)))
          (reset! translation-of-guess nil)
          (if (evaluate-guess @semantics-of-guess
                              @possible-correct-semantics)
            ;; got it right!
            (do (reset! translation-of-guess "")
                (if-correct-fn guess-string))

            ;; got it wrong: show english translation of whatever
            ;; the person said, if it could be parsed as Dutch and
            ;; translated to English:
            (if (not (= "_" (-> response :body :english)))
              (reset! translation-of-guess
                      (-> response :body :english))))))))

(def topic-name (r/atom ""))
(def curriculum-atom (r/atom nil))
(def specs-atom (r/atom
                 (-> "public/edn/specs.edn"
                     inline-resource 
                     cljs.reader/read-string)))

(defn get-curriculum []
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/curriculum.edn")))]
          (reset! curriculum-atom (-> response :body))))))

(defn get-name-or-children [node]
  (cond (and (:child node) (:name node)) 
        (cons node (map get-name-or-children (:child node)))
        (:child node)
        (map get-name-or-children (:child node))
        true node))

(defn get-title-for [major & [minor]]
  (->> @curriculum-atom
       (map get-name-or-children)
       flatten (filter #(or (and minor (= (:href %) (str major "/" minor)))
                            (and (nil? minor) (= (:href %) major))))
       (map :name) first))

(defn get-specs []
  (let [root-path (root-path-from-env)]
    (go (let [response (<! (http/get (str root-path "edn/specs.edn")))]
          (reset! specs-atom (-> response :body))))))

(defn find-matching-specs [major & [minor]]
  (get-specs)
  (->> @specs-atom
       (filter (fn [spec]
                 (not (empty? (filter #(= % major)
                                      (get spec :major-tags))))))
       (filter (fn [spec]
                 (or (nil? minor)
                     (not (empty? (filter #(= % minor)
                                          (get spec :minor-tags)))))))))

(defn tree-node [i node selected-path]
  [:div {:key (str "node-" (swap! i inc))}
   [:h1
    (if (:href node)
      (let [url (str "/nlquiz/curriculum/" (:href node))]
        (if (and (not (empty? selected-path))
                 (= url selected-path))
         (reset! topic-name (:name node)))
        [:a {:class (if (= url selected-path)
                      "selected" "")
             :href (str "/nlquiz/curriculum/" (:href node))}
         (:name node)])
      (:name node))]
   (doall
    (map (fn [child]
           (tree-node i child selected-path))
         (:child node)))])

(defn tree [selected-path]
  (get-curriculum)
  (let [i (atom 0)]
    (fn []
      [:div.curriculum
       (doall (map (fn [node]
                     (tree-node i node selected-path))
                   @curriculum-atom))])))

(defn quiz []
  (fn []
    (let [routing-data (session/get :route)
          path (session/get :path)]
      [:div.curr-major
       [:h4.normal
        "Welcome to nlquiz! Choose a topic to study."]
       [tree path "curriculum full"]])))

(defn get-expression [major & [minor]]
  (log/debug (str "get-expression: major: " major))
  (log/debug (str "get-expression: minor: " minor))
  (fn []
    (let [specs (find-matching-specs major minor)
          error (if (empty? specs)
                  (log/error (str "no specs found matching major=" major " and minor=" minor "!!")))
          spec (-> specs shuffle first)
          serialized-spec (-> spec dag_unify.serialization/serialize str)]
      (log/debug (str "generating with spec: " spec))
      (http/get (str root-path "generate") {:query-params {"q" serialized-spec}}))))

(defn quiz-component []
  (get-curriculum)
  (let [routing-data (session/get :route)
        path (session/get :path)
        major (get-in routing-data [:route-params :major])
        minor (get-in routing-data [:route-params :minor])]
    (new-question (get-expression major minor))
    (fn []
      [:div.curr-major
       (quiz-layout (get-expression major minor))
       (cond (and major minor guides (get guides major)
                  (-> guides (get major) (get minor)))
             [:div.guide
              [:div.h4
               [:h4 (get-title-for major minor)]]
              [:div.content [(-> guides (get major) (get minor))]]]

             (and major guides (fn? (get guides major)))
             [:div.guide
              [:div.h4
               [:h4 (get-title-for major)]]
              [:div.content [(-> guides (get major))]]]

             (and major guides (fn? (-> guides (get major) :general)))
             [:div.guide
              [(-> guides (get major) :general)]]
             true "")])))
