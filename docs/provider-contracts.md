# Provider wire contracts

Verified wire notes per route, per PLAN.md §3. This file is the source of truth
for anything the connector encodes about a provider. **Never invent a provider
field.** When a detail is marked *verify*, confirm it with a live call or an
official doc and record the result here **before** coding against it.

Last full review of the sources below: **2026-09-25**.

## Canonical request/response — TypeSafe `systemOne`

- **Endpoint:** `POST {baseUrl}/v1/systemone`
- **Request:** `state` (string | object | array, required), `model` (required),
  `questions` (non-empty map `id -> question`). A question is `type`
  (`noul` | `choice` | `score`) + `instructions` (string | object | array) + `criteria`.
- **Criteria:** Noul optional `{"true": …, "false": …}`; Choice required map
  `option -> description | null`, max 255 options; Score required ordered array of 2–10 levels.
- **Response:** `model`, `answers` (same ids), `usage.input_tokens`, `usage.output_tokens`.
  - Noul = `noul`.
  - Choice = `choice`, `probabilities`, `confidence`.
  - Score = `score` (may fall between levels), `legend`, `probabilities`, `confidence`.
- **Errors:** 401 bad key, 422 validation, 429 rate limit, 529 overloaded. Message
  extraction order (as the official SDK): `error` (string) → `error.message` →
  `message` → `detail` (string) → `detail.message` → `detail[].msg` joined with `loc`.
- **Headers:** request id `x-typesafe-request-id`; retry hints `retry-after-ms`
  (milliseconds, checked first) then `retry-after` (seconds or HTTP date).
- **Models:** `GET {baseUrl}/v1/models` → `{"models":[{"name","description","release_date"}]}`.

## Routes

| Route | Base URL | Auth | Model id | Deviations | Cost signal |
| --- | --- | --- | --- | --- | --- |
| `typesafe` | `https://api.typesafe.ai` | `Bearer` TypeSafe key | `jev-latest`, `jev-1.13.0` | none | tokens only; estimate |
| `openrouter` | `https://openrouter.ai/api` | `Bearer` OpenRouter key | `~typesafe/jev-latest`, `typesafe/jev-1.13` | alpha Decisions API exists, unused | `usage.cost` (USD) |
| `vercel` | `https://ai-gateway.vercel.sh/typesafe` | `Bearer` AI Gateway key / OIDC | `typesafe-ai/jev` | adds `provider_metadata.gateway`; errors `{message, error_type}` | `provider_metadata.gateway.cost` (string USD) |
| `compatible` | configurable | `Bearer` key | configurable | per gateway | estimate |
| `cloudflare` | `https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run` | `Bearer` API token | `typesafe/jev` | body wraps `{model, input:{state, questions}}`; no model list | estimate |

**Cloudflare envelope:** accept both the bare TypeSafe response and a `result`-wrapped
v4 envelope (`result`, `success`, `errors`). Treat `success:false` or a non-empty
`errors` array as a provider error.

## Local limits (fail fast, no billed call)

- `questions` non-empty; each has `type` and `instructions`.
- Choice ≤ 255 options; Score 2–10 levels.
- Context window 32,000 tokens (state + questions) on Cloudflare and OpenRouter; WARN
  when the serialized request exceeds the configurable character budget (default 100,000).

## Pricing

Input tokens only, output free. OpenRouter lists $0.042 / M input tokens. Prefer the
provider-reported cost (OpenRouter, Vercel); otherwise `tokens × configured price`.
`attributes.costSource` records which (`PROVIDER` vs `ESTIMATE`).

## Open *verify* items (tracked to milestones)

| # | Item | Milestone | Status |
| --- | --- | --- | --- |
| Q2 | Cloudflare live response envelope (bare vs `result`) | M2 | **resolved** — `CloudflareAdapter.unwrap` accepts both; `success:false` or a non-empty `errors` array becomes `JEV:PROVIDER_ERROR` |
| Q3 | Vercel `confidence` present on Choice/Score | M2 | **resolved by contract** — `confidence` is passed through unchanged; when a route omits it the field is `null` (no live Vercel key available to confirm presence; behaviour is correct either way) |
| Q5 | OpenRouter / Cloudflare request-id header | M2 | **resolved (best-effort, pending live confirmation)** — OpenRouter reads header `x-request-id`; Cloudflare exposes none (`RequestIdExtractor.NONE`). Revisit if a live call reveals a different header |
| Q8 | Value provider can read app question-set files at design time | M3 | open |
| Q9 | Computed `min.mule.version` with sdk-api HTTP client | M0 | see below |

### Q9 — computed minimum Mule version (M0)

`min.mule.version` is pinned to **4.9.0**. If the extension build computes a higher
per-component minimum, the API that raised it is recorded here and the owner is
consulted before raising the floor. _(No override recorded yet.)_
