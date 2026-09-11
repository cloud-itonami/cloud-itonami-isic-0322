# cloud-itonami-isic-0322

Open Business Blueprint for **ISIC Rev.5 0322: Freshwater aquaculture** -- a
freshwater farm (ponds, tanks, raceways) operations coordination actor.

## Overview

This actor supports a freshwater aquaculture farm's back-office coordination
workflow:

- **Pond record logging** -- stock count, weight, water-quality data
- **Farm operation scheduling** -- propose feeding, water-exchange and
  harvest scheduling
- **Health concern flagging** -- surface a disease outbreak, oxygen
  depletion or water-quality concern (always escalates to human)
- **Supply ordering** -- feed, fingerling and equipment procurement proposals

Unlike marine aquaculture (`cloud-itonami-isic-0321`), this domain has no
maritime-zone or vessel concepts -- coordination instead centers on pond/tank
registration and water-quality data for land-based freshwater facilities.

## Governance Model

All proposals carry `:effect :propose` -- this actor is **coordination
only**. It never:

- Administers treatments (veterinarian exclusive)
- Directly controls feeding equipment (farm operator exclusive)
- Directly controls harvest equipment (farm operator exclusive)
- Modifies pond identity or registration records

Hard blocks (no human override):
1. **Pond verification** -- pond/facility must be independently verified
   and registered, and `:status :active`, before any action
2. **Effect integrity** -- proposal effect must match the request operation
3. **Coordination-only effects** -- all effects are `:propose`
4. **Closed op-allowlist** -- only `:log-pond-record`,
   `:schedule-farm-operation`, `:flag-health-concern` and
   `:order-supplies` are legitimate proposal ops; anything else is rejected
5. **Forbidden fields** -- pond-id, registration-number, pond-status cannot
   be touched
6. **Blocked operations** -- direct treatment administration or direct
   feeding/harvest equipment control are rejected outright, permanently

Escalation triggers (human review required):
- **Health concerns** always escalate
- **High-cost supply orders** (>= 10,000 units) escalate
- **Low advisor confidence** (< 0.6) escalates

## Architecture

```
Request → Advisor (LLM) → Proposal
            ↓
         Governor (independent censor)
            ↓
         Decide (hold | escalate | commit)
            ↓
         Store (persistent audit ledger)
```

All modules are portable `.cljc` (ClojureScript-first, compatible with
JVM/nbb -- no JVM-only interop).

## Building & Testing

```bash
# Install (workspace already has langgraph-clj at ../../kotoba-lang/langgraph)
kbb -M:test

# Run demo
kbb -M:dev:run

# Lint
kbb -M:lint
```

## Files

- `blueprint.edn` -- open business blueprint metadata
- `src/freshwater/` -- core actor modules
  - `governor.cljc` -- compliance rules and hard blocks
  - `store.cljc` -- persistent store protocol + in-memory impl
  - `llm_advisor.cljc` -- advisor interface (mock or real LLM)
  - `operation.cljc` -- langgraph-clj state machine
  - `sim.cljc` -- demo simulation (drives the compiled graph end-to-end)
- `test/freshwater/` -- contract tests for governance, store and the
  end-to-end operation graph

## License

AGPL-3.0-or-later

## Contributing

This actor is part of the cloud-itonami open business blueprint fleet. See
CONTRIBUTING.md.
