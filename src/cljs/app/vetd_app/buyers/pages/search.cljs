(ns vetd-app.buyers.pages.search
  (:require [vetd-app.buyers.components :as c]
            [vetd-app.ui :as ui]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [reagent.format :as format]
            [re-frame.core :as rf]
            [re-com.core :as rc]))

(def last-query-id (atom 0))

(defn get-next-query-id []
  (swap! last-query-id inc))

;; Events
(rf/reg-event-fx
 :b/nav-search
 (fn [_ [_ search-term]]
   {:nav {:path (str "/b/search/" (when search-term (js/encodeURI search-term)))}
    :analytics/track {:event "Navigate"
                      :props {:category "Navigation"
                              :label "Buyers Products & Categories"}}}))

(rf/reg-event-fx
 :b/route-search
 (fn [{:keys [db]} [_ search-term]]
   {:db (assoc db :page :b/search)
    :dispatch [:b/update-search-term search-term]
    :analytics/page {:name "Buyers Products & Categories"}}))

(rf/reg-event-fx
 :b/update-search-term
 (fn [{:keys [db]} [_ search-term]]
   {:db (assoc db :page-params {:search-term search-term})
    :dispatch-debounce [{:id :b/search
                         :dispatch [:b/search search-term]
                         :timeout 250}]}))

(rf/reg-event-fx
 :b/search
 (fn [{:keys [db ws]} [_ q-str q-type]]
   (let [qid (get-next-query-id)]
     {:db (assoc db :buyer-qid qid)
      :ws-send {:payload {:cmd :b/search
                          :return {:handler :b/ws-search-result-ids
                                   :qid qid}
                          :query q-str
                          :buyer-id (-> db :memberships first :org-id)
                          :qid qid}}})))

(rf/reg-event-fx
 :b/ws-search-result-ids
 (fn [{:keys [db]} [_ results {:keys [return]}]]
   (def res1 results)
   #_ (println res1)
   (def ret1 return)
   (if (= (:buyer-qid db) (:qid return))
     {:db (assoc db :b/search-result-ids results)}
     {})))

(rf/reg-event-fx
 :b/start-round
 (fn [{:keys [db]} [_ etype eid]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/start-round
                          :return {:handler :b/start-round-success}
                          :etype etype
                          :eid eid
                          :buyer-id (->> (:active-memb-id db)
                                         (get (group-by :id (:memberships db)))
                                         first
                                         :org-id)}}
      :analytics/track {:event "Start Round"
                        :props {:type etype}}})))

(rf/reg-event-fx
 :b/start-round-success
 (constantly
  {:toast {:type "success"
           :title "Your VetdRound has begun!"
           :message "We'll be in touch with next steps."}}))

(rf/reg-event-fx
 :b/create-preposal-req
 (fn [{:keys [db]} [_ product-id]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/create-preposal-req
                          :return {:handler :b/create-preposal-req-success}
                          :prep-req {:from-org-id (->> (:active-memb-id db)
                                                       (get (group-by :id (:memberships db)))
                                                       first
                                                       :org-id)
                                     :from-user-id (-> db :user :id)
                                     :prod-id product-id}}}})))

(rf/reg-event-fx
 :b/create-preposal-req-success
 (constantly
  {:toast {:type "success"
           :title "Preposal Requested"
           :message "We'll be in touch with next steps."}}))

(rf/reg-event-fx
 :b/req-new-prod-cat
 (fn [{:keys [db]} [_ req]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/req-new-prod-cat
                          :return {:handler :b/req-new-prod-cat-success}
                          :org-id (-> db :memberships first :org-id)
                          :user-id (-> db :user :id)
                          :req req}}})))

(rf/reg-event-fx
 :b/req-new-prod-cat-success
 (constantly
  {:toast {:type "success"
           :title "Thanks for the suggestion!"
           :message "We'll let you know when we add it."}}))

;; Subscriptions
(rf/reg-sub
 :b/search-result-ids
 (fn [db _]
   (:b/search-result-ids db)))

(rf/reg-sub
 :search-term
 :<- [:page-params] 
 (fn [{:keys [search-term]}]
   (or search-term "")))


;; Components

;; TODO link to request preposal <==================
(defn rndr-preposal
  [{:keys [vendor-name pitch created]}]
  [:div {:style {:border "solid 1px #AAA"}}
   "Preposal"
   [:div pitch]
   [:div (str created)]])

(defn c-round-search-result
  [{:keys [id created status]}]
  [:div "Active Round " (str [status created])])

(defn c-preposal-search-result
  [{:keys [created]}]
  [:div "Preposal " (str created)])

(defn c-preposal-req-search-result
  [{:keys [created]}]
  [:div "Preposal Requested " (str created)])

(defn c-product-search-result
  [{:keys [id idstr pname short-desc logo rounds categories forms docs] :as product} org]
  (let [preposal-responses (-> docs first :responses)
        requested-preposal? (not-empty forms)]
    [:> ui/Item {:onClick #(rf/dispatch (if preposal-responses
                                          [:b/nav-preposal-detail (-> docs first :idstr)]
                                          [:b/nav-product-detail idstr]))}
     [:> ui/ItemImage {:class "product-logo"
                       :src (str "https://s3.amazonaws.com/vetd-logos/" logo)}]
     [:> ui/ItemContent
      [:> ui/ItemHeader
       pname " " [:small " by " (:oname org)]]
      [:> ui/ItemMeta
       (if preposal-responses
         (let [p-e-details (docs/get-field-value preposal-responses
                                                 "Pricing Estimate"
                                                 "details"
                                                 :sval)]
           (if-let [p-e-value (docs/get-field-value preposal-responses
                                                    "Pricing Estimate"
                                                    "value"
                                                    :nval)]
             [:span
              (format/currency-format p-e-value)
              " / "
              (docs/get-field-value preposal-responses "Pricing Estimate" "unit" :sval)
              " "
              [:small "(estimate) " p-e-details]]
             (when p-e-details
               (str "Price Estimate: " p-e-details))))
         (if requested-preposal?
           "Preposal Requested"
           [:a {:onClick #(do (.stopPropagation %)
                              (rf/dispatch [:b/create-preposal-req id]))}
            "Request a Preposal"]))]
      [:> ui/ItemDescription short-desc]
      [:> ui/ItemExtra
       [c/c-categories product]
       (when (and preposal-responses
                  (= "yes" (docs/get-field-value preposal-responses "Do you offer a free trial?" "value" :sval)))
         [:> ui/Label {:class "free-trial-tag"
                       :color "gray"
                       :size "small"
                       :tag true}
          "Free Trial"])]]
     (when (not-empty rounds)
       [c/c-round-in-progress {:props {:ribbon "right"
                                       :style {:position "absolute"
                                               :marginLeft -14}}}])]))

(defn c-vendor-search-results
  [v]
  [:> ui/ItemGroup {:class "results"}
   (for [p (:products v)]
     ^{:key (:id p)}
     [c-product-search-result p v])])

(defn c-category-search-results
  [{:keys [cname id idstr rounds] :as cat}]
  [:div.category-search-result
   (if (empty? rounds)
     [:> ui/Button {:on-click #(rf/dispatch [:b/start-round :category id])
                    :color "blue"
                    :icon true
                    :labelPosition "right"}
      (str "Start VetdRound for \"" cname "\"")
      [:> ui/Icon {:name "right arrow"}]]
     [:> ui/Label {:color "teal"
                   :size "large"}
      "VetdRound In Progress for \"" cname "\""])])

(defn c-search-results
  [search-query]
  (let [org-id @(rf/subscribe [:org-id])
        {:keys [product-ids vendor-ids category-ids] :as ids} @(rf/subscribe [:b/search-result-ids])
        prods (if (not-empty (concat product-ids vendor-ids))
                @(rf/subscribe [:gql/sub
                                {:queries
                                 [[:orgs {:id vendor-ids}
                                   [:id :oname :idstr :short-desc
                                    [:products {:id product-ids}
                                     [:id :pname :idstr :short-desc :logo
                                      [:forms {:ftype "preposal" ; preposal requests
                                               :from-org-id org-id}
                                       [:id]]
                                      [:docs {:dtype "preposal" ; completed preposals
                                              :to-org-id org-id}
                                       [:id :idstr :title
                                        [:from-org [:id :oname]]
                                        [:from-user [:id :uname]]
                                        [:to-org [:id :oname]]
                                        [:to-user [:id :uname]]
                                        [:responses
                                         [:id :prompt-id :notes
                                          [:prompt
                                           [:id :prompt]]
                                          [:fields
                                           [:id :pf-id :idx :sval :nval :dval
                                            [:prompt-field [:id :fname]]]]]]]]
                                      [:rounds {:buyer-id org-id
                                                :status "active"}
                                       [:id :created :status]]
                                      [:categories [:id :idstr :cname]]]]]]]}])
                [])
        categories (if (not-empty category-ids)
                     @(rf/subscribe [:gql/sub
                                     {:queries
                                      [[:categories {:id category-ids}
                                        [:id :idstr :cname
                                         [:rounds {:buyer-id org-id
                                                   :status "active"}
                                          [:id :created :status]]]]]}])
                     [])
        loading? (or (= :loading prods) (= :loading categories))
        prod-cat-suggestion (r/atom "")]
    (if loading?
      [:> ui/Loader {:active true
                     :style {:marginTop 20}}]
      (if (not-empty (concat product-ids vendor-ids))
        [:div
         [:div.categories
          (for [c (:categories categories)]
            ^{:key (:id c)}
            [c-category-search-results c])]
         (for [v (:orgs prods)]
           ^{:key (:id v)}
           [c-vendor-search-results v])]
        (when (> (count @search-query) 2)
          [:> ui/Segment {:placeholder true}
           [:> ui/Header {:icon true}
            [:> ui/Icon {:name "search"}]
            "We don't have that product or category."]
           [:> ui/SegmentInline
            [:> ui/Input {:label {:icon "asterisk"}
                          :labelPosition "left corner"
                          :placeholder "Product / Category . . ."
                          :style {:position "relative"
                                  :top 1
                                  :marginRight 15}
                          :onChange (fn [_ this]
                                      (reset! prod-cat-suggestion (.-value this)))}]
            #_[:> ui/Select {:compact true
                             :options [{:text "Product"
                                        :value "product"
                                        :key "product"}
                                       {:text "Category"
                                        :value "category"
                                        :key "category"}]
                             :defaultValue "product"}]
            [:> ui/Button {:color "blue"
                           :onClick #(rf/dispatch [:b/req-new-prod-cat @prod-cat-suggestion])}
             "Request It!"]]])))))

(defn c-page []
  (let [search-query& (rf/subscribe [:search-term])]
    (fn []
      [:> ui/Grid
       [:> ui/GridRow {:columns 3}
        [:> ui/GridColumn {:width 4}]
        [:> ui/GridColumn {:width 8}
         [:> ui/Input {:class "product-search"
                       :value @search-query&
                       :size "big"
                       :icon "search"
                       :autoFocus true
                       :spellCheck false
                       ;; :loading true ; todo: use this property
                       :onChange (fn [_ this]
                                   (rf/dispatch [:b/update-search-term (.-value this)]))
                       :placeholder "Search products & categories..."}]]
        [:> ui/GridColumn {:width 4}]]
       [:> ui/GridRow {:columns 3}
        [:> ui/GridColumn {:width 2}]
        [:> ui/GridColumn {:width 12}
         [c-search-results search-query&]]
        [:> ui/GridColumn {:width 2}]]])))
