# ZIO Blocks Agent Guidelines

Zero-dependency building blocks for Scala. Supports Scala 2.13 and 3.3+ with source compatibility across JVM, JS, and Native. Users should be able to migrate from Scala 2.13 to 3.3+ without changing code that uses ZIO Blocks.

## Development Workflow

**IMPORTANT: Command execution rules**
- Use explicit paths from workspace root. Never `cd`; verify with `pwd` if unsure.
- Requires bash (for `pipefail`). If your runner uses `sh`, wrap with `bash -c '...'`.

**Slow command workflow** (compile/test/format, or anything >200 lines output):

Step 1: Run and save output (nothing streams into context)
```bash
ROOT="$(git rev-parse --show-toplevel)" || exit 1
mkdir -p "$ROOT/.git/agent-logs"
LOG="$ROOT/.git/agent-logs/sbt-$(date +%s)-$$.log"; echo "LOG=$LOG"
set -o pipefail
sbt -Dsbt.color=false testJVM >"$LOG" 2>&1
status=$?; echo "Exit: $status"; exit "$status"
```

Step 2: Query the log (paste the absolute LOG path from Step 1)
```bash
LOG=/absolute/path/from/step1.log  # paste actual path

tail -n 50 "$LOG"                                                          # last 50 lines
grep -n -i -E 'error|exception|failed|failure' "$LOG" | head -30 || true   # find errors
sed -n '130,170p' "$LOG"                                                   # lines around N
```

Step 3: Re-run only if source code changed — otherwise query the log again.

**Anti-patterns:**
- `sbt test | grep error` — loses full log
- Using `$LOG` without re-setting it — fresh shell loses variables
- Relying on streamed output instead of querying the file
- Forgetting `pipefail` — masks sbt failures

## All Commands

```bash
# Required before merge (both Scala versions)
sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"        # Format all code
sbt "++3.3.7; testJVM" && sbt "++2.13.18; testJVM"  # Run all JVM tests

# CI checks
sbt check                  # Check formatting (version-agnostic)
sbt testJS                 # JS tests
sbt testNative             # Native tests

# Scala Next forward-compatibility (CI runs these on Scala 3.7.4)
sbt "++3.7.4; scalaNextTestsJVM/test; benchmarks/test"

# Fast dev loop (single project, default Scala 3)
sbt schemaJVM/compile      # Quick compile check
sbt schemaJVM/test         # Single project tests
sbt "schemaJVM/testOnly zio.blocks.schema.SchemaSpec"  # Single spec
```

Note: Always lint, test, and typecheck updated files. Use project-wide build sparingly.

## Project Structure

```
schema/           # Core schema library (cross-platform)
  shared/         # Common code for all Scala versions
    src/main/scala-2/   # Scala 2.x specific (blackbox macros)
    src/main/scala-3/   # Scala 3.x specific (quoted macros)
  jvm/            # JVM-specific code
  js/             # JS-specific code
  native/         # Native-specific code
chunk/            # High-performance immutable sequences (cross-platform)
streams/          # Pull-based streaming (cross-platform)
schema-avro/      # Avro codec (JVM only)
schema-bson/      # BSON codec (JVM only)
schema-thrift/    # Thrift codec (JVM only)
schema-toon/      # TOON codec (cross-platform)
benchmarks/       # JMH benchmarks (JVM only)
docs/             # Documentation
```

## Scala Version Compatibility

Code must compile and pass tests on **both** Scala 2.13.18 and Scala 3.3.7. CI also validates forward compatibility with Scala 3.7.x via `scalaNextTests`.

- Maximize shared code in `shared/src/main/scala/`
- Version-specific code goes in `scala-2/` or `scala-3/` directories
- Platform-specific code goes in `jvm/`, `js/`, `native/` directories
- Pattern: Define `*Specific` traits per version/platform, mix into shared code:
  - `XXVersionSpecific` - Scala version differences (e.g., `IntoVersionSpecific`)
  - `XXPlatformSpecific` - JVM/JS/Native differences (e.g., `ChunkPlatformSpecific`)
  - `XXCompanionVersionSpecific` - Companion object macros (e.g., `SchemaCompanionVersionSpecific`)

## Code Style

- Scalafmt enforced (maxColumn: 120, Scala 3 dialect)
- All new public methods and types need Scaladoc
- No comments explaining obvious code changes
- Follow existing patterns in neighboring files

## Testing

- Framework: ZIO Test (`zio.test.sbt.ZTestFramework`)
- Tests extend `SchemaBaseSpec` for consistent setup
- Property-based tests use `Gen` from ZIO Test
- Coverage minimums enforced by CI (varies by module, see build.sbt)

Good test patterns: `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala`
Test utilities: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonTestUtils.scala`

## Dependencies

**Zero ecosystem lock-in** - no dependencies on ZIO, Cats Effect, Akka, etc.

- Minimize external dependencies
- Implement functionality in-house when reasonable
- Test-only deps allowed: `zio-test`, `zio-prelude`

## Boundaries

### Always do
- Run tests after modifying code
- Format on both Scala versions: `sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"`
- Test on both Scala versions before merge: `sbt "++3.3.7; testJVM" && sbt "++2.13.18; testJVM"`
- Add Scaladoc for new public APIs
- Place shared code in `shared/`, version-specific in `scala-2/` or `scala-3/`
- New features need documentation in `docs/`
- New subprojects need their own README.md and AGENTS.md

### Ask first
- Adding new dependencies (even test-only)
- Creating new subprojects
- Changing build configuration
- Modifying CI workflows

### Never do
- Add ecosystem dependencies (ZIO, Cats, Akka, etc.)
- Break source compatibility between Scala versions
- Commit without formatting
- Delete or skip tests to make CI pass
- Reduce code coverage below minimums

### Maintaining AGENTS.md

Update only when **all true**: general (not task-specific), verified (you ran it), actionable (1–5 lines), not documented elsewhere.

**Update for:**
- Wrong/outdated commands or workflows
- New verified reusable patterns
- Build/tooling/CI changes affecting workflow
- New subprojects (update structure section)
- Recurring pitfalls worth preventing

**Do NOT update for:**
- One-off notes, workarounds, guesses, style opinions
- Long logs or non-recurring troubleshooting
- Content belonging in docs/ or module READMEs

**Approval:** Self-update OK for factual fixes. Ask first before changing normative rules (Always/Never/Ask first).

## PR Checklist

- [ ] Formatted on both versions: `sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"`
- [ ] Tests pass on Scala 3: `sbt "++3.3.7; testJVM"`
- [ ] Tests pass on Scala 2: `sbt "++2.13.18; testJVM"`
- [ ] New public APIs have Scaladoc
- [ ] Code coverage maintained or improved
- [ ] New features documented in `docs/`
