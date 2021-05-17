(ns rads.food.server.scraper.yelp
  (:require [inflections.core :as inflections]
            [rads.food.server.config :as config]
            [medley.core :as medley]))

(defn- ->entity [yelp-business]
  (medley/map-keys #(keyword "yelp-businesses" (name %))
                   yelp-business))

(defn- api-request [client options]
  (let [{:keys [http-request-fn yelp-api-key]
         :or {yelp-api-key (get config/config :yelp-api-key)}} client
        {:keys [endpoint query-params]} options]
    (http-request-fn {:method :get
                      :url (str "https://api.yelp.com/v3/" endpoint)
                      :as :json
                      :headers {"Authorization" (str "Bearer " yelp-api-key)}
                      :query-params query-params})))

(defn get-match [client store]
  (let [{:stores/keys [name address]} store
        {:keys [street city state]} address]
    (-> (api-request client {:endpoint "businesses/matches"
                             :query-params {"name" name
                                            "address1" street
                                            "city" city
                                            "state" state
                                            "country" "US"}})
        :body :businesses first ->entity)))


(defn get-details [client yelp-id]
  (-> (api-request client {:endpoint (str "businesses/" yelp-id)})
      :body
      inflections/hyphenate-keys
      (select-keys [:id :price :review-count :rating :photos :categories])
      ->entity))
