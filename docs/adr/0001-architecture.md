# ADR-0001: Freshwater Aquaculture Operations Coordination Actor Architecture

## Status
Accepted

## Context

Freshwater aquaculture farms (ponds, tanks, raceways) require coordination
of operations -- stock/water-quality monitoring, feeding/water-exchange/
harvest scheduling, health concern escalation, and supply ordering. These
are primarily back-office coordination tasks. However, critical decisions
(treatment administration, direct feeding-equipment control, direct
harvest-equipment control) must remain with human experts (veterinarians,
farm operators).

Unlike marine aquaculture (`cloud-itonami-isic-0321`), this domain has no
maritime-zone or vessel concepts; coordination instead centers on pond/tank
registration and water-quality data for land-based facilities.

The governance model must separate:
- **Coordination proposals** (advisory/proposal-stage) -- which the LLM can
  generate
- **Human-exclusive authority** (treatment administration, direct
  feeding/harvest equipment control) -- which are permanently blocked
- **Escalation points** (health concerns, high-cost supplies) -- which
  require human review

## Decision

Implement a freshwater aquaculture operations actor using the cloud-itonami
pattern:

1. **Governor**: Independent compliance layer that enforces hard rules
   - Pond/facility must be verified and active before any action
   - All effects are `:propose` only (coordination, not direct execution)
   - A closed op-allowlist is the only legitimate set of proposals --
     anything outside it is a structural, permanent block
   - Direct treatment administration and direct feeding/harvest equipment
     control are permanently blocked
   - Health concerns and high-cost supplies always escalate

2. **Store Protocol**: Abstracted data layer
   - In-memory implementation for development/testing
   - Future swap path to Datomic/kotoba-server
   - Append-only audit ledger for all operations

3. **LLM Advisor**: Proposal generator
   - Mock implementation for testing
   - Real LLM injection point via protocol
   - No external service calls in the base module

4. **State Machine**: langgraph-clj graph
   - Linear flow: request → advise → govern → decide → commit|hold|escalate
   - Checkpointed operation (one graph run = one auditable operation)
   - No unbounded loops
   - Explicit `set-entry-point` (a latent gap in the 0321 reference this
     repo mirrors: `aquaculture.operation` never calls `set-entry-point`,
     so its compiled graph has no edge from `START`; invoking it would
     throw `"No entry point — call set-entry-point"`. This was never
     caught because `aquaculture.sim` only prints a summary and never
     calls `g/invoke`. `freshwater.operation` fixes this, and
     `freshwater.sim` actually drives the compiled graph end-to-end
     through four scenarios to prove it.)

## Operations

**Allowed proposals (all :effect :propose, closed allowlist):**
- `:log-pond-record` -- stock count/weight/water-quality data logging
- `:schedule-farm-operation` -- feeding/water-exchange/harvest scheduling
- `:flag-health-concern` -- disease outbreak/oxygen-depletion/water-quality
  concerns (always escalates)
- `:order-supplies` -- feed, fingerling and equipment procurement

**Blocked permanently:**
- `:administer-treatment` -- veterinarian exclusive
- `:control-feeding-equipment` -- farm operator exclusive
- `:control-harvest-equipment` -- farm operator exclusive
- any other op not in the allowlist above (closed-allowlist enforcement)

**Hard Governor Rules (no human override):**
1. Pond verification: `:pond-id` must exist and have `:status :active`
2. Effect integrity: proposal `:effect` must match `:op`
3. Coordination-only: all effects must be `:propose`
4. Closed op-allowlist: `:op` must be an allowed or explicitly-blocked op
5. Forbidden fields: `:pond-id`, `:registration-number`, `:pond-status`
   never modifiable

**Escalation Triggers (human review required):**
- Health concern flags (always)
- Supply orders >= 10,000 units (default threshold)
- Advisor confidence < 0.6

## Consequences

- **Coordination safety**: LLM cannot propose direct treatment
  administration or direct feeding/harvest equipment control
- **Audit transparency**: All operations are logged with proposer, governor
  verdict, and human decisions
- **Farm authority preservation**: Farm operators and veterinarians retain
  exclusive authority over actuation
- **Extensibility**: Store protocol allows future swap to persistent
  database
- **Testability**: Pure governor checks, store protocol, and an actually-
  invoked operation graph enable comprehensive contract + integration tests

## Testing

- Governor contract tests: all hard rules (including closed-allowlist
  rejection and both new blocked-equipment ops), soft escalations
- Store contract tests: pond lookup, audit trail, operation logging
- Operation integration tests: the compiled `freshwater.operation` graph is
  actually invoked (`g/invoke`) end-to-end for commit/hold/escalate paths
- `freshwater.sim` demonstrates the full actor flow across four scenarios
