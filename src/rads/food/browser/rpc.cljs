(ns rads.food.browser.rpc
  (:require [cljs.pprint :as pprint]
            [ajax.interceptors :as ajax-interceptors]
            [cljs.reader :as reader]
            [ajax.protocols :as ajax-protocols]
            [re-frame.db :as rf-db]))

(def ^:dynamic *csrf-token* nil)

(def ^:private edn-request-format
  {:write #(with-out-str (pprint/pprint %))
   :content-type "application/edn"})

(def ^:private edn-response-format
  (ajax-interceptors/map->ResponseFormat
    {:read #(reader/read-string (ajax-protocols/-body %))
     :description "EDN"
     :content-type ["application/edn"]}))

(def ^:private edn-body-format
  {:request edn-request-format
   :response edn-response-format})

(defn request [key {:keys [on-success on-failure] :as params}]
  (js/console.log *csrf-token*)
  (let [params' (dissoc params :response-format :on-success :on-failure)]
    {:method :post
     :uri "/rpc"
     :format (:request edn-body-format)
     :response-format (:response edn-body-format)
     :timeout 10000
     :params [key params']
     :on-success (or on-success [:http-no-on-success])
     :on-failure (or on-failure [:http-no-on-failure])
     :headers {"X-CSRF-Token" *csrf-token*}}))