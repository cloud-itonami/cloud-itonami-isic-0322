(ns freshwater.sim
  "Simulation/demo: drive the freshwater aquaculture operations actor
  end-to-end through a few scenarios via the compiled langgraph-clj graph
  (not just a printed summary)."
  (:require [langgraph.graph :as g]
            [freshwater.store :as store]
            [freshwater.operation :as operation]))

(defn- run-op [actor request]
  (g/invoke actor {:request request :context {:actor-id "freshwater-demo"}}))

(defn -main []
  (println "Freshwater Aquaculture Operations Coordination Actor - Demo")
  (println "=============================================================")

  ;; Setup: create store with a test pond.
  (let [test-pond {:pond-id "freshwater-farm-001"
                   :name "Riverside Freshwater Farm"
                   :status :active
                   :location "Inland Valley"
                   :registration-number "REG-001-2024"
                   :pond-type :pond
                   :water-source :river
                   :surface-area-m2 12000}
        st (store/mem-store {:fixtures {"freshwater-farm-001" test-pond}})
        actor (operation/build st)]

    (println "\nPond/facility registered:")
    (println "  Pond ID:" (:pond-id test-pond))
    (println "  Name:" (:name test-pond))
    (println "  Type:" (name (:pond-type test-pond)))
    (println "  Surface area:" (:surface-area-m2 test-pond) "m^2")
    (println "  Registration:" (:registration-number test-pond))

    (println "\n-- Scenario 1: clean stock-record log (expect :commit) --")
    (let [result (run-op actor {:op :log-pond-record :pond-id "freshwater-farm-001"})]
      (println "  disposition:" (:disposition result)))

    (println "\n-- Scenario 2: health concern flag (expect :escalate, ALWAYS) --")
    (let [result (run-op actor {:op :flag-health-concern :pond-id "freshwater-farm-001"})]
      (println "  disposition:" (:disposition result)))

    (println "\n-- Scenario 3: unregistered pond (expect :hold, hard block) --")
    (let [result (run-op actor {:op :log-pond-record :pond-id "nonexistent-farm"})]
      (println "  disposition:" (:disposition result)))

    (println "\n-- Scenario 4: direct treatment administration (expect :hold, permanent block) --")
    (let [result (run-op actor {:op :administer-treatment :pond-id "freshwater-farm-001"})]
      (println "  disposition:" (:disposition result)))

    (println "\nOperation Actor built and driven end-to-end.")
    (println "Governance rules active:")
    (println "  - Pond/facility verification required")
    (println "  - All effects :propose only (coordination, not actuation)")
    (println "  - Closed op-allowlist enforced")
    (println "  - Health concerns always escalate")
    (println "  - High-cost supplies escalate")
    (println "  - Treatment administration / feeding & harvest equipment control blocked (human authority)")))
