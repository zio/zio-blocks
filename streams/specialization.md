# Complete Stream Specialization Plan

## Status and completion rule

This plan fixes every specialization and unchecked-variance issue found in the `streams` audit. The work packages below are in dependency order, not priority order: **every package is mandatory**. The work is complete only when every acceptance gate is green on Scala 2.13 and Scala 3, on JVM and Scala.js, and the allocation benchmarks demonstrate the required result.

The target covers all eight Scala/JVM value types:

| Lane | Exact scalar pull |
| --- | --- |
| `Boolean` | `readBoolean` |
| `Byte` | `readByte` |
| `Char` | `readChar` |
| `Short` | `readShort` |
| `Int` | `readInt` |
| `Long` | `readLong` or the collision-free `readLongs(..., length = 1)` protocol described below |
| `Float` | `readFloat` |
| `Double` | `readDouble` or the collision-free `readDoubles(..., length = 1)` protocol described below |

`readInt` is not an acceptable substitute for `Boolean`, `Byte`, `Char`, or `Short`. A generic `Reader.read` followed by a cast or unboxing is never an acceptable primitive pull.

## Scope

This plan covers:

- `Stream`, `Sink`, and `Pipeline`, including public constructors and operators;
- every sync and async `Reader`, source, adapter, wrapper, interpreter, buffer, and concurrent reader reachable through those APIs;
- JVM- and Scala.js-specific implementations;
- all 26 current `@uncheckedVariance` occurrences in the 23 affected `Stream` method groups;
- correctness, cancellation, close/finalizer, reset, error, and defect behavior while specialization changes are made;
- source-level dispatch tests, end-to-end tests, and allocation benchmarks.

It does not ban generic `Reader.read` for genuine reference lanes. It also does not pretend that values returned through `Async[A]`, erased Scala callbacks, `Either`, `Option`, or another generic result carrier can never be boxed. Such carrier-boundary boxing is distinct from the avoidable error addressed here: pulling an upstream primitive through generic `Reader.read`, or routing it through the wrong primitive lane.

## Non-negotiable invariants

1. **The reader owns the runtime representation.** Every materialized reader reports its actual element lane through `jvmType`. Every `Stream` node records whether its output is known, deliberately boxed, or late-bound independently of the stream's widened static type.
2. **Widening cannot erase a known source lane.** Widening `Stream[Nothing, Int]` to `Stream[Nothing, AnyVal]` and then filtering, tapping, dropping, taking, buffering, or otherwise preserving elements must still pull the source through `readInt`.
3. **Representation-changing operations use their result lane.** `map`, `collect`, `flatMap`, recovery, concatenation, zipping, unfolding, and similar operations may change representation. They must use output evidence or a conservative boxed lane where the possible outputs are genuinely heterogeneous; they must never reuse a source tag without proof that the representation is preserved.
4. **Every primitive uses its exact lane.** Boolean, Byte, Char, Short, Int, Long, Float, and Double each have an independently tested route. Sharing `readInt` for the four small primitive types is forbidden.
5. **EOF is out of band for full-domain primitives.** No internal algorithm may choose a `Long` or `Double` value as an EOF sentinel. Every `Long` bit pattern and every raw `Double` bit pattern, including all NaN payloads, must round-trip as data.
6. **Primitive-reachable generic pulls do not exist.** Generic `read` remains legal only on a reference/boxed lane. A primitive-tagged source must never reach it anywhere in a public `Stream`/`Sink`/`Pipeline` execution.
7. **Specialization adds no per-element allocation to synchronous primitive paths.** Reusable state may be allocated once per materialization. No array, box, option, tuple, or helper object may be allocated for each pulled element merely to distinguish data from EOF.
8. **Semantics do not drift.** Element order and cardinality, short-circuiting, backpressure, callback count, typed errors, defects, interruption, concurrent linearizability, upstream closure, finalizer order, and suppressed-cleanup failures remain unchanged.
9. **No unchecked variance remains.** The streams public API and implementation must contain zero `@uncheckedVariance`; variance is handled by sound signatures and runtime representation ownership, not annotations or equivalent unsafe casts hidden behind helpers.

## Architecture

### 1. Runtime output representation on `Stream`

Add a package-private output representation model to `Stream`, conceptually:

```scala
private[streams] sealed trait ElementRepresentation
private[streams] object ElementRepresentation {
  final case class Known(jvmType: JvmType) extends ElementRepresentation
  case object Boxed                            extends ElementRepresentation
  case object LateBound                        extends ElementRepresentation
}

private[streams] def elementRepresentation: ElementRepresentation
```

The three states are semantically distinct:

- `Known(jvmType)` means every materialization has that exact physical output lane;
- `Boxed` means every materialization deliberately normalizes output to the reference lane;
- `LateBound` means the physical lane is not known until a reader is materialized, after which preserving consumers dispatch from that reader's `jvmType`.

Every concrete node must implement one of these propagation rules:

- **preserving node:** propagate the source representation (`filter`, `distinct`, `drop`, `take`, `tapEach`, buffering, and other element-preserving stateful readers). For `LateBound`, materialize the source first and select the exact wrapper from `reader.jvmType`.
- **fixed result node:** map primitive `JvmType.Infer[B]` to `Known`, and its `AnyRef` fallback to `Boxed` (`map`, `collect`, `scan`, unfold/iterator/iterable sources, singleton/attempt sources, and other output-changing nodes).
- **late-bound synchronous source:** use `LateBound` for `Deferred`/`suspend`, synchronous `FromReader`, `FromAcquireRelease`, `FromResource`, and equivalent sources that can finish materialization before exposing the physical reader but can choose a different lane on each materialization. This includes the by-name `Reader.AsyncReader` overload backed by `StableAsyncSource`: although the resulting reader is asynchronous, its factory runs synchronously during materialization, so its physical `jvmType` can be adopted before exposure without adding evidence or boxing.
- **effect-deferred asynchronous source:** a source that exposes its outer reader before its effect has resolved cannot be `LateBound`, because `Reader.jvmType` must already be stable. `attemptAsync`, `fromAcquireReleaseAsync`, and `fromReaderAsync` require output evidence and normalize the eventual child into `Known(primitive)` or `Boxed` before emitting data. `AsyncSource` and any `StatefulReaderStream` path that receives an unresolved effect must encode and preserve that predetermined output representation; they may not start acquisition early merely to discover a tag. `StableAsyncSource` must retain distinct construction paths so its synchronous by-name reader factory stays `LateBound`, while any unresolved-effect path follows this predetermined rule.
- **combining/dynamic node:** require result evidence. Primitive result evidence gives `Known` and all branches must normalize to that exact lane; `AnyRef` result evidence gives `Boxed` and all primitive branches are explicitly boxed. This applies to concat, zip, `flatMap`/`flattenAll`, recovery, merge, unwrap, and other branch-switching nodes.

Some nodes rematerialize or switch a `LateBound` child after exposing a single output reader. That reader's `jvmType` cannot change during its lifetime. In particular, `Repeated` over `LateBound`, branch-switching recovery, concat, flatMap, and merge must normalize to a stable result representation before exposing the reader. If no precise primitive result evidence is available, the stable representation is `Boxed`; it is never a false primitive tag. `Repeated` may preserve `Known` and `Boxed`, but must box `LateBound` rematerializations unless a new API supplies a stable result contract.

Do not derive a preserving node's input lane from `JvmType.Infer[A]` at the call site. That loses specialization after covariant widening. Do not make `JvmType.Infer` variant: its invariance is what lets implicit resolution distinguish primitive evidence from the boxed fallback.

The compiler and materializers resolve `elementRepresentation` before creating a stable output reader. At runtime, adapters validate or dispatch from `reader.jvmType`; a statically widened type is not authority to reinterpret a reader's physical lane. No reader may advertise a tag that disagrees with the method family through which its data is physically available.

### 2. Representation rules for `Pipeline` and `Sink`

`Pipeline[-In, +Out]` must not carry call-site evidence for a contravariant input merely to select its input pull. When a pipeline is applied, the input `Stream.elementRepresentation`/materialized `Reader.jvmType` is authoritative:

- preserving pipelines propagate the input tag;
- output-changing pipelines retain only `JvmType.Infer[Out]`;
- composition passes the first pipeline's output representation into the second.

This allows `Pipeline.identity`, `filter`, `drop`, `take`, and `buffer` to preserve a primitive lane without `Infer[In]` and without variance cheats. `map`, `collect`, and their async forms keep output evidence only.

`Sink[+E, -A, +Z]` discovers its direct input lane from the reader passed to `drain`. Public generic sinks dispatch once per drain on `reader.jvmType`, then enter a lane-specific loop. They do not demand or store invariant evidence directly for contravariant `A`.

Transforming sinks need an explicit result representation for the value produced by their contramap callback. Change `contramap` and `contramapAsync` to introduce a method type bounded by the sink input, conceptually `A0 <: A`, and request invariant `Infer[A0]`; the callback is `A2 => A0`. This is variance-sound and lets the protected mapped reader advertise an exact output lane. A primitive result uses its exact lane; an `AnyRef` fallback is deliberately boxed. Result-preserving Sink combinators carry this expectation through unchanged. Compile tests must establish usable inference on both Scala versions.

### 3. Sound public signatures instead of `@uncheckedVariance`

Replace the 23 annotated `Stream` method groups as follows:

- **Element-preserving methods** (`distinct`, `distinctBy`, `distinctByAsync`, `filter`, `filterAsync`, `tapEach`, `tapEachAsync`, and input sides of `collect`, `collectAsync`, `flatMap`, `flatMapPar`, `map`, `mapAccum`, `mapAccumAsync`, `mapAsync`, `mapPar`, and `mapParAsync`) stop requesting `Infer[A @uncheckedVariance]`. Their nodes use the source representation/materialized reader tag for input and retain evidence only for a new output type.
- **Recovery methods** remove the redundant preserving overloads of `catchAll` and `catchDefect`. Keep one widening form with `A1 >: A` and `Infer[A1]`. This is essential: after a stream has been widened, a recovery branch can legally change from (for example) `Int` to `Double`, so blindly preserving the original lane would be unsound.
- **Concatenation methods** (`++`, `concat`) introduce an explicit method type `A0 >: A`, request `Concat` evidence for `A0`, and request `Infer[A3]` for the associated output. They safely widen `this` at the construction boundary and normalize both children to the result representation. Do not make `Concat` variant: it has output/associated-type responsibilities that require its current invariant relationship.
- **Tuple zip `&&`** similarly introduces `A0 >: A`, requests `Tuples.Tuples[A0, B]`, and requests `Infer[C]`. `leftUnit`/`rightUnit` tuple identities can produce primitive `C`, so blanket `AnyRef` output is not acceptable. Do not make `Tuples` variant because it also supports reverse separation through its associated output type.
- **Widening output operators** such as `intersperse[A1 >: A]` request `Infer[A1]`; they are not representation-preserving merely because they retain source elements.

Scala 2.13 and Scala 3 compile-only API tests must cover ordinary inference, explicit widening, aliases, symbolic methods, and bottom types before the old overloads or annotations are deleted. They assert exact inferred result types, not merely successful compilation, for custom `Concat`, Unit tuple identities, unions/LUBs, `Nothing`, aliases, and explicit widening. Where a compatibility overload is impossible without recreating unsoundness, record the source change in the migration notes and provide the mechanically equivalent widening call.

### 4. Collision-free scalar pulling

The existing widened scalar return types provide out-of-domain EOF values for Boolean/Byte/Char/Short/Int/Float, but not for Long or Double. Therefore:

- keep the existing scalar methods for compatibility and for places where a caller can prove a sentinel is outside its data domain;
- prohibit internal general-purpose algorithms from using `readLong(sentinel)` or `readDouble(sentinel)` to detect EOF;
- make `readLongs` and `readDoubles` the authoritative full-domain operations and use `readLongs(scratch, 0, 1)` / `readDoubles(scratch, 0, 1)` as the canonical collision-free single-element protocol, where the returned count is the out-of-band status;
- allocate the one-element primitive scratch array once in the owning sink/reader/materialization, or consume in larger primitive batches where practical; never allocate it in the pull loop;
- require every Long/Double wrapper to override the bulk operation; a scalar compatibility method may adapt from reusable per-reader scratch, but the bulk default may not call generic `read` and cast;
- apply the same rule to async readers. `Async[Int]` may box its result in the generic async carrier, but the upstream data is never routed through generic `read` and the scratch storage remains primitive.

Long and Double tests must include the exact values formerly used as sentinels and compare floating-point data by raw bits. Bulk tests cover zero length without consumption or segment transition, legal count domains, partial reads, length-one reads, repeated EOF, reset, and compositions of wrappers.

### 5. Central exact-lane dispatch

Introduce the smallest package-private dispatch utilities that remove duplicate error-prone switches. Separate sync and async variants are acceptable, but each must have eight explicit primitive branches plus one reference branch. The helper must not return a generic per-element value to a primitive caller; instead, it selects a lane-specific loop/reader once per materialization. Long and Double loops use the collision-free protocol above.

The helpers may own reusable scratch arrays and chunk builders. They may not become a generic wrapper that boxes primitives, erase exact small-primitive lanes, hide unchecked variance, or alter ownership/close behavior.

## Mandatory implementation work packages

### A. Contract and representation foundation

1. Add and document `ElementRepresentation` and `Stream.elementRepresentation`; update every concrete `Stream` node to one of the three representation rules, including normalization for every late-bound branch/rematerialization boundary.
2. Keep `JvmType.Infer[A]` invariant. Add package-private construction helpers only where they prevent duplicated lane switches without erasing primitive types.
3. Document `Reader.jvmType` as the physical pull contract, not a hint.
4. Define the exact eight-lane dispatch utilities and the Long/Double collision-free convention.
5. Add compile-only tests for the proposed variance-free signatures before migrating callers.

### B. Reader base contracts, generic helpers, and sources

1. Replace primitive defaults in both `Reader.SyncReader` and `Reader.AsyncReader` that call generic `read` and cast. Missing primitive exact/bulk implementations must fail loudly rather than silently box. Primitive-tagged implementations must supply exact methods; reference implementations may retain generic behavior only when `jvmType == AnyRef`.
2. Rewrite `readAll`, `readUpToN`, `readN`, `skip`, and `Reader.skipViaSentinel` to dispatch across all eight lanes. Long and Double use reusable primitive arrays/count status. `readAll` is independently tested rather than assumed to inherit the bounded-read implementation.
3. Complete exact scalar support in `FromChunk` for Boolean, Byte, Char, Short, Int, Long, Float, and Double. Retain specialized bulk paths where available.
4. Give `FromIterable`, iterator sources, `Unfold`, `UnfoldAsync`, `Stream.succeed`, `attempt`, `attemptAsync`, `fromAcquireReleaseAsync`, `fromReaderAsync`, `fromIterable`, `fromIterator`, `fromIteratorAsync`, `unfold`, and `unfoldAsync` output evidence and tagged readers. Deferred async sources normalize their eventual value/reader to the predetermined representation without eagerly starting the effect. Do not leave generic-source shortcuts for primitive instantiations.
5. Fix `CharReader.skip` to use `readChar`, not generic `read` or a different primitive route.
6. Audit `FromRange`, input/character stream readers, singleton readers, repeated readers, limit/skip readers, and all source factories for exact lane forwarding and collision-free EOF.
7. Change `Stream.succeed(Byte)` to return `Stream[Nothing, Byte]`, and change `Reader.singleByte` to expose the Byte lane. Document this intentional source/API correction and update callers that genuinely wanted an unsigned `Int` conversion to map explicitly.

### C. Reader wrappers and transforms

Implement all eight exact scalar paths, plus valid bulk forwarding, in:

- `SyncToAsyncReader`, async concatenation, synchronous `ConcatReader`, borrowed/release readers, and `DelegatingReader`;
- filtered, async-filtered, collected, mapped, async-mapped, flat-mapped, and parallel mapped reader families;
- repeated/async-repeated, skip/limit, take/take-while, singleton, and stateful wrappers;
- active error/recovery wrappers, specifically `ErrorMappedReader` and the current catch/recovery readers.

The dormant `CatchAllReader` and `CatchDefectReader` are not accepted as evidence that active recovery is fixed. Delete them if they are unreachable after confirming no reflection/API dependency; otherwise test and specialize them as well.

For each wrapper, choose the branch once from input/output tags and preserve close/reset/error state. A wrapper may box at an erased user callback boundary when the callback's static type requires it, but it must pull the upstream primitive through its exact reader method and emit through its exact output method.

### D. Stream-owned materializers and operators

Complete exact lane handling in every reader created directly by `Stream.scala`, including:

- `chunked`/`grouped` and `sliding` (all eight primitive builders, not only Byte/Int/Long/Float/Double);
- `intersperse` sync and async stateful implementations;
- `scan` and `scanAsync`, with all eight output states and exact input dispatch;
- zip/tuple readers and concatenation;
- `catchAll`, `catchDefect`, and active error mapping/recovery;
- buffering, merge, `flatMap`, `flattenAll`, `flatMapPar`, repeated, take/drop/takeWhile, and suspend/unwrap/resource materialization paths;
- `Deferred`, `FromReader`, `FromAcquireRelease`, and `FromResource` late-bound paths, including stable normalization when a child can change lanes after the outer reader is exposed;
- `AsyncSource`, both construction paths through `StableAsyncSource`, and `StatefulReaderStream`, including the distinction between a synchronously created late-bound async reader and any reader exposed before an async child resolves, which has a predetermined known/boxed representation and normalizes that child without eager acquisition;
- `attempt`/`attemptAsync`, iterators, iterables, unfold, `intersperse`, and every constructor currently forcing `AnyRef` despite a statically precise primitive result.

Every anonymous `SyncReader`/`AsyncReader` in `Stream.scala` must appear in the conformance matrix. No anonymous generic reader may be assumed reference-only merely because its declared type parameter is generic.

### E. Sink specialization

1. Replace generic helper loops in `Sink.scala` with one-time `reader.jvmType` dispatch and eight exact loops.
2. Cover every public sink that pulls input: collection, count/drain, folds (sync and async), foreach, predicates (`exists`, `forall`, `find`), head/last/take, writers/output streams, all numeric sums, and platform sinks.
3. Fix `fromJavaWriter`'s Char path and the audited Double/Float/Int/Long generic fold loops; add Boolean/Byte/Char/Short branches wherever generic sinks can consume them.
4. Use specialized chunk builders for every primitive. If the chunk module lacks a specialized builder for Boolean, Char, or Short, add the required builder there rather than silently accepting boxed accumulation; follow the chunk downstream verification rules.
5. Preserve sink short-circuiting and reader ownership exactly. The protected readers used by sink adapters must forward all eight exact methods and close behavior.
6. Update JVM `NioSinks` and any Scala.js/browser sink adapter so primitive-tagged readers never feed boxed `value.asInstanceOf[Primitive]` loops.
7. Implement variance-sound `contramap`/`contramapAsync` output evidence and remove the current hard-coded `AnyRef` mapped-reader output; test exact inferred types and all eight output lanes.

### F. Pipeline specialization

1. Make preserving pipelines propagate source representation without `Infer[In]`.
2. Keep output evidence for transforming pipelines and pass it into Stream nodes.
3. Ensure `applyToStream`, `applyToSink`, `RunViaSink`, composed pipelines, and synthetic `fromReader` streams preserve the physical reader tag.
4. Test every public pipeline both when applied to a stream and when precomposed with a sink, for all eight primitives and widened source types.

Pipeline currently owns few direct pulls; that does not exempt it. Its materialization routes through precisely the Stream and Sink paths being repaired and must prove that it does not erase their tags.

### G. Interpreters, async boundaries, concurrency, and platforms

1. Make `SyncInterpreter`, `AsyncInterpreter`, `AsyncStatefulReader`, `AsyncConcurrentReaders`, and `AsyncInterpreter.invokeRead` dispatch all eight exact methods. The async interpreter must preserve its collision-safe completion protocol; do not replace it with value sentinels.
2. Split exact physical pull identity from the interpreter's five internal storage lanes. Extend `OpTag`/reader metadata so read operations distinguish Boolean, Byte, Char, Short, Int, Long, Float, Double, and reference even though Boolean/Byte/Char/Short/Int may share Int storage after the pull. `laneOf` may continue selecting shared storage, but it must not select the source reader method. Cover `InternalVersionSpecific` callback adaptation and bridging logic.
3. Fix shared `SyncBufferedReader` and JVM `ConcurrentBufferedReader`, map-par readers, merge readers, `AsyncToSyncReader`, ByteBuffer/channel/NIO readers, and every primitive-specialized concurrent reader. Generic concurrent implementations dispatch all eight lanes when their runtime tag is primitive.
4. Fix Scala.js `SyncBufferedReader`, platform-specific adapters, browser `ReadableStream` readers, JS anonymous merge/map readers, and JS-created anonymous readers. JavaScript's runtime representation does not waive the public exact-lane contract.
5. Preserve the already tested no-overlap, no-post-close-touch, FIFO/linearizability, cancellation, reset, and failure replay properties. Never work around an `Async` bug in a reader; reduce and fix the underlying `Async` defect if a failing test demonstrates one.

### H. Remove unchecked variance completely

1. Apply the signature changes in the architecture section to all 26 annotations in the 23 method groups.
2. Remove the import of `scala.annotation.unchecked.uncheckedVariance` when the count reaches zero.
3. Search for equivalent new cheats: evidence casts, `asInstanceOf[JvmType.Infer[_]]`, locally redefined unsafe evidence, or hidden annotation aliases. Any such occurrence fails the gate.
4. Add widened-stream behavior and dispatch tests that would fail if specialization were again selected from call-site `Infer[A]`.

### I. Exhaustive conformance and regression tests

Build a reusable reader-probe test harness with these properties:

- one probe per primitive advertises that exact `jvmType`;
- its matching exact read method returns data and records calls;
- generic `read` throws immediately;
- every nonmatching primitive method throws immediately;
- Long/Double probes expose collision-safe bulk status and fail sentinel-dependent consumers;
- sync and async variants support close/cancel/failure assertions.

Use the harness to produce a generated/table-driven matrix over:

1. every Reader source and factory;
2. every wrapper/adapter in packages B and C;
3. every public Stream operator that can pull an input;
4. every public Sink that can pull an input;
5. every public Pipeline, both application routes;
6. sync and async interpreters;
7. buffers, concurrent map/merge readers, and JVM/JS platform readers.

Check in a symbol-level inventory alongside the harness (for example `SpecializationConformanceInventory.scala`) whose entries name the production symbol, expected input/output representation rule, sync/async applicability, platform, and test case IDs. The inventory is the completeness source of truth. Its reconciliation test is **bidirectional**: it fails both when an inventory entry lacks a test and when a production concrete/anonymous Reader, Reader factory, exact-read method, or primitive-dispatch site lacks an inventory entry. Implement the production-to-inventory side with a checked-in deterministic source/compiled-symbol scanner and stable IDs adjacent to otherwise anonymous reader allocations; do not rely on a human remembering to update the table. It must explicitly include:

- Reader bases/defaults, `readAll`, `readN`, `readUpToN`, skip helpers, `FromChunk` variants, `FromIterable`, range/input/char/iterator/unfold sources, singleton readers, `SkipLimitReader`, repeated readers, concat/delegating/borrowed/release/lifecycle wrappers, all filtered/mapped/collected/flat-mapped families, and zip readers;
- every anonymous reader in Stream, Sink, Pipeline, and platform code;
- `AsyncSource`, `StableAsyncSource`, `StatefulReaderStream`, `OpTag`, exact-read metadata, `SyncInterpreter`, `AsyncInterpreter`, `AsyncInterpreter.invokeRead`, `InternalVersionSpecific`, `AsyncStatefulReader`, and async concurrent readers;
- shared/JVM/JS buffered readers, `AsyncToSyncReader`, ByteBuffer, NIO, channel, browser readers, JS anonymous merge/map readers, and every generic or primitive concurrent map/merge family.

Source tests call the source reader's exact output method and verify data/EOF behavior. Public generic `read` must continue to work, so source dispatch direction is proved separately: a checked-in static call-site gate over shared/JVM/JS sources, plus JVM compiled-bytecode inspection, rejects any exact primitive method whose implementation invokes generic `Reader.read`; it also rejects generic-read/cast sequences in primitive dispatch helpers. Generic `read` may delegate outward to an exact method and box for its public compatibility contract, but an exact method may never delegate inward to generic `read`. Throwing injected upstream probes remain mandatory for wrappers. Interpreter probes separately record the exact physical pull method and the internal storage lane, proving that shared Int storage never causes a wrong source pull.

The matrix tests values and boundaries, not only method counts:

| Type | Required data |
| --- | --- |
| Boolean | `false`, `true` |
| Byte | minimum, `-1`, zero, maximum |
| Char | `\u0000`, ASCII, surrogate/code-unit boundary values, `\uffff` |
| Short | minimum, `-1`, zero, maximum |
| Int | minimum, `-1`, zero, maximum |
| Long | minimum, `-1`, zero, maximum, every formerly used EOF sentinel |
| Float | raw NaN payloads representable as Float, negative/positive zero, infinities, minimum finite, maximum finite |
| Double | multiple raw NaN payloads, negative/positive zero, infinities, minimum finite, maximum finite, every formerly used EOF sentinel bit pattern |

Add explicit tests for empty input; one element; exact/fewer/more-than batch size; repeated EOF; skip past EOF; short-circuiting; callback exception; typed failure; defect; cancellation while suspended; close before/after EOF; finalizer failure; reset/reuse where supported; and widened or heterogeneous streams (`Int` widened to `AnyVal`, `Int ++ Double`, and recovery from an Int source to a Double fallback).

Do not assert only final values. Assert that the exact reader method was used, generic and wrong-lane methods were not used, callback count is exact, and close/cancellation semantics are unchanged.

### J. Allocation and performance proof

Extend `streams-benchmark` with all-eight-primitive sync and async dispatch benchmarks, or add equivalent cases to its existing allocation campaign. For each primitive benchmark:

- source -> preserving operator -> transforming operator -> sink;
- Stream, Pipeline-applied-to-Stream, and Pipeline-applied-to-Sink routes;
- widened preserving route;
- empty, small, and large element counts sufficient to distinguish fixed materialization cost from per-element slope.

Run baseline and candidate with JMH `gc` profiling. Acceptance is:

- no per-element allocation slope for synchronous primitive pulling and primitive-preserving stages;
- no additional upstream boxing/allocation in asynchronous paths beyond the documented `Async`/callback result carrier cost;
- no throughput regression outside measurement noise after repeated forks;
- no performance result obtained by weakening correctness, batching away short-circuit semantics, or changing cancellation/close behavior.

Store the benchmark commands, raw logs, environment, and comparison table with the implementation review. Treat a regression as a failed implementation phase, not as documentation for later work.

### K. Documentation and cleanup

1. Update complete Scaladoc for affected Reader, Stream, Sink, and Pipeline APIs, including representation evidence, widening behavior, and Long/Double EOF constraints.
2. Add migration notes for any source signature/overload change.
3. Delete superseded generic primitive helpers and dormant classes only after reference searches and tests prove they are unused.
4. Re-run the original cast/generic-read/unchecked-variance audit and classify every remaining generic read as reference-only or every remaining primitive cast as callback/output representation work rather than an upstream pull.

## Audit-to-work traceability

| Audited root | Classification | Mandatory repair | Proof |
| --- | --- | --- | --- |
| `SyncReader` primitive defaults and generic `readAll`/`readN`/`readUpToN`/`skipViaSentinel` | DBG, MPS | B1-B2; exact dispatch and collision-free Long/Double status | Independent all-eight helper tests, extrema and EOF tests |
| `AsyncReader` primitive defaults and bulk defaults | DBG, MPS, UACB | B1-B2; exact upstream methods; accept only carrier boxing | Async probe matrix plus allocation comparison |
| `FromChunk` missing Boolean/Char/Short and source factories (`FromIterable`, iterators, unfold) | MPS, CNE8 | B3-B6; tag constructors and implement all eight exact routes | Source matrix for all types and boundaries |
| `CharReader.skip` | DBG/MPS | B5 | Generic and wrong-lane methods throw |
| Sync/async conversion, concat, delegation, borrowed/release | DBG, MPS | C | Adapter matrix plus close/failure replay tests |
| Filtered/Collected/Mapped/FlatMapped families | DBG, MPS | C and D | Every transform x eight lanes x sync/async |
| Repeated/AsyncRepeated/SkipLimit/TakenWhile/Singleton | DBG, MPS | C | Stateful boundary and repeated-EOF tests |
| `Stream.intersperse`, `scan`, `sliding`, `chunked` | DBG, MPS, CNE8 | D | Operator matrix, exact builders, values by raw bits |
| Late-bound `Deferred`, `FromReader`, acquire/release/resource and repeated/branching rematerialization | tag soundness | A, D | Per-materialization lane changes and stable normalization tests |
| Async `AsyncSource`, `StableAsyncSource`, `StatefulReaderStream`, `attemptAsync`, async acquire/release and async reader source | late-bound vs tag-unavailable-at-exposure distinction | A, B, D | Synchronous reader factory adopts late-bound physical tag; unresolved effect does not start eagerly and uses predetermined exact/boxed normalization |
| `attemptAsync`, `flattenAll`, widening `intersperse`, `succeed(Byte)`, `Reader.singleByte` | boxed/wrong output lane | B, D | Exact result-type and source-reader tests plus migration cases |
| Zip/concat and active `ErrorMappedReader`/recovery readers | DBG, MPS | C-D | Heterogeneous/widened and failure-switch tests |
| Generic Sink helpers and public sinks, including writer and numeric folds | DBG, MPS | E | Sink matrix, short-circuit and ownership assertions |
| Sink `contramap`/`contramapAsync` mapped-reader output | hard-coded boxed lane, variance risk | E, H | Exact output evidence/inference and all-lane protected-reader tests |
| Pipeline application/materialization | indirect specialization loss | F | Both application routes for every pipeline/type |
| `OpTag`, `laneOf`, `InternalVersionSpecific`, interpreter read/invoke and async state/concurrent readers | exact-pull identity collapsed into storage lane | G | Separate physical-pull and storage-lane probes, collision and concurrency tests |
| Shared/JVM/JS buffered and platform readers | DBG, MPS, CNE8 | G | Platform-specific matrix on JVM and JS |
| 26 `@uncheckedVariance` uses in 23 Stream method groups | UVSR | A, H | zero-count static gate plus compile/widening tests |
| Legitimate generic reference reads | LRGR | retain and classify in K4 | Reference-lane tests and final audit table |
| Boxing forced by `Async` or erased generic result carriers | UACB | do not disguise; avoid additional upstream boxing | Allocation benchmark and code inspection |

## Implementation and verification sequence

Each numbered batch includes its code, tests, fast-loop run, and correction before the next batch begins. This order controls dependency and review size; it does not make later batches optional.

1. A: representation contract, exact dispatch design, compile-only API tests.
2. B: Reader bases, helpers, and sources.
3. C: Reader wrappers and transforms.
4. D: Stream-owned materializers/operators.
5. E: Sink specialization.
6. F: Pipeline propagation and materialization.
7. G: interpreters, concurrency, JVM, and JS.
8. H: remove all unchecked variance and compatibility scaffolding.
9. I: close every conformance-matrix cell and lifecycle regression.
10. J: benchmark baseline/candidate and eliminate regressions.
11. K: documentation, dead-code deletion, final audit, and formatting.

If chunk-specialized builders must be added, treat that edit as part of batch E and run the required chunk, schema, and benchmark downstream verification before continuing.

## Acceptance gates

### Static and API gates

- `rg '@uncheckedVariance' streams/{shared,jvm,js}/src` returns no matches.
- No `asInstanceOf[Boolean|Byte|Char|Short|Int|Long|Float|Double]` is fed by generic `Reader.read`; remaining primitive casts have an explicit audit classification and are not reader-pull cheats.
- No primitive-tagged branch invokes generic `read` or a nonmatching primitive method.
- No internal Long/Double EOF detection compares a data value with a sentinel.
- Bidirectional reconciliation proves that every concrete/anonymous Reader, Reader factory, exact-read method, primitive dispatch site, and public Stream/Sink/Pipeline pull route has a checked-in conformance-inventory row linked to an executing test, and that no inventory row is stale.
- Static shared/JVM/JS call-site inspection and JVM bytecode inspection find no exact primitive method that invokes generic `Reader.read` and no generic-read/cast primitive dispatch sequence.
- Scala 2.13 and Scala 3 API compile tests demonstrate source inference for symbolic methods, widening, bottom types, recovery, and composition.
- Every exposed reader has a stable `jvmType` for its lifetime; late-bound children are resolved before dispatch or normalized at branch/rematerialization boundaries.

### Behavioral gates

- All eight exact-lane probe suites pass for sync and async execution on JVM and JS.
- All extrema, sentinel-collision, NaN raw-bit, signed-zero, batch-boundary, and repeated-EOF tests pass.
- Long/Double bulk tests cover zero-length no-op semantics, legal counts, partial reads, length one, repeated EOF, reset, and wrapper composition.
- Existing and new cancellation, close, finalizer, reset, typed-error, defect, cleanup-replay, and concurrent linearizability tests pass without retries or relaxed timing.
- The full original behavior suites for Stream, Sink, Pipeline, Reader, interpreters, platform adapters, and async/concurrent readers pass.

### Performance gates

- JMH baseline/candidate evidence meets package J's allocation and throughput requirements for all eight primitives.
- Primitive-preserving execution remains specialized after static widening.
- Async carrier boxing is measured and documented separately; no avoidable upstream reader boxing is attributed to it.

### Required verification commands

Use the repository's logged `sbt --client` command template and explicit Scala version on every invocation. Discover the exact Scala 2.13 version first; at the time of this plan it is 2.13.18.

1. Fast loop after each batch: `++3.8.3; streamsJVM/test` (or the narrowest named streams test suite while iterating, followed by `streamsJVM/test`).
2. Coverage after the fast loop is green: `++3.8.3; project streamsJVM; coverage; test; coverageReport`. Coverage must remain 100% for the async streams target and cover every new branch.
3. Cross-Scala: `++2.13.18; streamsJVM/test`.
4. Cross-platform: `++3.8.3; streamsJS/test; ++2.13.18; streamsJS/test`.
5. Downstream projects identified from `dependsOn` in `build.sbt`, including streams examples and benchmarks, plus chunk/schema dependents if chunk builders change.
6. JMH baseline and candidate commands for package J, with `-prof gc`, repeated forks, and retained raw logs.
7. Final format only after all verification is green: `++3.8.3; project streamsJVM; fmtDirty`, then any required `scalafmtSbt` for edited build files. Do not retest solely because formatting ran.
8. Final static searches, conformance-matrix completeness check, `git diff --check`, and review of the complete diff.

## Review record

The plan is not approved until two distinct Oracle reviews return consecutive thumbs-up verdicts:

1. a design and audit-completeness challenge;
2. an execution-readiness and verification challenge.

Any non-approval resets the consecutive count after the plan is corrected.
