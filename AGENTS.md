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

    If unsure, check `dependsOn` in `build.sbt` / `project/*.scala`.

If any step fails: return to phase 1, fix, get green in phase 2, rerun the failing step.

### 4. Format

Run once after verify passes.

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

### Ask First
- Adding dependencies (even test-only)
- Creating or removing subprojects
- Any repo-wide test or coverage run

### Never
- Use coverage as starting point for test design (think first, verify with coverage)
- Iterate coverage in tiny steps (if gaps exist, fix ALL at once, then rerun once)
- Cheat: delete code/tests, lower thresholds, or game metrics to appear compliant
- Retest after formatting (unless formatting broke the build)
- Use `sbt test` (repo-wide) when `sbt <project>/test` works
- Call work "optional" or "good enough" to justify not doing it