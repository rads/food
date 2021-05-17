(ns rads.food.browser.components
  (:require [clojure.string :as string]
            [rads.food.browser.rpc :as rpc]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [tick.alpha.api :as t]
            [tick.locale-en-us]))

(defn header []
  (let [[prefix suffix] (string/split "rads.food" #"\.")]
    [:h1.header [:span prefix] [:span "." suffix]]))

(defn stars [rating]
  (let [whole-stars (js/Math.floor rating)
        half-star (> rating whole-stars)
        empty-stars (- 5 (js/Math.ceil rating))
        all-stars (concat (repeat whole-stars [:i.fas.fa-star])
                          (when half-star [[:i.fas.fa-star-half-alt]])
                          (repeat empty-stars [:i.far.fa-star]))]
    [:span.stars (map-indexed (fn [i [el]] [el {:key i}]) all-stars)]))

(defn stores [props]
  [:ul.store-list
   (for [store (:stores props)]
     [:li.store-item {:key (:stores/id store)
                      :class (str "stars-" (string/replace (pr-str (:yelp-businesses/rating store)) "." "-"))}
      [:a.store-name {:href (str "https://www.doordash.com/store/" (:stores/doordash-id store))
                      :target "_blank"}
       (:stores/name store)]
      (when (:stores/closes-at store)
        [:div.store-hours "Closes at " (t/format (tick.format/formatter "h:mm a")
                                                 (:stores/closes-at store))])
      [:div.store-categories
       (interpose ", " (for [category (:stores/categories store)]
                         [:span {:key (:yelp-business-categories/rank category)}
                          (:yelp-business-categories/title category)]))]
      [:a.store-rating {:href (str "https://www.yelp.com/biz/" (:stores/yelp-id store))
                        :target "_blank"}
       [stars (:yelp-businesses/rating store)]]
      [:div.store-review-count (:yelp-businesses/review-count store)]
      [:div.store-photo
       [:img {:src (:yelp-business-photos/url (first (:stores/photos store)))
              :width 200}]]
      #_[:pre (with-out-str (cljs.pprint/pprint store))]])])

#_(rf/reg-event-db
    :stores-received
    (fn [db [_ stores]]
      (assoc db :stores stores)))

#_(rf/reg-event-fx
    :request-stores
    (fn [_ _]
      {:http-xhrio (rpc/request :stores {:on-success [:stores-received]})}))

(rf/reg-sub
  :stores
  (fn [db]
    (:stores db)))

(def root
  (reagent/create-class
    {#_#_:component-did-mount
     (fn [_]
       (rf/dispatch [:request-stores]))
     :reagent-render
     (fn [_]
       (let [st @(rf/subscribe [:stores])]
         [:div.root
          [header]
          [stores {:stores st}]]))}))

(comment
  (require '[tick.alpha.api :as t])
  (t/format (t/formatter "") (t/time))
  (t/format (t/formatter "h:mm a") (t/time (t/instant (cljs.reader/read-string "#inst \"1970-01-02T00:00:00.000-00:00\"")))))
