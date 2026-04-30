---
id: PLAN
title: Implementation Plan — Item Comparison API
version: v2
status: Draft
last_updated: 2026-04-29
depends_on: [SPEC-001, SPEC-002, SPEC-003, SPEC-004, ADR-0001, ADR-0003, ADR-0004]
---

# PLAN — From specs to running code

This is the bridge between the four specs and the keyboard. It answers
**how** the requirements land in the HackerRank-imposed package layout,
**in what order**, and **what stays out of v1**. It is deliberately
short: every section either decides something or points at the spec
that already did.

## 0. Ground rules

- Root package is the **HackerRank skeleton**: `com.hackerrank.sample`.
  ADR-0003 supersedes ADR-0002 and locks this in alignment with the
  paste-by-paste submission flow (SUBMISSION.md). Layered layout:
  `controller`, `service`, `repository`, `model`, `exception`.
  Sub-packages allowed when they earn it (e.g. `service/compare`,
  `service/ai`).
- One commit ≈ one task in `tasks.md` (to be written after this plan).
- Tests land **in the same task** as the production code they cover.
  No "tests later" tasks.
- The deliverable to HackerRank is `src/` + `pom.xml` + `README.md`.
  Everything in `docs/` is the trail of decisions and is linked from the
  README, not required by the runner.

## 1. Tooling decisions

### 1.1 Dependencies — what we add, what we drop

The `pom.xml` inherited from the skeleton carries residue from a
template that targeted a generic `/model` CRUD. ADR-0001 already decided
to discard the `/model` surface; the dependencies that only existed to
support its bundled JSON runner go with it.

| Action  | Dependency                          | Reason                                    |
|---------|-------------------------------------|-------------------------------------------|
| Drop    | `org.openjfx:javafx-controls`       | only used by the original runner for `javafx.util.Pair` |
| Drop    | `org.unitils:unitils-core`          | only used by the original runner          |
| Drop    | `org.junit.vintage:junit-vintage-engine` | runner was JUnit 4; we standardise on JUnit 5 |
| Drop    | `org.springframework.boot:spring-boot-starter-parent` listed inside `<dependencies>` | duplicate of the `<parent>` tag, no effect, just noise |
| Add     | `org.springframework.boot:spring-boot-starter-validation` | Bean Validation on DTOs (FR-10..12) |
| Add     | `org.springframework.boot:spring-boot-starter-aop`        | required transitively by some Actuator paths and AI bulkhead config |
| Add     | `org.springframework.ai:spring-ai-openai-spring-boot-starter` (1.0.0-M3 or current GA) | SPEC-004 chat client |
| Add     | `io.micrometer:micrometer-core`     | Already transitive via Actuator; pin version to expose `Counter`/`Timer` builders explicitly |
| Add     | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | already present, keep |
| Keep    | H2, JPA, Caffeine, Actuator, Web    | as-is                                     |
| Keep    | `jacoco-maven-plugin`               | enforce ≥ 80 % at `verify`                |

**Skeleton kept (ADR-0003).** The HackerRank package root stays;
ADR-0002 (which would have discarded it) is superseded. The runner
`HttpJsonDynamicUnitTest` is not physically present in the tree;
declarative HTTP fixtures, if useful, come from MockMvc tests with
sidecar JSON. ADR-0001 (revived) is the active position on the
runner + bundled fixtures.

### 1.2 Spring Boot version

`pom.xml` currently declares 3.2.5. SPEC-004 references Spring AI
1.0.0-M3+, which aligns with Spring Boot 3.3.x. **Bump to 3.3.5** in
the same task that adds Spring AI. Smaller jar + current-line patches.

### 1.3 Java version

Java 21 stays. We use only stable features: records, pattern-matching
in `instanceof`, sealed types where they help domain clarity (e.g.
`AttributeValue`).

## 2. Spec-to-package mapping

The four specs decompose into the layered structure as follows:

```
com.hackerrank.sample
├── Application.java                    # @SpringBootApplication, @EnableCaching, @EnableConfigurationProperties
│
├── controller/
│   ├── ProductController.java          # SPEC-003 §2.1, §2.2  (list, get-one)
│   ├── CompareController.java          # SPEC-003 §2.3       (compare)
│   └── advice/
│       └── GlobalExceptionHandler.java # @RestControllerAdvice → RFC 7807 (FR-12, NFR-4)
│
├── service/
│   ├── ProductService.java             # orchestrates list/get; Caffeine cache on get-one (NFR-8)
│   ├── compare/
│   │   ├── CompareService.java         # facade: resolveIds → diff → (parallel) summary
│   │   ├── DifferencesCalculator.java  # SPEC-001 FR-7, SPEC-003 §2.3 — pure
│   │   ├── BuyBoxSelector.java         # SPEC-002 §3.3 — pure
│   │   └── FieldSetProjector.java      # SPEC-003 §3 — pure (sparse fieldsets)
│   └── ai/
│       ├── SummaryService.java         # SPEC-004 §2 — chat client wrapper, fallbacks, metrics
│       ├── PromptLoader.java           # loads prompts/compare-summary.v1.md once
│       └── AiMetrics.java              # Micrometer meter holder
│
├── repository/
│   ├── CatalogProductEntity.java       # @Entity, attributes via AttributeConverter<Map,String>
│   ├── OfferEntity.java                # @Entity, FK catalog_product_id
│   ├── CatalogProductRepository.java   # JpaRepository
│   ├── OfferRepository.java            # JpaRepository
│   ├── AttributesJsonConverter.java    # AttributeConverter, Jackson-backed
│   └── seed/
│       ├── SeedLoader.java             # CommandLineRunner, fail-loud
│       └── catalog.json                # → moved into resources at build time (see §3)
│
├── model/                              # public API DTOs (records) + domain enums
│   ├── Category.java                   # enum (SMARTPHONE, SMART_TV, NOTEBOOK, HEADPHONES, REFRIGERATOR)
│   ├── Condition.java                  # enum (NEW, USED, REFURBISHED)
│   ├── ProductSummary.java             # list projection
│   ├── ProductDetail.java              # full product (with offers + buyBox)
│   ├── BuyBox.java                     # offer-shaped projection used in compare
│   ├── CompareResponse.java            # items + differences[] + summary?
│   ├── DifferenceEntry.java            # path, isComparable, winnerId, values
│   ├── PageResponse.java               # generic pagination envelope
│   └── problem/
│       └── ProblemDetail7807.java      # RFC 7807 body (uses Spring's built-in where convenient)
│
└── exception/
    ├── ProductNotFoundException.java
    ├── ProductsNotFoundException.java  # carries missingIds list
    ├── InvalidFieldsException.java
    └── InvalidLanguageException.java
```

Notes on this mapping:

- **DTOs in `model/`, entities in `repository/`.** The HackerRank layout
  forces `model` to mean *public API model*, since `repository` is the
  only place left for JPA entities. Boundary discipline is preserved:
  controllers never touch entities; the service layer maps.
- **`service/compare/` and `service/ai/` are sub-packages.** They earn
  their place: `CompareService` is non-trivial, the AI surface has its
  own lifecycle, and bundling everything flat in `service/` would hide
  cohesion.
- **Pure functions where possible.** `DifferencesCalculator`,
  `BuyBoxSelector`, `FieldSetProjector` are stateless — easy to unit-
  test exhaustively, and the coverage gate (NFR-2) lands cheaply on
  them.

## 3. Seed data

**Where it lives.** `src/main/resources/seed/catalog.json` (one file,
both products and offers). Loaded by `SeedLoader` (a
`CommandLineRunner`) on every boot — safe because H2 is in-memory and
recreated (`ddl-auto: create-drop`).

**Shape.** A single JSON object with two arrays:

```json
{
  "catalogProducts": [ { "id": 1, "name": "...", "category": "SMARTPHONE",
                         "attributes": { "battery": "4000 mAh", ... } } ],
  "offers":          [ { "id": 101, "catalogProductId": 1, "price": 4899.00, ... } ]
}
```

**Volume.** 50 catalog products: 10 each across `SMARTPHONE`,
`SMART_TV`, `NOTEBOOK`, `HEADPHONES`, `REFRIGERATOR`. 2–4 offers per
product, ~150 offers total. Edge cases woven in: one product with
zero stock across all offers (tests `buyBox = null`), one with only
USED/REFURBISHED offers (tests tier fallback per ADR-0004), one pair
near-identical except for price (tests sparse `differences[]`), one
trio where no attribute is numerically comparable (tests
`isComparable=false` ordering). Enough to exercise every diff branch
(numeric, ordinal, currency mismatch, missing key) without bloating
the repo.

**Validation.** Bean Validation on the `*SeedDto` records; invalid
records throw at startup. No silent skip (SPEC-002 §6).

## 4. Cross-cutting: error contract

A single `@RestControllerAdvice` produces every non-2xx body.

| Exception                              | Slug          | Status |
|----------------------------------------|---------------|--------|
| `MethodArgumentNotValidException`      | `validation`  | 400    |
| `ConstraintViolationException`         | `validation`  | 400    |
| `MethodArgumentTypeMismatchException`  | `bad-request` | 400    |
| `MissingServletRequestParameterException` | `bad-request` | 400 |
| `InvalidFieldsException`               | `bad-request` | 400    |
| `InvalidLanguageException`             | `bad-request` | 400    |
| `ProductNotFoundException`             | `not-found`   | 404    |
| `ProductsNotFoundException`            | `not-found`   | 404    |
| `Exception` (catch-all)                | `internal`    | 500    |

`type` is built from a configurable base URI
(`app.errors.base-uri=https://api.example.com/errors`) plus the slug.
The advice owns the only mapping; controllers just throw.

## 5. Cross-cutting: caching

Two named Caffeine caches, declared in `application.yml` and listed in
`spring.cache.cache-names`:

| Cache name   | Key                              | TTL  | Owner                   |
|--------------|----------------------------------|------|-------------------------|
| `products`   | catalog product `id`             | 5 m  | `ProductService.getById`|
| `ai-summary` | hash of `(sortedIds, fields, language)` | 60 m | `SummaryService` |

Cache hit ratio is observable via Actuator `caches` and
`cache_*` Micrometer meters.

## 6. Cross-cutting: observability

Endpoints exposed: `health`, `info`, `metrics`, `caches`. AI-specific
counters/timers (SPEC-004 §7) registered at startup by `AiMetrics`.
README documents how to scrape `/actuator/metrics/{name}`.

No tracing in v1; that's R-7 in the roadmap.

## 7. Compare endpoint — sequencing

The most decision-dense path; pinning it explicitly avoids re-debating
later.

```
CompareController
  → CompareService.compare(ids, fields, language)
       │
       ├─ resolveIds(ids)                         # repo lookup, throws ProductsNotFound on miss
       ├─ items = project(products, fields)       # FieldSetProjector
       ├─ diffsFuture = supplyAsync(() -> diff(items, fields))   # DifferencesCalculator, virtual thread
       ├─ summaryFuture = supplyAsync(() -> summary(items, diffs, language))  # SummaryService, may be Optional.empty()
       │       ↑ depends on diffs → chained, not parallel-with-diff
       ├─ join(diffsFuture, summaryFuture) within app.ai.timeout-ms
       └─ build CompareResponse
```

**SPEC-004 Q-1 resolved.** The summary depends on `differences[]`
(prompt input), so it cannot start before the diff completes. We **do**
keep the diff itself off the request thread to allow other future
parallelism, but the LLM call is sequenced after the diff. The 2 s
timeout applies only to the LLM call.

**SPEC-003 Q-1 resolved.** Spring binds `?ids=1,2,3` and `?ids=1&ids=2`
into `List<Long>` natively. We accept both. README's `curl` examples
show the CSV form.

## 8. Sparse fieldsets — algorithm

`FieldSetProjector` operates on the response DTO **after** entity → DTO
mapping, not on the entity. Steps:

1. Parse `fields` into a tree (`{ buyBox: { price }, attributes: {
   battery, memory } }`).
2. `id` is added implicitly.
3. For each item, walk the tree and copy selected fields into a fresh
   DTO of the same shape (with `null` for the unselected ones; Jackson
   is configured `non-null` so they disappear from JSON).
4. For compare, `differences[]` is computed **only over the projected
   paths**. If `fields` is null, all attribute keys are considered.
5. Special token `offers` (compare only): swaps `buyBox` for the full
   `offers` list on each item; orthogonal to the rest of the tree.

Unknown top-level paths are silently dropped. Unknown
`attributes.<key>` produces no entry but does not error (SPEC-003 §3).

## 9. Differences algorithm — winnerId rules

Per SPEC-003 §2.3:

- `buyBox.price` — lower wins, **only** if all `currency` values match;
  else `isComparable=false`.
- `attributes.battery`, `attributes.memory`, `attributes.storage`,
  `attributes.weight`, `attributes.size`, numeric-with-unit → parse
  number + unit; if all units identical, higher wins (lower for
  `weight`); else `isComparable=false`.
- `rating` — higher wins.
- Anything else (strings like `brand`, `os`, `color`) — `isComparable=false`.

Ordering of `differences[]`: comparable entries first, sorted by
**relative spread** (descending), then by path alphabetical for
stability.

A small unit-aware `NumericValue` value object handles parse + unit
equality; rejects anomalies to `isComparable=false` rather than
throwing.

## 10. AI surface — concretely

- **Bean wiring.** Spring AI's `ChatClient.Builder` + `EmbeddingModel`
  beans are auto-configured by the starter when `spring.ai.openai.api-key`
  is non-empty. `SummaryService` depends on `Optional<ChatClient>` so
  the empty-key path is type-safe.
- **Prompt file.** `src/main/resources/prompts/compare-summary.v1.md`
  with header (purpose, inputs, contract) and the template body.
  Loaded once by `PromptLoader` at startup; placeholders rendered with
  Spring AI's `PromptTemplate`.
- **Tests.** `ChatClient` is mocked via `@MockBean`. One golden test
  pins a deterministic input → expected stub output, so accidental
  prompt edits surface in CI.

## 11. README — what ships in the submission

Sections, in order:

1. **What this is** — one paragraph + the comparison example.
2. **Run it** — `mvn spring-boot:run`, then a curl. With and without
   `OPENAI_API_KEY`.
3. **Architecture decisions** — links to SPEC-001..004, ROADMAP,
   ADR-0001. Embedded Mermaid diagram (`compare` flow, §7 above).
4. **Endpoints** — table of routes + Swagger UI link.
5. **Testing** — `mvn verify`, JaCoCo report path.
6. **What's deliberately out of v1** — short list pointing at roadmap.

## 12. Build order (the only timeline that matters)

| Step | Deliverable                                      | Spec coverage           |
|------|--------------------------------------------------|-------------------------|
| 1    | `pom.xml` cleanup (drop runner deps, keep `com.hackerrank` / `sample` Maven coordinates) + Spring AI add + bump 3.3.5. Skeleton package layout stays per ADR-0003 (no package move). | §0, §1.1, §1.2 |
| 2    | Domain enums, entities, JSON converter, repo     | SPEC-002 §2–§3, §5      |
| 3    | Seed loader + `catalog.json`                     | SPEC-002 §5             |
| 4    | DTOs + `BuyBoxSelector` + `ProductService.getById` (cached) + `ProductController` (list, get-one) | SPEC-001 FR-1, FR-4–6; SPEC-003 §2.1–2.2 |
| 5    | `FieldSetProjector` (with tests covering quirks) | SPEC-003 §3             |
| 6    | `DifferencesCalculator` + `NumericValue`         | SPEC-001 FR-7; SPEC-003 §2.3 |
| 7    | `CompareService` + `CompareController`           | SPEC-001 FR-2–4, FR-10–11 |
| 8    | `GlobalExceptionHandler` + RFC 7807 body         | FR-12, NFR-4            |
| 9    | `SummaryService` + prompt + cache + metrics + parallel join | SPEC-004 entirely |
| 10   | OpenAPI annotations + Swagger UI examples        | NFR-3                   |
| 11   | README + Mermaid + ADR-0001 v2 amendment         | NFR-3, NFR-6            |

Each step ends with green `mvn verify` and updated tests. JaCoCo gate
flips on at step 4 (so it actually has something to measure).

## 13. What is *not* in v1 (final list)

Pulled forward from the four specs and locked here so we do not
re-debate during implementation:

- Semantic search (`/search`, embeddings, vector store) — roadmap R-2.
- LLM filter extraction — roadmap R-3.
- Multi-currency conversion — roadmap R-8 (we expose native currency,
  flag mismatched price as `isComparable=false`).
- Per-offer diff inside compare — roadmap, SPEC-002 §8 Q-1.
- Auth, write endpoints, rate limiting, GraphQL — out of scope.
- Streaming compare summary (SSE) — out of scope; inline + timeout is
  the contract.
- Multi-tenant prompts — roadmap R-4.

Anything that is asked of us during implementation and is not on this
list goes back to the spec or to the roadmap before any code is
written.

## 14. Risks and how the plan absorbs them

| Risk                                                       | Mitigation in this plan                              |
|------------------------------------------------------------|------------------------------------------------------|
| Spring AI starter API churn between Boot 3.3 milestones    | Pin a single Spring AI version; `Optional<ChatClient>` boundary keeps surface minimal |
| HackerRank evaluator runs `mvn test` only (no `verify`)    | Tests live under JUnit 5 / `*Test.java`; `mvn test` runs them. JaCoCo only matters for our own bar |
| OpenAI key ends up in repo by accident                     | `.gitignore` already covers `.env*`; README says *env var only*; no `application-local.yml` checked in |
| Reviewer mis-reads the discarded `/model` surface as a gap | ADR-0001 + README cross-link explain the choice      |
| Spring AI auto-config trips when `OPENAI_API_KEY` is empty | We rely on the starter's documented behaviour; `SummaryService` short-circuits before calling `ChatClient` when key is blank |
| Sparse fieldsets accidentally affect diff scope            | `FieldSetProjector` runs **before** `DifferencesCalculator` in `CompareService`; tests assert that `differences[]` matches the projected fields exactly |

## 15. Definition of done (per task)

A task is done when **all** of these hold:

- Production code matches the relevant FR/NFR in `tasks.md`.
- Unit and (where relevant) MockMvc tests are added in the same commit.
- `mvn verify` is green locally.
- JaCoCo coverage on the changed package is ≥ 80 % (from step 4
  onwards).
- Public API behaviour is reflected in OpenAPI annotations and visible
  in Swagger UI.

## 16. Changelog

- **v2 (2026-04-29)** — §0 root package reverted to
  `com.hackerrank.sample` per ADR-0003 (supersedes ADR-0002). §2 tree
  rooted at the skeleton root; Application class renamed to
  `Application`. §2 Category enum aligned with SPEC-002 v5
  (SMARTPHONE / SMART_TV / NOTEBOOK / HEADPHONES / REFRIGERATOR). §3
  seed volume bumped to 50 products / ~150 offers across the new
  category set. §1.1 paragraph re-tagged: ADR-0001 revived,
  ADR-0002 superseded. §12 Step 1 rewritten: pom cleanup only, no
  package move. ADR-0004 referenced from §2 (`BuyBoxSelector`).
- **v1 (2026-04-28)** — Initial plan derived from SPEC-001 v3, SPEC-002
  v3, SPEC-003 v2, SPEC-004 v1, ROADMAP v2, ADR-0001. Notes ADR-0001
  amendment needed (runner not physically present in tree).
