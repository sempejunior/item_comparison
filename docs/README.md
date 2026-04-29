# Documentation — Spec-Driven Development

This project is built using **Spec-Driven Development (SDD)**. The intent is
that specifications, not code, are the primary artifact: code is the
*compilation* of an approved specification.

## Why SDD here

The challenge explicitly values *"good practices in error handling,
documentation, testing, and any other relevant non-functional aspects"*. SDD
makes those concerns first-class: every requirement, decision, and tradeoff
is captured before implementation, so the resulting code is traceable line by
line back to a stated need.

## Workflow

```
SPECIFY  →  PLAN  →  DECIDE (ADRs)  →  TASKS  →  IMPLEMENT  →  VERIFY  →  EVOLVE
   │         │            │              │           │            │          │
   what     how        tradeoffs       atomic     code that      tests +    roadmap,
   why     stack       per topic      backlog    closes tasks   acceptance  next bets
```

A phase only starts after the previous one is approved. Changes that arrive
mid-implementation are pushed back into the spec/plan and re-flowed — the
documents are kept truthful, not retrofitted. The **Evolve** phase is not an
afterthought: roadmap items earn their place by being explicit about
*trigger* (when), *approach* (how), and *cost* (effort).

## Document index

| Phase    | Document                                                              | Status   |
|----------|-----------------------------------------------------------------------|----------|
| Specify  | [SPEC-001 — Item Comparison API](specs/001-item-comparison.md)        | Draft v4 |
| Specify  | [SPEC-002 — Product Domain Model](specs/002-product-domain-model.md)  | Draft v4 |
| Specify  | [SPEC-003 — API Contract](specs/003-api-contract.md)                  | Draft v2 |
| Specify  | [SPEC-004 — AI Features](specs/004-ai-features.md)                    | Draft v1 |
| Decide   | [ADR-0001 — Reframe skeleton tests as project fixtures](adrs/0001-reframe-skeleton-tests-as-project-fixtures.md) | Superseded |
| Decide   | [ADR-0002 — Discard skeleton, own package layout](adrs/0002-discard-skeleton-own-package-layout.md) | Accepted |
| Evolve   | [ROADMAP — Production scaling and next bets](roadmap.md)              | Draft v2 |
| Plan     | [PLAN — Implementation plan](plan.md)                                 | Draft v1 |
| Tasks    | _atomic backlog derived from plan_                                    | —        |

## Document conventions

- Each spec is **versioned** in its frontmatter (`v1`, `v2`, ...). Edits that
  change meaning bump the version and add a changelog entry.
- Requirements are **uniquely IDed** (`FR-1`, `NFR-3`, `AC-2`, ...) so tasks,
  tests, and ADRs can reference them.
- Every spec ends with an **Open Questions** section. Resolved questions move
  into the body and are removed; unresolved ones block downstream phases.
- ADRs follow a slim [MADR](https://adr.github.io/madr/) format: context,
  decision, alternatives, consequences.
- Roadmap items follow a fixed shape: *trigger → approach → architecture →
  tradeoffs → effort estimate*. No vague "would be nice" entries.

## How to review

Read in order: `001` → `002` → `003` → `004` → `roadmap.md`. Comment on Open
Questions first; those gate the plan. Surface any missing requirement before
the plan is written — catching it here costs minutes, catching it after code
costs hours.
