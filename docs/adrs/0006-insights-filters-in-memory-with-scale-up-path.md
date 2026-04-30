---
id: ADR-0006
title: Category-insights filters apply in-memory in v1, push down to the data layer at scale
status: Accepted
date: 2026-04-30
deciders: backend
supersedes: []
superseded_by: []
related: [SPEC-005, ADR-0003, ADR-0005]
---

# Context

SPEC-005 v5 (§5.6) introduces three opt-in structured filters on
`GET /api/v1/products/category-insights`: `minPrice`, `maxPrice`,
`minRating`. They narrow the slice that the deterministic rankings,
the heuristic picks, the top-K helper and the LLM summary all reason
about.

The filters can land at one of two layers:

1. **JPA push-down.** Compose `CatalogProductRepository` queries (a
   `Specification<CatalogProductEntity>` or named methods) so the
   database returns only the rows that already satisfy the bounds.
2. **In-memory in the service.** Hydrate the full category through
   the existing `productService.getAllByCategory(category)` and
   `.stream().filter(filters::matches)` before passing the list to
   `computeRankings`, `pickTopItems` and `computePicks`.

This decision is dense enough to deserve a record because (a) the two
options have very different cost profiles at scale, and (b) the
implementation we ship today must not paint the scale-up into a
corner.

# Decision

**Apply the three filters in-memory in `CategoryInsightsService`, after
the catalog hydrate, before ranking.** Isolate the predicate behind a
package-private `InsightsFilters` record with a single
`matches(ProductDetail) : boolean` method, so the *application point*
is one line in the service and the *predicate semantics* are one
record. When the scale trigger in §"Scale-up path" below fires, the
record stays, and the only change is where `matches` is evaluated.

# Rationale

Three reasons make in-memory the right v1 choice; one reason makes the
isolation discipline non-negotiable.

1. **`buyBox.price` is derived, not persisted.** The buy-box winner is
   computed per request from the set of live offers (ADR-0004:
   `stock>0 → tier NEW > REFURBISHED > USED → lowest price → highest
   reputation → seller-id`). It is not a column on
   `catalog_product`. To filter on `buyBox.price` in JPA today we
   would either (a) duplicate the buy-box rule in SQL (correctness
   risk: two implementations of the same rule drift), or (b)
   materialize a `buy_box_price` column populated by a trigger or an
   application listener. Both are real engineering — neither is
   appropriate for 50 products per category.

2. **Volume is irrelevant at the v1 scale.** The seed is 50 products
   per category. A streaming filter + ranking pass over 50 records
   completes in microseconds; the LLM call dominates request latency
   by three orders of magnitude. Push-down would shave nothing
   measurable off P95.

3. **The H2 + JPA detour costs more than it saves today.** A JPA
   `Specification` that filters on `rating` and on the (synthetic)
   `buyBox.price` would still need to project to `ProductDetail` —
   the join + projection logic that currently lives in
   `ProductService` would have to be split between the repository and
   the service or duplicated. The change touches more files than the
   in-memory variant, for zero behavior gain.

4. **The scale-up risk is mitigated by isolation, not by avoidance.**
   The risk we *do* care about is implementing this slice in a way
   that scatters filter logic across the service so the future
   push-down becomes a wide refactor. The mitigation is the
   `InsightsFilters` record contract: `matches(ProductDetail)` is the
   single semantic source of truth. When push-down lands, the
   record's static factory builds a `Specification<...>` instead of
   (or alongside) `matches`; every call-site that reads
   `filters.matches(p)` becomes a `repository.findAll(filters.toSpec(...))`
   without re-deriving the rule.

# Consequences

- **Code shape (slice 5).** New record
  `com.hackerrank.sample.service.insights.InsightsFilters`
  (package-private, alongside `Picks`). Methods: static
  `from(BigDecimal minPrice, BigDecimal maxPrice, Double minRating)`
  returning `null` when all three are `null` (no allocation for
  unfiltered calls); `matches(ProductDetail) : boolean`;
  `digest() : String` for the cache key (deterministic order,
  `BigDecimal.toPlainString`, omitted nulls); `describe(Language) :
  String` for the prompt block.
- **Application point.** One line in
  `CategoryInsightsService.insights(...)` between
  `productService.getAllByCategory(...)` and the existing
  `productCount` derivation: `filters == null ? products :
  products.stream().filter(filters::matches).toList()`.
- **`productCount` semantics shift.** It now refers to the filtered
  slice, not the full category. This is a documented spec change
  (SPEC-005 v5 FR-2). The unfiltered call path is unchanged because
  the filter object is `null` and the stream is skipped.
- **Cache key fragmentation.** Each unique filter combination produces
  a distinct cache entry. Acceptable while the catalog is small;
  monitored via the existing `cache_misses_total{cache=ai-category-insights}`
  counter. Cache key extension is backward-compatible: the empty
  `filtersHash` for unfiltered calls leaves v4 cache entries valid.
- **Boot-time cost.** None. No new beans, no eager work.

# Scale-up path

When does this decision flip? Concrete triggers, in priority order:

1. **Catalog size per category > 10 000 products.** Streaming the
   whole category through the service starts costing real CPU and
   memory per request, and the result set the LLM is asked to
   summarise is no longer human-meaningful anyway. Time to push down.
2. **P95 of `/category-insights` (LLM disabled) > 250 ms.**
   Independent of catalog size — if a hot path emerges we measure
   first (R-7 observability) and act.
3. **Filter combinations explode the AI cache miss rate above 30 %.**
   Each `(minPrice, maxPrice, minRating)` combination is a distinct
   cache key. When the cache stops absorbing real traffic, the LLM
   bill answers the question for us.

The migration when any trigger fires:

1. **Denormalize `buyBox.price` and `rating`** onto `catalog_product`,
   populated by an application event when an offer is upserted /
   removed (or by CDC on `offer` if we own a stream by then). This
   removes the per-request buy-box derivation for filtering only —
   the existing buy-box service stays as the source of truth for the
   *full* `BuyBox` projection.
2. **Composite indexes** `(category, buy_box_price)`,
   `(category, rating_avg)`. Cardinality is high enough on both that
   B-tree indexes are appropriate; no need to reach for partial or
   functional indexes yet.
3. **Add `InsightsFilters.toSpec() : Specification<CatalogProductEntity>`.**
   Same record, second method. The `matches` method stays — it is
   used by the test suite and by the prompt-context describer, both
   of which must stay independent of JPA.
4. **Switch the call-site** in `CategoryInsightsService.insights(...)`
   from `.stream().filter(filters::matches)` to a repository call
   that takes the spec. One line of production code changes; the rest
   of the slice 5 surface is invariant.
5. **Materialize the rankings themselves** when category cardinality
   crosses ~100 000. Top-N per attribute per category becomes a batch
   job (Spark / Flink) writing into `category_attribute_ranking`,
   refreshed hourly. The API reads the materialized table and joins
   the filter predicates on top. This is the R-11 entry on the
   roadmap.
6. **Cache the deterministic `rankings[]` / `topItems[]`** in Redis
   keyed by `(category, filtersHash)`. The LLM `summary` cache
   continues to live alongside as it does today.

The path above is *not* in scope for slice 5. It is the explicit
evolution we chose by isolating the predicate.

# Alternatives considered

- **Push down to JPA in slice 5.** Rejected for the three reasons
  above. Forcing a buy-box projection into SQL right now would either
  fork the buy-box rule or require denormalization that pays for
  itself only at scales we do not yet have.
- **Filter inside the controller before delegating to the service.**
  Rejected. The filter belongs to the analysis pipeline, not to HTTP
  parsing. The controller stays thin — its only filter responsibility
  is request-level Bean Validation (`minPrice ≥ 0`, `minRating ≤ 5`,
  cross-field `minPrice ≤ maxPrice`).
- **Build a new `FilteredCategoryInsightsService` that wraps the
  existing one.** Rejected. The wrapper would have to re-derive the
  same `productCount`, the same `picks`, and the same prompt context
  to keep the LLM honest about the slice it sees. Single service
  with one filter application point is simpler.
- **Apply the filter twice — once at the service input and again
  when computing `picks`.** Rejected as redundant. The pinned
  invariant is "one slice, one ranking, one picks set, one summary"
  and that requires a single filter application before any of the
  downstream stages run.
