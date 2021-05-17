(ns rads.food.server.scraper.doordash
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.zip :as zip]
            [etaoin.api :as e]
            [hickory.core :as hickory]
            [hickory.select :as s]
            [rads.food.server.config :as config]
            [rads.food.server.model :as model]
            [rads.food.server.scraper.yelp :as yelp]
            [taoensso.timbre :as log]
            [tick.alpha.api :as t]))

(def ^:private sign-in-button {:tag :a :fn/link "identity.doordash.com"})
(def ^:private store-card {:css "a[data-anchor-id='StoreCard']"})
(def ^:private login-submit-button {:css "#login-submit-button"})
(def ^:private login-email-input {:css "input[type='email']"})
(def ^:private login-email-value (:doordash-email config/config))
(def ^:private login-password-input {:css "input[type='password']"})
(def ^:private login-password-value (:doordash-password config/config))
(def ^:private got-it-button {:css "div[role='dialog'] button"})

(defn- parse-time [match]
  (let [[_ open-hour open-minute open-ampm close-hour close-minute
         close-ampm] match
        ->int #(Integer/parseInt %)
        ->24hr (fn [hour ampm]
                 (cond
                   (and (<= 1 hour 11) (= :pm ampm)) (+ 12 hour)
                   (and (= hour 12) (= :am ampm)) 0
                   :else hour))]
    {:opens-at (t/new-time (->24hr (->int open-hour) (keyword open-ampm))
                           (->int open-minute))
     :closes-at (t/new-time (->24hr (->int close-hour) (keyword close-ampm))
                            (->int close-minute))}))

(defn- parse-times [s]
  (let [re #"(\d\d?):(\d\d) (am|pm) - (\d\d?):(\d\d) (am|pm)"]
    (map parse-time (re-seq re s))))

(defn- parse-store-card [driver store-card]
  (let [href (e/get-element-attr-el driver store-card "href")
        [_ doordash-id] (re-matches #"^/store/(\d+)$" href)
        inner-html-str (e/get-element-inner-html-el driver store-card)
        open (not (re-find #"Currently Closed" inner-html-str))
        inner-html-tree (-> (e/get-element-inner-html-el driver store-card)
                            hickory/parse
                            hickory/as-hickory)
        name (-> (s/select (s/child (s/attr "color" #{"TextPrimary" "TextTertiary"})) inner-html-tree)
                 first
                 :content
                 first)]
    #:stores{:doordash-id doordash-id
             :name name
             :open open}))

(defn parse-stores [client]
  (let [{:keys [driver]} client
        st (->> (e/query-all driver store-card)
                (map-indexed #(assoc (parse-store-card driver %2) :index %1)))]
    (->> (set/index st [:stores/doordash-id])
         (map (fn [[_ v]] (first (sort-by :index v))))
         (sort-by :index))))

(defn login [client]
  (let [{:keys [driver last-login]} client
        now (t/now)
        skip (and @last-login (t/< (t/between now @last-login)
                                   (t/new-duration 2 :hours)))]
    (if skip
      (log/info "Skipping login")
      (do
        (doto driver
          (e/go "https://www.doordash.com/")
          (e/wait-visible sign-in-button)
          (e/click sign-in-button)
          (e/wait-visible login-email-input)
          (e/fill login-email-input login-email-value)
          (e/wait-visible login-password-input)
          (e/fill login-password-input login-password-value)
          (e/wait-visible login-submit-button)
          (e/click login-submit-button)
          (as-> $ (let [result (try
                                 (e/wait-visible $ got-it-button)
                                 (catch Exception e e))]
                    (when-not (isa? Throwable result)
                      (e/click $ got-it-button)))))
        (reset! last-login now)))))

(defn- scroll-to-end [driver & options]
  (let [{:keys [until]
         :or {until (constantly false)}} options
        s #(e/get-scroll driver)
        max-retries 5
        wait-secs 5]
    (loop [prev nil
           cur (s)
           retry-count 0]
      (when-not (until driver)
        (let [end-of-page (and (= (:y cur) (:y prev)) (not (zero? (:y cur))))]
          (if end-of-page
            (when (< retry-count max-retries)
              (e/wait wait-secs)
              (recur prev (s) (inc retry-count)))
            (do
              (e/scroll-bottom driver)
              (e/wait wait-secs)
              (recur cur (s) retry-count))))))))

(defn- load-store-list [client]
  (let [{:keys [driver ds]} client]
    (doto driver
      (e/go "https://www.doordash.com/")
      (e/wait-visible store-card)
      (scroll-to-end))
    (model/upsert-stores! ds (parse-stores driver) (t/now))))

(defn- parse-address [html]
  (let [business-link-selector (s/descendant (s/and (s/tag "a") (s/attr "href" #(string/starts-with? % "/business/"))))
        business-link-loc (first (s/select-locs business-link-selector html))
        parts (-> business-link-loc zip/up zip/node :content last :content)
        [_ street] (re-matches #"^(.+), $" (first parts))
        city (first (:content (second parts)))
        [_ state zip] (re-matches #"^, ([A-Z]{2}) ([0-9]{5}), USA" (nth parts 2))]
    {:street street
     :city city
     :state state
     :zip zip}))

(defn- load-store-details [client store]
  (let [{:keys [driver]} client]
    (doto driver
      (e/go (str "https://www.doordash.com/store/" (:stores/doordash-id store)))
      (e/wait-visible {:css "a[href^='/business/']"}))))

(defn- parse-store-details [html]
  (let [tree (-> html hickory/parse hickory/as-hickory)
        address (parse-address tree)
        {:keys [opens-at closes-at]} (first (parse-times html))]
    #:stores{:address address
             :opens-at opens-at
             :closes-at closes-at}))

(defn get-store-details [client store]
  (load-store-details client store)
  (->> (e/get-element-inner-html (:driver client) "html")
       parse-store-details))

(defn- found-closed? [driver]
  (e/visible? driver {:tag :span, :fn/text "Currently Closed"}))

(defn scroll-until-closed [client]
  (let [{:keys [driver]} client]
    (doto driver
      (e/go "https://www.doordash.com/")
      (e/wait-visible store-card)
      (scroll-to-end :until found-closed?))))

(defn stop [client]
  (e/stop-driver (:driver client)))
