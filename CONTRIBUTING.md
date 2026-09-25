# Contributing to the Jev Connector

Thanks for your interest in improving the Jev Connector. This document explains
how to build the project, the quality bar every change must clear, and how to
propose a change.

## Prerequisites

- JDK 17
- Maven 3.9+
- Access to the MuleSoft public Maven repository (declared in `pom.xml`)

## Building

```bash
mvn clean verify
```

`verify` compiles the extension, generates the extension model, runs the unit
tests, and enforces every quality gate. A change is only ready when this command
is green.

## Quality gates

The build fails on any of the following, so run them before opening a pull
request:

- **Formatting** — `formatter-maven-plugin` validates against `formatter.xml`
  (2-space indent, 120-column lines). Auto-format with:
  ```bash
  mvn net.revelc.code.formatter:formatter-maven-plugin:format \
      net.revelc.code:impsort-maven-plugin:sort
  ```
- **Import order** — `impsort-maven-plugin` enforces the groups
  `org.mule.`, `com.mulesoft.`, `java.`, `javax.`.
- **Checkstyle** — no star imports, redundant imports, or unused imports.
- **Tests** — JUnit 5 with Mockito. Pure logic (serialization, error mapping,
  retry timing, derivation, validation) is unit-tested without a Mule runtime;
  the HTTP layer is tested by mocking the transport.

## Design conventions

- Use the forward-compatible `org.mule.sdk.api.*` namespace, not the legacy
  `org.mule.runtime.extension.api.*` namespace.
- Operations that perform I/O are non-blocking: they use `sendAsync` with a
  `CompletionCallback` and never sleep a runtime thread.
- All JSON goes through `internal/util/Json`. Provider-reported fields and
  connector-computed fields (`derived`) are kept separate.
- Errors surface as typed `JEV:*` errors via `ModuleException`.

## Milestones

Development follows the milestone order in `PLAN.md` (M0 → M5). Each milestone
ends green on `mvn clean verify`.

## Submitting changes

1. Create a branch off the default branch.
2. Make your change with matching tests.
3. Ensure `mvn clean verify` is green.
4. Open a pull request describing the change and the milestone it advances.
