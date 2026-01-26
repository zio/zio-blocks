# ZIO Blocks Agent Guidelines

Zero-dependency building blocks for Scala. Supports Scala 2.13 and 3.3+ with source compatibility across JVM, JS, and Native.

## Staged Verification Workflow

**Goal:** Zero duplication. Each operation runs once at the smallest necessary scope.

### What needs work?

| Modified | Action |
|----------|--------|
| Project sources | Compile, test (with coverage), format — both Scala versions, all modified platforms |
| Downstream projects | Test only — both Scala versions, all applicable platforms |
| Unmodified + not downstream | Nothing |

### Stages

Examples use `<module>` as placeholder — substitute the actual project (e.g., `schemaJVM`, `streamsJVM`, `chunkNative`).

**Stage 1 — Fast Dev Loop** (iterate until ready)
- Single module, Scala 3, JVM: `sbt <module>JVM/test`
- If editing `scala-2/` sources: use Scala 2.13 instead
- If editing `js/` or `native/`: include that platform

**Stage 2 — Coverage** (once, replaces final test run)
- Modified module(s) only: `sbt "<module>JVM/coverage; <module>JVM/test; <module>JVM/coverageReport"`
- Add tests until coverage minimums met (see build.sbt for thresholds)
- This IS the test run — no separate test step needed

**Stage 3 — Cross-Scala** (once)
- Test modified module(s) on the OTHER Scala version only
- Example: `sbt "++2.13.18; <module>JVM/test"`

**Stage 4 — Cross-Platform** (once, if applicable)
- Modified module(s) on JS/Native if supported and sources touched
- Both Scala versions: `sbt "++3.3.7; <module>JS/test" && sbt "++2.13.18; <module>JS/test"`

**Stage 5 — Downstream** (once)
- Test downstream dependencies only (see dependency graph below)
- Both Scala versions, all applicable platforms
- Skip if no downstream deps exist

**Stage 6 — Format** (once, last)
- Modified module(s) only, matching platforms/versions touched
- **Important:** `scalafmt` only formats main sources; use `Test/scalafmt` for test sources
- Example: `sbt "<module>JVM/scalafmt; <module>JVM/Test/scalafmt"`
- If `build.sbt` or `project/**` changed: `sbt scalafmtSbt`

### Anti-patterns

- ❌ `sbt testJVM` — tests everything, not just modified
- ❌ `sbt fmt` — formats everything, not just modified
- ❌ Running tests then coverage separately (coverage includes test run)
- ❌ Testing a module twice at the same Scala version
- ❌ Formatting downstream modules
- ❌ Committing merge resolutions without test + format

### After merging

Conflict resolution = code change. Before committing:
- Run Stage 1 (test) and Stage 6 (format) on touched modules
- Add `sbt scalafmtSbt` if build files changed

## Command Execution

**Slow command workflow** (compile/test/format):
```bash
ROOT="$(git rev-parse --show-toplevel)" || exit 1
mkdir -p "$ROOT/.git/agent-logs"
LOG="$ROOT/.git/agent-logs/sbt-$(date +%s)-$$.log"; echo "LOG=$LOG"
set -o pipefail
sbt -Dsbt.color=false schemaJVM/test >"$LOG" 2>&1
status=$?; echo "Exit: $status"; exit "$status"
```

Query logs (set LOG path from step above):
```bash
tail -n 50 "$LOG"
grep -n -i -E 'error|exception|failed|failure' "$LOG" | head -30 || true
```

## Project Structure & Dependencies

```
chunk             # No dependencies (cross-platform)
  ├─→ schema      # Depends on chunk (cross-platform)
  │     ├─→ schema-avro        # JVM only
  │     ├─→ schema-bson        # JVM only
  │     ├─→ schema-thrift      # JVM only
  │     ├─→ schema-messagepack # cross-platform
  │     ├─→ schema-toon        # cross-platform
  │     └─→ docs               # JVM only
  └─→ streams     # Depends on chunk (cross-platform)
```

Arrows show dependencies (downstream projects). All projects are top-level directories.

CI handles: `scalaNextTests`, `benchmarks` (Scala 3.7.4 only)

## Source Organization

- `shared/src/main/scala/` — common code (default)
- `shared/src/main/scala-2/` — Scala 2.x specific (blackbox macros)
- `shared/src/main/scala-3/` — Scala 3.x specific (quoted macros)
- `jvm/`, `js/`, `native/` — platform-specific code

If editing `scala-2/` → Stage 1 uses Scala 2.13, Stage 3 uses Scala 3
If editing `scala-3/` → Stage 1 uses Scala 3, Stage 3 uses Scala 2.13

## Testing Requirements

**New code = new tests.** Every new/changed line requires tests covering all branches.

- Framework: ZIO Test (`zio.test.sbt.ZTestFramework`)
- Tests extend `SchemaBaseSpec`
- Coverage minimums enforced (see build.sbt per module)
- Good patterns: `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala`

## Code Style

- Scalafmt enforced (maxColumn: 120, Scala 3 dialect)
- New public APIs require Scaladoc
- Follow existing patterns in neighboring files
- No ecosystem dependencies (ZIO, Cats, Akka, etc.)

## Boundaries

### Always
- Follow staged verification workflow
- Add comprehensive tests for all new code
- Run coverage and meet minimums before cross-Scala testing

### Ask first
- Adding dependencies (even test-only)
- Creating new subprojects
- Changing build configuration

### Never
- Skip tests or reduce coverage to pass CI
- Use repo-wide commands when module-specific suffices
- Format or test unmodified/non-downstream modules
