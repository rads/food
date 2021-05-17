(ns rads.food.server.web
  (:gen-class)
  (:require [ring.adapter.jetty :as jetty]
            [bidi.ring :as bidi-ring]
            [hiccup.page :as page]
            [liberator.core :refer [defresource]]
            [muuntaja.middleware :as muuntaja]
            [jsonista.core :as json]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.reload :as reload]
            [ring.middleware.anti-forgery :as anti-forgery]
            [rads.food.server.db :as db]
            [rads.food.server.endpoints :as endpoints]
            [rads.food.server.model :as model]))

(defn bootstrap-data [{:keys [ds] :as options}]
  {:csrf-token (force anti-forgery/*anti-forgery-token*)
   :stores (model/get-stores ds)})

(defn layout [{:keys [ds] :as options}]
  (let [bootstrap-str (-> (bootstrap-data {:ds ds}) pr-str json/write-value-as-string)]
    (page/html5
      [:head
       [:title "rads.food"]
       [:link {:rel "stylesheet", :href "/css/fontawesome.css"}]
       [:link {:rel "stylesheet", :href "/css/index.css"}]]
      [:body
       [:div#app]
       [:script {:src "/cljs-out/dev-main.js"}]
       [:script "rads.food.browser._main(" bootstrap-str ");"]])))

(defresource index
  :available-media-types ["text/html"]
  :handle-ok
  (fn [ctx]
    (layout {:ds (get-in ctx [:request :ds])})))

(defresource rpc
  :allowed-methods [:post]
  :available-media-types ["application/edn"]
  :respond-with-entity? true
  :new? false
  :handle-ok
  (fn [{:keys [request] :as ctx}]
    (let [{:keys [body-params]} request
          [key endpoint-params] body-params
          endpoint-fn (get endpoints/endpoints key)
          common-params (select-keys request [:ds])
          endpoint-params' (merge endpoint-params common-params)]
      (endpoint-fn endpoint-params'))))

(defn not-found [_]
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "Not Found"})

(def routes
  ["/" [["" index]
        ["rpc" rpc]
        [true not-found]]])

(defn wrap-ds [handler]
  (fn [req]
    (handler (assoc req :ds (db/datasource)))))

(def handler
  (-> (bidi-ring/make-handler routes)
      wrap-ds
      muuntaja/wrap-format
      (defaults/wrap-defaults defaults/site-defaults)))

(defn web [& {:keys [port reload] :as opts}]
  {:port (or port (some-> (System/getenv "PORT") Integer/parseInt) 3000)
   :reload (or reload false)})

(defn start [web]
  (let [{:keys [port join reload]} web
        http-server (jetty/run-jetty (if reload
                                       (reload/wrap-reload #'handler)
                                       handler)
                                     {:port port, :join? join})]
    (assoc web :http-server http-server)))

(defn -main [& args]
  (start (web)))

(defn stop [web]
  (.stop (:http-server web))
  (dissoc web :http-server))

(comment
  (def w (web))
  (start w)
  (stop s))