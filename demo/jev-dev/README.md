# Jev Connector — end-to-end demo (`jev-dev`)

A ready-to-run Mule 4 application that exercises **every** operation of the Jev connector
over HTTP. Each flow has a logger before and after the call, so you can watch the decisions
stream past in the console.

| | |
|---|---|
| Runtime | Mule **4.11.0**, Java 17 |
| Connector | `com.mulesoft.connectors:mule4-jev-connector:1.0.0-SNAPSHOT` |
| Route | **OpenRouter** (real LLM calls); the key is read from `local.properties` |
| Listener | `http://localhost:8081` |

> This app lives inside the connector repo under [`demo/jev-dev`](.). It is **not** part
> of the connector build (the connector `pom.xml` does not include it as a module), so it
> never affects `mvn clean verify` on the connector.

---

## 1. Prerequisites

1. **JDK 17** and **Maven 3.9+** on your `PATH`.
2. **Install the connector into your local Maven repository** so the demo can resolve it.
   From the connector repo root (one level above this folder):

   ```bash
   mvn -f ../../pom.xml clean install -DskipTests -DskipMunitTests
   ```

   This publishes `mule4-jev-connector:1.0.0-SNAPSHOT` to `~/.m2`. Re-run it whenever you
   change the connector and want the demo to pick up the new build.
3. An **OpenRouter API key** (`sk-or-v1-…`). Get one at <https://openrouter.ai/keys>.

---

## 2. Configure the credential (required, never committed)

The API key is **not** stored in the flow XML. It is read from
`src/main/resources/local.properties`, which is gitignored. Create it from the template:

```bash
cp src/main/resources/local.properties.example src/main/resources/local.properties
```

Then edit `local.properties` and set your key:

```properties
jev.openrouter.apiKey=sk-or-v1-your-real-key-here
```

The flow references it as `${jev.openrouter.apiKey}` via `<configuration-properties>`.
**Never** hard-code the key in `jev-dev.xml`. `local.properties` is matched by both this
project's `.gitignore` and the connector repo's root `.gitignore`, so it cannot be
committed by accident.

> Prefer not to use a real key? See [§6 "Offline testing"](#6-offline-testing) — the
> `/apply-policy` endpoint runs fully offline.

---

## 3. Run in Anypoint Studio

1. **Import the project.** *File → Import… → Anypoint Studio → Anypoint Studio project
   from File System*, then point *Project Root* at this `demo/jev-dev` folder and finish.
   (Studio recognises it by its `mule-artifact.json` and `pom.xml`.)
2. **Make sure the connector is resolvable.** Studio reads from your local `~/.m2`, so if
   you completed [§1 step 2](#1-prerequisites) the `mule4-jev-connector` dependency
   resolves. If Studio shows it as missing, right-click the project →
   *Maven → Update Project*.
3. **Create `local.properties`** as in [§2](#2-configure-the-credential-required-never-committed)
   under `src/main/resources/` if you have not already.
4. **Run.** Right-click the project → *Run As → Mule Application*. Watch the **Console**
   view; you should see `Started app 'jev-dev'` and the listener bind to port 8081.
5. **Test** using the endpoints in [§5](#5-endpoints--one-per-operation).

**Palette tip.** If the Jev module shows stale operations or the wrong icon, it is a
cached extension model, not a bad build. Re-install the connector (§1 step 2), then
*Maven → Update Project*, and if needed bump the connector version suffix (e.g.
`1.0.0-SNAPSHOT-1`) so Studio treats it as new.

---

## 4. Run in Anypoint Code Builder

Anypoint Code Builder (ACB) is the VS Code–based IDE (desktop extension or the cloud
workspace). The project is a standard Mule Maven app, so ACB runs it directly.

1. **Open the folder.** In ACB (or VS Code with the *Anypoint Code Builder* extension
   pack), choose *File → Open Folder…* and select this `demo/jev-dev` folder. Let the
   Mule extensions finish activating.
2. **Resolve the connector.** ACB uses Maven under the hood and reads `~/.m2`. Ensure you
   ran [§1 step 2](#1-prerequisites). To force a refresh, open the Command Palette
   (`⇧⌘P` / `Ctrl+Shift+P`) → *MuleSoft: Refresh* / reload the window.
3. **Create `local.properties`** as in [§2](#2-configure-the-credential-required-never-committed).
4. **Run.** Either:
   - Command Palette → ***Run Mule Application*** (ACB creates a launch configuration on
     first run), **or**
   - open `src/main/mule/jev-dev.xml` and click the **Run** (▷) code lens above the
     canvas / flow, **or**
   - from a terminal in the folder: `mvn clean package mule:run`.
5. **Test** using the endpoints in [§5](#5-endpoints--one-per-operation). ACB forwards
   `localhost:8081`; in the cloud workspace use the forwarded-port URL it shows you.

> ACB writes its own `.vscode/launch.json` on first run. That file (like Studio's
> `.mule/` working dir) is intentionally **not** committed — the repo `.gitignore`
> excludes it, and it contains only `${workspaceFolder}` references, no secrets.

---

## 5. Endpoints — one per operation

All are `GET` on `http://localhost:8081`.

| Path             | Operation                     | Calls a provider? | This demo's flow                    |
|------------------|-------------------------------|-------------------|-------------------------------------|
| `/capabilities`  | [Util] Get Capabilities       | No (local)        | Per-route capability report.        |
| `/models`        | [Util] List Models            | Yes               | Enumerates models on OpenRouter.    |
| `/validate`      | [Util] Validate Question Set  | No (local)        | Validates the `ticket-triage` set.  |
| `/evaluate`      | [Decide] Evaluate             | Yes               | Full `ticket-triage` set on a ticket.|
| `/ask`           | [Decide] Ask Yes/No           | Yes               | Single noul (yes/no) question.      |
| `/choose`        | [Decide] Choose               | Yes               | Single choice over 3 team options.  |
| `/score`         | [Decide] Score                | Yes               | Single score over 5 levels.         |
| `/select`        | [Select] Candidate            | Yes               | Ranks 3 queues against a request.   |
| `/apply-policy`  | [Policy] Apply                | No (local)        | Inline decision vs. policy block.   |
| `/decide-chain`  | Evaluate ▶ Apply ▶ route       | Yes               | End-to-end §8.8 decide chain.       |

### What each operation does, and why this demo uses it

- **`/capabilities` → [Util] Get Capabilities.** Reports what each connected route
  supports (Noul/Choice/Score, confidence, model listing, option/level ceilings). *Purpose:*
  discover a route's limits or feature-gate a flow. Here it shows the OpenRouter route's
  capabilities without spending anything.

- **`/models` → [Util] List Models.** Lists the models available on the connected routes
  as `{id, route}` entries. *Purpose:* populate a model picker or audit availability. Here
  it enumerates the models OpenRouter exposes.

- **`/validate` → [Util] Validate Question Set.** Validates a question set locally, before
  any billed call, returning `{valid, errors[], warnings[]}`. *Purpose:* fail fast on a
  malformed set during authoring. Here it validates the bundled `ticket-triage` set.

- **`/evaluate` → [Decide] Evaluate.** The workhorse: runs a **whole question set** (many
  typed questions) in one call and returns `{model, answers}` with each answer enriched by
  a `derived` block. *Purpose:* make a multi-part decision at once. Here it asks the
  `ticket-triage` set (team + urgency + sentiment) about a sample support ticket.

- **`/ask` → [Decide] Ask Yes/No.** A single **Noul** question; returns just that answer
  (`payload.noul`, `payload.probability`). *Purpose:* a quick boolean judgement with a
  probability. Here it asks whether the ticket is urgent.

- **`/choose` → [Decide] Choose.** A single **Choice** over a fixed option set; returns the
  chosen option and its distribution. *Purpose:* classification / routing into one of N
  labels. Here it routes the ticket to `billing` / `technical` / `account`.

- **`/score` → [Decide] Score.** A single **Score** over ordered levels (a rubric); returns
  the level. *Purpose:* grading on an ordered scale. Here it rates customer sentiment from
  "very negative" to "very positive".

- **`/select` → [Select] Candidate.** Turns a list of **runtime rows** into a dynamic
  Choice — each row becomes an option keyed by `idField` — and returns the selected object,
  its probability/confidence, a no-match flag and the full ranking. *Purpose:* let Jev pick
  the best match from data you fetched (DB rows, Salesforce queues, search hits). Here it
  ranks three support queues against a billing request.

- **`/apply-policy` → [Policy] Apply.** Turns a decision into `ACCEPT` / `REVIEW` /
  `REJECT` plus a `routeKey`, judged against a policy (inline or a question-set's `policy`
  block). Pure local evaluation — no provider call. *Purpose:* governance / human-in-the-loop
  gating. Here it judges a hand-written decision against the `ticket-triage` policy (see
  [§6](#6-offline-testing)).

- **`/decide-chain` → the end-to-end §8.8 chain.** Composes the pieces: **Evaluate** the
  ticket, **Apply Policy** to the result, then a `<choice>` routes on the action (ACCEPT →
  auto, REVIEW → human, REJECT → error). *Purpose:* show the real production shape of a Jev
  flow.

```bash
curl -s localhost:8081/capabilities | jq
curl -s localhost:8081/models       | jq
curl -s localhost:8081/validate     | jq
curl -s localhost:8081/evaluate     | jq
curl -s localhost:8081/ask          | jq
curl -s localhost:8081/choose       | jq
curl -s localhost:8081/score        | jq
curl -s localhost:8081/select       | jq
curl -s localhost:8081/apply-policy | jq
curl -s localhost:8081/decide-chain | jq
```

---

## 6. Offline testing

`/apply-policy` and `/validate` and `/capabilities` make **no** provider call, so they
work without a valid key and without spending credits:

```bash
curl -s localhost:8081/apply-policy | jq   # evaluates an inline decision → REVIEW
```

`/apply-policy` feeds a hand-written `{model, answers}` decision to the `ticket-triage`
policy block. The sentiment answer is level `2`, which the policy lists under
`reviewLevels`, so the whole decision resolves to **REVIEW** (the most cautious action
wins) — a good deterministic smoke test.

The remaining endpoints (`/evaluate`, `/ask`, `/choose`, `/score`, `/select`,
`/decide-chain`) make real OpenRouter calls and consume credits.

---

## 7. Logging

`src/main/resources/log4j2.xml` sends output to the console **and** to
`<mule.home>/logs/jev-dev.log`:

- `jev.demo` — the demo's own before/after flow loggers (INFO).
- `com.mulesoft.connectors.jev` — the connector internals; set to `DEBUG` to see routing,
  failover and retries.
- Uncomment the `HttpMessageLogger` line to see raw provider wire traffic (DEBUG).

---

## 8. What NOT to commit

- `local.properties` — your real API key. Gitignored; keep it that way.
- `.mule/`, `target/`, `.vscode/` — IDE working dirs and build output. Gitignored.

Only `local.properties.example` (a placeholder) is tracked. Before pushing, sanity-check
with `git status` that no `local.properties` or key material is staged.
