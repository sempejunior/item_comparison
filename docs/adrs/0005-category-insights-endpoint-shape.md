---
id: ADR-0005
title: Category insights ship as a dedicated endpoint, not as a parameter on /compare
status: Accepted
date: 2026-04-30
deciders: backend
supersedes: []
superseded_by: []
related: [SPEC-005, SPEC-001, SPEC-003, SPEC-004]
---

# Context

SPEC-005 introduces a third comparison surface — *category insights* —
answering the shopper question *"what does the whole category look
like?"*. Two implementation shapes were on the table:

1. **Param on `/compare`.** Reuse `GET /api/v1/products/compare` and
   accept a new `category=<enum>` parameter (mutually exclusive with
   `ids`). The response would acquire a polymorphic shape: when
   `ids` is present, today's `items[] + differences[]`; when
   `category` is present, the new `rankings[] + topItems[] + coverage`
   shape from SPEC-005 §5.
2. **Dedicated endpoint.** New
   `GET /api/v1/products/category-insights?category=&topK=&language=`
   alongside `/compare`, with its own controller, response DTO, prompt,
   cache and validation rules.

This decision is dense enough to deserve a record because the next
slice's planning, the OpenAPI surface, and the LLM cost accounting all
depend on it.

# Decision

**Ship category insights as a dedicated endpoint
(`/api/v1/products/category-insights`).** `/compare` keeps its current
contract verbatim (FR-10: `ids` 2..10, required) — no overload, no
mutually-exclusive parameter dance.

# Rationale

Five concrete divergences make the dedicated endpoint cheaper:

1. **Response shape is fundamentally different.** Pairwise
   `items[] + differences[]` ranks attributes across a small,
   user-picked set; category `rankings[] + coverage + topItems` ranks
   attributes across the whole catalog with deliberate denormalization
   (winner `name`, runner-up, spread). Forcing both into a single DTO
   produces optional fields that mean different things in different
   modes — exactly the polymorphism the existing OpenAPI doc avoids.
2. **Prompt is fundamentally different.** `compare-summary.v1.md`
   addresses N items pairwise; `category-insights.v1.md` (SPEC-005
   FR-9) addresses a category panorama. Sharing one prompt would
   require runtime branching on payload shape, defeating the
   single-responsibility framing that makes the golden test cheap.
3. **Cache scope is different.** `ai-summary` keys on
   `(sortedIds, fields, language)`; insights key on
   `(category, topK, language)`. They do not collide and do not need
   the same TTL/size — surfacing them as two named caches keeps
   eviction tunable per use-case (SPEC-005 FR-10).
4. **Validation rules are different.** `/compare` requires `ids`
   (2..10), forbids `category`. Insights require `category`, forbid
   `ids`, and accept `topK` (1..20). Wiring both through one
   `@RequestParam`-bearing controller method would push branching
   logic into the controller, exactly the pattern PLAN §4 keeps out
   of controllers (advice-only error handling).
5. **OpenAPI clarity.** Two endpoints surface as two operations in
   Swagger UI with worked examples; one polymorphic endpoint surfaces
   as one operation whose response schema must enumerate both shapes
   under `oneOf`. Reviewers reading the doc see the second AI
   use-case faster when it is its own entry (NFR-3).

The shared infrastructure (`PromptLoader`, `AiMetrics`,
`DailyBudget`, `ChatModel`) is reused regardless — both endpoints
draw from the same in-process daily budget and the same metrics with
distinct `kind` tag values (`summary`, `category_insights`). The
duplication cost is one new controller + one new service + one new
DTO family. That is paid back the first time `/compare` evolves
without having to think about category mode, and the first time an
insights-only feature (e.g. category-level filters) lands without
having to extend `/compare`.

# Consequences

- A new controller `CategoryInsightsController` and interface
  `CategoryInsightsApi` (OpenAPI annotations) live alongside
  `CompareController` / `CompareApi`. ADR-0003's package layout is
  honored — both go under `controller/` and `controller/api/`.
- A new `service/insights/` sub-package holds
  `CategoryInsightsService`. `service/compare/` is untouched.
- `SummaryService` gains a second public method
  `summariseCategoryInsights(...)` (different prompt, different
  cache, different metric `kind`). The LLM invocation, fallback
  taxonomy, and budget enforcement are reused — the daily budget
  ceiling is shared across both kinds (SPEC-005 C-3).
- `application.yml` gets a third Caffeine cache name
  `ai-category-insights` with the same defaults as `ai-summary`.
- `attribute-metadata.json` gains a top-level
  `categoryRankings` map; `AttributeMetadata` loader fails fast at
  boot if any `Category` enum value lacks an entry — see
  Q-impl-2 in `plan-slice4.md`.
- The roadmap's R-9 (free-form natural-language category exploration)
  remains plugged into `CategoryInsightsService` rather than into
  `/compare`. R-9.1 calls into the same service the dedicated endpoint
  uses; the entry point changes, the engine does not.

# Alternatives considered

- **Param on `/compare` (option 1).** Rejected for the five reasons
  above. The only argument for it was symmetry — *"both are
  comparisons"* — but the response shapes prove the symmetry is
  superficial.
- **GraphQL-style single endpoint with field selection.** Out of
  scope (roadmap §"What we explicitly do not plan to do"). Adds
  query infrastructure for what is two REST operations.
- **Two endpoints sharing one DTO via `oneOf`.** Equivalent to
  option 1 from the client's perspective. Rejected for the same
  OpenAPI clarity reason.
