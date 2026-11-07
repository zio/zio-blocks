# ZIO Blocks Streams Benchmark-Specialization Audit

## Conclusion

The original eight findings have been remediated, but a second audit found three
additional fast paths whose implemented coverage still closely matches the
benchmark harness: `Int` elements reduced by a `Long` checksum. Two are in the
concurrent operator engine, which the first audit did not inspect deeply enough;
the third is the synchronous deep-concatenation shortcut. These are useful
optimizations, but their scheduling or traversal machinery is available only
to the benchmark's element/terminal product while other lanes and accumulators
fall back to materially different paths.

This is a static implementation audit, not a correctness report. “Confirmed
coupling” below means that the current production branch exactly recognizes a
benchmark shape and was introduced during the benchmark optimization campaign;
it does **not** establish improper intent or incorrect behavior. No benchmark
constants such as `N = 1000`, `N = 10000`, or a 10,000-node depth were found in
production.

## Findings

Progress: BS-1 through BS-11 are complete. An item receives a checkmark only after its specialized owner
is removed or the operation is generalized without semantic or performance
regression.

| ID | Confidence | Suspicious production path | Benchmark shape it matches | Missing generalization | Remediation record / next action |
|---|---|---|---|---|---|
| ✅ BS-1 | High — remediated | Direct async `Int => Async[Int] => Long` state machines | Every ready-effect benchmark uses `Stream[Int]` and `Sink.foldLeft[Int, Long]` | Shared terminal driver now owns all logical input and accumulator lanes; measured Int adapter preserves its allocation profile | [Completed remediation](benchmark-specialization-bs-1-remediation.md) |
| ✅ BS-2 | High — remediated | `Int`-only map/filter-chain recognizer and a hand-unrolled 0–10 operation evaluator | `filterMapChain` has exactly 10 alternating operations; `chainedMaps10` has 10 maps | The benchmark-shaped cutoff is gone; the shared interpreter owns arbitrary linear programs across every lane, while fixed power-of-two Int kernels remain measured physical adapters | [Completed remediation](benchmark-specialization-bs-2-remediation.md) |
| ✅ BS-3 | High — remediated | `Int` ready-effect `take`, `drop/take`, and `takeWhile` fused directly into a `Long` fold | Ready-effect pipeline `take`, `takeDrop`, and `takeWhile`, all checksummed as `Long` | Shared async interpreter owns slice and short-circuit state across logical lanes and accumulator lanes; Int/Long adapters remain measured JVM accelerators | [Completed remediation](benchmark-specialization-bs-3-remediation.md) |
| ✅ BS-4 | High — remediated | `Int` ready-effect concat/flatMap children fused into a `Long` terminal | Ready-effect `concat`, `flatMap`, `mapFilterFlatMap`, and mixed chains | Shared PUSH/segment traversal owns lifecycle for every lane, and its scalar-child visitor now inlines every exact logical scalar type | [Completed remediation](benchmark-specialization-bs-4-remediation.md) |
| ✅ BS-5 | High — remediated | Deep blocking `flatMap` shortcut only for `SingletonInt` chains ending in a `Long` fold | `nested_flatMap` is 10,000 `Int` singleton-to-singleton flatMaps reduced to `Long` | Blocking compilation now collapses scalar spines across all logical lanes and terminals; the measured Int/Long adapter preserves scalar replacement | [Completed remediation](benchmark-specialization-bs-5-remediation.md) |
| ✅ BS-6 | High — remediated | Dedicated `Vector[Int]` stream/reader plus `Long` slice and `takeWhile` loops | Native benchmarks construct `(0 until N).toVector`, then fold, take, drop, or drain | One immutable Vector source/reader now preserves all physical lanes and supports every accumulator through shared sinks; source-owned slices are element-lane-independent | [Completed remediation](benchmark-specialization-bs-6-remediation.md) |
| ✅ BS-7 | Medium-high — remediated | Source-aware allocation-minimal nodes only for protected async `Int` sources, plus an `Int` map-output node | Provider-local async microbenchmarks use the protected source; setup benchmarks repeatedly construct `Int` maps | Protected acquisition, source-owned map, and source-owned drop/take now preserve ownership independently of element lane; the Int→Int callback adapter remains as a measured JVM representation adapter | [Completed remediation](benchmark-specialization-bs-7-remediation.md) |
| ✅ BS-8 | Medium — remediated | Synchronous `FromRange`/mapped/filter/map-chain loops specialized for `Int` input and `Long` folds | Native range and pipeline benchmarks use `Int` sources and a `Long` checksum | One operation-neutral range traversal now owns raw/map/filter/slice/takeWhile semantics; all accumulator lanes use shared typed sinks and Long remains a measured zero-allocation adapter | [Completed remediation](benchmark-specialization-bs-8-remediation.md) |
| ✅ BS-9 | High — remediated | Async `mapPar` had a dedicated `Int => Async[Int] => Long` terminal state machine | Both concurrent suites map `Int` values and use `AsyncParity.fold`, a `Long` checksum | One `MapParFold` now owns concurrency for every lane and terminal; accumulator adapters retain only representation, and pending-source continuation no longer adds a callback capture per element | [Completed remediation](benchmark-specialization-bs-9-remediation.md) |
| ✅ BS-10 | High — remediated | Async `mergeAll`/`flatMapPar` had an `Int`-only JVM worker pool consumed by an `Int`/`Long` fold | Both fan-in cells process one million `Int` values into a `Long` checksum | The Int-only pool and hidden 1,024-element buffer are gone; one selector lifecycle and `MergeFold` serve every lane and terminal under the requested buffer policy | [Completed remediation](benchmark-specialization-bs-10-remediation.md) |
| ✅ BS-11 | High — remediated | Deep synchronous concat was flattened only for `Int` streams ending in a `Long` fold | `nested_concat` builds 10,000 singleton `Int` leaves and calls `zbFold: Long` | One depth-sized concat cursor now serves Int, Long, Double, and generic blocking folds across all element lanes | [Completed remediation](benchmark-specialization-bs-11-remediation.md) |

### BS-1 — the central ready-effect engine is an exact `Int`/`Int`/`Long` product

The benchmark helper creates a high-level ready-effect source as
`Stream.range(...).mapAsync(value => Async.succeed(value))`, then always consumes
it with `Sink.foldLeft[Int, Long](0L)(_ + _)`:

- [`StreamAsyncParityBench.scala:56-61`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncParityBench.scala#L56-L61)
- [`StreamAsyncParityBench.scala:86-87`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncParityBench.scala#L86-L87)

Production has a large state-machine family dedicated to precisely that product:

- `AsyncMappedIntLongUse` stores `Reader[Int]`, `Int => Async[Int]`, a `Long`
  accumulator, and `(Long, Int) => Async[Long]` at
  [`Stream.scala:8160-8171`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8160-L8171).
- Its hot loop uses primitive `Int`/`Long` step folds and hands suspension to
  `MappedNativeAsyncIntEitherLongFold` at
  [`Stream.scala:8217-8282`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8217-L8282).
- That continuation is itself a standalone class whose signature hard-codes
  the same product at
  [`MappedNativeAsyncIntEitherLongFold.scala:7-15`](streams/shared/src/main/scala/zio/blocks/streams/MappedNativeAsyncIntEitherLongFold.scala#L7-L15).
- `Stream.runAsync` recognizes the concrete `Sink.FoldLeftLong` before the
  ordinary managed sink path at
  [`Stream.scala:481-503`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L481-L503).

There is some broader accumulator support: mapped `Int` streams have direct
`Int`, `Float`, and `Double` fold entry points, and the sink has generic
fallbacks. That does not generalize the dominant path: async mapped input and
output remain `Int`, and most composite-operation fusion below exists only for
a `Long` terminal.

**Generalization direction:** generate state-machine variants from one semantic
template for useful input/output/accumulator lane combinations, or introduce a
primitive-lane strategy that shares cancellation, failure, cleanup, budgeting,
and resume logic. Avoid manually cloning the protocol for every combination.

### BS-2 — the linear-chain evaluator is tuned to the benchmark's operation count

`useLinearIntFoldLongDirect` walks only `Mapped` and `Filtered` nodes whose
stable representation is `Int`, packs them into an operation array, and rejoins
an async source, async map, or flatMap only through a `Long` fold:

- recognizer: [`Stream.scala:8930-8960`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8930-L8960)
- dispatch: [`Stream.scala:8982-9007`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8982-L9007)

`IntLinearContext.evaluate` selects a hand-unrolled implementation for arrays of
at most 10 operations at
[`Stream.scala:8596-8614`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8596-L8614).
That threshold exactly includes both `chainedMaps10` and the five-filter,
five-map `filterMapChain` built at
[`StreamAsyncPipelineBench.scala:114-149`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncPipelineBench.scala#L114-L149).

The optimization is useful beyond JMH, but the exact type product and threshold
make this one of the clearest benchmark-shaped paths. Chains of 11 operations,
non-`Int` primitives, a type-changing map, or a non-`Long` terminal use materially
different machinery.

**Generalization direction:** represent the linear operation program once and
execute it through generated primitive-lane evaluators. Choose unroll sizes from
independent instruction-cache/code-size measurements over varied depths rather
than preserving 10 as an unexplained magic boundary.

### BS-3 — slice and short-circuit fusion exists for the benchmark terminal only

`TakeDrop.runFoldLongDirect`, `Taken.runFoldLongDirect`, and
`TakenWhile.runFoldLongDirect` recognize async `Int` sources or
`AsyncMapped[Int, Int]` and construct dedicated `Int`/`Long` uses:

- [`Stream.scala:10493-10512`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L10493-L10512)
- [`Stream.scala:10627-10640`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L10627-L10640)
- [`Stream.scala:10702-10723`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L10702-L10723)

The mapped-source implementation even embeds drop/take counters around
`Int => Async[Int]` before feeding `(Long, Int) => Async[Long]` at
[`Stream.scala:4991-5017`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L4991-L5017).
These paths were added in the campaign commit titled “Optimize ready-effect
stream slices and concatenation” and directly serve the ready-effect operations
built at
[`StreamAsyncPipelineBench.scala:127-134`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncPipelineBench.scala#L127-L134).

**Generalization direction:** make slice/short-circuit state a reusable terminal
driver feature. Then specialize physical reads and accumulators by lane, instead
of coupling each stateful operator to `AsyncMapped[Int, Int]` and `Long`.

### BS-4 — concat and flatMap continuation paths retain the checksum types

The async child-continuation path special-cases async `Int` sources and
`SingletonInt`, preserving a `Long` carry at
[`Stream.scala:8820-8833`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8820-L8833).
`useLinearIntFlatMapLongDirect` recognizes only `Int` map/filter/flatMap chains
and creates `SyncLinearFlatMapIntLongUse` variants at
[`Stream.scala:8837-8927`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L8837-L8927).

The cross-provider ready-effect suite exercises those exact forms for `concat`,
`flatMap`, and filter/map/flatMap compositions at
[`StreamAsyncPipelineBench.scala:121-134`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncPipelineBench.scala#L121-L134).

**Generalization direction:** factor the inner-stream stack, cleanup, and resume
protocol from element and accumulator representation. The traversal is useful
for every type; only physical read and fold invocation should be lane-specific.

### BS-5 — deep flatMap collapse recognizes the exact singleton benchmark

`FlatMapped.runFoldLongBlocking` first requires `Int` input/output, walks only a
nested `FlatMapped` spine, proceeds only when its root is `SingletonInt`, and
then repeatedly requires every produced child to be another `SingletonInt`:
[`Stream.scala:5390-5442`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L5390-L5442).

The benchmark constructs exactly 10,000 applications of
`Stream.succeed(1).flatMap(_ => Stream.succeed(1))` and reduces the final `Int`
to `Long`:
[`StreamEvalBench.scala:339-343`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamEvalBench.scala#L339-L343),
[`StreamEvalBench.scala:475-482`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamEvalBench.scala#L475-L482).
This shortcut was introduced in “Optimize deeply nested blocking flatMap folds.”

This is more narrowly benchmark-shaped than generic stack-safe compilation:
deep singleton flatMap chains over `Long`, `Double`, or references, or consumed
by another terminal, do not receive the collapse.

**Generalization direction:** collapse singleton-producing flatMap spines using
the stream's stable representation and a lane-neutral trampoline, with generated
primitive scalar carriers where worthwhile.

### BS-6 — `Vector[Int]` receives a bespoke stream and bespoke slice terminals

`Stream.fromIterable` creates `FromVectorIntStream` only when the collection is
a `Vector` and the element type is `Int`:
[`Stream.scala:2929-2938`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L2929-L2938).
`Reader.fromIterable` repeats the same concrete match at
[`Reader.scala:2403-2409`](streams/shared/src/main/scala/zio/blocks/streams/io/Reader.scala#L2403-L2409).
The stream then directly implements `Long` slice and take-while folds at
[`Stream.scala:5930-5993`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L5930-L5993),
which are selected by `TakeDrop`, `Taken`, and `TakenWhile`.

The native benchmark's source is always `(0 until N).toVector` at
[`StreamEvalBench.scala:171-185`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamEvalBench.scala#L171-L185),
and the pipeline suite repeatedly calls `Stream.fromIterable(seq)`, including
its take/drop cell at
[`StreamPipelineBench.scala:420-436`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamPipelineBench.scala#L420-L436).
The source and slice classes were introduced in separate campaign commits aimed
at strict-vector construction and slices.

Unlike the full `Chunk` source, which has several primitive reader variants,
there is no corresponding `FromVectorLongStream`, `FromVectorDoubleStream`, or
general indexed-sequence source.

**Generalization direction:** define an indexed strict-source abstraction and
generate physical-reader/slice loops for the supported primitive lanes. Add a
fast path only when indexing/iteration characteristics are known, rather than
keying the design around the benchmark's concrete `Vector[Int]`.

### BS-7 — allocation-minimal graph nodes encode one benchmark source shape

The provider-local `AsyncParity.source` uses `Stream.fromReaderAsync` around an
`Int` reader, while the cross-provider ready-effect source uses
`Stream.range(...).mapAsync(...)`. Production has source-aware wrappers that
retain the former source's raw acquisition closure solely for the `Int` lane:

- `ProtectedIntAsyncMapped` and `ProtectedIntAsyncMappedInt`:
  [`Stream.scala:9602-9693`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L9602-L9693)
- `ProtectedIntAsyncTakeDrop`:
  [`Stream.scala:10528-10570`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L10528-L10570)
- output-only `IntMapped` node:
  [`Stream.scala:10346-10354`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L10346-L10354)

These are real allocation optimizations and can benefit users, but their
ownership optimization is unnecessarily tied to `Int`. The same raw-acquisition
retention is conceptually useful for every stable source lane and reference
type. The protected nodes are reached by the local suspension/microbenchmarks at
[`StreamAsyncMicroBench.scala:173-193`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncMicroBench.scala#L173-L193).
Separately, `IntMapped` is reached by ready-effect mapped graph construction at
[`StreamAsyncSetupBench.scala:108-124`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncSetupBench.scala#L108-L124).

**Generalization direction:** make source-aware ownership nodes generic in the
element representation, then use compact generated subclasses only to avoid
boxing of primitive callback fields.

### BS-8 — native range fusion is a source/terminal cross-product

Synchronous mapped and filtered readers bypass ordinary pull composition when
the source is exactly `Reader.FromRange` and the terminal is exactly a `Long`
fold:

- filtered range: [`Reader.scala:3131-3152`](streams/shared/src/main/scala/zio/blocks/streams/io/Reader.scala#L3131-L3152)
- mapped range: [`Reader.scala:4702-4733`](streams/shared/src/main/scala/zio/blocks/streams/io/Reader.scala#L4702-L4733)
- mapped-chain terminal dispatch: [`Sink.scala:2632-2693`](streams/shared/src/main/scala/zio/blocks/streams/Sink.scala#L2632-L2693)

Range is inherently `Int`, so the input specialization is justified. The
suspicious dimension is terminal coupling: the loop is exposed specifically as
`fold...Long`, matching the benchmark checksum, rather than as reusable range
traversal fused with the primitive accumulator family.

**Generalization direction:** retain range arithmetic specialization, but expose
it through a primitive-terminal protocol supporting at least `Int`, `Long`,
`Float`, and `Double` accumulators and non-fold terminals such as `drain` and
short-circuit predicates.

### BS-9 — async `mapPar` owns concurrency only for the benchmark type product

`Stream.AsyncMapPar.runFoldLongDirect` recognizes only stable `Int` input,
`Int` output, and a `Long` fold, then bypasses ordinary materialization through
the native Int/Long sink:

- direct dispatch: [`Stream.scala:4914-4945`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L4914-L4945)
- specialized concurrent fold: [`AsyncConcurrentReaders.scala:931-1169`](streams/shared/src/main/scala/zio/blocks/streams/internal/AsyncConcurrentReaders.scala#L931-L1169)

`ReadyIntLongFold` is not merely an unboxed value carrier. It owns source and
worker handoff, selector installation, cancellation/failure propagation,
pending callback continuation, cooperative yielding, and completion. Other
element and accumulator products therefore use a different concurrency path.
This exactly matches the cross-provider ready-effect cell at
[`StreamAsyncConcurrentBench.scala:123-136`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncConcurrentBench.scala#L123-L136)
and the provider-local parity cell at
[`StreamAsyncConcurrentParityBench.scala:20-44`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncConcurrentParityBench.scala#L20-L44).

**Generalization direction:** extract one lane-neutral concurrent fold state
machine that owns handoff, fairness, cleanup, and pending state. Generated or
handwritten physical adapters may retain unboxed slots, but must delegate to
the same lifecycle for all output and accumulator lanes.

### BS-10 — async fan-in pooling and direct folding exist only for `Int`/`Long`

On the JVM, `PlatformSpecific.createAsyncMergePool` creates a functional worker
pool only when the merged element lane is `Int`; every other lane receives a
no-op pool. The Int path also silently raises the requested batch size to at
least 1,024:

- pool selection: [`PlatformSpecific.scala:57-67`](streams/jvm/src/main/scala/zio/blocks/streams/PlatformSpecific.scala#L57-L67)
- Int-only bridge, array, and pooled reader: [`IntAsyncMergePool.scala:13-99`](streams/jvm/src/main/scala/zio/blocks/streams/internal/IntAsyncMergePool.scala#L13-L99)
- merge lifecycle and `Int`/`Long` terminal: [`AsyncConcurrentReaders.scala:1192-1282`](streams/shared/src/main/scala/zio/blocks/streams/internal/AsyncConcurrentReaders.scala#L1192-L1282)
- terminal dispatch: [`AsyncConcurrentReaders.scala:1533-1539`](streams/shared/src/main/scala/zio/blocks/streams/internal/AsyncConcurrentReaders.scala#L1533-L1539)

The pool accepts only an `AsyncToSyncReader[Int]`, fills `Array[Int]` batches,
and is consumed by `MergeIntLongFold`. As with BS-9, that terminal owns selector
and slot lifecycle rather than merely specializing representation. The exact
shape is exercised by the one-million-element `mergeAll` and `flatMapPar` rows
at [`StreamAsyncConcurrentBench.scala:123-136`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamAsyncConcurrentBench.scala#L123-L136).

**Generalization direction:** separate pooled asynchronous handoff from its
physical batch storage. Give every supported physical lane equivalent pool
eligibility and one shared fan-in lifecycle, then layer accumulator-specific
fold adapters over it. Preserve caller-visible buffer semantics rather than
hard-wiring a benchmark-derived minimum into only one lane.

### BS-11 — deep concat traversal is iterative only for `Int` into `Long`

`Concatenated.runFoldLongBlocking` traverses the concatenation tree with an
explicit pending stack only when the stream reports stable `Int` representation:

- direct traversal: [`Stream.scala:3716-3739`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L3716-L3739)
- matching 10,000-singleton fixture: [`StreamEvalBench.scala:345-348`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamEvalBench.scala#L345-L348)
- matching `Long` benchmark terminal: [`StreamEvalBench.scala:485-490`](streams-benchmark/src/main/scala/zio/blocks/streams/bench/StreamEvalBench.scala#L485-L490)

The generic compiler is stack-safe, so this is not a correctness defect. It is
still suspicious because the direct structural traversal—avoiding intermediate
concat readers—is selected by the exact element/accumulator product measured
by `zb_nested_concat`, rather than by concatenation structure itself.

**Generalization direction:** make iterative leaf traversal a property of
`Concatenated` independent of element and terminal lane. Each leaf can still use
its own primitive fold adapter, while tree flattening, failure propagation, and
left-to-right ordering remain shared.

## Paths reviewed and not considered benchmark-shaped

- `flatMapPar(1) => flatMap` and `mergeAll(1) => flatMap` at
  [`Stream.scala:267-276`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L267-L276) and
  [`Stream.scala:3088-3100`](streams/shared/src/main/scala/zio/blocks/streams/Stream.scala#L3088-L3100)
  are semantic identities with broad applicability. Their appearance in the
  parallelism-1 JMH cells does not make them suspect.
- Strict-source `drain` dispatch for ranges, vectors, and arbitrary iterables is
  terminal-specific but broadly useful: discarding without invoking a user
  callback is fundamentally simpler than folding. It should not be generalized
  into artificial work merely for symmetry.
- The generic `SyncInterpreter`/`AsyncInterpreter`, lane dispatch, and full
  primitive sink loops are reusable infrastructure. Internal casts are expected
  in this erased representation and are not evidence of benchmark coupling.
- `SingletonInt` and `IntMapped` alone are ordinary primitive specialization.
  They become suspicious only where higher-level fast paths require the exact
  singleton/map + `Long` terminal combinations described above.
- The post-BS-2 `IntLinearContext` has a 16-entry unrolled physical kernel plus
  an arbitrary-depth loop. The former exact ten-operation benchmark boundary is
  gone; fixed-size unrolling underneath a lane-general semantic owner does not
  reopen BS-2.
- The deep singleton-flatMap Int/Long adapter remains physical acceleration
  beneath the lane-general scalar-spine owner established by BS-5. It does not
  independently define traversal semantics.
- JVM primitive reader classes and lane dispatch are not suspicious by
  themselves. BS-9 and BS-10 are findings because terminal/type-specific
  classes own concurrency lifecycle, not because they use primitive arrays.

## Recommended order of remediation

1. Characterize BS-9 and BS-10 together. They share selector, cancellation,
   yielding, and terminal-fold concerns; a shared lane-neutral concurrent
   driver should prevent two new families of duplicated state machines.
2. Generalize BS-9 first because it has no pool/platform dimension. Establish
   the lifecycle protocol with asymmetric element/output/accumulator lanes.
3. Rebuild BS-10's fan-in terminal and JVM pool against that protocol. Separate
   batch storage specialization from scheduling and slot ownership.
4. Generalize BS-11 independently by moving iterative concat-tree traversal
   above terminal specialization.

For each generalization, benchmark asymmetric type combinations not present in
the cross-library suite—such as `Stream[Long] => Double`, `Stream[Double] =>
Long`, and reference elements with primitive accumulators—and verify equivalent
failure, cleanup, cancellation, early-termination, and cooperative-yield behavior
against the generic interpreter. That prevents “generalization” from becoming a
larger collection of unchecked benchmark-specific copies.
