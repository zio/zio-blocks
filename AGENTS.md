# ZIO Blocks Agent Guidelines

Zero-dependency building blocks for Scala. Supports Scala 2.13 and 3.3+ with source compatibility across JVM, JS, and Native. Users should be able to migrate from Scala 2.13 to 3.3+ without changing code that uses ZIO Blocks.

## Development Workflow

**Fast loop** - stay in one project, one Scala version:
```bash
# Run tests for current project (default Scala 3)
sbt schemaJVM/test
sbt "schemaJVM/testOnly zio.blocks.schema.SchemaSpec"

# Compile to check for errors quickly
sbt schemaJVM/compile
```

**Before commit/push** - run full checks:
```bash
# Format all code (must run on both Scala versions)
sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"

# Test both Scala versions (required before merge)
sbt "++3.3.7; testJVM" && sbt "++2.13.18; testJVM"

# If you changed a dependency (e.g., chunk), run root tests
sbt testJVM
```

**Slow operations** - save output to temp files:
```bash
# Compile/test are slow. Save output, grep from file:
sbt testJVM 2>&1 | tee /tmp/test-output.txt
grep -i "error" /tmp/test-output.txt
```

## All Commands

```bash
sbt fmt                    # Format code (required before PR)
sbt check                  # Check formatting (CI runs this)
sbt testJVM                # All JVM tests (Scala 3)
sbt testJS                 # All JS tests
sbt testNative             # All Native tests
sbt "++2.13.18; testJVM"   # All JVM tests (Scala 2.13)
sbt schemaJVM/test         # Single project tests
sbt "schemaJVM/testOnly zio.blocks.schema.SchemaSpec"  # Single spec
```

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

Code must compile and pass tests on **both** Scala 2.13.18 and Scala 3.3.7.

- Maximize shared code in `shared/src/main/scala/`
- Version-specific code goes in `scala-2/` or `scala-3/` directories
- Pattern: Define `*VersionSpecific` traits per Scala version, mix into shared code
- Example: `SchemaCompanionVersionSpecific` in `scala-2/` uses `blackbox.Context`, in `scala-3/` uses `quoted`

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
- Run `sbt fmt` before committing
- Test on both Scala 2.13 and Scala 3
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
- Commit without formatting (`sbt fmt`)
- Delete or skip tests to make CI pass
- Reduce code coverage below minimums

## PR Checklist

- [ ] `sbt fmt` run
- [ ] Tests pass on Scala 3: `sbt "++3.3.7; testJVM"`
- [ ] Tests pass on Scala 2: `sbt "++2.13.18; testJVM"`
- [ ] New public APIs have Scaladoc
- [ ] Code coverage maintained or improved
- [ ] New features documented in `docs/`
