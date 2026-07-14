(ns freshwater.operation
  "OperationActor -- one freshwater aquaculture operation = one supervised
  actor run, expressed as a langgraph-clj StateGraph. The advisor
  (Freshwater-LLM) is sealed into a single node (:advise); its proposal is
  ALWAYS routed through the FreshwaterAquacultureGovernor (:govern) before
  anything commits to the SSoT.

  One graph run = one operation (request -> advise -> govern -> decide ->
  commit | hold | escalate). No unbounded inner loop -- each operation is
  auditable and checkpointed.

  All proposals have :effect :propose (coordination only -- no direct
  feeding-system/harvest-equipment operation and no veterinary-decision
  authority). Escalation points: health concerns (always escalate),
  high-cost supply orders, low confidence."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [freshwater.llm-advisor :as advisor]
            [freshwater.governor :as governor]))

(defn- commit-fact
  "Ledger fact for a committed operation."
  [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :pond-id    (:pond-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :pond-id (:pond-id request)
   :op      (:op request)
   :value   (or (:value proposal) {})})

(defn build
  "Compiles an OperationActor graph bound to `store`.
  opts:
    :advisor      -- an Advisor (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; Freshwater aquaculture LLM inference -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      ;; FreshwaterAquacultureGovernor -- independent censor.
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition.
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [{:keys [hard? escalate?]} verdict]
            (cond
              hard?
              {:disposition :hold
               :audit [(governor/hold-fact request context verdict)]}

              escalate?
              {:disposition :escalate
               :audit [{:t :advisor-escalation
                        :op (:op request)
                        :pond-id (:pond-id request)
                        :soft-flags (:soft-flags verdict)
                        :confidence (:confidence verdict)}]}

              :else
              {:disposition :commit
               :record (commit-record request context proposal)
               :audit [(commit-fact request context proposal)]}))))

      ;; Finalize: apply to store or hold/escalate. `:record` is present
      ;; only when :decide committed; :disposition alone drives the caller.
      (g/add-node :finalize
        (fn [{:keys [disposition]}]
          {:disposition disposition}))

      ;; Entry + edges: linear flow with early exit on hold/escalate.
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-edge :decide :finalize)

      ;; Terminal states.
      (g/set-finish-point :finalize)

      (g/compile-graph {:checkpointer checkpointer})))
