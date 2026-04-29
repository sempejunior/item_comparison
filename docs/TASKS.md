---
id: TASKS
title: Atomic task breakdown — Item Comparison API
version: v2
status: Active
last_updated: 2026-04-29
depends_on: [SPEC-001, SPEC-002, SPEC-003, SPEC-004, PLAN, ADR-0001, ADR-0003, ADR-0004]
---

# TASKS — One commit, one task

This file decomposes `PLAN §12` into atomic, sequenceable tasks. Each
task names exactly:

- **What lands** (production files + test files) — paste-by-paste order
  matching `SUBMISSION.md §2.3`.
- **Which spec clauses** it satisfies (FR/NFR/AC + section references).
- **Definition of done** (DoD) — concrete acceptance to flip the task
  from `in_progress` to `completed`.
- **Dependencies** — the task IDs that must be `completed` first.

Conventions:

- Task IDs are stable: `T-NN`. Numeric ordering matches the build
  sequence and the paste sequence — pasting in this order at the
  HackerRank UI compiles cleanly at every step.
- One task ≈ one commit. Tests for a class land in the same task as
  the class (no "tests later" tasks).
- A task is **not** done until `mvn verify` is green locally and the
  spec sections it claims are actually exercised by tests.
- Nothing here introduces scope beyond SPEC-001..004. Anything new is
  pushed to `roadmap.md` instead of being absorbed into a task.

## 0. Task index

| ID    | Title                                                       | Status   | Layer        | Spec clauses                          | Blocked by  |
|-------|-------------------------------------------------------------|----------|--------------|---------------------------------------|-------------|
| T-01  | Cleanup `pom.xml` + bump to Boot 3.3.5 + add deps           | done     | build        | PLAN §1.1, §1.2; NFR-6                | —           |
| T-02  | Finalize `application.yml`                                  | done     | config       | NFR-5, NFR-8; SPEC-004 §3             | T-01        |
| T-03  | Domain enums (`Category`, `Condition`, `Language`)          | done     | model        | SPEC-002 §2.1, §3.1; SPEC-001 FR-8    | T-01        |
| T-04  | `AttributesJsonConverter`                                   | done     | repository   | SPEC-002 §5                           | T-01        |
| T-05  | JPA entities (`CatalogProductEntity`, `OfferEntity`)        | done     | repository   | SPEC-002 §2, §3, INV-1..INV-8         | T-03, T-04  |
| T-06  | Spring Data repositories                                    | done     | repository   | SPEC-002 §5                           | T-05        |
| T-07  | Public DTOs (records) + `ProblemDetail7807`                 | done     | model        | SPEC-003 §2.1–2.3, §4                 | T-03        |
| T-08  | `BuyBoxSelector` (pure)                                     | done     | service      | SPEC-002 §3.3, ADR-0004; FR-7         | T-05, T-07  |
| T-09  | `SeedLoader` + `catalog.json` (50 products / ~150 offers)   | done     | repository   | SPEC-002 §5, §6                       | T-05, T-06  |
| T-10  | `ProductService` (list, get-by-id, Caffeine cache)          | done     | service      | FR-1, FR-4, FR-5; NFR-8               | T-06, T-08  |
| T-11  | `ProductController` + MockMvc (list, get-by-id)             | done     | controller   | FR-1, FR-4; AC-1; SPEC-003 §2.1, §2.2 | T-10        |
| T-12  | `FieldSetProjector` (sparse fieldsets)                      | done     | service      | FR-3; SPEC-003 §3                     | T-07        |
| T-13  | `NumericValue` + `DifferencesCalculator`                    | done     | service      | FR-7; SPEC-003 §2.3; SPEC-002 §3.5    | T-07, T-08  |
| T-14  | `CompareService` (resolve → project → diff)                 | done     | service      | FR-2, FR-3, FR-7, FR-10, FR-11        | T-12, T-13  |
| T-15  | `GlobalExceptionHandler` + RFC 7807 wiring                  | done     | controller   | FR-12; NFR-4; SPEC-003 §4             | T-07        |
| T-16  | `CompareController` + MockMvc (compare, no LLM)             | done     | controller   | FR-2, FR-10–12; AC-2, AC-3, AC-4, AC-6, AC-7 | T-14, T-15 |
| T-17  | `PromptLoader` + `compare-summary.v1.md` + golden test      | done     | service      | SPEC-004 §4                           | T-07        |
| T-18  | `AiMetrics` (Micrometer counters/timer registration)        | done     | service      | SPEC-004 §7; NFR-5                    | T-01        |
| T-19  | `SummaryService` + Caffeine `ai-summary` + fallbacks        | done     | service      | FR-8, FR-9; SPEC-004 §2, §5, §6, §8   | T-13, T-17, T-18 |
| T-20  | Wire `summary` into `CompareController` + LLM-on/off MockMvc| done     | controller   | FR-8; AC-5                            | T-16, T-19  |
| T-21  | OpenAPI annotations + Swagger UI examples                   | pending  | controller   | NFR-3; AC-8                           | T-11, T-20  |
| T-22  | README + Mermaid diagram + ADR cross-links                  | pending  | docs         | NFR-3, NFR-6                          | T-21        |
| T-23  | Final pass: JaCoCo gate, manifest sync, smoke run           | pending  | quality      | NFR-2; AC-9, AC-10                    | T-22        |

JaCoCo coverage gate is **on from T-10 onwards** (the first task that
produces measurable service code). Earlier tasks do not enforce it.

---

## 1. Build / config

### T-01 — Cleanup `pom.xml` + bump to Boot 3.3.5 + add deps

**Goal.** Remove residue from the HackerRank `/model` runner, lift Spring
Boot to 3.3.5, and add the dependencies SPEC-001..004 actually need.
Maven coordinates stay `groupId=com.hackerrank` / `artifactId=sample`
(ADR-0003).

**Files touched.**
- `pom.xml`

**Changes.**
- Drop: `org.openjfx:javafx-controls`, `org.unitils:unitils-core`,
  `org.junit.vintage:junit-vintage-engine`, the duplicated
  `spring-boot-starter-parent` listed under `<dependencies>`.
- Add: `spring-boot-starter-validation`, `spring-boot-starter-aop`,
  `spring-ai-openai-spring-boot-starter` (1.0.0-M3), pin
  `micrometer-core`, `springdoc-openapi-starter-webmvc-ui` (already
  present — keep), `jacoco-maven-plugin` configured with 80 % line
  threshold on `controller`, `service`, `repository`.
- Bump Spring Boot parent to 3.3.5.

**DoD.** `mvn -DskipTests package` passes; `target/sample-1.0.0.jar`
produced; `mvn dependency:tree` shows Spring AI 1.0.0-M3 and no
JavaFX / unitils / vintage entries.

---

### T-02 — Finalize `application.yml`

**Goal.** Lock the runtime config the rest of the tasks rely on.

**Files touched.**
- `src/main/resources/application.yml`

**Content.**
- `spring.datasource` H2 in-memory + PostgreSQL mode + `ddl-auto:
  create-drop`.
- `spring.cache.type=caffeine` and `spring.cache.cache-names: products,
  ai-summary` with per-cache spec (max size + TTL per PLAN §5).
- `spring.ai.openai.api-key: ${OPENAI_API_KEY:disabled}` (placeholder
  per SPEC-004 §3).
- `spring.ai.openai.chat.options.model: gpt-5.4-nano`,
  `temperature: 0.2`.
- `app.ai.timeout-ms: 2000`, `app.ai.daily-request-limit: 0`.
- `app.errors.base-uri: https://api.example.com/errors` (PLAN §4).
- `management.endpoints.web.exposure.include: health,info,metrics,caches`.
- `springdoc.swagger-ui.path: /swagger-ui.html`.
- Logging: `com.hackerrank.sample: INFO`.

**DoD.** App boots locally with `mvn spring-boot:run` (no controllers
yet → empty mappings, but no exceptions). `/actuator/health` returns
`UP`. With and without `OPENAI_API_KEY` exported.

---

## 2. Domain & persistence

### T-03 — Domain enums

**Files touched.**
- `src/main/java/com/hackerrank/sample/model/Category.java` (already
  exists at the right shape — verify `{SMARTPHONE, SMART_TV, NOTEBOOK,
  HEADPHONES, REFRIGERATOR}`).
- `src/main/java/com/hackerrank/sample/model/Condition.java` (already
  exists — verify `{NEW, USED, REFURBISHED}`).
- `src/main/java/com/hackerrank/sample/model/Language.java` (new):
  `{PT_BR, EN}` + `fromTag(String)` parser accepting `pt-BR` / `en`.

**Tests.** `LanguageTest`: tag round-trip, unknown tag → throws
`InvalidLanguageException` (defined in T-15).

**DoD.** Enums compile. `LanguageTest` green.

---

### T-04 — `AttributesJsonConverter`

**Files touched.**
- `src/main/java/com/hackerrank/sample/repository/AttributesJsonConverter.java`
  (already exists — verify it implements
  `AttributeConverter<Map<String, Object>, String>` Jackson-backed,
  rejects nested objects/arrays per SPEC-002 INV-4).

**Tests.** `AttributesJsonConverterTest`: round-trip scalar map; reject
nested map; null → null; empty → `{}`.

**DoD.** Tests green; converter covers SPEC-002 §5 invariants.

---

### T-05 — JPA entities

**Files touched.**
- `repository/CatalogProductEntity.java` (`@Entity`, table
  `catalog_products`, `attributes` column with `@Convert`).
- `repository/OfferEntity.java` (`@Entity`, table `offers`, FK
  `catalog_product_id`).

**Invariants enforced at construction** (SPEC-002 §2.3, §3.2):
`category` non-null, `rating` in `[0,5]` or null, `price ≥ 0`,
`sellerReputation` in `[0,5]`, `stock ≥ 0`, ISO 4217 currency.

**Tests.** `CatalogProductEntityTest`, `OfferEntityTest`: invariants
fire on bad input; happy path passes.

**DoD.** Tests green; `mvn package` schema generation succeeds.

---

### T-06 — Spring Data repositories

**Files touched.**
- `repository/CatalogProductRepository.java` (`JpaRepository<…, Long>`,
  `findAllByCategory(Category, Pageable)`,
  `findAllByIdIn(Collection<Long>)`).
- `repository/OfferRepository.java` (`findAllByCatalogProductIdIn`).

**Tests.** `CatalogProductRepositoryTest` (`@DataJpaTest`): list with
pagination + category filter; `findAllByIdIn` preserves none on empty.

**DoD.** Tests green.

---

### T-07 — Public DTOs + Problem Details record

**Files touched.**
- `model/ProductSummary.java` (record): `id`, `name`, `imageUrl`,
  `rating`, `category`.
- `model/ProductDetail.java` (record): full canonical shape, `offers`,
  `buyBox`.
- `model/BuyBox.java` (record): offer projection used in compare.
- `model/CompareItem.java` (record): per-item compare projection
  (everything except `offers` by default; `offers` populated when
  opted in).
- `model/CompareResponse.java` (record): `items[]`, `differences[]`,
  `summary` (`String?`).
- `model/DifferenceEntry.java` (record): `path`, `isComparable`,
  `winnerId`, `values`.
- `model/PageResponse.java` (record): `items`, `page`, `size`,
  `totalElements`, `totalPages`.
- `model/problem/ProblemDetail7807.java` (record): `type`, `title`,
  `status`, `detail`, `instance`, `errors[]?`, `missingIds[]?`.

**Constraints.** Records use `@NotNull` / `@Size` / `@Min` where
controllers will validate inbound query params (compare ids list,
fields tokens, page size).

**Tests.** No behavior tests at this layer; assert (in a smoke test)
that Jackson roundtrips each record with `null` fields elided
(`@JsonInclude(NON_NULL)`).

**DoD.** Records compile; smoke test green.

---

## 3. Service utilities

### T-08 — `BuyBoxSelector`

**Files touched.**
- `service/compare/BuyBoxSelector.java` — pure function
  `select(List<Offer>) → Optional<Offer>`.

**Algorithm.** ADR-0004 verbatim: stock filter → tier preference
(NEW > REFURBISHED > USED) → lowest price → highest reputation →
lowest `sellerId` lex → empty when no stock.

**Tests.** `BuyBoxSelectorTest` exhaustive: empty list, all out of
stock, only USED, mixed tiers, price tie + reputation tie-break,
reputation tie + sellerId tie-break.

**DoD.** Tests green; coverage on `BuyBoxSelector` = 100 %.

---

### T-09 — `SeedLoader` + `catalog.json`

**Files touched.**
- `repository/seed/SeedLoader.java` — `CommandLineRunner` reading
  `classpath:seed/catalog.json`, validating each record with Bean
  Validation (`*SeedDto`), failing loudly on the first invalid entry.
- `src/main/resources/seed/catalog.json` — 50 catalog products
  (10 × {SMARTPHONE, SMART_TV, NOTEBOOK, HEADPHONES, REFRIGERATOR}),
  ~150 offers, edge cases woven in (PLAN §3): one product zero-stock,
  one product USED/REFURBISHED only, one near-identical pair, one
  trio without numeric-comparable attributes.

**Tests.** `SeedLoaderTest` (`@SpringBootTest`): after boot,
`CatalogProductRepository.count()` == 50, `OfferRepository.count() ≥
140`, edge-case ids resolvable; one corrupt-seed test using a
fixture under `src/test/resources/seed-bad/` to assert startup
fails fast.

**DoD.** App boots and the catalog is queryable.

---

### T-10 — `ProductService`

**Files touched.**
- `service/ProductService.java` — `getById(long)` with
  `@Cacheable("products")` and `list(Category?, Pageable) →
  PageResponse<ProductSummary>`. Maps entities → DTOs.

**Tests.** `ProductServiceTest`: cache hit on second call (mock
repository, assert single repo invocation); `ProductNotFoundException`
on missing id; pagination + category filter.

**DoD.** Tests green; JaCoCo on `service` ≥ 80 % from this point on.

---

### T-11 — `ProductController` (list, get-by-id)

**Files touched.**
- `controller/ProductController.java` — `GET /api/v1/products`,
  `GET /api/v1/products/{id}`. Bean Validation on query params;
  `language` query parameter wiring deferred to T-20.

**Tests.** `ProductControllerTest` (`@WebMvcTest`): list 200, list
filtered by category, get-by-id 200, get-by-id 404 (Problem Details
body shape verified after T-15 with a FIXME until then).

**DoD.** Tests green; AC-1 satisfied; `/swagger-ui.html` lists both
endpoints.

---

## 4. Compare core

### T-12 — `FieldSetProjector`

**Files touched.**
- `service/compare/FieldSetProjector.java` — pure function
  `project(CompareItem, FieldTree) → CompareItem`.

**Behavior.** PLAN §8: parse comma-separated dot-paths to a tree, `id`
implicit, unknown top-level keys silently dropped, `offers` token
swaps `buyBox` for `offers[]`, unknown `attributes.<key>` produces no
entry without erroring.

**Tests.** `FieldSetProjectorTest`: every quirk in PLAN §8 + AC-3 +
AC-4.

**DoD.** Tests green; AC-3, AC-4 exercised at unit level.

---

### T-13 — `NumericValue` + `DifferencesCalculator`

**Files touched.**
- `service/compare/NumericValue.java` — value object: parse
  `"4000 mAh"`, `"6.2 in"`, `"167 g"`. Equality of unit determines
  comparability; bad parse → not-comparable (no exception).
- `service/compare/DifferencesCalculator.java` — pure function
  `compute(List<CompareItem>, FieldTree) → List<DifferenceEntry>`.

**Rules.** PLAN §9: `buyBox.price` lower-wins iff currency match,
otherwise `isComparable=false` (SPEC-002 §3.5); numeric-with-unit
attributes higher-wins except `weight` (lower); `rating` higher-wins;
strings → `isComparable=false`. Sort: comparable first by relative
spread desc, then path alpha.

**Tests.** `NumericValueTest` (parse + unit equality matrix);
`DifferencesCalculatorTest`: ties, mixed types, currency mismatch,
missing keys, ordering stability, sparse fields restrict scope.

**DoD.** Tests green.

---

### T-14 — `CompareService`

**Files touched.**
- `service/compare/CompareService.java` — orchestrates `resolveIds →
  project → diff`. Validates id count (2..10) and uniqueness; throws
  `InvalidCompareRequestException` outside range; throws
  `ProductsNotFoundException(missingIds)` on any miss.

**Tests.** `CompareServiceTest`: happy path, dedup of ids preserves
order, < 2 / > 10 throws, unknown id throws with `missingIds`, sparse
fields restrict diff scope.

**DoD.** Tests green; FR-2, FR-10, FR-11 covered at service level.

---

## 5. Errors + compare endpoint

### T-15 — `GlobalExceptionHandler`

**Files touched.**
- `controller/advice/GlobalExceptionHandler.java` — `@RestControllerAdvice`
  mapping the table in PLAN §4 to RFC 7807 bodies. Reads
  `app.errors.base-uri` from config.
- `exception/ProductNotFoundException.java`.
- `exception/ProductsNotFoundException.java` (carries
  `List<Long> missingIds`).
- `exception/InvalidFieldsException.java`.
- `exception/InvalidCompareRequestException.java`.
- `exception/InvalidLanguageException.java`.

**Tests.** `GlobalExceptionHandlerTest` (`@WebMvcTest` + a tiny test
controller that throws each exception): every row of PLAN §4 returns
the right slug + status + body shape. Validation 400 carries
`errors[]`; not-found 404 for compare carries `missingIds[]`.

**DoD.** Tests green; FR-12, NFR-4 covered.

---

### T-16 — `CompareController` (no LLM yet)

**Files touched.**
- `controller/CompareController.java` —
  `GET /api/v1/products/compare?ids=&fields=&language=`. Wires
  `CompareService` and (later) `SummaryService`. For this task,
  `summary` is always absent.

**Tests.** `CompareControllerTest` (`@WebMvcTest`): AC-2, AC-3, AC-4,
AC-6, AC-7. `language` accepted but currently ignored
(documented). Comma-separated ids and repeated `ids=` both bind.

**DoD.** Tests green; deterministic compare path is end-to-end
exercisable from MockMvc.

---

## 6. AI surface

### T-17 — `PromptLoader` + golden prompt

**Files touched.**
- `service/ai/PromptLoader.java` — loads
  `classpath:prompts/compare-summary.v1.md` once at startup.
- `src/main/resources/prompts/compare-summary.v1.md` — header
  (purpose, inputs, contract) + template body with `{{language}}`,
  `{{products}}`, `{{differences}}` placeholders. ≤ 60 words,
  factual, no marketing language.
- `src/test/resources/prompts/golden/compare-summary.v1.txt` —
  expected stub output for a fixed `(products, differences,
  language=pt-BR)` triple.

**Tests.** `PromptLoaderTest`: template loaded; placeholders rendered
via Spring AI `PromptTemplate`. Golden assertion: rendering against
the fixed triple equals the pinned text. Editing the template
without updating the golden fails the test (regression guard,
SPEC-004 §4).

**DoD.** Tests green.

---

### T-18 — `AiMetrics`

**Files touched.**
- `service/ai/AiMetrics.java` — registers Micrometer meters at
  startup: `ai_calls_total{kind, outcome}` (counter),
  `ai_latency_seconds{kind}` (timer), `ai_tokens_total{kind,
  direction}` (counter), `ai_fallback_total{reason}` (counter).
  Constants for tag values (no string literals at call sites).

**Tests.** `AiMetricsTest`: meter ids registered with the expected
names + tag keys.

**DoD.** Tests green; `/actuator/metrics/ai_calls_total` exposes the
meter (verified manually in T-23).

---

### T-19 — `SummaryService`

**Files touched.**
- `service/ai/SummaryService.java` — accepts `(items, differences,
  language) → Optional<String>`. Behaviour:
  - Reads `OPENAI_API_KEY` env var on every call. Empty / equal to
    `disabled` → return `Optional.empty()` and increment
    `ai_fallback_total{reason=no_key}` (SPEC-004 §3).
  - Daily budget exceeded → fallback `reason=budget`.
  - Otherwise: render prompt, call `ChatClient` under `app.ai.timeout-ms`,
    cache hit/miss accounted in `ai-summary` Caffeine cache.
  - Errors mapped to fallback reasons per SPEC-004 §6 table; HTTP 401
    flips an in-memory `keyInvalidForBoot` flag.
- `service/ai/DailyBudget.java` — in-process counter (resets on boot)
  guarding `app.ai.daily-request-limit`. `0` = disabled.

**Tests.** `SummaryServiceTest` (mocked `ChatClient`):
- happy path returns string + emits `ok` outcome + token counters;
- timeout → `Optional.empty()` + `reason=timeout`;
- 401 → fallback + key marked invalid for boot;
- 5xx → fallback + `reason=server_error`;
- exception → fallback + `reason=exception`;
- missing key → fallback + `reason=no_key` + no HTTP call;
- second identical call → cache hit + `outcome=cache_hit`;
- language=`en` switches prompt language deterministically.

**DoD.** Tests green; SPEC-004 §6 every row exercised.

---

### T-20 — Wire `summary` into `CompareController`

**Files touched.**
- `controller/CompareController.java` — inject `SummaryService`,
  populate `CompareResponse.summary` when the service returns a
  value. Default `language=pt-BR`.
- `controller/CompareController.java` test: extend
  `CompareControllerTest` with two profiles:
  - LLM-on (stubbed `SummaryService` → fixed string) → AC-5.
  - LLM-off (stub returns `Optional.empty()`) → response identical
    to T-16 baseline.

**DoD.** Tests green; AC-5 (with and without `OPENAI_API_KEY`)
verifiable via `curl` once T-23 ships.

---

## 7. Documentation + quality gate

### T-21 — OpenAPI annotations + Swagger UI examples

**Files touched.**
- All controllers: `@Operation`, `@ApiResponse`, `@Parameter`
  annotations matching SPEC-003 §7 example payloads.
- `controller/advice/GlobalExceptionHandler.java`: `@ApiResponse`s for
  400/404/500 with example RFC 7807 bodies.

**DoD.** `/swagger-ui.html` shows every endpoint with example
request + response payloads (AC-8).

---

### T-22 — README + Mermaid + ADR cross-links

**Files touched.**
- `README.md` (project root). Sections per PLAN §11. Embedded Mermaid
  diagram of the compare flow (`PLAN §7`). Links to all four specs,
  the four ADRs, and the roadmap. Documents `OPENAI_API_KEY`
  optional-ness (SPEC-004 §3, NFR-9), the `app.ai.daily-request-limit`
  cost guard (SPEC-004 §8), and the manifest at `SUBMISSION.md`.

**DoD.** README renders cleanly on GitHub. Every link resolves.

---

### T-23 — Final pass: JaCoCo gate + manifest sync + smoke

**Files touched.**
- `SUBMISSION.md` — flip every artefact in §2.3 from `pending` to
  `ready`. Update §2.1 doc statuses (PLAN/ADRs/TASKS now exist).
  Add §6 changelog entry.
- `HANDOFF.md` §9.2 — clear the pending list; final state.

**Smoke checklist (manually executed, recorded in commit body).**
- `mvn verify` green; JaCoCo HTML report under `target/site/jacoco/`
  shows ≥ 80 % on `controller`, `service`, `repository` (NFR-2).
- `mvn spring-boot:run` boots in < 10 s without `OPENAI_API_KEY`
  (AC-10).
- `curl` examples from SPEC-003 §7 produce the expected payloads
  (AC-1 through AC-7 covered live).
- `/swagger-ui.html` reachable (AC-8).
- `/actuator/health` = `UP`; `/actuator/metrics/ai_fallback_total`
  reachable.
- `grep -r 'sk-' .` returns nothing.

**DoD.** Every AC in SPEC-001 §9 is reachable from a live curl. The
manifest reflects reality.

---

## 8. Out of every task (locked)

These do **not** appear in any task above and never will inside v1:

- Semantic search (`/search`), embeddings, vector store (roadmap R-2).
- LLM-driven filter extraction (roadmap R-3).
- Multi-currency conversion (roadmap R-8).
- Per-offer comparison (`compareOffers`) (SPEC-002 §8 Q-1).
- Auth, write endpoints, rate limiting, GraphQL.
- Streaming compare summary.
- Per-tenant prompt selection (roadmap R-4).

If during implementation a need appears for any of the above, it goes
into the roadmap — not into a new task here.

## 9. Changelog

- **v2 (2026-04-29)** — Added `Status` column to the §0 task index.
  T-01..T-20 marked `done` (Slices 1, 2, 3 shipped). T-21..T-23 remain
  `pending`. SPEC-004 v3 transitioned to `Accepted` after T-20 wiring +
  smoke validation (LLM-off → `summary` absent; LLM-on → graceful
  fallback to `Optional.empty()` recorded as `ai_fallback_total{reason=timeout}`).
- **v1 (2026-04-29)** — Initial atomic breakdown derived from PLAN v2
  + SPEC-001 v5 + SPEC-002 v5 + SPEC-003 v2 + SPEC-004 v3 + ADR-0001
  + ADR-0003 + ADR-0004. Paste-by-paste order matches
  `SUBMISSION.md §2.3`.
