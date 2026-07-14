(ns freshwater.governor
  "FreshwaterAquacultureOperationsGovernor -- independent compliance layer
  for freshwater aquaculture (ponds, tanks, raceways) operations
  coordination. The Advisory-LLM has no notion of pond registration, safety
  thresholds, or escalation conditions, so this MUST be a separate system
  able to reject a proposal and fall back to HOLD.

  Governance Rules (all HARD, no overrides):

  1. Pond/facility verification: the pond record must be independently
     verified and registered before ANY action. :pond-id must exist in
     store and have :status :active.

  2. Effect matches op: proposal :effect must match the ONE legitimate
     effect for the request :op (see `op->effect`).

  3. Effect only :propose: all proposals have `:effect :propose` only --
     this actor never commits direct farm operations or treatment
     decisions, which remain exclusive human authority.

  4. Closed op allowlist: the request :op must be a member of the
     allowed-ops set (see below) or the blocked-ops set. Any other op --
     unknown, unlisted, made up -- is rejected outright rather than
     silently passed through.

  5. Blocked permanently (structural holds, no human override):
     - Any proposal to directly administer treatment.
     - Any proposal to directly control feeding equipment.
     - Any proposal to directly control harvest equipment.

  6. Forbidden fields: :pond-id, :registration-number, :pond-status can
     never be modified via a proposal.

  Escalation rules (SOFT -- human sign-off required, not a rejection):
  - :flag-health-concern ALWAYS escalates (human sign-off mandatory).
  - supply orders above cost threshold (default 10000 units) escalate.
  - low advisor confidence (< 0.6) escalates."
  (:require [freshwater.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 10000)

(def allowed-ops
  "The closed set of proposal ops this actor may ever generate. Purely
  coordination -- no direct feeding-system/harvest-equipment operation and
  no veterinary-decision authority."
  #{:log-pond-record :schedule-farm-operation :flag-health-concern :order-supplies})

(def blocked-ops
  "Operations that are permanently, structurally blocked -- human authority
  only, no proposal is ever legitimate for these."
  #{:administer-treatment :control-feeding-equipment :control-harvest-equipment})

;; ----------------------------- checks -----------------------------

(def op->effect
  "The ONE legitimate :effect a proposal may declare for each allowed op."
  {:log-pond-record        :propose
   :schedule-farm-operation :propose
   :flag-health-concern    :propose
   :order-supplies         :propose})

(defn- pond-verification-violations
  "HARD: pond/facility must be verified and active before any action."
  [{:keys [op pond-id]} _proposal st]
  (when (and op pond-id)
    (let [p (store/pond st pond-id)]
      (cond
        (nil? p)
        [{:rule :pond-not-found
          :detail "登録されていないため池/施設 ID での提案"}]
        (not= :active (:status p))
        [{:rule :pond-inactive
          :detail "非アクティブなため池/施設での操作提案"}]
        :else nil))))

(defn- effect-mismatch-violations
  "HARD: effect must match op, for ops with a defined expected effect."
  [{:keys [op]} proposal]
  (when-let [expected (op->effect op)]
    (when (not= expected (:effect proposal))
      [{:rule :effect-mismatch
        :detail (str "op " op " の提案は :effect " expected
                     " のはずが実際には " (:effect proposal) " になっている")}])))

(defn- only-propose-violations
  "HARD: all effects must be :propose (coordination only, no direct actuation)."
  [{:keys [op]} proposal]
  (when (and op (not= :propose (:effect proposal)))
    [{:rule :non-propose-effect
      :detail "この actor では :effect は :propose のみ。直接操作系は人間の authority に留める"}]))

(defn- unknown-op-violations
  "HARD: closed op-allowlist. Any op that is neither an allowed proposal op
  nor an explicitly-blocked op is rejected outright."
  [{:keys [op]} _proposal]
  (when (and op (not (allowed-ops op)) (not (blocked-ops op)))
    [{:rule :unknown-operation
      :detail (str "op " op " はこの actor の closed allowlist に含まれていない")}]))

(defn- forbidden-fields-violations
  "HARD: certain fields are never modifiable via proposals."
  [_request proposal]
  (let [forbidden #{:pond-id :registration-number :pond-status}
        patch-fields (when-let [v (:value proposal)] (set (keys v)))
        found (seq (filter forbidden patch-fields))]
    (when found
      [{:rule :forbidden-field
        :detail (str "変更禁止フィールドが含まれている: " (vec found))}])))

(defn- blocked-operation-violations
  "HARD: certain operations are permanently blocked -- direct treatment
  administration or direct feeding/harvest equipment control."
  [{:keys [op]} _proposal]
  (when (contains? blocked-ops op)
    [{:rule :blocked-operation
      :detail "この操作は人間の veterinarian / farm manager の exclusive authority です"}]))

(defn- health-concern-escalation-violations
  "SOFT: :flag-health-concern ALWAYS escalates."
  [{:keys [op]} _proposal]
  (when (= op :flag-health-concern)
    [{:rule :health-concern-escalation
      :detail "健康懸念フラグは必ず人間の確認で escalate されます"}]))

(defn- supply-cost-escalation-violations
  "SOFT: supply orders above threshold escalate."
  [{:keys [op]} proposal]
  (when (= op :order-supplies)
    (let [cost (get-in proposal [:value :estimated-cost] 0)]
      (when (>= cost supply-cost-threshold)
        [{:rule :high-cost-escalation
          :detail (str "コスト " cost " 超過。人間の確認で escalate 必須")}]))))

(defn check
  "Censors an Advisor proposal against the freshwater aquaculture governor
   rules. Returns
   {:ok? bool :violations [..] :soft-flags [..] :confidence c
    :escalate? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (effect-mismatch-violations request proposal)
                           (pond-verification-violations request proposal st)
                           (only-propose-violations request proposal)
                           (unknown-op-violations request proposal)
                           (forbidden-fields-violations request proposal)
                           (blocked-operation-violations request proposal)))
        soft (into []
                   (concat (health-concern-escalation-violations request proposal)
                           (supply-cost-escalation-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        hard? (boolean (seq hard))
        soft? (boolean (seq soft))]
    {:ok?          (and (not hard?) (not low?) (not soft?))
     :violations   hard
     :soft-flags   soft
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? soft?))}))

(defn hold-fact
  "Audit fact for a rejected proposal (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :pond-id    (:pond-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :soft-flags (:soft-flags verdict)
   :confidence (:confidence verdict)})
