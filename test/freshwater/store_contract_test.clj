(ns freshwater.store-contract-test
  "Contract tests for the freshwater aquaculture Store."
  (:require [clojure.test :refer [deftest is]]
            [freshwater.store :as store]))

(deftest pond-lookup
  (let [f {:pond-id "pond-001" :name "Test Pond" :status :active}
        st (store/mem-store {:fixtures {"pond-001" f}})]
    (is (= f (store/pond st "pond-001")))
    (is (nil? (store/pond st "nonexistent")))))

(deftest log-pond-record
  (let [st (store/mem-store {:fixtures {"pond-001" {:pond-id "pond-001" :status :active}}})
        record {:count 1000 :weight-kg 850}
        st2 (store/log-pond-record! st "pond-001" record)]
    (is (contains? (store/pond st2 "pond-001") :last-pond-record))
    (is (= (:count (get-in (store/pond st2 "pond-001") [:last-pond-record]))
           1000))))

(deftest audit-trail
  (let [st (store/mem-store {:fixtures {"pond-001" {:pond-id "pond-001" :status :active}}})
        st1 (store/log-pond-record! st "pond-001" {:count 1000})
        st2 (store/flag-health-concern! st1 "pond-001" {:concern "low-dissolved-oxygen"})
        log (store/audit-log st2 "pond-001")]
    (is (= 2 (count log)))
    (is (some #(= :pond-record-logged (:type %)) log))
    (is (some #(= :health-concern-flagged (:type %)) log))))

(deftest schedule-farm-operation
  (let [st (store/mem-store {:fixtures {"pond-001" {:pond-id "pond-001" :status :active}}})
        op {:type :water-exchange :scheduled-date "2026-07-20"}
        st2 (store/schedule-farm-operation! st "pond-001" op)]
    (is (contains? (get-in (store/pond st2 "pond-001") [:pending-operations 0])
                   :type))
    (is (= :water-exchange (get-in (store/pond st2 "pond-001") [:pending-operations 0 :type])))))

(deftest order-supply
  (let [st (store/mem-store {:fixtures {"pond-001" {:pond-id "pond-001" :status :active}}})
        order {:item "fingerlings" :quantity 5000 :estimated-cost 8000}
        st2 (store/order-supply! st "pond-001" order)]
    (is (contains? (get-in (store/pond st2 "pond-001") [:supply-orders 0])
                   :item))
    (is (= 8000 (get-in (store/pond st2 "pond-001") [:supply-orders 0 :estimated-cost])))))
