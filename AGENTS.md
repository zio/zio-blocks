# ZIO Blocks Agent Guidelines

Zero-dependency Scala building blocks (2.13 + 3.x; JVM/JS).

## Setup

If `.git/bin/sbtn` doesn't exist, download [sbtn](https://github.com/sbt/sbtn-dist/releases) and place it there. Always run: `export PATH="$PWD/.git/bin:$PATH"`

If `sbt --client` is causing trouble, try:

```bash
sbt --client shutdown 2>/dev/null; pkill -f sbt 2>/dev/null; rm -rf .bsp project/target/active.json project/target/.sbt-server-connection.json
```

## Policies

- [Symbolic Operator Policy](SYMBOLIC_OPS_POLICY.md) — Rules for when symbolic operators are allowed in APIs. Consult before adding or reviewing any symbolic method.

### Sentinel performance policy (streams)

The streams module's primitive lanes (`Long`/`Double`/`Int`/`Float`) signal end-of-stream with in-band primitive sentinels (`Long.MaxValue`, `Double.MaxValue`, …) precisely so that hot drain loops stay a **single primitive comparison per element** with zero boxing and zero allocation. These loops are deliberately optimized; their shape is a feature, not a bug.

**Any change to sentinel handling is disallowed unless it has virtually no impact on performance.** In particular, bug-finding or "correctness" passes (human or agentic) must NOT add per-element work to these loops — no per-element rawbits conversions, extra branches, flag reads on the non-collision path, boxing, or allocation. A sentinel-valued element colliding with EOF is an accepted, documented edge case; the approved remedies are, in order of preference:

1. **Zero-cost lossless disambiguation**: `v == sentinel && reader.lastReadWasEOF` — the short-circuit means the out-of-band EOF flag is consulted only on the rare value/sentinel collision; the hot path is unchanged. (For statically known non-NaN sentinels such as `Double.MaxValue`, use this inline form — `doubleEOF`'s rawbits comparison exists only for NaN sentinels and must not appear per-element in hot loops.)
2. **Document + throw early**: keep the raw `v != sentinel` loop and, after the loop exits, consult `lastReadWasEOF` once; if the exit was caused by a real sentinel-valued element rather than EOF, throw a clear exception (see `NioSinks.fromByteBufferLong/Double`). Zero per-element cost; silent truncation becomes a loud error.
3. **Document only**: when neither applies, document the limitation; do not "fix" it.

Validate any sentinel-adjacent change against the streams JMH benchmarks before and after; an unexplained regression means the change is rejected.

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
#   "++3.8.3; <project>/test"                             — fast loop, Scala 3
#   "++<scala2>; <project>/test"                          — fast loop, Scala 2
#   "++3.8.3; project <project>; coverage; test; coverageReport"   — coverage, Scala 3
#   "++<scala2>; project <project>; coverage; test; coverageReport"  — coverage, Scala 2
#   "++3.8.3; <project>/test; ++<scala2>; <project>/test" — cross-Scala
#   "++3.8.3; fmtDirty"                                  — format main sources
#   "++3.8.3; project <project>; fmtDirty"               — format sources in specific project
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

**Streams exception:** `Stream`, `Sink`, `Reader`, `Writer`, `Pipeline`, and `Interpreter` are fully split across `scala-2/` and `scala-3/`. Keep both in sync.

## Testing

ZIO Test framework. Search codebase for `SchemaBaseSpec` for patterns.

### Render Assertions

Always assert the **full rendered string** with `==`. Never use `.contains()`:

```scala
// ✅ Correct — catches all regressions (attribute order, whitespace, closing tags)
assertTrue(result.render == """<div class="main">content</div>""")

// ❌ Wrong — misses attribute order, extra whitespace, missing tags
assertTrue(result.render.contains("main"))
```

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
- In the middle of executing a skill, if you discover a deviation from the skill's instructions, encounter missing information or unclear guidance, or discover a better approach than what was written, update that skill file to reflect what you learned.
- Treat docs as part of the change: new data type, new feature, API change, or API removal isn't done until its `docs/` page(s) match
- **README.md is auto-generated.** Never edit `README.md` directly. Edit `docs/index.md` instead, then run `sbt --client docs/generateReadme` to regenerate `README.md`. (The `generateReadme` task is provided by `WebsitePlugin` on the `docs` project; running it unscoped at the root fails with "Not a valid command".)
  - **Caveat — manually-maintained sections.** `generateReadme` runs mdoc and only emits sections whose code can compile against the `docs` project's `dependsOn` classpath. Modules **not** in `docs`' `dependsOn` (e.g. `config` and its adapters) cannot be mdoc-generated, so their README section is maintained by hand (raw-pasted into README, see the Config section added in #1426). A fresh `generateReadme` will silently **drop** such sections. After regenerating, diff against `origin/main` and re-insert any manually-maintained section (currently only `## Config`, which sits between `## The Blocks` and `## Core Principles`) so you don't regress it.

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