# ZIO Blocks Agent Guidelines

Zero-dependency Scala building blocks (2.13 + 3.x; JVM/JS).

## Setup

If `.git/bin/sbtn` doesn't exist, download [sbtn](https://github.com/sbt/sbtn-dist/releases) and place it there. Always run: `export PATH="$PWD/.git/bin:$PATH"`

If `sbt --client` is causing trouble, try:

```bash
sbt --client shutdown 2>/dev/null; pkill -f sbt 2>/dev/null; rm -rf .bsp project/target/active.json project/target/.sbt-server-connection.json
```

## Mindset

**sbt is slow—minutes per compile/test.** Wasted cycles waste hours.

- **Batch edits.** Do not: edit → compile → edit → compile. Do: edit everything → compile once.
- **Design tests with code.** Cover every statement and branch; if coverage reveals gaps, fix all at once.
- **Extreme ownership.** "Optional" means required; do the work completely.

## Commands

```bash
# Discover project names and Scala versions
sbt projects
sbt 'show crossScalaVersions'              # note the 2.13.x version

# sbt command template (use for ALL sbt invocations):
ROOT="$(git rev-parse --show-toplevel)" && mkdir -p "$ROOT/.git/agent-logs"
LOG="$ROOT/.git/agent-logs/sbt-$(date +%s)-$$.log"
sbt --client -Dsbt.color=false <command> >"$LOG" 2>&1; echo "Exit: $? | Log: $LOG"
# Query: tail -50 "$LOG" or grep -i error "$LOG"

# <command> examples:
#   "++3.7.4; <project>/test"                             — fast loop, Scala 3
#   "++<scala2>; <project>/test"                          — fast loop, Scala 2
#   "++3.7.4; project <project>; coverage; test; coverageReport"   — coverage, Scala 3
#   "++<scala2>; project <project>; coverage; test; coverageReport"  — coverage, Scala 2
#   "++3.7.4; <project>/test; ++<scala2>; <project>/test" — cross-Scala
#   "++3.7.4; <project>/scalafmt"                         — format main sources
#   "++3.7.4; <project>/Test/scalafmt"                    — format test sources
#   scalafmtSbt                                           — format build files
#
# IMPORTANT: --client mode preserves Scala version across invocations.
# ALWAYS specify ++<version> at the start of commands to avoid version drift.
```

`<project>` = any project name from `sbt projects` (e.g., `schemaJVM`, `schemaJS`, `schema-avro`).
`<scala2>` = the 2.13.x version from `show crossScalaVersions`.

## Workflow

Phases 1-3 repeat until verify passes. Phase 4 runs once at the end.

### 1. Edit
Batch code and tests together. For large changes, split into batches—each batch includes its tests.

### 2. Fast Loop
One project, one Scala version: get `<project>/test` green. Default Scala 3; use Scala 2 if editing `scala-2/` sources. No coverage here.

### 3. Verify
Enter only when fast loop is green. Run in order:

1. **Coverage** — Scala version you developed with
2. **Cross-Scala** — the other Scala version, same project
3. **Cross-platform** — other platform projects, if cross-built and you touched shared/ or platform sources
4. **Downstream** — all projects that depend on what you changed:
    - `chunk*` → `schema*`, `benchmarks`
    - `schema*` → `schema-avro`, `schema-bson`, `schema-thrift`, `schema-messagepack*`, `schema-toon*`, `scalaNextTests*`, `benchmarks`, `docs`
    - `scope*` → `scope-examples`

    If unsure, check `dependsOn` in `build.sbt` / `project/*.scala`.

If any step fails: return to phase 1, fix, get green in phase 2, rerun the failing step.

### 4. Format

Run once after verify passes.

## Cross-Version Code Structure

For version-specific code: ONE shared `package.scala` extending a trait with per-version implementations in `scala-2/` and `scala-3/`. See `markdown/` module (`MdInterpolator`). Never separate package objects per version.

## Testing

ZIO Test framework. Search codebase for `SchemaBaseSpec` for patterns.

## Git & CI (Prefer `gh` CLI)

Commit often, whenever fast loop is green.

PR already open (check!) and think you're done? Push, update PR title/description, then loop: monitor CI and review comments, fix all CI issues (including conflicts) and all **valid** review comments via Workflow. Don't stop until CI is green, & PR is approved & merged.

When waiting on PR checks, suppress watch output to avoid context bloat:
`sleep 30 && gh pr checks <PR> --watch --fail-fast > /dev/null 2>&1 || true && gh pr checks <PR>`

## Boundaries

### Always
- Follow workflow phases in order
- Batch edits; keep sbt runs scoped to one project
- Update AGENTS.md if you find errors or gaps
- Document new data types in `docs/`; update existing docs when behavior changes
- **README.md is auto-generated.** Never edit `README.md` directly. Edit `docs/index.md` instead, then run `sbt --client generateReadme` to regenerate `README.md`.

### Ask First
- Adding dependencies (even test-only)
- Creating or removing subprojects
- Any repo-wide test or coverage run
- **New modules:** When adding a new module, update the `testJVM`, `testJS`, `docJVM`, and `docJS` command aliases in `build.sbt`.

### Never
- Use coverage as starting point for test design (think first, verify with coverage)
- Iterate coverage in tiny steps (if gaps exist, fix ALL at once, then rerun once)
- Cheat: delete code/tests, lower thresholds, or game metrics to appear compliant
- Retest after formatting (unless formatting broke the build)
- Use `sbt test` (repo-wide) when `sbt <project>/test` works
- Call work "optional" or "good enough" to justify not doing it

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **repo** (2155 symbols, 2120 relationships, 0 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/repo/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/repo/context` | Codebase overview, check index freshness |
| `gitnexus://repo/repo/clusters` | All functional areas |
| `gitnexus://repo/repo/processes` | All execution flows |
| `gitnexus://repo/repo/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## CLI

- Re-index: `npx gitnexus analyze`
- Check freshness: `npx gitnexus status`
- Generate docs: `npx gitnexus wiki`

<!-- gitnexus:end -->
