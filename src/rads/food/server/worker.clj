(ns rads.food.server.worker
  (:require [chime.core :as chime]
            [ring.adapter.jetty :as jetty]
            [rads.food.server.scraper :as scraper]
            [taoensso.timbre :as log]
            [tick.alpha.api :as t]
            [rads.food.server.config :as config]
            [medley.core :as medley]
            [etaoin.api :as e]))

(def default-open-check-schedule
  (chime/periodic-seq (t/now) (t/new-duration 5 :minutes)))

(defn start-open-check-schedule [worker & options]
  (let [{:keys [scraper]} worker
        {:keys [schedule on-finished]
         :or {schedule default-open-check-schedule
              on-finished (fn [])}} options]
    (log/info "Starting open check schedule")
    (chime/chime-at
      schedule
      (fn [_]
        (log/info "Starting open check")
        (scraper/refresh-store-open-status scraper)
        (log/info "Completed open check"))
      {:on-finished (fn []
                      (log/info "Completed open check schedule")
                      (on-finished))})))

(defn health-check [_]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Server is up"})

(defn start-http-server [{:keys [port] :as worker}]
  (jetty/run-jetty health-check {:port port, :join? false}))

(defn worker [& {:keys [scraper port] :as opts}]
  {:scraper (or scraper (scraper/scraper))
   :port (or port 3001)})

(defn start [worker]
  (let [http-process (start-http-server worker)
        open-check-process (start-open-check-schedule
                             worker
                             :on-finished #(scraper/stop (:scraper worker)))]
    (merge worker {:http-process http-process
                   :open-check-process open-check-process})))

(defn stop [worker]
  (let [{:keys [http-process open-check-process]} worker]
    (.stop http-process)
    (.close open-check-process)
    (dissoc worker [:http-process :open-check-process])))

(comment
  (def w (atom nil))
  (reset! w (worker :scraper (scraper/scraper :driver (e/chrome))))
  (swap! w start)
  (swap! w stop))