# telemetry and otel Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-sql-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `telemetry` module has comments on 203 of 567 public API members (35%), all in package `zio.blocks.telemetry`. `otel` has 17 of 33 (51%), in `zio.blocks.telemetry.otel`. Nobody has checked that the existing comments are true; earlier rollouts found 19% to 40% of inherited comments wrong or imprecise. Neither module has a `{{{` example, and one comment differs between the JVM and JS copies (`ContextStorage`).

The example checker cannot take these modules as it is. The `telemetry` jar is built for Java 21, and its `log.*` macros run inside the compiler, so even compiling code that uses it needs JDK 21 or newer. `scaladoc-check` runs on every JDK in the CI matrix (17 and 25) and depends on its modules, so adding `telemetry.jvm` to it would break the JDK 17 jobs. The root build already runs telemetry's tests and docs only on JDK 25 (`whenJdkAtLeast(25, ...)`).

## Goal

Every public member of `telemetry` and `otel` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0 (whichever versions telemetry builds on) on JDK 25. `--drift` stays empty. The JDK 17 jobs are untouched.

## Decisions (Milad, 2026-11-06)

- **Checker: a third project, `scaladoc-check-jdk21`.** It shares `scaladocCheckSettings` with the other two checkers, lists `telemetry.jvm` and `otel` in its own module list, and runs only through `whenJdkAtLeast(25, "scaladoc-check-jdk21/test")` in `testJVMScala2Command` and `testJVMScala3Command`, the way telemetry's own tests do. `scaladoc-check` is unchanged. The extractor unit tests stay in `scaladoc-check`.
- **`packageOverrides`:** add `"otel" -> "zio.blocks.telemetry.otel"` in `project/ScaladocSnippets.scala`, with a unit test.
- **Tier 1:** `log`, `trace`, `metric`, `Span`, `Tracer`, `Meter`, `Counter`, `Histogram`, `Resource`, `Attributes`, `Severity`, `LogWriter`; otel: `OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, `ExporterConfig`, `W3CTraceContextPropagator`, `B3Propagator`, `BatchProcessor`. Everything else is tier 2 (with `@see` when a reference page exists) or tier 3.
- **AGENTS.md:** one sentence under the Scaladoc bullet naming `scaladoc-check-jdk21`, its JDK 21+ requirement and its check command.
- **One PR** for both modules.
- **Examples** do not depend on printed log output (mdoc-style capture limits, shared global state): they use returned values or `trace.collectedSpans`.

## Layout

- `telemetry/shared/src/main/scala/zio/blocks/telemetry/` (59 files); `telemetry/jvm/.../` (5 files: `AttributeBuilderPool`, `ContextStorage`, `FileLogWriter`, `LogAnnotations`, `PlatformExecutor`); `telemetry/js/.../` (3 copies: `AttributeBuilderPool`, `ContextStorage`, `LogAnnotations`). jvm/js copies get identical text. The existing `ContextStorage` difference is replaced by one text that states the real platform difference.
- `otel/src/main/scala/zio/blocks/telemetry/otel/` (13 files), JVM only.
- Reference pages: `docs/reference/telemetry/` (`common/`, `logging/`, `metrics/`, `tracing/`, `otel/`); links use `https://zioblocks.com/reference/telemetry/<section>/<page>/`.
- Known bugs to reflect, in plain words ("a known bug"): default log line format, `log.*` on 2.13 outside the package, `log.removeAll()`, `metric.counter` registering new instruments, `Resource` ambiguity with scope, `trace.collectedSpans` after install/removeAll, `log.install` dropping writers, `ContextStorage` and trace ids; otel: W3C traceparent version handling, B3 single-header sampling-only.

## Tasks

- **Tooling (controller):** the new project, the `packageOverrides` entry and its test, the gated test-alias entries, the root aggregate, the style guide's module list, AGENTS.md.
- **Task 1 (Opus), "logging, common types and platform files":** `log`, `Logger`, `LoggerProvider`, `LogRecord*`, `LogWriter`, `LogFormatter`, `LogEnrichment`, `LogEmitter`, `Severity`, `GlobalLogState`, `Attributes`, `AttributeKey/Type/Value/Visitor`, `Resource`, `InstrumentationScope`, `SourceLocation`, `EpochClock`, and the jvm/js files.
- **Task 2 (Opus), "tracing and metrics":** `trace`, `Span*`, `Tracer*`, `Sampler`, `TraceId`, `TraceFlags`, `metric`, `Meter*`, `Counter`, `Gauge`, `Histogram`, `UpDownCounter`, `Observable*`, `MetricData`, `MetricReader`, instruments, and `InMemorySpanProcessor`. Runs sbt only after Task 1 hands back.
- **Task 3 (Opus), "otel":** all 13 otel files.
- **Task 4 (Sonnet):** tier-2 and tier-3 gaps the coverage report still lists.
- **Review:** a fresh-context Opus fact-check reviewer that re-runs probes (scala-cli against 0.0.56 on JDK 21+) and every bug reproducer, one fix round, a re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check-jdk21/test; ++3.10.0; ...; ++2.13.18; ...` on JDK 21+, plus `++3.9.0; scaladoc-check/test` (extractor tests), and `scaladoc-check` still green on JDK 17 if the design touches it.
2. `telemetryJVM/doc`, `telemetryJS/doc`, `otel/doc` on 3.9.0 and 2.13.18: no new warnings.
3. Coverage 100% for both, `--drift` empty, `comments-only.py 121cb1024 telemetry` and `otel` print 0 code changes.
4. `check; headerCheckAll` on 2.13.18 and 3.9.0; `scalafmtAll`, `scalafmtSbt`.

## Out of scope

Library code changes, reference-page rewrites (errors go to the coordinator as docs-issues entries), `docs/dev/` files other than the style guide's module list, CI workflow files, other modules.
