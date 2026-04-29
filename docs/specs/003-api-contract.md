---
id: SPEC-003
title: API Contract
version: v3
status: Draft
last_updated: 2026-04-29
depends_on: [SPEC-001, SPEC-002]
---

# SPEC-003 — API Contract

The contract a frontend consumer can rely on. The OpenAPI document served
at `/v3/api-docs` is generated from the code; this spec is the source of
truth that the OpenAPI document must match.

## 1. Conventions

- Base path: `/api/v1`.
- All responses are JSON (`application/json`).
- Successful reads use `200 OK`. There are no `201`/`204` (read-only API).
- Errors follow **RFC 7807 Problem Details** (`application/problem+json`).
- The `id` field is always present in any product representation, even
  when `fields` is used, so the consumer can correlate columns.
- The default product representation in compare exposes `buyBox`, not
  the full `offers` list. Use `?fields=offers` to opt in.

## 2. Endpoints

### 2.1 List products

```
GET /api/v1/products?page={page}&size={size}&category={category}
```

| Param      | Type   | Default | Constraints              |
|------------|--------|---------|--------------------------|
| `page`     | int    | 0       | ≥ 0                      |
| `size`     | int    | 20      | 1..100                   |
| `category` | string | —       | one of the enum values   |

**200 OK** — summary projection. No `attributes`, no full `offers`; only
the buy-box price is surfaced for picker UIs.

```json
{
  "items": [
    { "id": 1, "name": "Galaxy S24", "category": "SMARTPHONE",
      "imageUrl": "https://example.com/s24.jpg",
      "buyBox": { "price": 4899.00, "currency": "BRL" } }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 12,
  "totalPages": 1
}
```

### 2.2 Get one product

```
GET /api/v1/products/{id}?fields={csv}
```

| Param    | Type   | Default | Constraints                            |
|----------|--------|---------|----------------------------------------|
| `fields` | string | —       | comma-separated paths; dot for nested  |

**200 OK** — full product (or filtered when `fields` present), including
the full `offers` list and computed `buyBox`. The single-product GET
always returns offers — sparse fieldsets are the way to slim it down.

**404** — `type=/errors/not-found`, body includes the missing id.

```bash
curl -s 'http://localhost:8080/api/v1/products/1?fields=name,buyBox.price,attributes.battery'
```
```json
{
  "id": 1,
  "name": "Galaxy S24",
  "buyBox": { "price": 4899.00 },
  "attributes": { "battery": "4000 mAh" }
}
```

### 2.3 Compare products

```
GET /api/v1/products/compare?ids={csv}&fields={csv}&language={lang}
```

| Param      | Type   | Default | Constraints                                            |
|------------|--------|---------|--------------------------------------------------------|
| `ids`      | string | —       | required; 2..10 unique ids; duplicates deduplicated    |
| `fields`   | string | —       | optional; same syntax as §2.2 + special token `offers` |
| `language` | string | `pt-BR` | one of `pt-BR`, `en`. Drives the `summary` language.   |

**200 OK** — products in the order supplied (after dedup), each filtered
if `fields` is given, plus a `crossCategory` boolean, a `differences[]`
array, an optional `exclusiveAttributes` map, and (when the LLM is
configured and reachable) a `summary` string. See SPEC-001 §5.2 FR-7a/FR-7b
for the intersection semantics that drive `differences[]` and
`exclusiveAttributes`.

```bash
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2'
```
```json
{
  "fields": null,
  "language": "pt-BR",
  "crossCategory": false,
  "items": [
    { "id": 1, "name": "Galaxy S24", "category": "SMARTPHONE",
      "buyBox": { "price": 4899.00, "currency": "BRL" },
      "attributes": { "battery": "4000 mAh", "memory": "8 GB",
                       "storage": "256 GB", "brand": "Samsung" } },
    { "id": 2, "name": "iPhone 15", "category": "SMARTPHONE",
      "buyBox": { "price": 6999.00, "currency": "BRL" },
      "attributes": { "battery": "3349 mAh", "memory": "6 GB",
                       "storage": "128 GB", "brand": "Apple" } }
  ],
  "differences": [
    { "path": "buyBox.price", "isComparable": true, "winnerId": 1,
      "values": { "1": 4899.00, "2": 6999.00 } },
    { "path": "attributes.battery", "isComparable": true, "winnerId": 1,
      "values": { "1": "4000 mAh", "2": "3349 mAh" } },
    { "path": "attributes.memory", "isComparable": true, "winnerId": 1,
      "values": { "1": "8 GB", "2": "6 GB" } },
    { "path": "attributes.storage", "isComparable": true, "winnerId": 1,
      "values": { "1": "256 GB", "2": "128 GB" } },
    { "path": "attributes.brand", "isComparable": false, "winnerId": null,
      "values": { "1": "Samsung", "2": "Apple" } }
  ],
  "summary": "O Galaxy S24 leva vantagem em bateria, memória, armazenamento e preço; o iPhone 15 é mais caro mas mantém o ecossistema Apple."
}
```

Notes on shape:

- `differences[]` includes **only the attributes that actually differ**
  across the compared products. Equal-value attributes are not repeated
  here — the frontend can read them from `items[]`. This keeps the
  payload lean and the diff section focused.
- `isComparable` is `true` when values are numerically or ordinally
  comparable across all products (e.g. prices in the same currency,
  numeric `battery` values with the same unit). When `false`, `winnerId`
  is `null`.
- `winnerId` follows attribute semantics: lower is better for `price`;
  higher is better for `battery`, `memory`, `storage`, `rating`. Other
  attributes default to `isComparable: false`.
- `summary` is omitted entirely when the LLM is not configured, times
  out, or fails. The consumer must treat it as optional.
- `crossCategory` is `true` when the compared items do not all share
  the same `category`. Frontends should display a caveat next to
  cross-category winners (e.g. "comparing RAM across phone and laptop
  is not strictly equivalent").
- `exclusiveAttributes` is an object keyed by product id, listing the
  attribute keys each product carries that were dropped from the
  intersection used to build `differences[]`. The field is omitted
  entirely when all compared products share the same attribute keys.
  Sparse `fields` selections (e.g. `attributes.battery`) bypass the
  intersection for the included paths but do not change
  `exclusiveAttributes`.

```bash
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2&fields=name,buyBox.price&language=en'
```
```json
{
  "fields": ["name", "buyBox.price"],
  "language": "en",
  "items": [
    { "id": 1, "name": "Galaxy S24", "buyBox": { "price": 4899.00 } },
    { "id": 2, "name": "iPhone 15",  "buyBox": { "price": 6999.00 } }
  ],
  "differences": [
    { "path": "buyBox.price", "isComparable": true, "winnerId": 1,
      "values": { "1": 4899.00, "2": 6999.00 } }
  ],
  "summary": "Galaxy S24 is significantly cheaper than iPhone 15 in this listing."
}
```

`differences[]` is computed across the **selected** fieldset: when
`fields` is provided, only those paths are considered for diffing.

```bash
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2&fields=offers'
```
Returns each item with the full `offers` list instead of `buyBox`. The
diff still operates on `buyBox` for the price entry; per-offer diffs
are out of scope (see SPEC-002 §8 Q-1, roadmap).

#### Cross-category example

```bash
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,21'
```
```json
{
  "fields": null,
  "language": "pt-BR",
  "crossCategory": true,
  "items": [
    { "id": 1, "name": "Galaxy S24", "category": "SMARTPHONE",
      "buyBox": { "price": 4899.00, "currency": "BRL" },
      "attributes": { "battery": "4000 mAh", "memory": "8 GB",
                       "storage": "256 GB", "brand": "Samsung",
                       "os": "Android 14" } },
    { "id": 21, "name": "ThinkPad X1", "category": "NOTEBOOK",
      "buyBox": { "price": 12499.00, "currency": "BRL" },
      "attributes": { "memory": "16 GB", "storage": "1 TB",
                       "brand": "Lenovo", "cpu": "Core i7-1365U" } }
  ],
  "differences": [
    { "path": "buyBox.price", "isComparable": true, "winnerId": 1,
      "values": { "1": 4899.00, "21": 12499.00 } },
    { "path": "attributes.memory", "isComparable": true, "winnerId": 21,
      "values": { "1": "8 GB", "21": "16 GB" } },
    { "path": "attributes.storage", "isComparable": true, "winnerId": 21,
      "values": { "1": "256 GB", "21": "1 TB" } },
    { "path": "attributes.brand", "isComparable": false, "winnerId": null,
      "values": { "1": "Samsung", "21": "Lenovo" } }
  ],
  "exclusiveAttributes": {
    "1":  ["battery", "os"],
    "21": ["cpu"]
  }
}
```

Note how `attributes.battery` is **not** in `differences[]` — it exists
only on the smartphone, so it is dropped from the intersection and
listed under `exclusiveAttributes["1"]`. Likewise `cpu` exists only on
the notebook. To force a comparison anyway, the client opts in via
`fields`:

```bash
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,21&fields=name,attributes.battery'
```

The `attributes.battery` path is then included in `differences[]` with
`values: { "1": "4000 mAh", "21": null }` and `isComparable: false`.
`exclusiveAttributes` is unchanged because it reflects the intersection
logic, not the sparse selection.

**404** — at least one id is unknown. No partial result.
```json
{
  "type": "https://api.example.com/errors/not-found",
  "title": "Product(s) not found",
  "status": 404,
  "detail": "One or more product ids do not exist",
  "instance": "/api/v1/products/compare",
  "missingIds": [99, 100]
}
```

**400** — fewer than 2 or more than 10 ids, malformed `fields`,
unsupported `language`, etc.

## 3. The `fields` parameter

- Comma-separated list of paths. Whitespace around commas is tolerated.
- A path is a sequence of segments joined by `.`; valid prefixes in v1:
  `name`, `description`, `imageUrl`, `rating`, `category`,
  `buyBox`, `buyBox.<offerField>`, `attributes.<key>`, and the special
  token `offers` (compare only).
- Unknown top-level paths are ignored (no error). Unknown `attributes.*`
  keys produce no entry but do not error — the frontend should be
  resilient to product-level differences (e.g. battery is meaningless on
  a book).
- `id` is implicitly included.
- Inclusion-only; there is no exclusion syntax in v1.

## 4. Error model (RFC 7807)

All non-2xx responses share this body shape:

```json
{
  "type":   "https://api.example.com/errors/<slug>",
  "title":  "<short human title>",
  "status": <int>,
  "detail": "<longer explanation>",
  "instance": "<request path>",
  "<context-specific fields>": ...
}
```

Slugs and statuses defined for v1:

| Slug              | Status | When                                               |
|-------------------|--------|----------------------------------------------------|
| `validation`      | 400    | Bean Validation failure on params or body          |
| `bad-request`     | 400    | Malformed query param (e.g. non-numeric id, bad `language`) |
| `not-found`       | 404    | Single id unknown, or any id in a compare unknown  |
| `internal`        | 500    | Unhandled exception (generic message)              |

`validation` includes a `fieldErrors` array; `not-found` includes
`missingIds` for compare and `id` for single GET.

## 5. HTTP status reference

| Status | Used for                              |
|--------|---------------------------------------|
| 200    | Successful read                       |
| 400    | Validation / malformed input          |
| 404    | Resource not found                    |
| 500    | Unhandled internal error              |

`401`, `403`, `409`, `422` are not used in v1 (no auth, no writes).

## 6. Caching headers

Single-product GET responses set `Cache-Control: max-age=300` and a
strong `ETag` (via Spring's `ShallowEtagHeaderFilter`). The compare
endpoint does not set `Cache-Control` because its response varies on
every parameter combination, including `language` and the LLM availability.

## 7. Examples (curl)

```bash
# list
curl -s 'http://localhost:8080/api/v1/products?page=0&size=5'

# single, full
curl -s 'http://localhost:8080/api/v1/products/1'

# single, sparse
curl -s 'http://localhost:8080/api/v1/products/1?fields=name,buyBox.price'

# compare, default (buyBox + differences + summary if available)
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2,3'

# compare, sparse + English summary
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2,3&fields=name,buyBox.price,attributes.battery&language=en'

# compare, with full offers list per product
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,2&fields=offers'

# compare, error: unknown id
curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,99'

# compare, error: too many ids
curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,2,3,4,5,6,7,8,9,10,11'

# compare, cross-category (smartphone + notebook)
curl -s 'http://localhost:8080/api/v1/products/compare?ids=1,21'
```

## 8. Open questions

- **Q-1** — Accept ids as repeated params (`?ids=1&ids=2`) in addition
  to CSV? *Proposed: support both — Spring binds `List<Long>` from
  either form. Decide in planning.*
- **Q-2** — Add `X-Total-Count` header on the list endpoint as well as
  in the body? *Proposed: no. The body already carries totals; an
  extra header is redundant and easy to forget to keep in sync.*

*Q-1 (v1) about ETag is resolved: yes, ship via
`ShallowEtagHeaderFilter` on single-product GET (§6). No code cost.*

## 9. Changelog

- **v3 (2026-04-29)** — Aligned with SPEC-001 v6: compare response now
  documents `crossCategory: boolean` and an optional
  `exclusiveAttributes: { [productId]: string[] }` map. Added a
  cross-category curl example and a sparse-override note. The diff
  scope (intersection vs. user-pinned `fields`) is described in §2.3
  with cross-references to SPEC-001 FR-7a/FR-7b. No endpoint shape
  removed; only additions.
- **v2 (2026-04-28)** — Aligned with SPEC-001 v3 and SPEC-002 v3:
  removed `/search` mention (none in v1; deferred to roadmap R-2/R-3);
  compare endpoint now documents `differences[]` (Option A — only
  differentiators), `summary` (LLM-optional), `language` query param
  (default `pt-BR`), and `fields=offers` opt-in. List response now
  surfaces `buyBox` instead of a flat `price`. ETag question resolved.
- **v1 (2026-04-28)** — Initial draft.
