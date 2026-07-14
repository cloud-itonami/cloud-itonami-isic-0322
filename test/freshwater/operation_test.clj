(ns freshwater.operation-test
  "Integration tests: drive the compiled langgraph-clj OperationActor
  end-to-end (not just the governor/store units in isolation)."
  (:require [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [freshwater.store :as store]
            [freshwater.operation :as operation]))

(def ^:private active-pond
  {:pond-id "pond-001" :name "Test Pond" :status :active})

(defn- run-op [actor request]
  (g/invoke actor {:request request :context {:actor-id "test-actor"}}))

(deftest clean-log-commits
  (let [st (store/mem-store {:fixtures {"pond-001" active-pond}})
        actor (operation/build st)
        result (run-op actor {:op :log-pond-record :pond-id "pond-001"})]
    (is (= :commit (:disposition result)))))

(deftest health-concern-always-escalates
  (let [st (store/mem-store {:fixtures {"pond-001" active-pond}})
        actor (operation/build st)
        result (run-op actor {:op :flag-health-concern :pond-id "pond-001"})]
    (is (= :escalate (:disposition result)))))

(deftest unregistered-pond-holds
  (let [st (store/mem-store)
        actor (operation/build st)
        result (run-op actor {:op :log-pond-record :pond-id "nonexistent"})]
    (is (= :hold (:disposition result)))))

(deftest blocked-treatment-op-holds
  (let [st (store/mem-store {:fixtures {"pond-001" active-pond}})
        actor (operation/build st)
        result (run-op actor {:op :administer-treatment :pond-id "pond-001"})]
    (is (= :hold (:disposition result)))))
