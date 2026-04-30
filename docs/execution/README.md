# Execution log

This folder is the bridge between the specs (the *what*) and the source
tree (the *how it actually shipped*). It is **not** required reading
for a code review — the specs and ADRs already justify every choice.
It exists so that anyone curious about *the order in which things were
built* and *what each commit was meant to deliver* has a written
trail.

## Files

| File              | What it is                                           |
|-------------------|------------------------------------------------------|
| `plan.md`         | Master implementation plan (slices 1–3).             |
| `plan-slice4.md`  | Plan for the *category insights* slice (T-24..T-30). |
| `plan-slice5.md`  | Plan for the *insights filters* slice (T-31..T-35).  |
| `TASKS.md`        | Atomic task breakdown (T-01..T-35) with DoD per task.|

## How to read alongside `git log`

Each task `T-NN` corresponds to one atomic commit. Mapping:

- Slices 1–3 (T-01..T-23) — see `plan.md §12` and PRs #2, #3, #4.
- Slice 4 (T-24..T-30) — see `plan-slice4.md` and PR #8.
- Slice 5 (T-31..T-35) — see `plan-slice5.md` and PR #9.

`git log --oneline main` shows the squash-merge history; the per-task
SHAs live in the changelog at the bottom of `TASKS.md`.
