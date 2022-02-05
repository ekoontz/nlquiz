(ns nlquiz.parse.widgets)

(defn en-widget [trees lexemes rules]
  [:div.en_widget
   "EN"
   @trees
   @lexemes
   @rules])

(defn nl-widget [trees lexemes rules]
  [:div.nl_widget
   "NL"
   @trees
   @lexemes
   @rules])

