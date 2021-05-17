(ns user
  (:require [figwheel.main.api :as figwheel]))

(defonce system (atom nil))

(defn dev []
  (require 'rads.food.server.web)
  (figwheel/start {:mode :serve} "dev")
  (swap! system assoc :web
         ((resolve 'rads.food.server.web/start)
          ((resolve 'rads.food.server.web/web) :reload true))))

(defn cljs []
  (figwheel/cljs-repl "dev"))

(defn stop []
  (require 'rads.food.server.web)
  (figwheel/stop "dev")
  (swap! system update :web
         (resolve 'rads.food.server.web/stop)))