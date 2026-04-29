---
id: ADR-0003
title: Keep HackerRank skeleton root and align with paste-by-paste submission
status: Accepted
date: 2026-04-29
deciders: candidate
supersedes: ADR-0002
superseded_by: —
related: [ADR-0001, SPEC-001, SPEC-002, PLAN, SUBMISSION]
---

# ADR-0003 — Keep HackerRank skeleton root and align with paste-by-paste submission

## Status

Accepted — 2026-04-29. Supersedes ADR-0002. Revives ADR-0001 (whose
"keep skeleton, drop bundled fixtures" position is now the active one).

## Context

ADR-0002 discarded the skeleton root and adopted
`com.mercadolivre.itemcomparison`. Two facts surfaced after that
decision that change the calculus:

1. **Submission mechanic.** The HackerRank challenge for *Item
   Comparison V2* delivers via the platform's editor UI: the candidate
   pastes class by class. The reviewer running the harness expects
   the same package conventions the skeleton ships with. A custom root
   makes the reviewer's first action ("paste the file") more
   error-prone — they have to mentally re-target every paste to a new
   directory.
2. **Asymmetric cost.** Owning the namespace yielded zero functional
   benefit. The runner (`HttpJsonDynamicUnitTest`) does not bind to the
   package root. The pom dependencies that ADR-0002 dropped can be
   dropped independently of the package root. The "candidate-owned
   architecture" signal is delivered better by README + ADRs than by a
   custom package name.

The constraint was thus mis-weighted in ADR-0002: paste friction is
concrete, namespace ownership is signalling.

## Decision

Keep the HackerRank skeleton root and sub-packages.

- **Root package:** `com.hackerrank.sample`.
- **Sub-packages:** `controller`, `service`, `repository`, `model`,
  `exception` (the skeleton-imposed five). Sub-packages under those
  (e.g. `service/compare`, `service/ai`) are allowed when cohesion
  earns them; they show up in PLAN §2.
- **Application class:** named `Application` (skeleton convention),
  not `ItemComparisonApplication`.
- **Maven coordinates:** `groupId = com.hackerrank`, `artifactId =
  sample`. Build artifact stays `target/sample-1.0.0.jar`.
- **Submission flow is paste-friendly.** Each class is small enough to
  paste in one go; no inner classes that span >1 screen; explicit
  `import` lists (no wildcard `*` imports) so dependency-by-class is
  obvious to the reviewer pasting in order. SUBMISSION.md tracks paste
  order.

ADR-0001 (the original "keep skeleton, drop bundled fixtures"
position) is revived; ADR-0002 is superseded.

## Alternatives considered

### A. Stay with ADR-0002 (`com.mercadolivre.itemcomparison`)
Rejected. Concrete paste friction; signalling benefit already captured
by README + ADRs.

### B. Skeleton root + flatten everything into the five top-level packages
Rejected. `service/compare` and `service/ai` cohesively group code
that would be noise at the flat level. Sub-packages survive
paste-by-paste because the reviewer creates the sub-folder once per
group, not per file.

### C. Skeleton root for production code, custom root for tests
Rejected. Mixed roots are a recipe for confusion in code review; the
test runner (when restored) discovers tests by classpath scan, not by
root.

## Consequences

### Positive
- Zero paste-time friction. Reviewer pastes into the package each
  file's `package` line declares; no mental re-targeting.
- The skeleton's mental model (controller / service / repository /
  model / exception) is preserved for reviewers who skim.
- Application class name (`Application`) matches what the HackerRank
  starter ships with — drop-in replacement for the skeleton stub.

### Negative / tradeoffs
- "Candidate-owned namespace" signal is lost from the package itself.
  Mitigation: README's *Architecture decisions* section + this ADR
  carry the signal explicitly.
- Re-pasting `com.hackerrank.sample.*` everywhere makes the project
  look generic at first glance. The README's first paragraph fixes
  that within ~20 seconds of reading.

### Risk: skeleton's pom dependencies pollute the build
Independent from the package decision. ADR-0002's dependency-cleanup
section is preserved (drop `unitils-core`, `junit-vintage-engine`,
`javafx-controls`, the duplicated parent listing). PLAN Step 1
enforces it.

## Implementation notes (already applied in this session)

- Java sources moved back to `com/hackerrank/sample/...`. Application
  class renamed to `Application`. The `com/mercadolivre/itemcomparison/`
  tree was deleted.
- `pom.xml` `groupId` → `com.hackerrank`, `artifactId` → `sample`.
  Build artifact again is `target/sample-1.0.0.jar`.
- `application.yml` log target reset to `com.hackerrank.sample`.
- ADR-0001 status flipped back to *Accepted*; ADR-0002 marked
  *Superseded by ADR-0003*.

## References

- ADR-0001 — Reframe HackerRank skeleton tests as project test
  fixtures (revived).
- ADR-0002 — Discard skeleton (superseded).
- SUBMISSION.md — paste order and per-file status.
- PLAN — §0, §2, §12 Step 1 updated to v2 to reflect this decision.
