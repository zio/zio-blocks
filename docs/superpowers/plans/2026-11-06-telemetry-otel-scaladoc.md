# telemetry and otel Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-06-telemetry-otel-scaladoc-design.md`

## In plain English

**The problem.** `telemetry` is the logging, tracing and metrics module, and `otel` sends its data to an OpenTelemetry collector. Only 35% of telemetry's public members (51% of otel's) have comments, nobody has checked that those comments are true, and no comment has a code example. The build's example checker can't take these modules: they need Java 21 to compile, and the checker also runs on Java 17 in CI.

**The goal.** Every public member has a true, short comment. The 19 types a user meets first have an overview, a compiled example and a link to their reference page. CI compiles every example, on Java 25 only.

**The steps.**
1. **Tooling.** Add a third checker project, `scaladoc-check-jdk21`, that compiles the telemetry and otel examples. It runs only on Java 25, the way telemetry's own tests do, so the Java 17 jobs never see it. Teach the checker that otel's package is `zio.blocks.telemetry.otel`. Update the style guide and AGENTS.md.
2. **Logging, common types and platform files (Opus).** A strong-model agent documents `log`, `Logger`, `LogWriter`, `Severity`, `Attributes`, `Resource` and their neighbours, and the JVM/JS files, makes examples compile, and checks every existing comment against the code.
3. **Tracing and metrics (Opus, after step 2 finishes with sbt).** Same for `trace`, `Span`, `Tracer`, `metric`, `Meter`, `Counter`, `Histogram` and neighbours.
4. **otel (Opus).** Same for the exporters, propagators and `BatchProcessor`.
5. **Gaps (Sonnet).** A cheaper agent writes the short comments still missing.
6. **Review.** A fresh agent tests every changed claim against the code and the published 0.0.56 library; authors fix what it finds; a last agent re-checks only the fixes.
7. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-06-telemetry-otel-scaladoc/global-constraints.md` (skill template with `telemetry`/`otel` filled in). Run telemetry builds on JDK 21+ (`--java-home`); one sbt server (`SBT_OPTS="-Xmx6g"`), `free -g` before each run, `sbt --client shutdown` after each gate round.

## Task 0 (controller): tooling

**Files:** `build.sbt` (`scaladocCheckedJdk21Modules`, `scaladoc-check-jdk21`, gated entries in `testJVMScala2Command`/`testJVMScala3Command`, root aggregate), `project/ScaladocSnippets.scala` (`otel` package override), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (one test), `docs/dev/scaladoc-style.md` (module list, checker command), `AGENTS.md` (approved sentence).

- [ ] Step 1: write the build and extractor changes and the test.
- [ ] Step 2: run `scaladoc-check/test` on 3.9.0 (green) and `scaladoc-check-jdk21/test` on 3.9.0 and 2.13.18 (green, zero examples yet).
- [ ] Step 3: commit (`build: compile telemetry and otel Scaladoc examples in scaladoc-check-jdk21`), then `docs: ...` for the style guide and AGENTS.md.

## Task 1 (Opus): logging, common types and platform files

**Files:** `log.scala`, `Logger`, `LoggerProvider`, `LogRecord`, `LogRecordProcessor`, `ConsoleLogRecordProcessor`, `StdoutLogRecordProcessor`, `FormattedLog*`, `LogWriter`, `LogFormatter`, `LogEnrichment`, `LogEmitter`, `LogRateLimit`, `GlobalLogState`, `Severity`, `Attributes`, `AttributeKey`, `AttributeType`, `AttributeValue`, `AttributeVisitor`, `Resource`, `InstrumentationScope`, `SourceLocation`, `EpochClock`, jvm `AttributeBuilderPool`, `ContextStorage`, `FileLogWriter`, `LogAnnotations`, `PlatformExecutor`, and the js copies.
**Tier 1:** `log`, `LogWriter`, `Severity`, `Attributes`, `Resource`. **Reference pages:** `docs/reference/telemetry/logging/*`, `common/*`.
**Known bugs to reflect:** default log line format, `log.*` on 2.13 outside the package, `log.removeAll()`, `log.install` dropping writers, `Resource` ambiguity with scope, `ContextStorage` and trace ids.

- [ ] Step 1: research (source, tests, reference pages), then write comments and compiled examples (no reliance on printed log output).
- [ ] Step 2: tier 1, then a fact-check table of every existing comment (member, claim, kind, evidence); one shared `ContextStorage` text.
- [ ] Step 3: gates, one commit per file group, report and issues file.

## Task 2 (Opus): tracing and metrics

**Files:** `trace.scala`, `Span`, `SpanBuilder`, `SpanContext`, `SpanData`, `SpanId`, `SpanKind`, `SpanProcessor`, `SpanStatus`, `Tracer`, `TracerProvider`, `Sampler`, `TraceId`, `TraceFlags`, `InMemorySpanProcessor`, `metric.scala`, `Meter`, `MeterProvider`, `Counter`, `Gauge`, `Histogram`, `UpDownCounter`, `Observable*`, `LabeledInstruments`, `SyncInstruments`, `MetricData`, `MetricReader`.
**Tier 1:** `trace`, `Span`, `Tracer`, `metric`, `Meter`, `Counter`, `Histogram`. **Reference pages:** `tracing/*`, `metrics/*`.
**Known bugs to reflect:** `metric.counter` registering new instruments, `trace.collectedSpans` after install/removeAll.

- [ ] Step 1: research, then comments and compiled examples (use returned values or `trace.collectedSpans`).
- [ ] Step 2: tier 1, fact-check table.
- [ ] Step 3: gates (only after the controller says Task 1 is done with sbt), commits, report and issues file.

## Task 3 (Opus): otel

**Files:** the 13 files in `otel/src/main/scala/zio/blocks/telemetry/otel/`.
**Tier 1:** `OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, `ExporterConfig`, `W3CTraceContextPropagator`, `B3Propagator`, `BatchProcessor`. **Reference pages:** `otel/index.md`, `otel/custom-exporter.md`.
**Known bugs to reflect:** W3C traceparent version handling; B3 single-header sampling-only. Examples must not need a network collector (use `HttpSender` stubs or encode-only calls).

- [ ] Steps as Task 2.

## Task 4 (Sonnet): tier 2 and 3 gaps

- [ ] List members the coverage script still reports; write comments; probe anything behavioural; gates; commit; report.

## Task 5: review and ship

- [ ] Opus fact-check review (fresh context) re-running probes and every bug reproducer; fix round (authors resumed); scoped re-review.
- [ ] `git fetch origin && git merge origin/main`; re-run gates; push `khajavi telemetry-otel-scaladoc`; open the PR with `--body-file`; watch CI (JDK 17 and 25 jobs) to green; no force-push, no merge.
- [ ] Send the coordinator the bug entries (one file, target line each), the §13s text and the metric.
