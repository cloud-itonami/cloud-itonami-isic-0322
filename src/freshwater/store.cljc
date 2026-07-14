(ns freshwater.store
  "Store protocol and in-memory implementation for freshwater aquaculture
  operations coordination (ponds, tanks and raceways -- not net-pens or
  vessels). Separates data persistence from business logic, enabling swaps
  (in-mem -> Datomic/kotoba-server)."
  #?(:clj (:refer-clojure :exclude [type])))

(defprotocol Store
  "Persistent store for freshwater aquaculture pond/facility records, stock
  history, and operation logs."

  (pond [st pond-id]
    "Returns pond/facility record by :pond-id, or nil if not found.
     Pond record: {:pond-id .. :name .. :status :active|:inactive
     :location .. :registration-number .. :pond-type :pond|:tank|:raceway
     :water-source .. :surface-area-m2 ..}")

  (log-pond-record! [st pond-id record]
    "Commits a pond record (stock count, weight, water-quality data).
     Returns updated store.")

  (schedule-farm-operation! [st pond-id operation-proposal]
    "Schedules a farm operation (feeding, water exchange, harvest).
     Returns updated store.")

  (flag-health-concern! [st pond-id concern]
    "Logs a health concern (disease outbreak, oxygen depletion,
     water-quality issue). Returns updated store.")

  (order-supply! [st pond-id order]
    "Logs a supply order proposal (feed, fingerlings, equipment).
     Returns updated store.")

  (audit-log [st pond-id]
    "Returns all audit entries for this pond/facility."))

(defn- now []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(deftype MemStore [state-atom]
  Store
  (pond [_st pond-id]
    (get-in @state-atom [:ponds pond-id]))

  (log-pond-record! [st pond-id record]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:ponds pond-id :last-pond-record]
                           (constantly (assoc record :t (now))))
                 (update-in [:audit pond-id] (fnil conj [])
                           {:t (now)
                            :type :pond-record-logged
                            :record record}))))
    st)

  (schedule-farm-operation! [st pond-id operation-proposal]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:ponds pond-id :pending-operations] (fnil conj [])
                           (assoc operation-proposal :t (now)))
                 (update-in [:audit pond-id] (fnil conj [])
                           {:t (now)
                            :type :farm-operation-scheduled
                            :proposal operation-proposal}))))
    st)

  (flag-health-concern! [st pond-id concern]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:ponds pond-id :health-concerns] (fnil conj [])
                           (assoc concern :t (now)))
                 (update-in [:audit pond-id] (fnil conj [])
                           {:t (now)
                            :type :health-concern-flagged
                            :concern concern
                            :escalated true}))))
    st)

  (order-supply! [st pond-id order]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:ponds pond-id :supply-orders] (fnil conj [])
                           (assoc order :t (now)))
                 (update-in [:audit pond-id] (fnil conj [])
                           {:t (now)
                            :type :supply-ordered
                            :order order}))))
    st)

  (audit-log [_st pond-id]
    (get-in @state-atom [:audit pond-id] [])))

(defn mem-store
  "Creates an in-memory freshwater aquaculture store, optionally initialized
  with fixtures."
  [& [{:keys [fixtures]}]]
  (let [state (cond-> {:ponds {} :audit {}}
                fixtures (update :ponds merge fixtures))]
    (MemStore. (atom state))))
