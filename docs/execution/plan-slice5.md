---
id: PLAN-SLICE-5
title: Implementation Plan — Slice 5 (Insights filters)
version: v1
status: Accepted
last_updated: 2026-04-30
depends_on: [SPEC-005, ADR-0003, ADR-0005, ADR-0006]
---

# PLAN — Slice 5 (Insights filters)

Slice plan narrowed to SPEC-005 v5 §5.6 (`minPrice`, `maxPrice`,
`minRating`). Single-PR shipping unit:
`feat/slice5-insights-filters → main`. T-31..T-35 are commits inside
that PR.

## 0. Ground rules (inherited)

- Root package `com.hackerrank.sample` (ADR-0003) — no new top-level
  package; new code lives under existing `service/insights/`.
- One task ≈ one commit. Tests in the same commit as the production
  code they cover.
- `mvn verify` green at every commit; JaCoCo gate ≥ 80 % stays armed.
- Filters apply **in-memory** in the service layer (ADR-0006). No JPA
  push-down in this slice.

## 1. Pinned decisions

| ID         | Decision                                                                                                       |
|------------|----------------------------------------------------------------------------------------------------------------|
| Q-impl-1   | Filters live in a package-private `InsightsFilters` record alongside `Picks` (`service/insights/`). Same package boundary as v4. |
| Q-impl-2   | `InsightsFilters.from(minPrice, maxPrice, minRating)` returns `null` when all three are null — preserves v4 hot path with zero allocation. |
| Q-impl-3   | `productCount` semantics shift: filtered slice (SPEC-005 v5 FR-2). Documented in changelog; controller-level test pins the change. |
| Q-impl-4   | `appliedFilters` field added to `CategoryInsightsResponse` as a nullable record; absent (omitted via `@JsonInclude(NON_NULL)`) when no filter is set. v4 wire format preserved verbatim for unfiltered calls. |
| Q-impl-5   | Cache key extended with `filtersHash` (empty string when unfiltered). v4 cache entries remain valid because the empty-hash key matches the v4 key shape. |
| Q-impl-6   | Cross-field `minPrice ≤ maxPrice` enforced via class-level Bean Validation constraint on a request bean (`@FilterBoundsConsistent` on `InsightsFiltersRequest`). Single 400 path through `GlobalExceptionHandler` advice. |
| Q-impl-7   | Prompt template stays at `category-insights.v3.md`. The applied-filter block is rendered conditionally by `SummaryService` and appended to the existing context. No new prompt file. Cache key prefix bumps from `v3|` to `v3.1|` only when filters are present, so unfiltered cache entries survive. |

## 2. Spec-to-package mapping (delta)

```
com.hackerrank.sample
├── controller/
│   ├── CategoryInsightsController.java   ← TOUCHED (T-31): forwards filters
│   └── api/
│       ├── CategoryInsightsApi.java         ← TOUCHED (T-31): @RequestParam + Bean Validation
│       └── CategoryInsightsApiExamples.java ← TOUCHED (T-31): three new error examples
│
├── model/insights/
│   ├── AppliedFilters.java               ← NEW (T-31): response echo record
│   └── CategoryInsightsResponse.java     ← TOUCHED (T-31): + appliedFilters (nullable)
│
└── service/insights/
    ├── InsightsFilters.java              ← NEW (T-32): predicate + digest + describe
    └── CategoryInsightsService.java      ← TOUCHED (T-32, T-33): apply filter, thread to summary
```

Resources / templates: no new files. `category-insights.v3.md` stays;
`SummaryService` injects the `applied filters` block at render time
when the filter object is non-null.

## 3. Cross-cutting

### 3.1 Errors

`GlobalExceptionHandler` already handles every shape SPEC-005 v5 FR-20
needs:

| Failure                                | Exception path                            | Slug         | Status |
|----------------------------------------|-------------------------------------------|--------------|--------|
| `minPrice < 0`                         | `ConstraintViolationException`            | `validation` | 400    |
| `maxPrice < 0`                         | `ConstraintViolationException`            | `validation` | 400    |
| `minRating` outside `[0, 5]`           | `ConstraintViolationException`            | `validation` | 400    |
| `minPrice > maxPrice`                  | `ConstraintViolationException` (class-level) | `validation` | 400    |
| Non-numeric value (`minPrice=abc`)     | `MethodArgumentTypeMismatchException`     | `bad-request`| 400    |

No new exception classes. No advice changes.

### 3.2 Caching

- Cache name `ai-category-insights` unchanged.
- Key adds `filtersHash` as the eighth component (FR-10). Empty
  string when no filter is set, so v4 entries remain reachable.
- Per-key behavior: each `(category, topK, language, filters)`
  combination is a distinct entry. Acceptable at this catalog size;
  the 30 % miss-rate trigger in ADR-0006 monitors when this stops
  being acceptable.

### 3.3 Observability

No new meter names, no new tag values. The `filtersHash` component
of the cache key is enough to keep cache hit/miss accounting clean
on the existing `kind=category_insights` tag.

## 4. Build order

| ID    | Title                                                                                | Spec clauses                       | Blocked by |
|-------|--------------------------------------------------------------------------------------|------------------------------------|------------|
| T-31  | Controller params + Bean Validation + DTO `AppliedFilters` + response field          | FR-16, FR-20; AC-15                | —          |
| T-32  | `InsightsFilters` record + service-layer application + `productCount` shift          | FR-16, FR-17, FR-19; AC-13, AC-14  | T-31       |
| T-33  | `SummaryService.summariseCategoryInsights` accepts filters; renders applied-filter block; cache key extended | FR-18, FR-10                       | T-32       |
| T-34  | Tests: controller validation MockMvc; service unit (filter + edge cases); summary cache-key test | NFR-2, NFR-6; AC-13..AC-15        | T-33       |
| T-35  | README + `SUBMISSION.md` + `TASKS.md` rows + smoke live (LLM on/off) + `mvn verify`  | NFR-3, AC-9, AC-10                 | T-34       |

### Definition of Done (per task)

Inherited from `plan.md §15` and `plan-slice4.md §4`:

- production code matches the FR clauses listed;
- tests in the same commit;
- `mvn verify` green;
- JaCoCo on changed packages ≥ 80 %;
- public API surfaces in OpenAPI annotations and Swagger UI.

## 5. Smoke checklist (executed before opening the PR, on `:8081`)

Without `OPENAI_API_KEY`:

- `?category=SMARTPHONE&minRating=4.5` → 200, `appliedFilters.minRating=4.5`,
  `productCount` ≤ unfiltered count, every `topItems[].rating ≥ 4.5`.
- `?category=SMART_TV&minPrice=2000&maxPrice=4000` → 200,
  `appliedFilters.minPrice/maxPrice` echoed, every `topItems[].price`
  in `[2000, 4000]`, products without `buyBox` excluded.
- `?category=SMARTPHONE` (no filter) → response **byte-identical to v4**
  (regression check: no `appliedFilters` field in the JSON).
- `?category=SMARTPHONE&minPrice=-1` → 400 `validation`.
- `?category=SMARTPHONE&minPrice=3000&maxPrice=2000` → 400 `validation`
  (cross-field message).
- `?category=SMARTPHONE&minRating=6` → 400 `validation`.
- `?category=NOTEBOOK&minPrice=999999` (filter zeroes pool) → 200,
  `productCount=0`, `rankings: []`, `topItems: []`, no `summary`.
- Regression: `/api/v1/products`, `/products/{id}`, `/products/compare`
  unchanged.

With `OPENAI_API_KEY`:

- Filtered call → 200, `summary` opens by acknowledging the filter.
- Same call repeated → cache hit, `summary` byte-identical.
- Unfiltered call after filtered → cache hit on the v4 entry (key
  shape preserved by empty `filtersHash`).

Server stopped after smoke; report stop in commit body.

## 6. Out of scope (locked)

- `condition` / `brand` filters — roadmap R-10.
- Per-attribute numeric ranges (`attributes.batteryMah ≥ X`) — not
  planned.
- JPA push-down of filters — roadmap R-11.2.
- Materialized rankings — roadmap R-11.3.
- Free-form natural-language `q=` — roadmap R-9.

## 7. Risks

| Risk                                                                                       | Mitigation                                                                                                       |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `productCount` semantic shift breaks an existing client that relied on v4 wording          | v4 unfiltered call is byte-identical (filter object null ⇒ stream skipped ⇒ same value). Documented in changelog. |
| Cache key extension invalidates v4 entries                                                  | `filtersHash` is the empty string when unfiltered; key shape matches v4. Pinned by a test.                       |
| LLM hallucinates and ignores the applied-filter block                                       | Prompt block is required in the rendered prompt when filters are set; few-shots from v3 already constrain tone. Smoke verifies the summary acknowledges the filter. |
| Class-level Bean Validation constraint adds annotation processor overhead at compile time   | Hibernate Validator already on classpath (Spring Boot starter). No new annotation processor.                     |
| Filter combinations explode the cache                                                       | Already documented in ADR-0006 §"Scale-up path"; monitored by `cache_misses_total{cache=ai-category-insights}`.  |

## 8. Changelog

- **v1 (2026-04-30)** — Initial slice plan derived from SPEC-005 v5
  and ADR-0006. Q-impl-1..7 pinned. T-31..T-35 sequenced. Smoke
  checklist mirrors the pattern from `plan-slice4.md §5` plus the
  three new validation cases.
