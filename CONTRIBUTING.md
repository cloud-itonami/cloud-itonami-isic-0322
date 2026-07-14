# Contributing

`cloud-itonami-isic-0322` accepts contributions to the OSS actor, governor
tests, documentation, and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, store, or operation-graph
behavior.

## Rules

- Do not commit real farm-operator applications, credentials, or veterinary
  records.
- Keep coordination proposals behind `FreshwaterAquacultureOperationsGovernor`
  -- never remove a hard check, and never add `:administer-treatment`,
  `:control-feeding-equipment` or `:control-harvest-equipment` to
  `freshwater.governor/allowed-ops`.
- Treat this as a high-stakes domain (animal welfare, water safety): add
  tests for any new governor rule, escalation trigger, or audit-logging
  change.
- A new op added to `freshwater.governor/allowed-ops` MUST ship with a
  governor test AND an `op->effect` entry -- never rely on implicit
  pass-through.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator docs need updates
