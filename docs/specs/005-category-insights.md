---
id: SPEC-005
title: Category Insights
version: v5
status: Accepted
last_updated: 2026-04-30
depends_on: [SPEC-001, SPEC-002, SPEC-003, SPEC-004]
---

# SPEC-005 — Category Insights

## 1. Context

`/api/v1/products/compare` answers the question *"how do these N specific
items stack up against each other?"* The user picks 2–10 ids and gets a
deterministic diff plus an optional LLM summary.

A second, equally common shopper question is unanswered today:

> *"What does the **whole category** look like? Which product wins on
> each attribute that matters?"*

Today the only way to approximate this is to call `/products?category=X`
and then issue a multi-id `/compare` for the whole list — which fails the
moment the category exceeds 10 items (FR-10) and produces a diff shape
that does not scale (`differences[]` was designed for pairwise
distinctions, not category-wide rankings; the LLM prompt assumes 2–10
items in pairwise terms).

This spec adds a third comparison surface — **category insights** — that
answers the category-wide question with a deterministic ranking per
attribute and a category-level natural-language summary, reusing the
existing AI infrastructure (`SummaryService`, Caffeine cache, daily
budget, fallback policy).

## 2. Goal

Serve a category-wide overview a shopper can read in 10 seconds:

1. The **best item per attribute that matters** (best price, best
   battery, best rating, etc.) — deterministic, always present.
2. A **panorama summary** in natural language describing the category's
   landscape ("the cheap-but-capable bracket is dominated by X and Y;
   premium tier splits between A and B"), produced opportunistically by
   the LLM with the same graceful-fallback contract as `/compare`.
3. A small set of **representative items** so the frontend can show a
   concrete picker alongside the rankings.

Non-goals: search, faceted filtering beyond `category`, pagination of
the ranking itself (top-K is enough), per-offer rankings, multi-currency
normalization (R-8).

## 3. Stakeholders

| Stakeholder            | Interest                                              |
|------------------------|-------------------------------------------------------|
| End user (shopper)     | "What's the lay of the land for category X?" answer in one call |
| Frontend developer     | Stable contract; reusable picker data                  |
| Challenge reviewer     | Sees a second, deeper AI use-case beyond pairwise summary |

## 4. User stories

- **US-1** — As a shopper, I can ask the API for a panorama of a single
  category and get a ranking of the best item per attribute that matters
  (price, rating, and the top numeric attributes for that category).
- **US-2** — As a shopper, I can read a short natural-language overview
  of the category in `pt-BR` or `en`.
- **US-3** — As a frontend dev, I can render a "category highlights"
  module without first calling `/products?category=X` and then a second
  `/compare`.
- **US-4** — As a frontend dev, I can ask for the top-K representative
  items inline with the rankings.

## 5. Functional requirements

### 5.1 Endpoint shape

- **FR-1** — A new endpoint `GET /api/v1/products/category-insights`
  accepts a required `category` query parameter and an optional `topK`
  (default `5`, range `1..20`) and an optional `language` (default
  `pt-BR`, also `en`). It does **not** accept `ids` — that is what
  `/compare` is for.
- **FR-2** — The response always carries:
  - `category` — echoed.
  - `productCount` — number of catalog products considered after any
    structured filters from §5.6 are applied. When no filter is set,
    this is the full count for the category at request time. The field
    intentionally reflects the *analyzed slice*, not the underlying
    category cardinality, so the frontend can show "12 of N products
    matched your filters" without a second call.
  - `appliedFilters` — echo of the structured filters that narrowed the
    slice (`minPrice`, `maxPrice`, `minRating`); present only when at
    least one filter was supplied. Absent (omitted from JSON) when the
    request carried no filter — the existing client contract is
    preserved unchanged for unfiltered calls.
  - `rankings[]` — one entry per ranked attribute (see §5.2).
  - `topItems[]` — `topK` representative items in summary projection
    (same shape as the list endpoint's items: `id`, `name`, `category`,
    `imageUrl`, `buyBox.price + currency`, `rating`).
  - `language` — echoed.
  - Optional `summary` — natural-language overview, present only when
    the LLM is configured and reachable. Fallback policy identical to
    SPEC-004 §6.
- **FR-3** — When the category contains fewer than 2 catalog products,
  `rankings[]` is `[]` and `summary` is omitted (a one-product
  "ranking" is meaningless and the LLM has nothing to compare). The
  endpoint still returns 200 so the frontend can render an empty state.

### 5.2 Rankings (deterministic)

- **FR-4 — Ranked attributes.** For each category, a fixed set of
  *ranked* attributes is configured. v1 ships:

  | Category      | Ranked attributes                                          |
  |---------------|------------------------------------------------------------|
  | SMARTPHONE    | `buyBox.price`, `rating`, `attributes.battery`, `attributes.memory`, `attributes.storage` |
  | SMART_TV      | `buyBox.price`, `rating`, `attributes.screen`, `attributes.refresh_rate` |
  | NOTEBOOK      | `buyBox.price`, `rating`, `attributes.memory`, `attributes.storage`, `attributes.weight` |
  | HEADPHONES    | `buyBox.price`, `rating`, `attributes.battery`, `attributes.weight` |
  | REFRIGERATOR  | `buyBox.price`, `rating`, `attributes.capacity`, `attributes.energy_class` |

  The mapping lives in the same `attribute-metadata.json` resource that
  already drives `DifferencesCalculator` direction semantics. A new
  top-level key `categoryRankings` adds the per-category list. An
  attribute that lacks a direction in the metadata is **skipped** with
  a warning log; it is not silently ranked in an arbitrary direction.

- **FR-5 — Ranking shape.** Each `rankings[]` entry has:

  ```
  {
    "path": "<dot-notated path>",
    "isComparable": true|false,
    "coverage": { "withValue": <int>, "total": <int> },
    "winner": { "id": <long>, "value": <number|string>, "name": "<product name>" } | null,
    "runnerUp": { "id": <long>, "value": <...>, "name": "<...>" } | null,
    "spread": { "min": <...>, "max": <...>, "median": <...> } | null
  }
  ```

  - `isComparable` is `true` only when at least 2 products in the
    category have a comparable value at `path` and the metadata
    supplies a direction. Otherwise `false`, with `winner`/`runnerUp`/
    `spread` all `null`.
  - `coverage` reports how many products carry the attribute
    (`withValue`) out of the total considered (`total`). Always present.
    A coverage of `2/10` means the attribute is rare in the category but
    can still be a real differentiator (e.g. `dual_sim` on half the
    smartphones, `noise_cancelling` on a few headphones). The frontend
    decides how to surface low-coverage rankings (badge, secondary
    section, etc.). Rankings are computed over the products that **do**
    carry the attribute.
  - `winner` follows the metadata direction (`HIGHER_BETTER` /
    `LOWER_BETTER`). Ties are broken by lexicographic `id` ascending —
    deterministic, no randomness.
  - `runnerUp` is the next product down in the same ordering. Omitted
    (`null`) when only one product in the category carries the attribute
    with a comparable value.
  - `spread` is computed only for numeric paths (`min`, `max`, and the
    median across products that carry the attribute). For ordinal/string
    paths `spread` is `null`.
  - The product `name` is denormalized into the entry so the frontend
    does not need a second lookup. `id` is the source of truth.

- **FR-6 — Items considered.** Only catalog products with `buyBox != null`
  participate in price-related rankings (zero-stock catalog products are
  unreachable, so ranking them on price would mislead). For non-price
  paths, all catalog products in the category are considered, regardless
  of `buyBox`.

### 5.3 Top items

- **FR-7** — `topItems[]` returns up to `topK` items sorted by a
  composite score: `rating` descending, then `buyBox.price` ascending,
  then `id` ascending. Items with no `buyBox` are pushed to the bottom
  (they are unreachable). This is a deliberately simple
  "popular-and-affordable" heuristic, not personalized — it is a picker
  helper, not a recommendation engine.

### 5.4 Summary (LLM)

- **FR-8** — When `OPENAI_API_KEY` is set and the LLM call succeeds, the
  response carries a `summary` string in the requested `language`. The
  summary is a **buying guide**: it presents three pre-computed picks
  (best overall, best value, cheapest) and closes with a one-line
  "if you prioritise X, go with Y" directive. The LLM is a *narrator* —
  it may only cite product names from the picks block, never invent
  recommendations. The prompt is fed:
  - `category` (string).
  - `productCount` (integer).
  - `rankings` (the deterministic array above, slimmed: `path`,
    `winner.name + value`, `runnerUp.name + value`, `spread`) —
    supporting evidence.
  - `topItems` (slimmed: `name`, `price`, `rating`) — supporting
    evidence.
  - `picks` — JSON object with `bestOverall`, `bestValue`, `cheapest`,
    each `{ id, name, price, currency, rating, reason }`. Heuristics:
    - `bestOverall` = highest `rating`; ties broken by lower price
      then lower id.
    - `bestValue` = highest `rating / price`; ties by higher rating
      then lower id (skipped when no product has both fields).
    - `cheapest` = lowest `buyBox.price`; ties by higher rating then
      lower id.
    Picks are computed in `CategoryInsightsService` and never exposed
    on the API response — they exist only as input to the prompt, so
    callers must derive recommendations from `rankings`/`topItems`.
  - `language`.
- **FR-9** — Prompt template lives at
  `src/main/resources/prompts/category-insights.v2.md`, versioned by
  filename (same convention as `compare-summary.v1.md`). v2 supersedes
  v1 (which framed the summary as a neutral landscape description; v2
  reframes it as a buying guide built on the deterministic picks).
  Output contract: single paragraph, ≤ 110 words, plain text, no
  markdown. The model must name the three picks using their `reason`
  field, may cite at most one supporting fact per pick from `rankings`,
  and must close with "if you prioritise X, go with Y" where Y is one
  of the picks. The cache key includes the prompt version (`v2|...`)
  so a future v3 prompt invalidates the cache automatically.
- **FR-10** — Cache key is `(promptVersion, category, topK, language,
  rankingsHash, topItemsHash, picksHash, filtersHash)`. `filtersHash`
  is the deterministic digest of the structured filters from §5.6
  (empty string when no filter is set, preserving cache hits for
  unfiltered calls verbatim across slices). The cache lives alongside
  `ai-summary` as a new Caffeine cache `ai-category-insights` with the
  same `maximum-size` / `ttl-minutes` defaults. Hits are counted as
  `ai_calls_total{kind=category_insights, outcome=cache_hit}`.
- **FR-11** — Fallback policy matches SPEC-004 §6 row-for-row, with the
  same `ai_fallback_total{reason=...}` taxonomy. The deterministic
  `rankings[]` and `topItems[]` are always present; the `summary` is
  the only field that may be absent.

### 5.5 Errors and validation

- **FR-12** — `category` is required. Missing → 400 `validation`.
  Unknown enum value → 400 `bad-request` (same handling as the list
  endpoint's `category`).
- **FR-13** — `topK` outside `1..20` → 400 `validation`.
- **FR-14** — `language` outside `{pt-BR, en}` → 400 `bad-request`
  (same handler as compare).
- **FR-15** — When the category exists in the enum but no catalog
  products of that category are in the store, the response is 200 with
  `productCount: 0`, `rankings: []`, `topItems: []`, no `summary`. This
  is not an error condition.

### 5.6 Structured filters (slice 5)

- **FR-16 — Filter parameters.** The endpoint accepts three optional,
  opt-in query parameters that narrow the slice analyzed by the
  rankings, picks, top-K and summary stages:

  | Param       | Type        | Range                   | Applied to                       |
  |-------------|-------------|-------------------------|-----------------------------------|
  | `minPrice`  | BigDecimal  | `≥ 0`                   | `buyBox.price`                    |
  | `maxPrice`  | BigDecimal  | `≥ 0`, `≥ minPrice`     | `buyBox.price`                    |
  | `minRating` | Double      | `[0.0, 5.0]`            | `rating`                          |

  Filters are independent and additive (logical AND). Absent ⇒ no
  constraint. Products with `buyBox == null` are excluded as soon as
  *any* price filter is set (they are unreachable by definition); they
  are still considered when only `minRating` is set, provided the
  rating is present. Products with `rating == null` are excluded as
  soon as `minRating` is set.

- **FR-17 — Pipeline placement.** Filters are applied **once**, in the
  service layer, against the products returned by
  `productService.getAllByCategory(category)`, *before* `computeRankings`,
  `pickTopItems` and `computePicks` see the list. There is a single
  source of truth for the filtered subset — every downstream stage
  reasons on the same slice. Out-of-scope for v1: pushing the predicate
  down to the JPA repository — see ADR-0006 for the scale-up trigger
  and migration path.

- **FR-18 — Prompt context.** When at least one filter is set, the
  rendered prompt receives an additional `appliedFilters` block listing
  the supplied bounds in the requested `language`. This keeps the LLM
  honest about the slice it is summarising — *"smartphones acima de R$
  3.000 com 4+ estrelas"* must not be summarised as the full category
  panorama. The block is omitted from the prompt when no filter is set,
  so cached unfiltered summaries from prior slices remain valid.

- **FR-19 — Empty filtered slice.** When filters reduce the pool to
  fewer than 2 products, the endpoint still returns 200 with
  `productCount` reflecting the filtered count (`0` or `1`),
  `appliedFilters` echoed, `rankings: []`, `topItems` of size `0` or
  `1`, and **no `summary`** (same gate as FR-3 and FR-15). The
  frontend renders an empty / under-constrained state with the echoed
  filter values so the user can broaden them.

- **FR-20 — Validation.** `minPrice < 0`, `maxPrice < 0`,
  `minPrice > maxPrice`, or `minRating` outside `[0.0, 5.0]` ⇒ 400
  `validation` (RFC 7807, same advice as FR-13). The cross-field
  `minPrice ≤ maxPrice` check is enforced by a class-level Bean
  Validation constraint on the request bean so the error surfaces
  before the controller body executes.

## 6. Non-functional requirements

- **NFR-1 — Performance.** Endpoint must complete in under 200 ms on
  developer hardware when the LLM is disabled; under 4 s with cold LLM
  call (LLM dominates; the deterministic computation is sub-50 ms even
  scanning all 50 seed products).
- **NFR-2 — Coverage.** New code under `service/insights` and the
  controller path covered by JaCoCo at the same ≥ 80 % gate enforced by
  `mvn verify`.
- **NFR-3 — Documentation.** OpenAPI annotated at the controller
  interface (same pattern as `ProductApi` / `CompareApi`); curl examples
  added to README and `docs/testing-guide.md`.
- **NFR-4 — Observability.** AI metrics extended with the new `kind`
  tag value `category_insights` (no new meter names). Caffeine cache
  exposed at `/actuator/caches`.
- **NFR-5 — Graceful degradation.** Same NFR-9 contract from SPEC-001:
  no AI dependency may break the endpoint.
- **NFR-6 — Determinism.** Given the same seed, two calls without LLM
  must return identical `rankings[]` and `topItems[]` byte-for-byte.
  This is asserted by a snapshot-style test.

## 7. Out of scope (v1) — and where it lives

| Item                                                      | Where           |
|-----------------------------------------------------------|-----------------|
| Cross-category insights (`?category=X,Y`)                 | not planned     |
| Faceted filtering by `condition` / `brand` / per-attribute ranges     | roadmap R-10 (post-v5)  |
| Push-down of `minPrice` / `maxPrice` / `minRating` to JPA repository  | roadmap R-11 (scale-up) |
| Personalized ranking weights per user                     | roadmap R-4     |
| Per-offer rankings (which seller wins on price)           | not planned     |
| Multi-currency normalization                              | roadmap R-8     |
| Pagination of `rankings[]`                                | not planned (rankings are inherently bounded by the metadata config) |
| Streaming the summary                                     | not planned     |
| Caching `rankings[]` (deterministic, cheap to recompute)  | not planned in v1 — only the LLM `summary` is cached |

## 8. Constraints

- **C-1** — No new top-level package. New code lives under
  `service/insights/` (alongside `service/compare/`, `service/ai/`)
  per ADR-0003.
- **C-2** — No new external dependencies. Reuses Spring AI, Caffeine,
  Micrometer, the existing `SummaryService` collaborators (`PromptLoader`,
  `AiMetrics`, `DailyBudget`).
- **C-3** — Respect the in-process daily budget already enforced by
  `DailyBudget` — `category-insights` calls count against the same
  ceiling as `compare` summaries. They are accounted separately by the
  `kind` tag but share the budget.

## 9. Acceptance criteria

- **AC-1** — `GET /api/v1/products/category-insights?category=SMARTPHONE`
  returns 200 with `productCount` matching the seed count for that
  category, a `rankings[]` covering at least `buyBox.price`, `rating`
  and the smartphone-specific attributes from the metadata config, a
  `topItems[]` of up to 5 entries, and (with `OPENAI_API_KEY` set) a
  `summary`.
- **AC-2** — `&topK=10` returns up to 10 items in `topItems[]`. `&topK=21`
  returns 400 `validation`.
- **AC-3** — `&language=en` returns the summary in English. `&language=zz`
  returns 400 `bad-request`.
- **AC-4** — Without `OPENAI_API_KEY`, the same call succeeds with
  identical `rankings[]` / `topItems[]` and no `summary` field.
- **AC-5** — `?category=BOGUS` returns 400 `bad-request`.
- **AC-6** — `?category=` (empty) returns 400 `validation`.
- **AC-7** — Each ranking entry has `winner.name` populated when
  `isComparable: true`. Ties broken by ascending `id`.
- **AC-8** — A category with one product returns `rankings: []`,
  `topItems` of size 1, and no `summary`.
- **AC-9** — The endpoint appears in `/swagger-ui.html` under a
  "Category Insights" tag with a worked example.
- **AC-10** — `mvn verify` passes with the JaCoCo ≥ 80 % gate intact.
- **AC-11** — Two consecutive calls without LLM produce byte-identical
  responses (NFR-6).
- **AC-12** — Cache hit on second LLM call counted as
  `ai_calls_total{kind=category_insights, outcome=cache_hit}`; first
  call counted as `outcome=ok`. `ai_fallback_total{reason=cache_hit}`
  is **not** incremented (preserves the OBS-5 fix from Slice 3).
- **AC-13** — `?category=SMARTPHONE&minRating=4.5` narrows the slice:
  `productCount` reflects only products with `rating ≥ 4.5`,
  `appliedFilters.minRating == 4.5`, every `topItems[].rating ≥ 4.5`,
  and the rendered `summary` (when LLM available) opens by acknowledging
  the filter.
- **AC-14** — `?category=SMART_TV&minPrice=2000&maxPrice=4000` narrows
  to products whose `buyBox.price` is in `[2000, 4000]`, products
  without `buyBox` are excluded, `appliedFilters` echoes both bounds,
  and `summary` describes the filtered slice (not the full category).
- **AC-15** — `?category=SMARTPHONE&minPrice=-1` → 400 `validation`;
  `?category=SMARTPHONE&minPrice=3000&maxPrice=2000` → 400 `validation`
  with the cross-field constraint message; `?category=SMARTPHONE&minRating=6`
  → 400 `validation`. Unfiltered calls keep the v4 contract verbatim
  (no `appliedFilters` field in the JSON body, same cache key, same
  `productCount` semantics).

## 10. Open questions

*None remaining. v2 resolves Q-1 (rank all attributes regardless of
coverage; surface coverage explicitly so the frontend can flag rare
differentiators), Q-2 (`productCount` counts all catalog products in
the category, consistent with the list endpoint), and Q-3 (no `fields`
on `topItems` in v1 — projection is already lean).*

## 11. Changelog

- **v5 (2026-04-30)** — Added §5.6 (Structured filters: `minPrice`,
  `maxPrice`, `minRating`) — opt-in query parameters that narrow the
  analyzed slice before rankings, picks, top-K and summary are
  computed. New response field `appliedFilters` (present only when at
  least one filter is set) so the unfiltered v4 wire contract is
  preserved verbatim. `productCount` now reflects the filtered subset
  (FR-2 wording sharpened to spell out "analyzed slice" instead of
  "category total"). FR-10 cache key extended with `filtersHash`
  (empty string when unfiltered, so v4 cache entries remain valid).
  AC-13/14/15 added. Implementation pinned to in-memory filtering in
  the service layer (FR-17); ADR-0006 records the choice and the
  scale-up trigger that flips it to a JPA push-down (roadmap R-11).
  No prompt template change in this slice — the prompt v3 receives an
  optional `appliedFilters` block when filters are set.
- **v4 (2026-04-30)** — Sharpened the buying-guide prompt after smoke
  showed the v2 output sounded robotic ("o best overall é o..."). The
  `Picks.Pick` record now carries a `highlights: List<String>` field
  computed in `CategoryInsightsService` from the deterministic
  `rankings[]` (each pick is tagged with the spec leaderships it owns,
  e.g. `["memory: 26 GB", "storage: 1408 GB"]`). Prompt template
  bumped to `category-insights.v3.md` with three few-shot examples
  (pt-BR notebook, en smartphone, pt-BR refrigerator) and stricter
  anti-hallucination rules: the LLM may only cite price, rating, and
  the values inside `highlights`; the literal labels "best overall /
  best value / cheapest" are forbidden in the prose; raw
  "<label>: <value>" strings must be reworded into natural target-
  language phrasing ("26 GB de memória" instead of "memory: 26 GB").
  Cache key prefix bumped to `v3|...`. SPEC §5.4 FR-8/FR-9 updated;
  no public schema change. v2 prompt removed.
- **v3 (2026-04-30)** — Reframed the LLM `summary` from a neutral
  landscape description to a **buying guide** anchored on three
  deterministic picks (best overall, best value, cheapest) computed in
  `CategoryInsightsService` and fed to the prompt as a separate
  `picks` block. Heuristics: `bestOverall` = highest rating (ties:
  lower price, lower id); `bestValue` = highest `rating / price` (ties:
  higher rating, lower id); `cheapest` = lowest price (ties: higher
  rating, lower id). Picks are *not* part of the public response —
  they are only an input to the prompt, which keeps the LLM as a
  narrator (it can only cite the names it received, removing the
  hallucination risk identified during smoke). Prompt template bumped
  to `category-insights.v2.md` and the `ai-category-insights` cache
  key now includes the prompt version (`v2|...`), so a future v3
  prompt invalidates cached entries automatically. Old v1 template
  removed since it has no remaining callers (history kept in git).
  FR-8/FR-9/FR-10 updated; AC list unchanged. No public schema change.
- **v2 (2026-04-30, status flipped Draft → Accepted)** — Status flipped
  on Slice 4 kickoff alongside ADR-0005 (dedicated endpoint vs param on
  `/compare`) and `plan-slice4.md` v1. No content change vs the v2
  draft entry below.
- **v2 (2026-04-30)** — Resolved Q-1, Q-2, Q-3. Q-1 flipped from the
  intersection proposal: every attribute that has a direction in the
  metadata config is ranked, regardless of how many products carry it,
  because rare attributes are often the strongest differentiators (e.g.
  `dual_sim` on half the smartphones, `noise_cancelling` on a few
  headphones). Coverage is surfaced explicitly via the new
  `coverage: { withValue, total }` field on every ranking entry (FR-5);
  the ranking is computed only over the products that carry the
  attribute, ties broken by ascending `id`. Frontend decides how to
  present low-coverage rankings (badge, secondary section). The earlier
  intersection-based `partialAttributes` map idea is dropped — coverage
  on each entry is enough. The natural-language category exploration
  evolution (RAG over rankings + free-form user query) is captured in
  `roadmap.md` R-9.
- **v1 (2026-04-30)** — Initial draft. Adds a third comparison surface
  (`/products/category-insights`) with deterministic per-attribute
  rankings, a top-K picker, and an optional LLM panorama summary
  reusing SPEC-004's AI infrastructure (prompt versioning, cache,
  metrics, fallback). Open questions on partial-attribute handling
  (Q-1) and `productCount` semantics (Q-2) flagged for planning.
