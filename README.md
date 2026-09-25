<!-- icon banner: icon/icon.svg -->
# Jev Connector for Mule 4

Jev is a decision model, not a chat model. You give it a **state** plus named, typed
**questions** — Noul (yes/no probability), Choice (option + distribution) or Score
(ordered rubric) — and it returns one typed **answer** per question. This connector makes
those answers first-class Mule values that drive Choice routers, Batch filters, error
handlers and follow-up calls.

> **Status:** active development. Milestones **M0–M3** have landed (skeleton, transport &
> decision engine, all five routes + failover, and the full decide/policy/utility
> operation set with DataSense). See [`PLAN.md`](PLAN.md) §15 for the roadmap and
> [`CHANGELOG.md`](CHANGELOG.md) for what has shipped.

## Contents

- [Concepts](#concepts) · [Routes](#routes) · [Operations](#operations)
- [Requirements](#requirements) · [Maven](#maven) · [Quick start](#quick-start)
- [Demo app](#demo-app) · [Building](#building) · [License](#license)

## Concepts

| Term | Meaning |
| --- | --- |
| **State** | The JSON context you are deciding about (a ticket, an order, a message). |
| **Question** | A named, typed ask: `noul`, `choice` or `score`. |
| **Answer** | The typed result for a question, enriched with a `derived` block and, where the route supports it, `confidence` and a probability distribution. |
| **Question set** | A reusable file of questions (and an optional `policy` block) on the classpath under `questions/`. |
| **Policy** | Thresholds that turn answers into an `ACCEPT` / `REVIEW` / `REJECT` action, evaluated locally. |

## Routes

The connector calls Jev through five interchangeable routes plus a keyless `mock` route
for testing. All four hosted routes speak the TypeSafe `systemOne` contract; Cloudflare
nests the request under `input`.

| Route | Auth | Data path |
| --- | --- | --- |
| TypeSafe (direct) | Bearer key | Sends state to TypeSafe |
| OpenRouter | Bearer key | Sends state to OpenRouter → TypeSafe |
| Vercel AI Gateway | Bearer key / OIDC | Sends state to Vercel → TypeSafe |
| Compatible gateway | Bearer key | Sends state to the configured gateway |
| Cloudflare Workers AI | Bearer token | Sends state to Cloudflare Workers AI |
| Mock | none | Stays in-process; for tests and demos |

Each keyed route accepts an ordered list of **fallback routes** used on connectivity,
rate-limit, overload or timeout errors (never on validation or authorization failures).

## Operations

Ten operations across three families. "Billed" operations make a provider call; the rest
are local.

### Decide — the billed decision operations

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Decide] Evaluate** | `evaluate` | ✔ | The workhorse. Runs a **full question set** (many typed questions) against the route in a single call and returns `{model, answers}`, each answer enriched with a `derived` block. Supply questions inline or by naming a classpath question-set file; DataSense then types the output (e.g. `payload.answers.team.choice`). Attributes carry provider, usage, cost, timing and a `traceEntry`. |
| **[Decide] Ask Yes/No** | `ask-noul` | ✔ | Single **Noul** shortcut. Asks one yes/no question and returns just that answer, so a flow reads `payload.noul` and `payload.probability` directly. Use for a quick boolean judgement with a probability (e.g. "is this urgent?"). |
| **[Decide] Choose** | `choose` | ✔ | Single **Choice** shortcut over a fixed set of options; returns the chosen option and its probability distribution. Use for classification / routing into one of N labels, with an optional no-match option. |
| **[Decide] Score** | `score` | ✔ | Single **Score** shortcut over ordered levels (a rubric); returns the level and `derived.level`. Use for grading on an ordered scale — severity, sentiment, priority. |
| **[Select] Candidate** | `select-candidate` | ✔ | Turns a list of **upstream rows** (DB records, Salesforce queues, search hits) into a dynamic Choice: each candidate becomes an option keyed by `idField` and described by `labelField`/`descriptionField`. Returns the selected candidate object, its probability and confidence, a no-match flag, and the full ranking. Use to let Jev pick the best match from runtime data. |

### Policy — local governance

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Policy] Apply** | `apply-policy` | ✗ | Turns a decision into an `ACCEPT` / `REVIEW` / `REJECT` action plus a `routeKey` ready for a `<choice>` router, judged against policy thresholds supplied inline or from a question-set file's `policy` block. Pure local evaluation — no provider call, no connection. Can optionally raise `JEV:BELOW_THRESHOLD` on REVIEW or `JEV:REJECTED` on REJECT for error-based routing. |

### Utility — discovery and validation

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Util] Get Capabilities** | `get-capabilities` | ✗ | Reports what each connected route supports — Noul/Choice/Score, confidence, model listing, and option/level ceilings — primary route first. Local; use to feature-gate a flow or discover a route's limits. |
| **[Util] List Models** | `list-models` | ✔ | Lists the models available on the connected routes as `{id, route}` entries, primary first. Routes that cannot enumerate models are skipped; raises `JEV:UNSUPPORTED_BY_PROVIDER` if none can. Use to populate a model picker or audit availability. |
| **[Util] Validate Question Set** | `validate-question-set` | ✗ | Validates a question set locally, **before** any billed call, returning `{valid, errors[], warnings[]}`. Errors are the hard API limits; warnings flag legal-but-risky sets. Local; use as a fail-fast authoring check. |

## Requirements

- Java 17
- Mule Runtime ≥ 4.9.0

## Maven

```xml
<dependency>
  <groupId>com.mulesoft.connectors</groupId>
  <artifactId>mule4-jev-connector</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <classifier>mule-plugin</classifier>
</dependency>
```

## Quick start

```xml
<jev:config name="Jev_Config">
  <jev:openrouter-connection apiKey="${jev.openrouter.apiKey}" />
</jev:config>

<flow name="triage">
  <http:listener config-ref="HTTP_Listener_config" path="/triage" />
  <jev:evaluate config-ref="Jev_Config" questionSet="ticket-triage" step="triage">
    <jev:state>#[payload]</jev:state>
  </jev:evaluate>
  <jev:apply-policy config-ref="Jev_Config" questionSet="ticket-triage" target="decision">
    <jev:decision>#[payload]</jev:decision>
  </jev:apply-policy>
  <choice>
    <when expression="#[vars.decision.action == 'ACCEPT']"> <!-- auto-route --> </when>
    <when expression="#[vars.decision.action == 'REVIEW']"> <!-- send to a human --> </when>
    <otherwise> <!-- reject --> </otherwise>
  </choice>
</flow>
```

Never hard-code credentials — read them from a property (e.g. `${jev.openrouter.apiKey}`).

## Demo app

A complete, runnable app that exercises **every** operation over HTTP lives in
[`demo/jev-dev`](demo/jev-dev). It includes step-by-step instructions for both **Anypoint
Studio** and **Anypoint Code Builder**, a logged flow per operation, and an offline
smoke-test endpoint. See [`demo/jev-dev/README.md`](demo/jev-dev/README.md).

The demo is standalone and is not wired into the connector build, so it never affects
`mvn clean verify`.

## Building

```bash
mvn clean verify
```

The build runs the formatter, import sort and checkstyle gates, the JUnit suite and the
MUnit reference-flow suite. To auto-format before verifying:

```bash
mvn net.revelc.code.formatter:formatter-maven-plugin:format net.revelc.code:impsort-maven-plugin:sort
```

To make a local build available to a consuming app (such as the demo), install it:

```bash
mvn clean install -DskipTests -DskipMunitTests
```

## License

Apache-2.0. See [`LICENSE.txt`](LICENSE.txt).
