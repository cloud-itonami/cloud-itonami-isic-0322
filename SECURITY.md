# Security Policy

This project handles freshwater aquaculture farm coordination data,
including pond registration records and stock/water-quality logs. Treat
vulnerabilities as potentially high impact even when the demo data is
synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real farm-operator or pond-registration data exposure
- authorization bypass
- `FreshwaterAquacultureOperationsGovernor` bypass
- a path that lets a blocked op (`:administer-treatment`,
  `:control-feeding-equipment`, `:control-harvest-equipment`) commit
  instead of hold
- a path that lets `:flag-health-concern` skip escalation
- audit-ledger tampering
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on operator data, governor enforcement, actuation invariant or
  audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real farm-operator/veterinary data outside this repository.
- Run governor and store tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never wire `:administer-treatment`, `:control-feeding-equipment` or
  `:control-harvest-equipment` to run without a human approval step,
  regardless of confidence.
