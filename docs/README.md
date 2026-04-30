# Documentation index

This project was built using **Spec-Driven Development (SDD)**:
specifications are the primary artifact, and code is the *compilation*
of an approved spec. The challenge prompt explicitly values *"good
practices in error handling, documentation, testing, and any other
relevant non-functional aspects"* — SDD makes those concerns
first-class, so every line of code traces back to a stated need.

## Workflow

```
SPECIFY  →  DECIDE (ADRs)  →  PLAN  →  IMPLEMENT  →  VERIFY  →  EVOLVE
   what          tradeoffs      how      code that     tests      roadmap,
   why           per topic    + tasks   closes tasks   green     next bets
```

A phase only starts after the previous one is approved. Mid-flight
changes are pushed back into the spec/plan and re-flowed — documents
are kept truthful, not retrofitted. **Evolve** is not an afterthought:
every roadmap item is explicit about *trigger* (when), *approach*
(how), and *cost* (effort).

## Index

### Specify — *what* and *why*

| Doc | Focus | Status |
|-----|-------|--------|
| [SPEC-001 — Item Comparison API](specs/001-item-comparison.md) | scope, user stories, FR/NFR/AC | Accepted (v8) |
| [SPEC-002 — Catalog Product Domain Model](specs/002-product-domain-model.md) | aggregates, invariants, persistence | Accepted (v6) |
| [SPEC-003 — API Contract](specs/003-api-contract.md) | endpoints, payloads, RFC 7807 | Accepted (v4) |
| [SPEC-004 — AI Features](specs/004-ai-features.md) | LLM surface, fallback, metrics, cost guard | Accepted (v3) |
| [SPEC-005 — Category Insights](specs/005-category-insights.md) | rankings + buying-guide + filters | Accepted (v5) |

### Decide — architecture decisions (MADR)

| ADR | Decision | Status |
|-----|----------|--------|
| [ADR-0001](adrs/0001-reframe-skeleton-tests-as-project-fixtures.md) | Reframe HackerRank skeleton tests as project fixtures | Accepted |
| [ADR-0002](adrs/0002-discard-skeleton-own-package-layout.md) | Discard skeleton, own package layout | Superseded by 0003 |
| [ADR-0003](adrs/0003-keep-skeleton-paste-friendly-submission.md) | Keep skeleton (`com.hackerrank.sample`) — paste-friendly submission | Accepted |
| [ADR-0004](adrs/0004-buybox-selection-heuristic.md) | BuyBox selection heuristic | Accepted |
| [ADR-0005](adrs/0005-category-insights-endpoint-shape.md) | Category-insights endpoint shape | Accepted |
| [ADR-0006](adrs/0006-insights-filters-in-memory-with-scale-up-path.md) | Insights filters: in-memory now, scale-up path | Accepted |

### Evolve — forward-looking

| Doc | Focus |
|-----|-------|
| [Roadmap](roadmap.md) | R-1..R-10 — production scaling, semantic search, multi-tenant prompts, FX, etc. |

### Execute — *how it actually shipped*

The execution log lives in [`execution/`](execution/). It is **not**
required reading for code review (specs and ADRs already justify every
choice), but documents the slice-by-slice build order and DoD per
atomic task. Each `T-NN` corresponds to one commit.

## Conventions

- Specs are **versioned** (`v1`, `v2`, ...) and carry a `Changelog` at
  the bottom. Edits that change meaning bump the version.
- Requirements have **stable IDs** (`FR-1`, `NFR-3`, `AC-2`, ...) so
  tasks, tests, and ADRs can reference them.
- Every spec ends in `Open Questions`. Resolved questions migrate into
  the body and disappear; open ones block downstream phases.
- ADRs follow a slim [MADR](https://adr.github.io/madr/) template:
  context, decision, alternatives, consequences.
- Roadmap items follow a fixed shape: *trigger → approach →
  architecture → tradeoffs → effort*. No "would be nice" entries.

## Suggested reading order

1. `specs/001-item-comparison.md` — the *what*.
2. `specs/002-product-domain-model.md` — the data shape.
3. `specs/003-api-contract.md` — the contract.
4. `specs/004-ai-features.md` — the LLM gateway and fallback policy.
5. `specs/005-category-insights.md` — the second endpoint family + filters.
6. `adrs/` — the six decisions, in order, comparing alternatives.
7. `roadmap.md` — what comes after v1, with the same rigor.
