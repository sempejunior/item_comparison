---
id: SPEC-001
title: Item Comparison API
version: v8
status: Draft
last_updated: 2026-04-30
---

# SPEC-001 — Item Comparison API

## 1. Context

Mercado Livre challenge: build a backend API that supplies product details to
a frontend item-comparison feature. The deliverable is a self-contained
service runnable locally; no real database, no auth, no production
infrastructure required.

Mercado Livre's real catalog uses a **catalog-product** abstraction
(canonical product) with multiple **offers** (one per seller). v2 adopted
that model from the start so the design reflects the domain the reviewer
recognizes, instead of a simplified `Product` that would have to be
reworked the moment the system met production data.

v3 narrows the v1 scope: **semantic search is moved to the roadmap (R-2)**
in favor of a tighter, more coherent delivery focused on what the
challenge actually asks for — comparison. The AI surface is reduced to a
single, clearly-scoped feature: a natural-language summary of the
comparison, with a deterministic fallback. See §7 and `roadmap.md` R-2.

## 2. Goal

Serve normalized catalog data so a frontend can:

1. Render side-by-side comparisons across two or more catalog products.
2. Highlight what is actually different between them (deterministic).
3. Optionally enrich the comparison with a natural-language summary
   produced by an LLM, with a graceful fallback when the LLM is
   unavailable.

## 3. Stakeholders

| Stakeholder            | Interest                                              |
|------------------------|-------------------------------------------------------|
| End user (shopper)     | Compares items quickly to decide a purchase           |
| Frontend developer     | Consumes a stable, documented contract                |
| Challenge reviewer     | Evaluates code quality, decisions, completeness       |

## 4. User stories

- **US-1** — As a shopper, I can fetch full details of one catalog product
  by id, including its offers.
- **US-2** — As a shopper, I can request a comparison of N catalog
  products in one call.
- **US-3** — As a shopper, I can restrict the comparison to a chosen
  subset of fields so the UI is not noisy.
- **US-4** — As a shopper, I can see *which* fields actually differ across
  the compared products, with the winning product flagged when the
  attribute is comparable.
- **US-5** — As a shopper, I can read a one-paragraph natural-language
  summary of the comparison ("X has a larger battery but Y is lighter and
  cheaper"), in my preferred language, produced opportunistically by an
  LLM.
- **US-6** — As a frontend dev, I can list available catalog products
  with paging so I can build a product picker.

## 5. Functional requirements

### 5.1 Read

- **FR-1** — Retrieve a single catalog product by id, including its
  offers and a derived `buyBox`.
- **FR-2** — Retrieve N catalog products in one call (comparison
  endpoint), in the order requested.
- **FR-3** — Support **sparse fieldsets** via a `fields` query parameter
  using comma-separated paths with dot notation (e.g.
  `name,buyBox.price,attributes.battery`). The token `offers` opts in to
  the full offers list (default response carries only `buyBox`).
- **FR-4** — List catalog products with pagination and optional category
  filter (summary projection — no offers, no attributes).
- **FR-5** — Each catalog product carries: `id`, `name`, `description`,
  `imageUrl`, `rating`, `category`, `attributes` (flexible map), and an
  `offers` list. The current best offer is precomputed as `buyBox` and is
  the default representation in compare responses.
- **FR-5a — Rating shape.** `rating` is a single scalar `Double` in
  `[0.0, 5.0]`, rounded to two decimals at the source (seed/persistence)
  to keep payloads stable across runs. It is **not** an object. A
  `{ average, count }` shape was considered (review-confidence signal)
  and explicitly cut from v1: the challenge prompt does not require
  review counts, the seed is synthetic so `count` would carry no signal,
  and inflating the type would ripple through three records, the entity,
  the projector, the seed, and the LLM golden test for marginal value.
  The richer shape is captured in `roadmap.md` for a future iteration
  where real review data is available.
- **FR-6** — Each offer carries: `sellerId`, `sellerName`,
  `sellerReputation`, `price`, `currency`, `condition` (NEW | USED |
  REFURBISHED), `freeShipping`, `stock`.

### 5.2 Comparison enrichment (hybrid)

- **FR-7** — The comparison response carries a `differences[]` array
  containing **only the attributes that actually differ** across the
  compared products. Each entry has `path` (dot-notated, e.g.
  `attributes.battery`, `buyBox.price`), the per-product values, an
  `isComparable` flag (true when values are numerically/orderable
  comparable), and `winnerId` when comparable. This is **deterministic
  and always present**. Rationale for the lean shape: the frontend can
  always reconstruct draws from `items[]`; duplicating them in
  `differences[]` only inflates the payload.
- **FR-7a — Attribute scope (intersection).** When the request does not
  pin specific `attributes.*` paths via `fields`, the diff considers the
  **intersection** of attribute keys present in **all** compared
  products. Attributes that exist in only some of the items are excluded
  from `differences[]` and instead surfaced in `exclusiveAttributes` (FR-7b).
  When the client explicitly asks for an attribute path via `fields`
  (e.g. `attributes.battery`), the client's choice wins — the attribute
  is included in `differences[]` even if missing on some items, with
  `null` values where absent and `isComparable: false`.
- **FR-7b — Cross-category awareness.** The compare response carries:
  - `crossCategory: boolean` — `true` when the compared items do not all
    share the same `category`. The frontend uses this to caution the
    user that winners across heterogeneous categories may not be
    semantically equivalent (e.g. RAM in a phone vs. a laptop).
  - `exclusiveAttributes: { [productId]: string[] }` — for each
    product, the attribute keys it holds that were not present in
    every compared product (i.e. dropped from the intersection).
    Omitted from the response when empty (all items share the same
    attribute keys). Sparse `fields` selections do not populate this
    map — it reflects only what the diff intersection left out.
- **FR-8** — When an LLM is configured and reachable, the comparison
  response carries an additional `summary` field with a natural-language
  comparison overview. The endpoint accepts a `language` query parameter
  (default `pt-BR`, also accepts `en`). When the LLM is missing, times
  out, or fails, `summary` is omitted and the rest of the response is
  unaffected.
- **FR-9** — The LLM call has a hard timeout (default 2 s) and a
  per-call cache keyed by `(sortedIds, fields, language)`.

### 5.3 Errors and validation

- **FR-10** — Comparison accepts **2 to 10** unique ids; outside this
  range yields a structured 400. Duplicates are deduplicated silently.
- **FR-11** — When any id in a compare call is unknown, the call fails
  with 404 listing the missing ids; no partial result.
- **FR-12** — All errors are returned as **RFC 7807 Problem Details**.

## 6. Non-functional requirements

- **NFR-1 — Performance.** P95 latency for cached single-product reads
  under 100 ms on developer hardware (informal target).
- **NFR-2 — Test coverage.** Line coverage of `controller`, `service`,
  and `repository` packages ≥ 80 %, enforced by JaCoCo at `mvn verify`.
- **NFR-3 — Documentation.** OpenAPI 3 generated automatically and served
  at `/swagger-ui.html`; every endpoint has summary + example payloads.
- **NFR-4 — Error contract.** All non-2xx responses follow RFC 7807.
- **NFR-5 — Observability.** Spring Boot Actuator exposes `health`,
  `info`, `metrics`, and `caches`. AI-specific metrics (`ai_calls_total`,
  `ai_latency_seconds`, `ai_fallback_total`, `ai_tokens_total`) are
  emitted via Micrometer. See SPEC-004.
- **NFR-6 — Reproducibility.** A fresh clone builds and runs with
  `mvn spring-boot:run` and no extra setup beyond JDK 21, Maven, and
  optionally an `OPENAI_API_KEY`.
- **NFR-7 — Code quality.** Layered architecture matching the HackerRank
  skeleton (`controller / service / repository / model / exception`);
  Bean Validation on inputs; no business logic in controllers.
- **NFR-8 — Caching.** Single-product lookups cached in-process
  (Caffeine). LLM responses cached in-process. Cache stats observable via
  Actuator.
- **NFR-9 — Graceful degradation.** No feature that depends on the LLM
  may bring the API down. The product is fully usable without an LLM key.
- **NFR-10 — Cost guard.** AI calls have a configurable timeout and an
  optional daily request limit, both observable.

## 7. Out of scope (v1) — and where it lives

| Item                                            | Where                            |
|-------------------------------------------------|----------------------------------|
| Authentication / authorization                  | not planned                      |
| Write operations (POST/PUT/DELETE)              | not planned for this challenge   |
| Persistent or distributed databases             | roadmap R-1                      |
| Inventory / stock movement                      | not planned                      |
| Rate limiting, quotas, throttling at API edge   | roadmap R-6                      |
| **Semantic search (`/search` with embeddings)** | **roadmap R-2 (introduction) and R-3 (hybrid)** |
| LLM-based filter extraction for search          | roadmap R-3                      |
| Internationalization of payloads                | partially via `language` on summary; full I18N not planned |
| Reviews and Q&A                                 | not planned                      |
| Multi-tenant / per-vertical prompts             | roadmap R-4                      |
| Buy-box selection beyond simple deterministic   | not planned (heuristic in SPEC-002 §3.3) |

The semantic-search omission is deliberate: the challenge asks for
*comparison*. Adding a search pipeline (embeddings + vector store + LLM
rerank) introduces a feature the prompt does not request, expands the
failure surface (model availability, snapshot management, cost), and
dilutes the focus of the deliverable. The roadmap captures it with the
same rigor as if it were in scope, signalling awareness without
overreach.

## 8. Constraints

- **C-1** — Java 21 + Spring Boot 3.3 + Maven.
- **C-2** — Persistence simulated locally (H2 in-memory + seed JSON
  file). No vector store in v1.
- **C-3** — Package layout under the HackerRank skeleton root
  `com.hackerrank.sample`: `controller`, `service`, `repository`,
  `model`, `exception`, plus `Application`. Sub-packages such as
  `service/compare` and `service/ai` allowed where cohesion earns
  them. See ADR-0003 (which supersedes ADR-0002) and SUBMISSION.md.
- **C-4** — No real external dependency required at runtime. With
  `OPENAI_API_KEY` set, the LLM `summary` activates. Without it, the
  endpoint still returns the full deterministic comparison.

## 9. Acceptance criteria

- **AC-1** — `GET /api/v1/products/{id}` returns 200 with the canonical
  product (including offers and computed `buyBox`), or 404 with a Problem
  Details body.
- **AC-2** — `GET /api/v1/products/compare?ids=1,2,3` returns the three
  catalog products in the requested order (each with `buyBox` by
  default, no full `offers` list), plus a `differences[]` section
  containing only the attributes that differ. Each entry includes
  `winnerId` when comparable.
- **AC-3** — Adding `&fields=name,buyBox.price,attributes.battery` to
  AC-2 returns exactly those fields; `id` is always returned.
- **AC-4** — Adding `&fields=offers` to AC-2 includes the full `offers`
  list per product instead of only `buyBox`.
- **AC-5** — With `OPENAI_API_KEY` set, the call from AC-2 also
  contains a `summary` string in `pt-BR` by default. Adding
  `&language=en` returns the summary in English. Without the key, the
  same call succeeds with the same `differences[]` and no `summary`
  field.
- **AC-6** — Compare with unknown ids returns 404 with `missingIds` in
  the Problem Details body.
- **AC-7** — Compare with fewer than 2 or more than 10 ids returns 400.
- **AC-8** — All endpoints visible and exercisable in `/swagger-ui.html`.
- **AC-9** — `mvn verify` passes; JaCoCo report shows ≥ 80 % coverage on
  the three core packages.
- **AC-10** — `mvn spring-boot:run` boots the service in under 10 s on
  developer hardware, with or without `OPENAI_API_KEY`. There is no
  embedding warm-up phase.
- **AC-11** — Compare across two products of the same category returns
  `crossCategory: false` and omits `exclusiveAttributes`.
  `differences[]` contains only attribute keys that actually differ
  (intersection equals union when categories match the seed).
- **AC-12** — Compare across products of different categories
  (e.g. one `SMARTPHONE` and one `NOTEBOOK`) returns
  `crossCategory: true`, a `differences[]` restricted to the
  intersection of attribute keys (plus `buyBox.price`, `rating`, etc.
  that are common at the product level), and an `exclusiveAttributes`
  map listing the per-product attributes dropped from the intersection.
- **AC-13** — Compare across different categories with explicit
  `fields=attributes.battery` includes `attributes.battery` in
  `differences[]` even if one product lacks it; the missing value is
  `null` and `isComparable` is `false`. `exclusiveAttributes` reflects
  the intersection logic, not the sparse selection.

## 10. Open questions

*None remaining. v3 resolves Q-1 (language hint, default `pt-BR`),
Q-2 (semantic search removed from v1), and Q-3 (Option A: only
differentiators in `differences[]`).*

## 11. Changelog

- **v8 (2026-04-30)** — Reframe of the optional `/compare` `summary`
  field as a buying guide instead of a flat description. Driven by
  smoke output that read like a narration of `differences[]` ("X has
  3000 mAh, Y has 3200 mAh") with no recommendation. The deterministic
  contract is unchanged: `differences[]` shape, `crossCategory`,
  `exclusiveAttributes`, `language` parameter, sparse `fields=`
  semantics — all stable. The only changes are internal to the LLM
  layer: (a) the prompt is bumped from `compare-summary.v1.md` to
  `compare-summary.v2.md` with anti-hallucination rules, currency
  formatting per language, two distinct buying-guide few-shots and a
  third for the empty-`differences` case; (b) the prompt payload now
  carries an internal `wins` map (per product id, the list of raw
  `<label>: <value>` axes where it wins), computed from
  `differences[*].winnerId` and discarded after rendering; (c) the
  compare cache key is prefixed with `v2|` to invalidate v1 entries.
  Decisions: `wins` is internal-only (does not appear in the JSON
  response); diffs with `winnerId == null` are skipped (cross-category
  exclusivity is already covered by `crossCategory` +
  `exclusiveAttributes`). No FR/AC renumbering. SPEC-004 is unaffected
  — the fallback contract (timeouts, no-key, budget) and the metric
  surface stay identical.
- **v7 (2026-04-29)** — Locked `rating` shape to a scalar `Double` in
  `[0.0, 5.0]` via FR-5a. A prior internal note had floated a
  `{ average, count }` object; that was cut from v1 because the
  challenge prompt does not require review counts, the seed is
  synthetic so `count` would carry no signal, and the richer shape
  would inflate three records, the entity, the projector, the seed,
  and the LLM golden test for marginal value. SPEC-002 already
  documents the scalar shape (§4 INV-3). Added a roadmap pointer for
  the richer shape when real review data is available. SPEC-003 v4
  reflects the same lock with an explicit note in §3.
- **v6 (2026-04-29)** — Cross-category compare semantics formalized.
  Added FR-7a (attribute intersection scope when `fields` does not pin
  attribute paths; sparse selection overrides intersection) and FR-7b
  (`crossCategory` boolean and `exclusiveAttributes` map in the
  response). Added AC-11/AC-12/AC-13 covering same-category, mixed-
  category, and sparse-override paths. No FR/AC renumbering of prior
  items. Drives SPEC-003 v3.
- **v5 (2026-04-29)** — C-3 reverted: HackerRank skeleton root
  (`com.hackerrank.sample`) kept; Application class name reverted to
  `Application`. Driven by ADR-0003 and the paste-by-paste submission
  constraint formalized in SUBMISSION.md. ADR-0001 revived;
  ADR-0002 superseded.
- **v4 (2026-04-28)** — C-3 rewritten: HackerRank skeleton root
  discarded, layered layout adopted under
  `com.mercadolivre.itemcomparison`. See ADR-0002.
- **v3 (2026-04-28)** — Semantic search removed from v1 and moved to
  roadmap R-2/R-3. US-6 (search) dropped; old US-7 renumbered to US-6.
  FR-10..12 (search) dropped; FR-13..15 renumbered to FR-10..12.
  AC-7 (search) dropped; AC-4 added for `fields=offers`. `language`
  query parameter added to compare/summary (default `pt-BR`).
  `differences[]` reshaped to Option A (only differentiators).
  Out-of-scope table now points to roadmap items by ID. Q-1/Q-2/Q-3
  resolved and removed.
- **v2 (2026-04-28)** — CatalogProduct + Offer adopted. Hybrid
  comparison enrichment (deterministic + LLM summary). Semantic search
  added (since reverted in v3).
- **v1 (2026-04-28)** — Initial draft.
