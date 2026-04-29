---
id: ADR-0004
title: BuyBox selection heuristic for compare responses
status: Accepted
date: 2026-04-29
deciders: candidate
supersedes: —
superseded_by: —
related: [SPEC-001, SPEC-002, PLAN]
---

# ADR-0004 — BuyBox selection heuristic for compare responses

## Status

Accepted — 2026-04-29.

## Context

SPEC-002 §3.3 sketches a buy-box selection rule but does not lock the
precise tie-breaking sequence. The compare endpoint depends on this
being deterministic in two places:

- `differences[].path = "buyBox.price"` reads `buyBox.price` per
  product. If the heuristic flips between equally valid offers across
  runs, the same compare call yields different `winnerId`s.
- The ordering of `differences[]` (by relative spread of comparable
  values) depends on stable per-product prices.

The rule must therefore be a **pure, total function** over
`List<Offer>` with a defined output for every input — including ties
and "no offer with stock".

## Decision

The buy-box of a `CatalogProduct` is selected by:

1. **Filter** offers where `stock > 0`.
2. **Tier preference.** Prefer `condition = NEW`. If no NEW offer has
   stock, prefer `REFURBISHED`. Else use `USED`.
3. **Within the chosen tier, lowest `price`.**
4. **Tie on price → highest `sellerReputation`.**
5. **Tie persists → lowest `sellerId` lexicographically** (stable,
   total order).
6. If no offer has stock at any tier, `buyBox = null`. The product
   still appears in compare responses; its `buyBox`-derived diff
   entries (e.g. `buyBox.price`) are marked `isComparable = false` with
   a null per-product value.

The algorithm lives in `service/BuyBoxSelector` (per PLAN §2), takes
`List<Offer>` and returns `Offer | null`, and is unit-tested
exhaustively against the edge cases below.

## Alternatives considered

### A. Weight `freeShipping` ahead of price
Rejected. Whether free shipping outweighs an X-real price gap is a
per-user judgement, not something the API should bake in. Surfacing
both `price` and `freeShipping` on the offer lets the UI present them
and lets the user choose.

### B. Weight `sellerReputation` ahead of tier
Rejected. A 5★ used offer beating a 3★ NEW offer is surprising for the
default representation. Reputation is rightly a *tie-breaker*, not the
primary sort.

### C. Composite score `(price * w₁) + (1 - reputation/5) * w₂ + ...`
Rejected. Harder to explain, harder to test, harder to defend. The
lexicographic rule is honest about what it optimizes; the composite
hides the trade-off behind opaque weights. Reserved for a roadmap
iteration if real ranking ever becomes a goal.

### D. Match Mercado Livre's actual buy-box (delivery time, fulfilment, store score, ...)
Rejected for v1: the ML algorithm uses signals the seed does not have
(delivery time, fulfilment program, category-specific reputation).
Reproducing it would require synthesising those inputs, which dilutes
the comparison feature. Roadmap if production parity ever becomes the
goal.

## Consequences

### Positive
- **Deterministic.** Same offers ⇒ same buyBox, every run. Compare
  responses are stable across calls.
- **Cheap to test.** Pure function. The unit-test matrix below covers
  it exhaustively in <30 lines of test code.
- **Honest about scope.** README states the rule in two lines; nothing
  is hidden in tuning weights.

### Edge cases the unit tests must cover
- All offers `stock = 0` → `null`.
- Mix of NEW + USED with NEW available → NEW always wins.
- Only USED offers with stock → USED is chosen.
- Two NEW offers tied on price → higher reputation wins.
- Two NEW offers tied on price + reputation → lower `sellerId` wins.
- Empty offer list → `null`.
- Single offer `stock > 0` → that offer (no tie-break path exercised).

### Negative / tradeoffs
- The heuristic is not Mercado Livre's. Reviewers familiar with ML's
  actual buy-box may flag this. Mitigation: SPEC-002 §3.3 + this ADR +
  README explicitly call it out as a deliberate v1 simplification,
  with the real algorithm pointed at the roadmap.

## Implementation notes

- Class: `service/BuyBoxSelector`. Pure (no Spring annotation needed);
  may be `@Component` if convenient for injection.
- Signature: `Offer | null select(List<Offer> offers)`.
- Tier comparison uses an explicit
  `Map<Condition, Integer>{NEW=0, REFURBISHED=1, USED=2}` rather than
  `Enum.ordinal()` so the tier order is independent of declaration
  order in `Condition.java`.
- Currency is **not** considered: the selector operates on a single
  `CatalogProduct`'s offers, which all share the same currency by
  SPEC-002 §3.5 invariant. Cross-product currency comparison is a
  separate concern handled in `DifferencesCalculator`.

## References

- SPEC-002 §3.3 (buy-box selection sketch — locked here).
- SPEC-002 §3.5 (currency handling boundary).
- PLAN §2 (`service/compare/BuyBoxSelector`).
- PLAN §9 (differences algorithm consumes `buyBox.price`).
