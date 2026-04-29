---
id: ADR-0001
title: Reframe HackerRank skeleton tests as project test fixtures
status: Accepted
date: 2026-04-28
deciders: candidate
supersedes: —
superseded_by: —
related: [SPEC-001, SPEC-002, SPEC-003, ADR-0003]
---

# ADR-0001 — Reframe HackerRank skeleton tests as project test fixtures

## Status

Accepted — 2026-04-28. Originally superseded by ADR-0002 (which
discarded the skeleton root). ADR-0003 reverses ADR-0002 and keeps
the HackerRank skeleton, which **revives this ADR** as the active
position on the runner and bundled fixtures.

## Context

The HackerRank starter project ("JavaSpringBootSample") ships two assets
that interact awkwardly with this challenge:

1. **A JSON-driven test runner** (`HttpJsonDynamicUnitTest`) that loads
   every `*.json` file in `src/test/resources/testcases/` and replays
   each one as an HTTP call against the application via `MockMvc`,
   asserting status code, content type, and JSON body equality.
2. **A bundled set of sample testcases** that exercise a generic CRUD
   surface at `/model` with a `{id, name}` payload. The placeholder
   names (`model-000-000-001`, `model-000-000-002`) and the residual
   dependencies in the original `pom.xml` (`javafx.util.Pair`,
   `unitils-core 3.4.6`, `junit-vintage-engine`) make it clear that
   the bundled fixtures are template residue, not a contract authored
   for the **Item Comparison V2** challenge.

The challenge brief, by contrast, asks the candidate to design a
product comparison API with a rich domain model (`name`, `description`,
`price`, `imageUrl`, `rating`, `attributes`, smartphone-specific fields
like `battery`, `camera`, `memory`, `storage`, `brand`, `model`, `OS`),
and explicitly states that *"any key architectural decisions you made
during development"* are part of the evaluation.

The two assets cannot both be honoured literally. Treating the bundled
testcases as a binding contract would force a `/model` surface that
contradicts the brief; ignoring the runner would discard infrastructure
that is already wired and documented.

## Decision

Treat the JSON-driven test runner as a **project asset** and the
bundled testcases as **discardable scaffolding**.

- Keep `HttpJsonDynamicUnitTest` and the runner mechanism intact.
- Delete the bundled `*.json` testcases and the matching entries in
  `description.txt`.
- Author new testcases under `src/test/resources/testcases/` that
  exercise this project's actual API (`/api/v1/products`, including
  the comparison endpoint defined in SPEC-003).
- Do **not** expose any `/model` route in production code.

The runner stays. The fixtures are rewritten. The API surface is
single and coherent: `/api/v1/products`.

## Alternatives considered

### A. Dual API surface — keep `/model` and ship `/api/v1/products` in parallel

Rationale: would have preserved the bundled tests as-is while still
letting the rich domain be implemented for the human reviewer.

Rejected because:

- It accepts a contract authored by the template, contradicting the
  brief's explicit ask that the candidate own the architectural
  decisions.
- It dilutes the API surface and forces a defensive section in the
  README ("we have two surfaces because of template residue") that
  signals deference rather than judgement.
- The `/model` surface adds production code (entity, repository,
  controller) whose only purpose is to satisfy fixtures that were
  never authored for this challenge.

### B. Drop the JSON runner entirely, rely only on hand-written tests

Rationale: would have removed the awkward asset and replaced it with
conventional `@SpringBootTest` / `MockMvc` tests.

Rejected because:

- The runner is genuinely useful: declarative HTTP-level testcases in
  JSON are easy to read, easy to add to, and serve as living examples
  of the API contract.
- Removing it would discard infrastructure that is already configured
  and documented in the skeleton, with no real upside.

### C. Keep `/model` as the only surface and ignore the brief

Not seriously considered. It would forfeit the qualitative evaluation
entirely.

## Consequences

### Positive

- A single, coherent API surface authored by the candidate.
- The JSON runner gains a meaningful purpose: it now documents and
  enforces the real contract by example.
- The architectural choice is explicit and defensible — the kind of
  decision the brief asks the candidate to make and explain.

### Negative / tradeoffs

- ~10 JSON fixtures and the `description.txt` entries must be
  rewritten before the test suite is meaningful again. Estimated
  effort: under one hour after the API is implemented.
- Reviewers who skim the diff without reading this ADR may briefly
  wonder why the bundled fixtures disappeared. The README and this
  document are the answer.

### Risk: hidden test battery on submit

There is no evidence of a hidden test battery executed during
*Save & Proceed* beyond what `mvn test` runs. The runner reads its
inputs entirely from the candidate's repository
(`src/test/resources/testcases/`), and no other test classes are
present in the skeleton. If a hidden battery did exist and depended on
`/model`, the correct mitigation would still be a clear README and ADR
explaining the architectural choice — not preserving template residue.

## Implementation notes

- The runner class (`HttpJsonDynamicUnitTest`) and its supporting
  dependencies (`junit-vintage-engine`, `javafx-controls` for
  `javafx.util.Pair`, `unitils-core`) are kept in the `pom.xml` to
  avoid touching the runner itself.
- New testcases follow the runner's expected schema:
  `{request: {method, url, headers, body}, response: {status_code,
  headers, body}}`.
- `description.txt` keeps its `filename: human-readable test name`
  format so the runner's report stays legible.
- The README's "Testing" section points to this ADR.

## References

- SPEC-001 — Item Comparison API (functional scope).
- SPEC-002 — Product Domain Model.
- SPEC-003 — API Contract (target endpoints under `/api/v1/products`).
