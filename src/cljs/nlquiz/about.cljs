(ns nlquiz.about
  (:require
   [nlquiz.speak :as speak]))

(defn component []
  (fn []
    [:div {:style {:float "left" :margin "0.5em"}}
     [:h3 "About nlquiz"]
     [:p "This is a way to learn some vocabulary and short phrases in Dutch."]
     [:p "Choose a topic in the curriculum to practice with that type of phrase. You'll get English phrases of that type, which you should try to translate to Dutch."]
     [:p "If you don't know how to translate a phrase, just hit the " [:button.weetniet "Ik weet het niet"] " ('I don't know') button, and you'll be shown a possible translation."]
     [:p "If you see a " [:button.speak {:on-click #(speak/nederlands "hallo")} "🔊"] " button next to a phrase, you can click it to hear the pronunciation of that phrase, for example:"]
     (let [i 0]
       [:div
        [:table
         [:tbody
          [:tr {:key i
                :class (if (= 0 (mod i 2)) "even" "odd")}
           [:th (+ i 1)]
           [:th.speak [:button.speak {:on-click #(speak/nederlands "de kat")} "🔊"]]
           [:td.target "de kat"]
           [:td.source "the cat"]]]]])

     [:p "You may need to use headphones to hear the sound on mobile devices; I'm not sure yet why this is sometimes required."]
     [:p "⚠ Caution⚠ There are likely many errors because I am only a beginner myself at learning Dutch. Not to be used as a substitute for a real class or learning materials."]
     [:p "The entire software stack is 100% Free Software/Open Source, though with various licenses according to its various origins. To learn more about "
      " the software stack, start " [:a {:href "https://github.com/ekoontz/nlquiz"} "here"] "."]

     [:p "Problems or questions? Please create an issue on " [:a {:href "https://github.com/ekoontz/nlquiz/issues"} "github"]
      " or " [:a {:href "mailto:ekoontz@hiro-tan.org"} "email me."]]]))

