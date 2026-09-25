<!-- icon banner: icon/icon.svg -->
# Jev Connector for Mule 4

Jev is a decision model, not a chat model. You give it a **state** plus named, typed
**questions** — Noul (yes/no probability), Choice (option + distribution) or Score
(ordered rubric) — and it returns one typed **answer** per question. This connector makes
those answers first-class Mule values that drive Choice routers, Batch filters, error
handlers and follow-up calls.

> **Status:** early development. Milestone **M0 (skeleton)** is in place; see
> [`PLAN.md`](PLAN.md) §15 for the roadmap and [`CHANGELOG.md`](CHANGELOG.md) for what has landed.

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

## Building

```bash
mvn clean verify
```

The build runs the formatter, import sort and checkstyle gates. To auto-format before
verifying:

```bash
mvn net.revelc.code.formatter:formatter-maven-plugin:format net.revelc.code:impsort-maven-plugin:sort
```

## License

Apache-2.0. See [`LICENSE.txt`](LICENSE.txt).
