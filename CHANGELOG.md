# Changelog

All notable changes to the Jev Connector are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project uses semantic versioning.

## [Unreleased]

### Added
- **M3 — Decide chain.** The `evaluate`, `ask-noul`, `choose`, `score` and `select-candidate`
  decision operations; the `apply-policy` governance operation; question-set files on the
  classpath with a value provider; `validate-question-set`; DataSense output metadata so
  `evaluate` types its answers from the referenced question set; and an MUnit reference-flow
  suite that runs the §8.8 decide chain end-to-end against `mock`. Added a runnable demo app
  under [`demo/jev-dev`](demo/jev-dev).
- **M2 — Routes & failover.** All five keyed routes (TypeSafe, OpenRouter, Vercel,
  Compatible, Cloudflare), ordered fallback routes for transient failures, per-route
  capabilities, and the `list-models` operation.
- **M1 — Transport & engine.** The Mule HTTP-client transport, the non-blocking
  `DecisionEngine` with a runtime retry scheduler, and the governance/provider-contract docs.
- **M0 — Skeleton.** Project scaffolding on the forward-compatible `mule-java-extension-parent`
  (1.12.3), `min.mule.version` 4.9.0, Apache-2.0 license. Extension class `Jev` (`jev` prefix),
  single `<jev:config>`, `mock` connection provider and `MockAdapter`, `Capabilities` model,
  full `JEV:*` error-type enum, `[Util] Get Capabilities` operation, connector icon, and the
  formatter / impsort / checkstyle quality gates.
