# Endpoint Scaladoc plan

Spec: `docs/superpowers/specs/2026-11-06-endpoint-scaladoc-design.md`.

## Problem

Only 65 of 314 public endpoint members have Scaladoc, the existing comments are unchecked, and no example is compiled.

## Goal

314 of 314 documented, every claim probed against the published 0.0.56, every example compiled by `scaladoc-check` on Scala 2.13.18, 3.9.0 and 3.10.0, in one PR.

## Steps

1. **Tooling (in Task 1).** Add `endpoint.jvm` to `scaladocCheckedModules` (the list of modules whose examples the checker compiles) in `build.sbt`; add `endpoint` to the module and tier-1 lists in `docs/dev/scaladoc-style.md`; after Milad approves the diff, add it to the AGENTS.md bullet and add a downstream line.
2. **Task 1 (Opus, runs sbt).** Tier-1 types and all gaps in the main shared files; fact-check every existing comment; write bug entries to `task-1-issues.md`. Commit after each file group.
3. **Task 2 (Sonnet, parallel, no sbt).** Gaps in the internal and version-specific files; identical text in `scala-2` and `scala-3` copies; suspected bugs to `task-2-suspects.md`. Commits use explicit paths only.
4. **Controller checks.** Comments-only script, coverage, `--drift`, commit list: verify them myself before review.
5. **Review.** Fresh-context Opus fact-check (re-runs probes and reproducers), findings file, fix round by the same implementer (re-probe every suggested sentence), then a re-review of the fix diff.
6. **Gates** as listed in the spec, read from logs, not exit codes. One sbt run at a time; check `free -g` first.
7. **Ship.** Merge `origin/main`, push to `khajavi`, open the PR with a plain-English body, watch CI, rerun only known flakes. Do not merge.
8. **Report.** Send the coordinator one issues file (with target lines), the §13n results text, and the metrics.

Ledger: `.superpowers/sdd/2026-11-06-endpoint-scaladoc/` (`progress.md` with numbered rulings, briefs, reports, findings).
