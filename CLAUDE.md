# CLAUDE.md

Guidance for Claude Code (and any AI assistant) working in this repo. Read
this before making changes. For the product roadmap see [`PLAN.md`](PLAN.md);
for what has shipped see [`CHANGELOG.md`](CHANGELOG.md).

## What this is

The **Jev Connector** — a Mule 4 Java SDK connector. Jev is a *decision* model
(not a chat model): given a **state** plus named, typed **questions** (Noul /
Choice / Score) it returns one typed **answer** per question.

- Coordinates: `com.mulesoft.connectors:mule4-jev-connector:1.0.0-SNAPSHOT`
- Packaging: `mule-extension` · XML prefix `jev` · namespace
  `http://www.mulesoft.org/schema/mule/jev`
- Parent: `org.mule.extensions:mule-java-extension-parent:1.12.3`
- Java 17 · Apache-2.0 · min Mule Runtime **4.9.0**
- Repo: `github.com/MuleSoft-Forge/mule4-jev-connector`

## Current status (2026-09-25)

Milestones **M0–M4** are complete, merged to `develop` and `main`, and pushed.

| Milestone | Scope | State |
| --- | --- | --- |
| M0 | Skeleton, mock route, error enum, quality gates | ✅ |
| M1 | HTTP transport, DecisionEngine, retry scheduler | ✅ |
| M2 | Five routes + ordered failover, `list-models` | ✅ |
| M3 | decide/policy/utility ops, question sets, DataSense, MUnit suite | ✅ |
| M4 | `evaluate-batch`, `filter`, cache, budget guard, stats, 3 sources | ✅ |
| M5 | Release: keyless-mock demo variant, docs/Javadoc polish, live smoke tests, Maven Central + Exchange publish profiles | ⏳ next |

## Build & quality gates

```bash
# Auto-format FIRST — the validate phase runs formatter/impsort/checkstyle as gates
mvn net.revelc.code.formatter:formatter-maven-plugin:format net.revelc.code:impsort-maven-plugin:sort

mvn clean verify          # full build: JUnit + MUnit (run ONLINE — MUnit provisioning needs the runtime BOM)
mvn -o test -Dtest=Foo    # JUnit-only runs can be offline
```

## Non-obvious gotchas (learned the hard way — do not relearn these)

- **SDK namespace is `org.mule.sdk.api.*`, NOT `org.mule.runtime.extension.api.*`.**
  The forward-compatible `mule-java-extension-parent` puts only `mule-sdk-api`
  (compile) + `mule-api` (provided) on the classpath — no `mule-extensions-api`.
  Legacy imports fail with "package does not exist". *Exception:* `connect()`
  throws `org.mule.runtime.api.connection.ConnectionException` (there is no
  sdk-api variant).
- **HTTP client is `org.mule.runtime.http.api.*`, not sdk-api.** There is no
  `org.mule.sdk.api.http`. Add `org.mule.runtime:mule-service-http-api` (pinned
  `${mule.service.http.api.version}` = 4.9.0) as **provided**. Manage the client
  in `Startable.start()` / `Stoppable.stop()`.
- **`@Inject` must be `javax.inject.Inject`, never `jakarta`.** Mule 4.9's
  injection machinery scans `javax.inject.Inject` only; jakarta fields stay
  `null` at `start()`. Dependency `javax.inject:javax.inject:1` (provided).
- **Pin `maven-surefire-plugin` to 3.5.2** (`${surefire.plugin.version}`). The
  parent pulls 2.22.0, which silently reports "Tests run: 0" for JUnit 5.
- **Do NOT put `@Parameter` on operation-method arguments** (that annotation
  targets fields only). Method args are parameters implicitly; use
  `@Optional` / `@Content` as needed.
- **The displayed connector icon is the project-root `icon/icon.svg`**, not
  `src/main/resources/icon/`. The Mule Maven plugin copies the root file to
  `META-INF/mule-artifact/icon.svg`; a resources copy is ignored.

## Branching workflow

- **`develop`** is the integration branch — feature branches merge here first.
- **`main`** is the release/stable line — promote `develop` → `main` for
  releases or when asked.
- Feature work goes on its own `feature/*` branch, merges to `develop`
  (`--no-ff`), and is deleted after merge.

## Security (hard constraints)

- **Never commit or expose credentials/keys to git or GitHub** — including by
  copying the demo folder.
- The demo's real key lives only in the gitignored
  `demo/jev-dev/src/main/resources/local.properties`. Only
  `local.properties.example` (placeholder) is committed.
