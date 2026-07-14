# Governance

`cloud-itonami-isic-0322` is an OSS open-business blueprint. Governance
covers both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- The Freshwater-LLM advisor cannot directly administer treatment, or
  directly control feeding or harvest equipment.
- `FreshwaterAquacultureOperationsGovernor` remains independent of the
  advisor.
- Hard governor violations (unverified/inactive pond, effect mismatch,
  non-`:propose` effect, an op outside the closed allowlist, a forbidden
  field, or a blocked operation) cannot be overridden by human approval --
  they are permanent structural holds.
- `:effect` is never anything other than `:propose` for any op this actor
  emits.
- The op-allowlist (`freshwater.governor/allowed-ops`) stays closed --
  adding an op requires an explicit governor rule and test coverage, never
  an implicit pass-through.
- Every commit, hold and escalation path is auditable via the store's
  append-only audit log.
- Real customer, farm-operator or veterinary-record data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, actuation invariant, public business model, or operator
certification should add or update an ADR.
