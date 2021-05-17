(ns ^:figwheel-hooks rads.food.browser
  (:require [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [reagent.dom :as reagent]
            [rads.food.browser.components :as components]
            [rads.food.browser.routes :as routes]
            [cljs.reader :as edn]))

(def container
  (js/document.getElementById "app"))

(defn ^:after-load render []
  (reagent/render [components/root] container))

(rf/reg-event-db
  ::new-route
  (fn [db [_ route]]
    (assoc db :route route)))

(defn- init-router! []
  (accountant/configure-navigation!
    {:nav-handler #(rf/dispatch [::new-route (bidi/match-route routes/routes %)])
     :path-exists? #(bidi/match-route routes/routes %)})
  (accountant/dispatch-current!))

(rf/reg-event-db
  :add-bootstrap-data
  (fn [db [_ bootstrap-data]]
    (merge db bootstrap-data)))

(defn ^:export -main [bootstrap-str]
  (let [bootstrap-data (edn/read-string bootstrap-str)]
    (enable-console-print!)
    (init-router!)
    (rf/dispatch-sync [:add-bootstrap-data bootstrap-data])
    (render)))
