# Changelog

All notable changes to the Jev Connector are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project uses semantic versioning.

## [Unreleased]

### Fixed
- **Question and answer shapes now match TypeSafe.** `ask-noul`, `choose`, `score` and
  `select-candidate` sent `criteriaTrue`/`criteriaFalse`, `options` and `levels`, which
  TypeSafe rejects (422 direct, 400 via OpenRouter) or silently ignores; they now send
  `criteria`. The bundled `ticket-triage.json` uses `criteria` too, and its sentiment policy
  levels are 0-based (`acceptLevels ["2","3","4"]`, `reviewLevels ["1"]`) to match the
  0-based `legend` TypeSafe returns. `apply-policy` and `filter` read the Noul answer's
  `noul` value (they read a `probability` field TypeSafe never returns, so every yes/no
  judged as 0); `filter`'s `scores` entries are now `{index, noul, kept}`. Stats bucket
  Score answers by `derived.level` rather than the continuous `score`.
- `evaluate` and `evaluate-batch` validate questions locally before calling a route, so a
  malformed set, including `options`/`levels`/`legend`/`criteriaTrue`/`criteriaFalse`, fails
  as `JEV:INVALID_QUESTION_SET` without a billed call. The `mock` route reads only `criteria`
  and answers in TypeSafe's shapes, so keyless tests catch contract drift.

### Added
- **M4 — Scale & governance.** The `evaluate-batch` and `filter` scale operations, fanned out
  behind a non-blocking concurrency limit with per-item de-duplication, budgeting, caching and
  stats. Governance foundation: a decision cache, a cluster-wide `BudgetGuard` (call and
  input-token limits per rolling window, raising `JEV:BUDGET_EXCEEDED`), and a
  privacy-safe `DecisionStatsRecorder` — all backed by the runtime Object Store. Three
  monitoring polling sources — `on-drift-detected` (no-match rate, mean confidence and
  Jensen–Shannon distribution shift), `on-budget-threshold` and `on-provider-failover` —
  each firing once per breach and re-arming on recovery. New config tabs for cache and budget
  settings.
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
