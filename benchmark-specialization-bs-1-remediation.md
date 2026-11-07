# BS-1 Remediation: General Async Terminal Folding

## Completion result

`AsyncInterpreter.TerminalDriver` now accepts every logical input lane and
`Int`, `Long`, `Float`, `Double`, or reference accumulators through one
`TerminalFold` protocol. Scheduling, pending replacement, cancellation,
failure recovery, and cleanup remain interpreter-owned. The former mapped
`Int -> Long` helper dedicated to an async-mapped `Long` output was removed.

The existing direct `Int => Async[Int] => Long` adapter remains. A
primitive-map observation experiment doubled allocation (264 to 544 B/op) and
reduced throughput by about 26%, violating the 5% acceptance gate. Routing the
generic async fold through a concrete custom sink made all primitive callbacks
fast, but exposed a real ownership defect: cancellation could complete before
a late-acquired Float/Double reader was closed. That lifecycle-unsafe variant
was rejected. The retained anonymous sink route passes the cancellation suite.

Three-fork JMH with `-prof gc` is archived in
`streams/benchmarks/specialization/bs-1/`. Relative to `d5b51e8d`, the valid
candidate preserves Int allocation and throughput, improves the Long callback
from 103 to 1,838 ops/s while reducing allocation from 3.75 MB to 1.44 MB per
operation, and leaves Float/Double callback allocation effectively unchanged.
The lane-general terminal driver is therefore the semantic owner, while the
concrete adapter is retained as a JVM representation optimization. This meets
the generalization goal without trading away either lifecycle semantics or the
historical Int control's performance.

## Decision

Replace the dedicated `Int => Async[Int] => Long` state-machine family with a
lane-general terminal-fold capability in `AsyncInterpreter`. Generate typed
callback adapters, not lifecycle state machines. One implementation must own
acquisition, pending replacement, yielding, cancellation, failure provenance,
and cleanup for every lane combination.

Logical lanes are `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`,
`Double`, and reference. They continue to use the existing five physical
carriers: int-like, `Long`, `Float`, `Double`, and reference.

## Current coupling

- `Stream.runAsync` / `runFoldAsync` select exact direct paths in
  `streams/shared/src/main/scala/zio/blocks/streams/Stream.scala`.
- `AsyncMappedIntLongUse` and its resume/bracket protocol hard-code an `Int`
  reader, `Int => Async[Int]`, and a `Long` accumulator.
- `MappedNativeAsyncIntEitherLongFold.scala` and continuations in `Sink.scala`
  duplicate part of that protocol.
- `AsyncInterpreter.TerminalDriver` currently exposes only the corresponding
  `foldIntLongAsync` shape.

## Stage 1: characterize before changing production

Add a differential harness comparing public optimized execution with an opaque
`Sink.createAsync` / `Sink.foldAsyncReader` route that forces the existing
general path.

### Semantic matrix

- Exercise all 9 × 9 × 9 input, mapped-output, and accumulator combinations
  with short ready streams.
- Include primitive extrema, narrow-lane conversions, signed zero, infinities,
  raw-bit-distinct NaNs, nullable references, and sentinel-collision values.
- Compare result, callback order/count, physical read method, pull count, EOF,
  acquisition count, and exact-once close.
- Cover raw async readers, borrowed synchronous readers, and protected and
  unprotected acquisition.
- Cover synchronous fold, ready async fold, pending async fold, drain, terminal
  `map`, terminal `mapAsync`, and early termination.

### Lifecycle matrix

For representative asymmetric triples (`Int/Int/Long`, `Long/Double/Int`,
`Double/Long/Double`, narrow int-like transitions, reference-to-primitive, and
primitive-to-reference), inject ready, pending, thrown, typed, and defecting
outcomes at acquisition, read, async map, fold, and close. Test cancellation at
every pending state, replacement pollables, primary-plus-suppressed cleanup
failure, and yield boundaries around 255/256 and 1023/1024/1025.

Required artifacts:

- `streams/shared/src/test/scala/zio/blocks/streams/AsyncMappedFoldMatrixSpec.scala`
- typed lane and scripted lifecycle fixtures shared by later remediation work
- additions to `StreamAsyncParityBench.scala`, `StreamAsyncMicroBench.scala`,
  benchmark correctness, and the benchmark manifest
- baseline throughput, sample latency, GC allocation, and environment metadata
  under `streams/benchmarks/bs-1-baseline/`

Commit the characterization and baseline before production changes.

## Stage 2: generalize

### Shared interpreter terminal

In `internal/AsyncInterpreter.scala`:

1. Replace `foldIntLongAsync` with an internal terminal descriptor carrying
   logical output/accumulator types, initial value, and sync/async step.
2. Add physical accumulator registers parallel to element registers.
3. Let `ReadOperation` invoke the terminal for every physical output lane.
4. Keep all pending, stale-completion, cancellation, recovery, cleanup, and
   cooperative-yield behavior in the interpreter.
5. Add a callback-free drain terminal rather than encoding drain as a fake
   `Long` fold.

### Generated adapters

Add `project/GenerateAsyncTerminalAdapters.scala`, source-generator wiring in
`build.sbt`, and generated adapters under `sourceManaged`. Generate only the
logical callback/commit adapters needed for output and accumulator lanes. The
generated code reconstructs narrow values, invokes concretely typed callbacks,
commits ready values to physical registers, and boxes only the final generic
result. It must not contain reader loops, brackets, cancellation, or scheduling.

### Sink and stream integration

- Give every `Sink.foldLeft` and `foldLeftAsync` implementation a private fold
  descriptor without changing public signatures.
- Route all `runFoldAsync` overloads through the descriptor/interpreter path.
- Keep terminal `map` / `mapAsync` as ordinary post-terminal composition.
- Once parity and performance gates pass, remove `AsyncMappedIntLongUse`, its
  resume variants, mapped native `Int`/`Long` fold continuations, and
  `MappedNativeAsyncIntEitherLongFold.scala`.
- Update specialization inventories and internal documentation.

## Verification and acceptance

- The exhaustive ready 9³ matrix agrees with forced fallback.
- Every primitive appears in pending read, map, and fold tests.
- Source errors retain trusted provenance; callback-thrown `StreamError`s are
  untrusted defects.
- Cancellation joins active replacement work and closes exactly once.
- Nothing starts after early termination; ready infinite sources yield.
- Scala 2/3, JVM/JS, targeted coverage, and API-signature checks pass.
- There is exactly one lifecycle state machine; generated code is adapters only.
- Existing `Int/Int/Long` throughput and allocation regress by at most 5%; an
  isolated pending resume regresses by at most 10%.
- Before/after JMH and GC profiles use at least three forks and are archived.

## Commit sequence

1. Characterization tests and forced fallback.
2. JMH matrix and baseline evidence.
3. Generated terminal adapters and completeness tests.
4. General `AsyncInterpreter` terminal protocol.
5. Sink descriptors and stream routing.
6. Delete superseded state machines and update inventories/docs.
7. Candidate benchmarks and tuning of the shared kernel.

## Risks and effort

Primary risks are cancellation/cleanup drift, erased callback casts,
narrow-lane/sentinel corruption, and generated bytecode growth. Reusing the
interpreter protocol is the central mitigation. Estimated effort: 7–10
engineering days plus cross-build and benchmark time.
