(ns rads.food.server.endpoints
  (:require [rads.food.server.model :as model]))

(def endpoints
  {:stores (fn [{:keys [ds] :as params}]
             (model/get-stores ds))})