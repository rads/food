(ns rads.food.server.model
  (:require [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [honey.sql :as hsql]
            [inflections.core :as inflections]
            [medley.core :as medley]
            [clj-uuid :as uuid]
            [rads.food.server.db :as db]
            [clojure.set :as set]))

(defn- ->entities [db-results]
  (sequence (comp (map inflections/hyphenate-keys)
                  (map #(medley/remove-vals nil? %)))
            db-results))

(defn get-yelp-business-photos [ds yelp-business-ids]
  (let [query-map {:select [:*]
                   :from :yelp_business_photos
                   :where [:in :yelp_id yelp-business-ids]}]
    (->entities (jdbc/execute! ds (hsql/format query-map)))))

(defn get-yelp-business-categories [ds yelp-business-ids]
  (let [query-map {:select [:*]
                   :from :yelp_business_categories
                   :where [:in :yelp_id yelp-business-ids]}]
    (->entities (jdbc/execute! ds (hsql/format query-map)))))

(defn- add-store-relationships [ds stores]
  (when (seq stores)
    (let [yelp-business-ids (map :stores/yelp-id stores)
          photos (-> (get-yelp-business-photos ds yelp-business-ids)
                     (set/index [:yelp-business-photos/yelp-id]))
          categories (-> (get-yelp-business-categories ds yelp-business-ids)
                         (set/index [:yelp-business-categories/yelp-id]))]
      (map (fn [{:stores/keys [yelp-id] :as store}]
             (merge store
                    (->> #:stores{:photos (->> (get photos {:yelp-business-photos/yelp-id yelp-id})
                                               (sort-by :yelp-business-photos/rank))
                                  :categories (->> (get categories {:yelp-business-categories/yelp-id yelp-id})
                                                   (sort-by :yelp-business-categories/rank))}
                         (medley/remove-vals nil?))))
           stores))))

(defn get-stores [ds]
  (let [query-map {:select [:*]
                   :from [[:stores :s]]
                   :left-join [[:yelp_businesses :y] [:= :s.yelp_id :y.yelp_id]]
                   :order-by [[:rating :desc] [:review_count :desc]]
                   :where [:and
                           [:= :open true]
                           [:>= :rating 3.5]
                           [:>= :review_count 5]]
                   :limit 10000}]
    (->> (->entities (jdbc/execute! ds (hsql/format query-map)))
         (add-store-relationships ds))))

(defn upsert-stores! [ds stores updated-at]
  (let [store->row (fn [{:stores/keys [name doordash-id yelp-id opens-at closes-at
                                       open]}]
                     (->> {:id (uuid/v4)
                           :doordash_id doordash-id
                           :yelp_id yelp-id
                           :name name
                           :opens_at opens-at
                           :closes_at closes-at
                           :open open
                           :updated_at updated-at}
                          (medley/remove-vals nil?)))
        rows (map store->row stores)
        upsert-fields (keys (dissoc (first rows) :id :doordash_id))
        query-map {:insert-into :stores
                   :values rows
                   :on-conflict [:doordash_id]
                   :do-update-set {:fields upsert-fields}}]
    (jdbc/execute! ds (hsql/format query-map))))

(defn set-stores-closed! [ds updated-at]
  (let [query-map {:update :stores
                   :set {:open false}
                   :where [:< :updated_at updated-at]}]
    (jdbc/execute! ds (hsql/format query-map))))

(defn- yelp-business->rows [yelp-business updated-at]
  (let [{:yelp-businesses/keys [id rating review-count price photos categories]} yelp-business]
    {:yelp_businesses
     [{:id (uuid/v4)
       :yelp_id id
       :rating rating
       :review_count review-count
       :price price
       :updated_at updated-at}]

     :yelp_business_photos
     (map-indexed
       (fn [i url]
         {:id (uuid/v4)
          :yelp_id id
          :url url
          :rank i})
       photos)

     :yelp_business_categories
     (map-indexed
       (fn [i category]
         {:id (uuid/v4)
          :yelp_id id
          :title (:title category)
          :rank i})
       categories)}))

(defn upsert-yelp-businesses! [ds yelp-businesses updated-at]
  (let [rows (map #(yelp-business->rows % updated-at) yelp-businesses)
        insert-businesses {:insert-into :yelp_businesses
                           :values (mapcat :yelp_businesses rows)
                           :on-conflict [:yelp_id]
                           :do-update-set {:fields [:rating :review_count :price
                                                    :updated_at]}}
        insert-photos {:insert-into :yelp_business_photos
                       :values (mapcat :yelp_business_photos rows)
                       :on-conflict [:yelp_id :rank]
                       :do-update-set {:fields [:url]}}
        insert-categories {:insert-into :yelp_business_categories
                           :values (mapcat :yelp_business_categories rows)
                           :on-conflict [:yelp_id :rank]
                           :do-update-set {:fields [:title]}}
        query-maps [insert-businesses insert-photos insert-categories]]
    (println query-maps)
    (doseq [q query-maps]
      (jdbc/execute! ds (hsql/format q)))))

(comment
  (require '[rads.food.server.db :as db]
           '[rads.food.server.config :as config]
           '[rads.food.server.scraper.yelp :as yelp]
           '[tick.alpha.api :as t]
           '[clj-http.client])
  (def ds (db/datasource))
  (def yb (yelp/get-details {:http-request-fn clj-http.client/request
                             :yelp-api-key (:yelp-api-key config/config)}
                            "mGTS2oVPE94uLvQvBwC2Kg"))

  (upsert-yelp-businesses! ds [yb] (t/now))
  (get-stores ds)
  (->> (get-stores ds) (filter :stores/photos))
  (get-yelp-business-photos ds [(:id yb)])
  (get-yelp-business-categories ds [(:id yb)]))