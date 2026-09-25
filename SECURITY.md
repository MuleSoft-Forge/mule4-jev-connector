## Security

Please report any security issue to the project maintainers as soon as it is
discovered, and avoid filing security reports as public issues.

This connector deliberately limits its runtime dependencies (Jackson for JSON,
and the Mule-provided HTTP client) in order to reduce the total cost of
ownership and the attack surface as much as can be. All consumers should remain
vigilant and have their security stakeholders review all third-party products
(3PP) like this one and their dependencies.

### Handling of sensitive data

- API keys are declared as password parameters and are never written to
  `attributes`, logs, or the raw response payload.
- Decision `state` text is never placed in `attributes`. Only a SHA-256
  `stateHash` is exposed for auditing and cache keys.
