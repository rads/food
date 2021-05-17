(ns rads.food.server.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [inflections.core :as inflections]
            [clojure.tools.reader.edn :as edn]
            [tick.alpha.api :as t]
            [rads.food.server.config :as config]))

(extend-protocol rs/ReadableColumn
  java.sql.Time
  (read-column-by-label [^java.sql.Time v _]
    (.toLocalTime v))
  (read-column-by-index [^java.sql.Time v _2 _3]
    (.toLocalTime v)))

(def schema
  [{:create-table :stores
    :with-columns
    [[:id :uuid [:primary-key]]
     [:doordash_id [:varchar 255] [:not nil] [:unique]]
     [:yelp_id [:varchar 255]]
     [:name [:varchar 255] [:not nil]]
     [:opens_at :time]
     [:closes_at :time]
     [:open :boolean]
     [:updated_at :timestamptz [:not nil] [:default [:now]]]]}

   {:create-table :yelp_business_photos
    :with-columns
    [[:id :uuid [:primary-key]]
     [:yelp_id [:varchar 255] [:not nil]]
     [:url :text [:not nil]]
     [:rank :int [:not nil]]
     [:updated_at :timestamptz [:not nil] [:default [:now]]]
     [[:unique nil :yelp_id :rank]]]}

   {:create-table :yelp_business_categories
    :with-columns
    [[:id :uuid [:primary-key]]
     [:yelp_id [:varchar 255] [:not nil]]
     [:title [:varchar 255] [:not nil]]
     [:rank :int [:not nil]]
     [:updated_at :timestamptz [:not nil] [:default [:now]]]
     [[:unique nil :yelp_id :rank]]]}

   {:create-table :yelp_businesses
    :with-columns
    [[:id :uuid [:primary-key]]
     [:yelp_id [:varchar 255] [:not nil] [:unique]]
     [:rating :real [:not nil]]
     [:review_count :integer [:not nil]]
     [:price [:varchar 255]]
     [:updated_at :timestamptz [:not nil] [:default [:now]]]]}])

(defn tables [schema]
  (keep :create-table schema))

(defn datasource []
  (jdbc/get-datasource (:jdbc-database-url config/config)))

(defn migrate [ds]
  (dorun
    (map #(jdbc/execute! ds (hsql/format %)) schema)))

(defn seed-data-from-db [ds table-name]
  (let [query-map {:select [:*]
                   :from table-name}]
    (->> (jdbc/execute! ds (hsql/format query-map))
         (map inflections/hyphenate-keys))))

(defn seed-path [table-name]
  (str (io/resource "config/db/seed") "/" (name table-name) ".edn"))

(defn seed-data-from-file [table-name]
  (edn/read-string (slurp (seed-path table-name))))

(defn reset [ds]
  (let [tbls (tables schema)]
    (jdbc/with-transaction [tx ds]
      (let [rename-tables (map (fn [table-name]
                                 {:alter-table (keyword (name table-name))
                                  :rename-table (keyword (str (name table-name) "-" (inst-ms (t/now))))})
                               tbls)
            insert-rows (map (fn [table-name]
                               (let [rows (seed-data-from-file table-name)]
                                 {:insert-into table-name
                                  :values rows}))
                             tbls)
            query-maps (concat rename-tables schema insert-rows)]
        (dorun (map #(jdbc/execute! tx (hsql/format %)) query-maps))))))

(comment
  (def ds (datasource))
  (migrate ds)
  (reset ds)
  (let [table-name :yelp_businesses]
    (spit (seed-path table-name)
          (with-out-str (pprint/write (seed-data-from-db ds table-name)
                                      :length 1000000
                                      :level 1000
                                      :lines* 1000000)))))