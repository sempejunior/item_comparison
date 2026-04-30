---
id: ROADMAP
title: Production scaling and next bets
version: v5
status: Accepted
last_updated: 2026-04-30
---

# Roadmap — From challenge to production

This roadmap is part of the SDD trail. Every entry follows the same shape:

> **Trigger** — what business or technical signal opens the item.
> **Approach** — concrete strategy.
> **Architecture** — diagram or component sketch.
> **Tradeoffs** — what we accept by choosing this.
> **Effort** — order-of-magnitude estimate.

Items are independent: the order below is *recommended*, not *required*.

## Principles that constrain every step

- **Backwards compatibility on the public API.** Versioning by URL prefix
  (`/api/v1`, `/api/v2`); a v1 client never breaks because of a server
  evolution. Internal contracts can change freely.
- **Reversibility.** Every step ships behind a feature flag or a config
  switch so it can be turned off without a redeploy.
- **Cost-aware AI.** Any LLM/embedding feature ships with timeouts, daily
  budgets, and a deterministic fallback. The product never depends on the
  LLM being available.
- **Observability before optimization.** No tuning is done before metrics
  are in place. *No measure, no change.*

---

## R-1 · Catalog as a service

**Trigger.** The catalog grows past what fits comfortably in H2 (~10⁴
products), or another team needs to write to it.

**Approach.** Extract the `repository` package into a standalone
`catalog-service` (Spring Boot, PostgreSQL). The current `front-api` keeps
its `controller`/`service` layers and consumes the catalog over REST or
gRPC.

**Architecture.**

```mermaid
flowchart LR
  Client -->|HTTPS| FrontAPI
  FrontAPI -->|REST/gRPC| CatalogSvc
  CatalogSvc --> Postgres[(PostgreSQL)]
  CatalogSvc -->|Outbox| EventBus[(Kafka)]
```

**Tradeoffs.** Network hop adds 1–5 ms; gain is independent scaling and a
clear ownership boundary. PostgreSQL replaces H2 for both relational data
and JSON attributes (`jsonb`).

**Effort.** ~3 dev-days for the extraction, plus ~1 for migration tests.

---

## R-2 · Semantic search — from zero to scale

The challenge prompt asks for a comparison API. Semantic search is a
neighbouring feature that adds clear product value (natural-language
discovery: *"a fridge that fits in 60 cm under R$ 3000"*) but introduces
a substantially larger surface: embeddings, a vector store, snapshot
management, model versioning, and a real cost angle. v1 deliberately
**does not ship search** — it stays focused on the asked-for
comparison. This roadmap entry captures the introduction *and* the
scale-up path with the same rigor it would have if it were in scope.

### R-2.0 Introduction (single-process, in-memory)

**Trigger.** Product or business asks for natural-language discovery
across the catalog, and the catalog still fits comfortably in memory
(~10² to ~10³ products).

**Approach.**

1. Add Spring AI's `EmbeddingClient` and `SimpleVectorStore` (in-memory).
2. On boot, serialize each `CatalogProduct` to the deterministic payload
   defined originally in SPEC-002 v2 §7 (`<name>. <description>.
   Category: <cat>. Specifications: k=v; ...`) and embed it.
3. Persist the in-memory store to disk as a JSON snapshot
   (`./.cache/vectors.json`, `.gitignored`) so reboots avoid re-paying
   for embeddings. Snapshot is invalidated by a hash of `(model, seed
   file)`.
4. Expose `GET /api/v1/products/search?q=&category=&maxPrice=&topK=`:
   query embedded → cosine similarity → top-K → optional LLM rerank
   with one-line `explanation` per hit.
5. **Lexical fallback** (`name + description + attributes` regex) when
   no `OPENAI_API_KEY` is set. The endpoint never returns 503.
6. Hard filters (`category`, `maxPrice`) are applied on the structured
   side **before** vector ranking — the LLM does not see filtered-out
   products.

**Architecture.**

```mermaid
flowchart LR
  Boot[Spring boot] --> Loader[Seed loader]
  Loader --> Embed[EmbeddingClient]
  Embed --> Store[(SimpleVectorStore<br/>in-memory + JSON snapshot)]
  Q[GET /search?q=...] --> Filter[Hard filters]
  Filter --> Sim[Cosine top-K vs Store]
  Sim --> Rerank[LLM rerank + explanation<br/>optional]
  Rerank --> Resp[Response]
  Q -.->|no key / LLM down| Lex[Lexical fallback]
  Lex --> Resp
```

**Tradeoffs.** This is "level 2" RAG: good enough for a catalog the
size of a demo or an MVP, terrible for a mutable production catalog.
Updates require a full re-embed (boot-time only). No write path — the
moment any team needs to insert/update/delete products at runtime,
move to R-2.1.

**Effort.** ~2 dev-days including snapshot, lexical fallback, prompt
template, and tests with a stubbed `EmbeddingClient`.

### R-2.1 Lifecycle of a product update at scale

```mermaid
sequenceDiagram
  autonumber
  participant Admin
  participant CatalogSvc
  participant DB as PostgreSQL
  participant Outbox
  participant Bus as Kafka topic<br/>catalog.product.upserted
  participant Worker as embedding-worker
  participant OpenAI
  participant Vec as Vector DB<br/>(pgvector / OpenSearch)
  participant FrontAPI

  Admin->>CatalogSvc: PUT /products/42
  CatalogSvc->>DB: BEGIN tx
  CatalogSvc->>DB: UPDATE products SET ...
  CatalogSvc->>Outbox: INSERT outbox(event=ProductUpserted, id=42, version=v3)
  CatalogSvc->>DB: COMMIT
  Outbox-->>Bus: relay (debezium / outbox poller)
  Bus->>Worker: ProductUpserted{id=42, version=v3}
  Worker->>CatalogSvc: GET /products/42 (read canonical)
  Worker->>OpenAI: embeddings.create(input=serialize(product))
  OpenAI-->>Worker: vector[1536]
  Worker->>Vec: UPSERT (id=42, vector, model_version=text-3-small, payload_version=v3)
  Worker-->>Bus: ack
  FrontAPI->>Vec: similarity_search(query_vector, topK)
```

Key properties of this design:

- **Outbox pattern** — the embedding worker never reads from the
  application database directly; it consumes events emitted as part of the
  same DB transaction that wrote the product. No "lost event" race.
- **Idempotency** — every event carries a monotonic `version`. The worker
  drops events with a version older than what is already in the vector
  store. Replays from the topic are safe.
- **Eventual consistency** — search reflects writes within seconds, not
  milliseconds. This is the right tradeoff: comparison reads are by id
  (always strongly consistent via `CatalogSvc`), only semantic search is
  eventually consistent. Document this in the API contract.
- **Worker isolation** — the embedding worker is a separate deployable.
  It can be scaled, paused, or rate-limited without touching the API.

### R-2.2 Insert and delete

- **Insert.** Same flow as update; the vector store treats it as an
  upsert. No special path.
- **Delete.** Catalog emits `ProductDeleted{id, version}`. Worker removes
  the vector from the store. The worker also tombstones recently deleted
  ids for `~24 h` to absorb out-of-order events.

### R-2.3 Embedding model versioning

Embeddings produced by `text-embedding-3-small` are not comparable with
embeddings from a different model. When upgrading models:

1. Bring up the new model alongside the old one.
2. Re-embed the entire catalog into a *new* index/namespace
   (`embeddings_v2`) using the worker, throttled by the daily budget.
3. Switch read traffic to the new index behind a feature flag, with
   shadow-comparing precision/recall on a labeled sample.
4. Remove the old index once stable.

This is **blue-green for vector indexes**. It is mandatory whenever the
embedding model or the serialization format of the input changes.

### R-2.4 Backpressure and cost guard

- **Token budget per day**, enforced at the worker (`AI_DAILY_REQUEST_LIMIT`).
  Excess events park on the topic with consumer lag observable in
  Grafana — no data loss, just slower index freshness.
- **Per-event cost telemetry** — `ai_embedding_tokens_total{model=...}` is
  emitted per event. The cost dashboard is computed from these counters.
- **Circuit breaker** on the OpenAI client (Resilience4j). When tripped,
  the worker pauses consumption rather than burning the budget on
  failures.

### R-2.5 Vector store choices

| Option         | When to pick                                                      |
|----------------|-------------------------------------------------------------------|
| `pgvector`     | Already on PostgreSQL, < 10⁶ products, want one less infra piece. |
| `OpenSearch` k-NN | Already on OpenSearch for full-text; combines lexical + vector. |
| `Weaviate`/`Qdrant` | Need hybrid search and rich metadata filters out of the box. |
| Managed (Pinecone) | No platform team, willing to pay for SLA.                     |

We default to **pgvector** in R-2.5 because it ships with the catalog DB
and is good enough up to ~10⁶ documents.

**Tradeoffs.** This pipeline is not free: it adds a topic, a worker, an
outbox, and a vector DB. The win is that the index is always *correct*
and the API is decoupled from OpenAI's availability.

**Effort.** R-2.0 (introduction) ~2 dev-days. R-2.1 to R-2.4 (scale-up)
~5 dev-days on top. R-2.5 (pgvector) ~1 day.

---

## R-3 · Search level 3 — hybrid retrieval

**Trigger.** R-2.0 (level 2 RAG) is live, but quality plateaus on queries
that mix hard constraints (size, price) with soft preferences (style,
use case).

**Approach.** Two-stage retrieval:

1. **LLM filter extraction** — `query → { category, price≤X, width≤Y, ... }`.
   Cheap, structured, validated by Bean Validation. Anything the LLM
   outputs that does not match a known attribute is dropped, not raised.
2. **Vector similarity** on the filtered universe.
3. **LLM rerank + explanation** on the top K.

**Architecture.**

```mermaid
flowchart TB
  Q[User query] --> Extract[LLM: extract filters]
  Extract --> Hard[Hard filters in catalog]
  Hard --> Vec[pgvector top-K by similarity]
  Vec --> Rerank[LLM rerank + 1-line explain]
  Rerank --> Resp[Response]
  Q -.->|fallback if LLM down| Lexical[Lexical search by tokens]
  Lexical --> Resp
```

**Tradeoffs.** One extra LLM call per query (cost). Two-stage error mode:
extractor can drop a useful soft constraint. Mitigated by always
embedding the *original* query in the rerank context.

**Effort.** ~3 dev-days on top of R-2.

---

## R-4 · Comparison v3 — multi-tenant prompts and personalization

**Trigger.** Different verticals (electronics vs fashion vs grocery) want
the comparison summary to emphasize different things.

**Approach.** Prompts as **versioned resources** (`prompts/compare/v3.md`)
selected by `category` and optionally by `tenantId`. Per-tenant rate
limits and overrides. A/B testing of prompt variants with outcome metrics
(click-through on each compared product, time on page) collected via a
dedicated event topic.

**Tradeoffs.** Prompt sprawl is the real risk. Mitigation: every prompt
in git, with a one-paragraph rationale and a regression test that pins a
golden output for a fixed input.

**Effort.** ~2 dev-days for the framework, ongoing for each vertical.

---

## R-5 · Compared-pairs analytics

**Trigger.** Product team wants to know what users actually compare so
the catalog can highlight competitive pairs.

**Approach.** Every compare call emits a `ComparisonRequested` event with
the (sorted) ids and timestamp. A streaming job (Flink / Kafka Streams)
maintains rolling top-pairs by category. Surface in an internal dashboard
and feed back into "frequently compared" UI hints.

**Tradeoffs.** Privacy: events are catalog ids only, no user PII unless
strictly needed and consented.

**Effort.** ~3 dev-days end to end.

---

## R-6 · Resilience and SLO

**Trigger.** The API leaves "demo" status and gets a real SLO.

**Approach.**

- Per-endpoint **SLO** (e.g. `GET /products/{id}` 99.9 % availability,
  P95 < 100 ms cached / < 300 ms cold).
- Resilience4j **circuit breakers** on every cross-service call (catalog,
  OpenAI, vector DB).
- **Bulkheads** — separate thread pools for AI calls so a stuck OpenAI
  request cannot exhaust the connector pool.
- **Adaptive timeouts** based on observed P99 latency.
- **Chaos drills** — kill OpenAI, kill vector DB, kill catalog, in a
  staging environment monthly.

**Effort.** ~4 dev-days plus ongoing.

---

## R-7 · Observability uplift

**Trigger.** Anything beyond local development.

**Approach.**

- **OpenTelemetry** for traces; correlation id from the edge through
  catalog and AI workers.
- **Micrometer** metrics already exposed in v1; route to Prometheus +
  Grafana, dashboards versioned in git (`infra/grafana/`).
- **Structured JSON logs** with `trace_id`, `tenant_id`, `cache_hit`,
  `ai_used`, `ai_fallback_reason` fields. Loki or CloudWatch.
- **AI-specific signals** (counter set):
  - `ai_calls_total{kind=summary|search, outcome=ok|timeout|error|fallback}`
  - `ai_latency_seconds{kind=...}`
  - `ai_tokens_total{kind=...,direction=in|out}`
  - `vector_search_latency_seconds`
  - `cache_hits_total{cache=products|ai_summary|embeddings}`

**Effort.** ~3 dev-days for the first wave.

---

## R-8 · Multi-region / multi-currency

**Trigger.** The product expands to a second country in MELI.

**Approach.** `currency` and `locale` become first-class on `Offer` (not
`CatalogProduct`). Comparisons within a region only. Search prompts
localized; embeddings can stay multilingual (`text-embedding-3-small`
handles ~100 languages reasonably) or split per language for precision.

**Tradeoffs.** Re-embedding cost when adding a language. Currency
conversion is a UX decision — we do *not* convert in the API; we expose
the offer's native currency.

**Effort.** ~5 dev-days.

---

---

## R-9 · Natural-language category exploration (RAG over insights)

**Trigger.** SPEC-005 is live and shoppers ask the catalog questions in
their own words — not "show me SMARTPHONE insights" but *"qual celular
custa até R$ 3000 e tem boa bateria?"* or *"melhor notebook leve para
viagem"*. The enum-based `category` parameter cannot answer that.

**Approach.** Two layered evolutions on top of SPEC-005, each shippable
independently:

### R-9.1 Free-form query → category insights

1. New endpoint `GET /api/v1/products/explore?q=<natural-language>&topK=`.
2. **LLM filter extraction** (R-3 reused) maps the query to
   `(category, attribute constraints)`. Constraints are validated
   against the existing attribute metadata; anything unrecognized is
   dropped, not raised.
3. The structured constraints feed `CategoryInsightsService` (SPEC-005)
   so the response shape is the same `rankings[] / topItems[] / summary`
   the frontend already renders. Only the entry point changes.
4. The LLM `summary` is given the **original user query** as additional
   prompt context so the panorama is framed around what the user
   actually asked.

### R-9.2 RAG over rankings

1. Embeddings of the seed catalog (R-2.0 vector store) plus embeddings
   of the *deterministic ranking entries* are stored alongside each
   product.
2. Query is embedded; top-K products by cosine similarity are mixed
   with the deterministic insights from R-9.1 in a single LLM rerank
   prompt that explains *why* each item fits the query and how it
   compares on the key attributes.
3. Lexical fallback when the LLM is down: regex over `name +
   description + attributes`, scored by attribute matches relative to
   the parsed constraints from R-9.1. The endpoint never returns 503.

**Architecture.**

```mermaid
flowchart LR
  Q[GET /products/explore?q=...] --> Extract[LLM: extract category + filters]
  Extract --> Insights[CategoryInsightsService<br/>SPEC-005]
  Q --> Embed[Query embedding]
  Embed --> Vec[(Vector store<br/>R-2.0)]
  Vec --> Top[Top-K candidates]
  Insights --> Merge[Merge: rankings ∪ candidates]
  Top --> Merge
  Merge --> Rerank[LLM rerank + per-item explanation<br/>+ panorama summary]
  Rerank --> Resp[Response: rankings + topItems + summary + perItemExplanations]
  Q -.->|LLM down| Lex[Lexical fallback]
  Lex --> Resp
```

**Tradeoffs.** Adds two LLM calls per query (extract + rerank). Cost is
mitigated by caching `(normalizedQuery, locale)` and by the budget guard
already enforced for SPEC-004/SPEC-005 (`DailyBudget`). The
deterministic guarantee weakens — two queries that mean the same thing
in different words will produce slightly different responses. Mitigation:
the cache key normalizes the query (lowercase, trimmed punctuation) and
the deterministic `rankings[]` for the inferred category remains the
load-bearing payload — only the framing changes.

**Effort.** R-9.1 ~2 dev-days on top of SPEC-005 + R-3. R-9.2 ~3
dev-days on top of R-2.0 + R-9.1.

---

## R-10 · Extended filters on /category-insights (post-v5)

**Status.** v5 of SPEC-005 ships `minPrice`, `maxPrice`, `minRating`
applied **in-memory** in the service layer (ADR-0006). R-10 covers the
*next* filters that did not make the v5 cut.

**Trigger.** UX research shows that price + rating bounds aren't enough
for some categories — shoppers want to slice by `condition` (NEW vs
REFURBISHED vs USED) or by `brand`.

**Approach.** Add two more opt-in parameters to
`/api/v1/products/category-insights`, validated and threaded through
the same `InsightsFilters` record introduced in slice 5:

- `condition` (enum: `NEW | REFURBISHED | USED`) — applied on the
  buy-box offer.
- `brand` (String, exact match against `attributes.brand`).

Both follow the same pipeline as v5: applied once in the service,
described in the prompt context, hashed into the cache key.

**Tradeoffs.** Surface grows: more validation paths, more OpenAPI
examples, more RFC 7807 cases, ~1 extra test per filter. Cache
fragmentation accelerates — track the miss rate to decide when R-11
denormalization becomes mandatory.

**Effort.** ~1 dev-day on top of slice 5 (the predicate record,
prompt block and cache plumbing already exist).

---

## R-11 · Insights at scale — denormalize, push down, materialize

**Trigger.** Any of: catalog cardinality per category passes ~10 000
products; P95 of `/category-insights` (LLM disabled) exceeds 250 ms;
`ai-category-insights` cache miss rate climbs above 30 %. ADR-0006
records the same triggers from the perspective of the slice-5 design.

**Approach.** Three layered moves, each shippable independently:

### R-11.1 Denormalize derived columns

`buyBox.price` is computed per request from the offer set today
(ADR-0004 rule). At scale, that is the wrong shape for a filter. The
move:

1. Add `buy_box_price`, `buy_box_currency`, `buy_box_offer_id`,
   `rating_avg`, `rating_count` columns on `catalog_product`.
2. Populate from an application event (`OfferUpserted`,
   `OfferRemoved`, `ReviewCreated`) emitted in the same DB
   transaction that wrote the offer/review (outbox pattern, same
   shape as R-2.1). The buy-box service stays as the source of
   truth for the *full* `BuyBox` projection — denormalization only
   serves filter predicates.
3. Composite indexes `(category, buy_box_price)`,
   `(category, rating_avg)`. B-tree is enough; both have high
   cardinality.

### R-11.2 Push filters into the data layer

`InsightsFilters` (slice 5 record) gains a second method
`toSpec() : Specification<CatalogProductEntity>`. The `matches`
method stays — tests and the prompt-context describer must remain
JPA-independent. The single line in `CategoryInsightsService.insights`
flips from `.stream().filter(filters::matches)` to
`repository.findAll(filters.toSpec(category))`. No other slice-5
surface changes.

### R-11.3 Materialize rankings

When category cardinality crosses ~100 000, computing rankings per
request (even from the DB) becomes the bottleneck. Move to a batch
job (Spark / Flink) writing into `category_attribute_ranking`
(`category`, `attribute_path`, `winner_id`, `runner_up_id`, `spread`,
`computed_at`), refreshed hourly. The API reads the materialized
table and applies filter predicates as a final WHERE on top.

**Architecture.**

```mermaid
flowchart LR
  Off[Offer write] --> Tx[(catalog tx)]
  Tx --> Outbox[(outbox)]
  Outbox --> Bus[Kafka<br/>catalog.product.upserted]
  Bus --> Rebuild[denorm-worker<br/>updates buy_box_price, rating_avg]
  Rebuild --> Pg[(PostgreSQL<br/>catalog_product +<br/>category_attribute_ranking)]
  Bus --> Batch[batch ranker<br/>hourly]
  Batch --> Pg
  API[/category-insights/] --> Cache[(Redis<br/>category, filtersHash)]
  Cache -.miss.-> Pg
  Pg --> Cache
  Cache --> API
```

**Tradeoffs.** Eventual consistency: filters reflect writes within
seconds, materialized rankings within an hour. Acceptable because the
shopper's mental model of a "category panorama" is not real-time.
Operational cost: a topic, a worker, two more indexed columns, a
batch job. The win is filter latency under 50 ms even at MELI scale
and AI cost decoupled from per-request work.

**Effort.** R-11.1 ~2 dev-days. R-11.2 ~1 day on top. R-11.3 ~3 days
on top, plus standing ops for the batch.

---

## What we explicitly do not plan to do

- **GraphQL.** REST + sparse fieldsets covers the same need with less
  infrastructure for our scale.
- **WebSocket / SSE for compare.** Inline summary with timeout is the
  right shape; streaming is a UX preference, not a backend constraint.
- **Custom-trained ranking model.** Off-the-shelf embeddings + LLM rerank
  is enough until we have labeled relevance data, which we will not have
  until R-5 has been live for months.
- **Edge / CDN caching of compare.** Combinatorial keyspace makes the
  hit-rate too low to justify; per-product GET already supports `ETag`
  for client and CDN caching.

## Effort summary

| Item | Days | Order |
|------|------|-------|
| R-1 Catalog as a service | 4 | First if scale forces it |
| R-2.0 Semantic search introduction | 2 | First AI-discovery bet |
| R-2.1–2.5 Embedding pipeline at scale | 6 | After R-2.0 outgrows in-memory |
| R-3 Hybrid search (level 3) | 3 | After R-2.1+ |
| R-4 Multi-tenant prompts | 2+ | When verticals diverge |
| R-5 Comparison analytics | 3 | Product-driven |
| R-6 Resilience / SLO | 4 | Production gate |
| R-7 Observability uplift | 3 | Production gate |
| R-8 Multi-region | 5 | Business-driven |
| R-9.1 Free-form query → category insights | 2 | After SPEC-005 + R-3 |
| R-9.2 RAG over rankings | 3 | After R-2.0 + R-9.1 |
| R-10 Extended filters on /category-insights (`condition`, `brand`) | 1 | After Slice 5 |
| R-11 Insights at scale (denormalize + push down + materialize)     | 6 | When triggers fire |

## Changelog

- **v5 (2026-04-30)** — Reframed R-10 to cover only the *post-v5*
  filters (`condition`, `brand`); the v5 filters (`minPrice`,
  `maxPrice`, `minRating`) are SPEC-005 v5, not roadmap. Added R-11
  (Insights at scale) covering the three layered moves the slice 5
  ADR-0006 promised: R-11.1 denormalize `buy_box_price` + `rating_avg`
  via outbox-driven worker; R-11.2 push `InsightsFilters` into a JPA
  `Specification`; R-11.3 materialize `category_attribute_ranking`
  via hourly batch + Redis cache. Triggers, architecture diagram,
  tradeoffs and effort spelled out. Effort table updated.
- **v4 (2026-04-30)** — Added R-10 (Structured filters on
  `/category-insights`: `minPrice`, `maxPrice`, `minRating`,
  `condition`, optional `brand`). Filters are applied as JPA predicates
  before ranking and threaded into the LLM prompt context. Earmarked
  as Slice 5, to ship right after SPEC-005 (Slice 4) closes. Effort
  table updated.
- **v3 (2026-04-30)** — Added R-9 (Natural-language category
  exploration) as a two-step evolution of SPEC-005: R-9.1 wraps an
  LLM filter extractor over `CategoryInsightsService` so the user can
  ask in free-form text instead of choosing a category enum; R-9.2
  layers vector retrieval (R-2.0) and per-item rerank explanations on
  top. Effort table updated accordingly.
- **v2 (2026-04-28)** — Added R-2.0 (Semantic search introduction) at
  the top of R-2 to capture the level-2 RAG that v1 of SPEC-001
  originally proposed and that v3 deferred. R-2.1+ explicitly described
  as the scale-up path. R-3 trigger reworded to depend on R-2.0. Effort
  table split R-2.0 from R-2.1–2.5.
- **v1 (2026-04-28)** — Initial draft. Captures embedding pipeline at
  scale, hybrid search evolution, observability and resilience uplifts.
