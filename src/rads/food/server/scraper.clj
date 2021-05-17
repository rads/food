(ns rads.food.server.scraper
  (:require [clj-http.client :as http]
            [etaoin.api :as e]
            [rads.food.server.model :as model]
            [rads.food.server.scraper.doordash :as doordash]
            [rads.food.server.scraper.yelp :as yelp]
            [tick.alpha.api :as t]
            [rads.food.server.db :as db]
            [medley.core :as medley]
            [taoensso.timbre :as log]
            [rads.food.server.config :as config]))

(defn scraper [& {:keys [driver ds yelp-api-key] :as opts}]
  (->> {:http-request-fn http/request
        :driver (or driver (e/chrome))
        :ds (or ds (db/datasource))
        :last-login (atom nil)
        :yelp-api-key (or yelp-api-key (:yelp-api-key config/config))}
       (medley/remove-vals nil?)))

(defn refresh-yelp-details [scraper stores]
  (let [{:keys [ds]} scraper]
    (doseq [store stores]
      (try
        (let [store' (merge store (doordash/get-store-details scraper store))
              _ (println store')
              yelp-business (yelp/get-match scraper store')
              _ (println yelp-business)
              yelp-id (:yelp-businesses/id yelp-business)
              yelp-details (yelp/get-details scraper yelp-id)
              store'' (assoc store' :yelp-id yelp-id)
              updated-at (t/now)]
          (model/upsert-yelp-businesses! ds [yelp-details] updated-at)
          (model/upsert-stores! ds [store''] updated-at))
        (catch Exception e
          (log/error (ex-message e)))))))

(defn refresh-store-open-status [scraper]
  (let [{:keys [ds]} scraper
        updated-at (t/now)]
    (doordash/login scraper)
    (doordash/scroll-until-closed scraper)
    (let [stores (doordash/parse-stores scraper)]
      (model/upsert-stores! ds stores updated-at)
      (model/set-stores-closed! ds updated-at))))

(defn stop [scraper]
  (e/stop-driver (:driver scraper)))

(comment
  (def scraper (scraper))
  (doordash/login scraper)
  (doordash/scroll-until-closed scraper)
  (def st (model/get-stores (:ds scraper)))
  st
  (refresh-yelp-details scraper st)
  (refresh-store-open-status scraper))