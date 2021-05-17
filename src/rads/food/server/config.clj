(ns rads.food.server.config
  (:require [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [medley.core :as medley]))

(def app-env (or (keyword (System/getenv "APP_ENV")) :dev))

(def filesystem-path (io/resource (str "config/" (name app-env) ".edn")))
(def filesystem-config (some-> filesystem-path slurp edn/read-string))

(def env-variables-config
  (->> {:yelp-api-key (System/getenv "YELP_API_KEY")
        :doordash-email (System/getenv "DOORDASH_EMAIL")
        :doordash-password (System/getenv "DOORDASH_PASSWORD")
        :jdbc-database-url (System/getenv "JDBC_DATABASE_URL")}
       (medley/remove-vals nil?)))

(def config
  (merge filesystem-config
         env-variables-config))