---
id: PLAN-SLICE-4
title: Implementation Plan — Slice 4 (Category Insights)
version: v1
status: Accepted
last_updated: 2026-04-30
depends_on: [SPEC-005, PLAN, ADR-0003, ADR-0005]
---

# PLAN — Slice 4 (Category Insights)

This is a *slice plan*. The main `plan.md` v2 covers SPEC-001..004 and
remains the system-wide reference. This document narrows the lens to
SPEC-005: how the new endpoint lands inside the established skeleton,
which packages absorb which clauses, and which decisions are pinned so
implementation does not re-debate them.

The shipping unit is **one PR**: `feat/slice4-category-insights → main`.
No micro-PRs. T-24..T-30 are commits inside that PR.

## 0. Ground rules (inherited)

- Root package stays `com.hackerrank.sample` (ADR-0003).
- Every commit ≈ one task. Tests in the same commit as the production
  code they cover.
- `mvn verify` green at every commit; JaCoCo gate ≥ 80 % stays armed.
- No new top-level packages — new sub-package `service/insights/` per
  ADR-0005.

## 1. Pinned decisions

These resolve the two `Q-impl` items left open in `HANDOFF.md §9.2.6`.

### Q-impl-1 — `SummaryService` reuse vs new class

**Decision.** Add a second public method
`summariseCategoryInsights(category, productCount, rankings, topItems,
language)` on the existing `SummaryService`. **Do not** create a
parallel `CategoryInsightsSummaryService`.

**Why.** The fallback taxonomy, timeout enforcement, daily budget,
HTTP error classification and `keyInvalidForBoot` flag are identical
across both call kinds. Duplicating ~120 lines of private helpers into
a sibling class adds coupling that has to be kept in lock-step. A
second method on the same class costs:

- two new constants (`CACHE_NAME_INSIGHTS`,
  `PROMPT_TEMPLATE_INSIGHTS`),
- a new `cacheKey` overload,
- a new `slim` payload helper,
- one extra `kind` tag value on the existing meters
  (`AiMetrics.KIND_INSIGHTS`).

The class moves from ~320 to ~440 lines. Acceptable. Extracting an
`LlmInvoker` collaborator to host the shared helpers is a clean
refactor but is **not** in this slice — it would expand scope without
shipping value. Logged as a Slice 5 candidate.

### Q-impl-2 — `categoryRankings` location

**Decision.** Add a top-level `categoryRankings` object to the
existing `attribute-metadata.json`. Extend `AttributeMetadata` to load
it and **fail fast at boot** if any value of the `Category` enum is
missing an entry.

**Why.** SPEC-005 §5.2 explicitly references the metadata file. Keeps
runtime configuration in one resource and avoids a second
hot-reloadable place to forget. The boot-time check protects against
the silent drift that would otherwise happen when a sixth category is
added to the enum without updating the rankings list.

**Shape:**

```json
{
  "attributes": {
    "battery": { "direction": "higher_better" }
  },
  "categoryRankings": {
    "SMARTPHONE":   ["buyBox.price", "rating", "attributes.battery", "attributes.memory", "attributes.storage"],
    "SMART_TV":     ["buyBox.price", "rating", "attributes.screen_size_inches", "attributes.refresh_rate_hz"],
    "NOTEBOOK":     ["buyBox.price", "rating", "attributes.memory", "attributes.storage", "attributes.weight"],
    "HEADPHONES":   ["buyBox.price", "rating", "attributes.battery", "attributes.weight"],
    "REFRIGERATOR": ["buyBox.price", "rating", "attributes.capacity_l", "attributes.energy_class"]
  }
}
```

Paths use the same dot-notation that `DifferencesCalculator` already
parses (`buyBox.price`, `attributes.<key>`). Attributes referenced
here that lack a `direction` in `attributes` map are **logged once at
warn level** and skipped at request time (SPEC-005 FR-4) — they do
not crash the boot. Only enum coverage is fail-fast.

## 2. Spec-to-package mapping (delta)

```
com.hackerrank.sample
├── controller/
│   ├── CategoryInsightsController.java   ← NEW (T-27)
│   └── api/
│       ├── CategoryInsightsApi.java         ← NEW (T-27): OpenAPI annotations
│       └── CategoryInsightsApiExamples.java ← NEW (T-27): @ExampleObject bodies
│
├── service/
│   ├── insights/                          ← NEW sub-package
│   │   └── CategoryInsightsService.java  ← NEW (T-26)
│   ├── compare/
│   │   └── AttributeMetadata.java         ← EXTENDED (T-24): categoryRankings + boot-time validation
│   └── ai/
│       └── SummaryService.java            ← EXTENDED (T-28): summariseCategoryInsights(...)
│
└── model/
    └── insights/                          ← NEW sub-package for response DTOs
        ├── CategoryInsightsResponse.java ← NEW (T-25): top-level response
        ├── RankingEntry.java              ← NEW (T-25)
        ├── Coverage.java                  ← NEW (T-25)
        ├── RankedItem.java                ← NEW (T-25): winner / runner-up shape
        ├── Spread.java                    ← NEW (T-25)
        └── TopItem.java                   ← NEW (T-25): topItems[] entry
```

Resources:

```
src/main/resources/
├── attribute-metadata.json     ← EXTENDED (T-24)
├── application.yml              ← EXTENDED (T-28): ai-category-insights cache name
└── prompts/
    └── category-insights.v1.md ← NEW (T-28)

src/test/resources/prompts/golden/
└── category-insights.v1.txt    ← NEW (T-28): pinned LLM stub output
```

## 3. Cross-cutting

### 3.1 Errors

`GlobalExceptionHandler` already maps every exception SPEC-005 FR-12..14
needs:

| FR     | Exception path                                                   | Slug         | Status |
|--------|------------------------------------------------------------------|--------------|--------|
| FR-12  | missing `category` → `MissingServletRequestParameterException`   | `bad-request`| 400    |
| FR-12  | empty `category=` → `MethodArgumentTypeMismatchException`        | `bad-request`| 400    |
| FR-12  | unknown enum value → `MethodArgumentTypeMismatchException`       | `bad-request`| 400    |
| FR-13  | `topK` outside 1..20 → `ConstraintViolationException`            | `validation` | 400    |
| FR-14  | invalid language tag → `InvalidLanguageException` (existing)     | `bad-request`| 400    |

No new exception classes. The advice already returns RFC 7807. The
only edit to `GlobalExceptionHandler` is an OpenAPI `@ApiResponse`
annotation update (T-27) so the new endpoint surfaces error examples
in Swagger UI.

### 3.2 Caching

A third Caffeine cache `ai-category-insights` is added in
`application.yml` (T-28) with the same shared spec
(`maximumSize=1000,expireAfterWrite=5m,recordStats`). The deterministic
`rankings[]/topItems[]` are **not** cached in v1 (SPEC-005 §7) — only
the LLM `summary` payload is cached, keyed on
`(category, topK, language)`.

### 3.3 Observability

No new meter names. The existing
`ai_calls_total{kind, outcome}`,
`ai_latency_seconds{kind}`,
`ai_tokens_total{kind, direction}`,
`ai_fallback_total{reason}`
gain a new value on the `kind` tag: `category_insights`. Cache-hit
accounting follows the OBS-5 fix from Slice 3 — counts as
`outcome=cache_hit`, **does not** increment `ai_fallback_total`.

## 4. Build order

| ID    | Title                                                              | Spec clauses                              | Blocked by |
|-------|--------------------------------------------------------------------|-------------------------------------------|------------|
| T-24  | Extend `attribute-metadata.json` + `AttributeMetadata` (rankings + boot-time enum check) | SPEC-005 FR-4, FR-5, §5.2; PLAN-S4 §1.Q-impl-2 | —          |
| T-25  | Insights DTOs (`CategoryInsightsResponse`, `RankingEntry`, `Coverage`, `RankedItem`, `Spread`, `TopItem`) | SPEC-005 FR-2, FR-5; SPEC-003 §4 | T-24       |
| T-26  | `CategoryInsightsService` (deterministic rankings + topK heuristic) | SPEC-005 FR-3..7, AC-1, AC-7, AC-8, AC-11 | T-24, T-25 |
| T-27  | `CategoryInsightsController` + `CategoryInsightsApi` + advice wiring + MockMvc | SPEC-005 FR-1, FR-12..15, AC-1, AC-2, AC-5, AC-6, AC-9 | T-26       |
| T-28  | Prompt v1 + golden + `SummaryService.summariseCategoryInsights` + `ai-category-insights` cache + `kind=category_insights` metrics | SPEC-005 FR-8..11, AC-3, AC-4, AC-12      | T-27       |
| T-29  | Wire `summary` into `CategoryInsightsController` + LLM-on/off MockMvc | SPEC-005 FR-2, AC-3, AC-4              | T-28       |
| T-30  | README + testing-guide + smoke + `SUBMISSION.md` + `TASKS.md` v4 + JaCoCo verify | NFR-2, NFR-3, AC-9, AC-10            | T-29       |

Each task ends with `mvn verify` green. JaCoCo gate stays at ≥ 80 %.

### Definition of Done (per task)

Same as `plan.md §15`:
- production code matches the FR/NFR clauses listed;
- unit + MockMvc tests live in the same commit;
- `mvn verify` green;
- JaCoCo on changed packages ≥ 80 %;
- public API surfaces in OpenAPI annotations and Swagger UI.

## 5. Smoke checklist (executed before opening the PR, on `:8081`)

Without `OPENAI_API_KEY`:

- `GET /api/v1/products/category-insights?category=SMARTPHONE` → 200,
  `productCount=10`, `rankings[].length ≥ 5`, every `coverage` populated,
  `topItems[].length ≤ 5`, **no `summary`** field.
- Same call repeated → byte-identical body (NFR-6 / AC-11).
- `?category=SMARTPHONE&topK=10&language=en` → 200, `topItems` up to 10,
  no `summary`.
- `?category=BOGUS` → 400 `bad-request`.
- `?category=` (empty) → 400 `bad-request` (type mismatch path).
- *omit* `category` → 400 `bad-request` / `validation` (whichever the
  advice resolves to for missing required param — record actual slug).
- `?category=SMARTPHONE&topK=21` → 400 `validation`.
- `?category=SMARTPHONE&language=zz` → 400 `bad-request`.
- Regression: `/api/v1/products`, `/products/{id}`, `/products/compare`
  (happy + cross-category + lang) all still 200.

With `OPENAI_API_KEY`:

- First call → 200, `summary` populated; metrics show
  `ai_calls_total{kind=category_insights, outcome=ok}`.
- Same call → 200, `summary` identical; metrics show
  `outcome=cache_hit`; `ai_fallback_total{reason=cache_hit}` **not**
  incremented (AC-12).
- `?language=en` → English summary.

Server stopped after smoke; report stop in commit body.

## 6. Out of scope (locked, do not absorb mid-slice)

- `?category=X,Y` cross-category insights (SPEC-005 §7).
- Faceted filtering inside insights — roadmap R-9 candidate.
- Free-form natural-language `q=` — roadmap R-9.1 / R-9.2.
- Streaming `summary`.
- Caching `rankings[]` / `topItems[]`.
- Personalized ranking weights — roadmap R-4.
- Per-offer insights.
- Multi-currency normalization — roadmap R-8.

If a need for any of the above appears mid-slice, it goes into
`docs/roadmap.md`, not into a new T-2X.

## 7. Risks

| Risk                                                                                                                | Mitigation                                                                                                   |
|---------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Boot-time enum check fails after future enum addition without metadata update                                       | That **is** the intended behavior (fail fast). Documented in T-24 commit + README.                            |
| `SummaryService` grows past readability                                                                             | Accepted for Slice 4. Slice 5 extracts `LlmInvoker` collaborator if total length crosses ~500 lines.          |
| `attributes.energy_class` (REFRIGERATOR) has no `direction` because it is ordinal-string                            | Skipped with a one-time warn at boot per FR-4 wording; ranking entry surfaces with `isComparable=false`.      |
| Prompt drifts without updating golden                                                                               | Golden test pins rendered output for fixed input — same regression guard as `compare-summary.v1.md`.          |
| LLM cost spike from category insights repeatedly hitting same `(category, topK, language)`                          | Cache hits are free; cache TTL 5 m matches `ai-summary`. Daily budget is shared (SPEC-005 C-3).               |
| OpenAPI `oneOf` complexity                                                                                          | None — dedicated endpoint avoids polymorphic schema (ADR-0005).                                                |

## 8. Changelog

- **v1 (2026-04-30)** — Initial slice plan derived from SPEC-005 v2,
  ADR-0005, HANDOFF §9.2.5..§9.2.7. Q-impl-1 resolved (extend
  `SummaryService`); Q-impl-2 resolved (`categoryRankings` in JSON +
  boot-time enum coverage check).
