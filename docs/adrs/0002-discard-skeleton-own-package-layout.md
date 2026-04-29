---
id: ADR-0002
title: Discard HackerRank skeleton, own the package root and layout
status: Superseded
date: 2026-04-28
deciders: candidate
supersedes: ADR-0001
superseded_by: ADR-0003
related: [SPEC-001, SPEC-002, SPEC-003, PLAN]
---

# ADR-0002 — Discard HackerRank skeleton, own the package root and layout

## Status

Superseded by ADR-0003 (2026-04-29). The decision to discard the
skeleton was reversed once the submission constraint became clear:
HackerRank requires class-by-class paste in their UI, and using
`com.hackerrank.sample` as the package root removes one source of
friction. ADR-0003 documents the reversal; ADR-0001 (the original
compromise position) is revived and now active.

## Context

The starter project ships with `com.hackerrank.sample` as the package
root and `controller`, `service`, `repository`, `model`, `exception`
sub-packages, plus a `pom.xml` carrying dependencies that exist only to
support a generic `/model` CRUD demo (`unitils-core`, `junit-vintage`,
`javafx-controls`).

ADR-0001 originally tried to compromise: keep the package root, drop
the bundled `/model` fixtures. Two facts make that compromise weaker
than going the rest of the way:

1. The challenge brief is explicit that *"any key architectural
   decisions you made during development"* are part of the evaluation.
   Honouring `com.hackerrank.sample` signals deference to template
   residue, not architectural ownership.
2. The original `HttpJsonDynamicUnitTest` runner is not even
   physically present in the working tree (only `.gitkeep` files in
   `src/test/`). The dependencies that supported it are still in
   `pom.xml` — pure dead weight.

Both points push in the same direction: own the package layout the way
any production-quality Spring Boot service would.

## Decision

Discard the skeleton's package root and runner-related dependencies.
Adopt our own root and a deliberate layered structure.

- **Root package:** `com.mercadolivre.itemcomparison`.
- **Layout:** `controller`, `service`, `repository`, `model`,
  `exception`, with `service/compare` and `service/ai` sub-packages
  where cohesion earns them (see PLAN §2).
- **`pom.xml`:** drop `unitils-core`, `junit-vintage-engine`,
  `javafx-controls`, and the duplicated `spring-boot-starter-parent`
  inside `<dependencies>`. Update `groupId`/`artifactId` to reflect
  ownership.
- **Tests:** standardise on JUnit 5 + MockMvc. No JUnit 4 runner
  reintroduced. Declarative HTTP fixtures, if needed, can be sidecar
  JSON files read by a small MockMvc helper — but not required for v1.

The HackerRank submission is a Spring Boot service like any other, in
a package the candidate owns.

## Alternatives considered

### A. ADR-0001 (keep skeleton root, drop only `/model` fixtures)

Rejected: leaves residual dependencies in `pom.xml`, signals deference
to template choices, gains nothing concrete in return. Already
documented as superseded.

### B. Full hexagonal / clean architecture (ports + adapters)

Rejected for v1: adds indirection (`domain/application/infrastructure`,
explicit ports per repository and per AI client) that does not pay off
on a 5-day, ~12-product, read-only deliverable. Layered with
disciplined boundaries already demonstrates Clean Architecture, SOLID,
and testability without the abstraction tax. If the catalog grows or
write paths appear, R-1 in the roadmap (extract `catalog-service`) is
the trigger to revisit.

### C. Keep skeleton root for "compatibility" with HackerRank automated checks

Rejected: there is no evidence of automated checks tied to the package
root. The runner reads inputs from the candidate's repo only; `mvn
test` discovers tests by class-path scan, not by package name. The
package root is a candidate decision.

## Consequences

### Positive

- Architecture decisions are owned end to end, including the namespace.
- `pom.xml` carries only dependencies the project actually uses.
  Smaller surface, faster build, fewer audit findings.
- The candidate can defend every package and every dependency in an
  interview without prefacing with *"the template gave me ..."*.

### Negative / tradeoffs

- A reviewer who expects `com.hackerrank.sample` to be present may need
  one extra glance at the README. Mitigation: README's "Architecture
  decisions" section links this ADR.
- Files that were in the skeleton (the empty `Application.java` under
  `com/hackerrank/sample`) are moved/renamed in PLAN Step 1. One-time
  cost.

### Risk: hidden HackerRank automated check tied to package root

Considered and accepted. The runner — which would have been the only
plausible hook — is not present. Even if it were, it would scan
`src/test/resources/testcases/` and not the package root. The risk is
hypothetical; mitigation cost (keeping the wrong root forever) is
concrete.

## Implementation notes

Mapped to PLAN Step 1:

- Rename `pom.xml` `groupId` → `com.mercadolivre`, `artifactId` →
  `item-comparison-api`, drop runner deps, bump Spring Boot to 3.3.5,
  add Spring AI starter + Bean Validation.
- Move `Application.java` to
  `com.mercadolivre.itemcomparison.ItemComparisonApplication`. Delete
  the old `com/hackerrank/sample/` tree.
- `application.yml` `spring.application.name` already
  `item-comparison-api`. `logging.level.com.hackerrank.sample` retargeted
  to `com.mercadolivre.itemcomparison`.
- README's *Architecture decisions* section links ADR-0001 (superseded)
  and ADR-0002 (active).

## References

- ADR-0001 — Reframe HackerRank skeleton tests as project test
  fixtures (superseded).
- PLAN — Implementation plan §0, §1.1, §2, §12 Step 1.
- SPEC-001 §8 (constraints), SPEC-002 §7 (relationship to layout).
