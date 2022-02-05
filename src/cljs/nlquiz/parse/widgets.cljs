(ns nlquiz.parse.widgets)

(defn en-widget [trees lexemes rules]
  [:div.en_widget
   "🇬🇧 "
   @trees
   @lexemes
   @rules])

(defn nl-widget [trees lexemes rules]
  [:div.nl_widget
   "🇳🇱 "
   @trees
   @lexemes
   @rules])

