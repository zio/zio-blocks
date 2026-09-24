# Benchmark Fairness Arbitration and Required Improvements

## Executive verdict

**The required P0/P1 benchmark changes are implemented.** The revision:

1. Updates Kyo to 1.0.0-RC6 and restores bounded unordered `mapPar`; Kyo remains N/A for bounded dynamic fan-in.
2. Replaces Kyo source-level `Async.defer` with a ready-value effectful map.
3. Keeps `StreamAsyncConcurrentBench` a source-axis counterpart by using ZIO Blocks native `mapPar`, not `mapParAsync`.
4. Isolates Pekko runtime and provider-specific prebuilt graphs from non-Pekko trials.

The current high-level **one-ready-effect-per-element** suite is defensible after the Kyo fix. It does not need further splitting before publication, provided it is reported narrowly and not called generic “async stream performance.” Fixed-chunk pulls, buffered producer/consumer boundaries, and genuine suspension are separate future families.

Current ZIO Blocks suspension and low-level reader benchmarks may remain provider-local. A multi-provider suspension suite is not required if reporting strictly excludes those classes from provider rankings.

## Scope and evidence standard

The authoritative object reviewed was the current git diff and all benchmark sources under `streams-benchmark/src/main/scala/zio/blocks/streams/bench`. The unrelated `Reader.scala` modification is outside this ruling.

Pinned providers are:

| Provider | Version |
|---|---:|
| FS2 | 3.14.0 |
| Apache Pekko Streams | 1.7.0 |
| Kyo | 1.0.0-RC6 |
| Ox | 1.0.6 |
| ZIO Blocks Streams | Local project at the benchmarked commit |

Provider judgments use only public APIs and documentation cited by the ten advocate reports, notably:

- [FS2 3.14.0 API](https://javadoc.io/doc/co.fs2/fs2-core_3/3.14.0/fs2/Stream.html)
- [Kyo 1.0.0-RC6 stream API](https://javadoc.io/static/io.getkyo/kyo-core_3/1.0.0-RC6/kyo/StreamCoreExtensions$.html)
- [Pekko `concatLazy`](https://pekko.apache.org/docs/pekko/1.3/stream/operators/Source-or-Flow/concatLazy.html)
- [Ox 1.0.6 Flow guide](https://ox.softwaremill.com/v1.0.6/streaming/flows.html)
- [ZIO Blocks Stream guide](https://github.com/zio/zio-blocks/blob/main/docs/reference/streams/stream.md)
- [ZIO Blocks Reader guide](https://github.com/zio/zio-blocks/blob/main/docs/reference/streams/reader.md)

No provider implementation is relied upon.

## Contract taxonomy

These contracts must remain separate in code and reporting.

| Contract | Definition | Current disposition |
|---|---|---|
| **Native no-effect source** | Ordinary high-level range or collection source, without a synthetic effect or explicit buffer | Existing synchronous suites |
| **One ready effect per element** | One effectful transformation invocation per source element, returning an already-successful value without external completion or waiting | Current `StreamAsyncEvalBench`, `StreamAsyncPipelineBench`, `StreamAsyncSetupBench`, and source axis of `StreamAsyncConcurrentBench` |
| **Native bounded unordered map** | Provider’s documented operator bounding simultaneous unordered transformations; callback representation follows the native public API | `StreamConcurrentBench` and the map cell in `StreamAsyncConcurrentBench` |
| **Bounded fan-in** | Dynamically admit and replenish at most `parallelism` active inner streams | Existing `mergeAll` and `flatMapPar` cells; Kyo is N/A because `collectAll` and binary `merge` do not expose this contract |
| **Fixed-chunk/effectful pull** | Explicit common chunk size and pull count | Deferred future family |
| **Buffered producer/consumer boundary** | Explicit boundary and fixed capacity allowing upstream/downstream overlap | Deferred future family; Ox `.buffer()` belongs here |
| **Nonblocking pending completion** | Callback/effect/Future is incomplete when returned and completes later without occupying the stream callback thread | Deferred cross-provider family |
| **Blocking direct callback** | Mapping callback waits synchronously for external release | Separate future family; not interchangeable with nonblocking suspension |
| **Provider-local diagnostics** | Low-level readers, primitive lanes, runtime resume, cooperative yielding, adapter construction | Excluded from provider rankings |

### Ready-effect provider shapes

The accepted high-level ready-effect matrix is:

| Provider | Public shape |
|---|---|
| ZIO Blocks | `Stream.range(...).mapAsync(i => Async.succeed(i))` |
| FS2 | `Stream.range(...).covary[IO].evalMap(IO.pure)` |
| Kyo | `Stream.range(...).map(i => (i: Int < Async))` |
| Pekko | `Source(range).mapAsync(1)(i => Future.successful(i))` |
| Ox | N/A |

This contract compares public representations, not allocations or internal interpreter steps. It must be described as a **ready-effect stage**, not an asynchronous thread boundary or genuine suspension.

## Proposal-by-proposal decisions

| Proposal or rebuttal | Decision | Rationale and action |
|---|---|---|
| **ZB R1:** use `mapParAsync` in the comparative async `mapPar` cell | **Rejected** | `StreamAsyncConcurrentBench` is now a ready-**source** counterpart. To isolate that source axis, preserve each provider’s native operator from `StreamConcurrentBench`. Use ZB `mapPar`; reserve `mapParAsync` for a separately contracted effect-returning or pending-callback family. |
| **ZB R2:** replace the bespoke reader in comparative suites with high-level `range.mapAsync` | **Accepted — already resolved** | `readyEffectSource` is now used by comparative ZB cells. The custom reader may remain provider-local. |
| **FS2 F1:** diagnose bulk-reader versus `evalMap(IO.pure)` mismatch | **Accepted — already resolved in design** | The comparative ZB source is no longer the low-level reader. |
| **FS2 F1 remedy:** remove only FS2 `evalMap(IO.pure)` | **Rejected** | That would change FS2 alone to a no-effect source inside the ready-effect family. Retain it there; FS2’s pure source is already represented in native suites. |
| Split the ready-effect suite further before publication | **Rejected as a current requirement** | Native no-effect and ready-effect suites are already distinct. Chunk, buffer, and suspension families are deferred rather than prerequisites. |
| **ZB R3:** Ox N/A for one-ready-effect-per-element | **Accepted — already resolved** | Ox is absent from measured ready-effect parameters. Its direct-style API must not be forced into an invented effect wrapper. |
| **Ox 1:** use `.buffer()` as Ox’s ready-effect equivalent | **Rejected** | Ox documents `.buffer()` as producer/consumer concurrency with buffering, not one completed effect per element. |
| **Ox 2:** do not add `.buffer()` before native concurrent operators | **Accepted — already resolved** | Ox native concurrent cells correctly retain plain `fromIterable`; `mapPar`/`flattenPar` introduce their own documented concurrency. |
| **ZB R4:** split or relabel async setup | **Accepted — mostly already resolved** | Native construction and ready-effect construction are separate classes. After replacing Kyo `defer`, `StreamAsyncSetupBench` is defensible as construction of a graph containing one ready-effect source stage. |
| **Kyo RF1:** add `mapParUnordered` | **Accepted — implemented** | RC6 publicly exposes `mapParUnordered(parallel, bufferSize)`. Kyo now participates in `mapPar` in both concurrent families and correctness coverage. |
| **Kyo RF3:** keep Kyo N/A for bounded dynamic fan-in | **Accepted — implemented** | Public `collectAll` merges an already-built sequence and binary `merge` combines two streams; neither dynamically admits and replenishes up to `parallelism` inner streams. A fixed-striped composition changes the workload and must not occupy the `mergeAll` or `flatMapPar` cells. |
| Kyo buffer policy | **Accepted with fixed policy for `mapPar`** | Use a preregistered `kyoBufferSize = 16`, held constant across parallelism and workload. Report it separately from `parallelism`; do not tune it after viewing results. |
| **Kyo RF2:** replace source-level `Async.defer` | **Accepted — implemented** | `Async.defer` suspends computation and is not an already-ready source value. The ready-effect suites now use native `Stream.range` plus a compile-checked typed ready value. |
| **Pekko RF1:** replace sequential concat cells with `concatLazy` | **Accepted — already resolved** | This arbitration defines concat as exhausting the left input before demanding the right. Public Pekko docs state that ordinary `concat` eagerly pulls both inputs through detachment buffers; `concatLazy` matches the selected contract. |
| **Ox rebuttal:** retain Pekko native detached `concat` | **Rejected** | The matrix compares the declared sequential-demand semantic contract, not method-name identity. Detached concat may be a separate provider-native benchmark. |
| **Ox 3:** isolate Pekko runtime | **Accepted — implemented** | Pekko runtime state is created only for Pekko trials, and `StreamEvalBench` builds only the selected benchmark provider's graphs. |
| **ZB R6:** remove heavy fan-in rows | **Rejected** | Heavy rows remain useful fan-in stressors. |
| **ZB R6 reporting restriction** | **Accepted — required reporting rule** | `mergeAll` and `flatMapPar` must be reported as bounded fan-in under inner-stream work, never as equivalent CPU scheduler scaling. |
| **FS2 F2 / Pekko RF2:** add providers to genuine suspension | **Deferred** | Public counterparts exist for a narrower pending-callback contract, but current classes are ZB diagnostics. Firewalling is sufficient now. |
| **Ox 4:** add blocking Ox callback to nonblocking suspension matrix | **Rejected** | A blocked direct-style callback is not a nonblocking suspend/resume callback. Benchmark it separately if required. |
| **ZB R5:** remove `Stream.unwrap(Async.succeed(inner))` | **Accepted — implemented** | Removed from both the comparative cell and `StreamAsyncConcurrentParityBench`. |
| Clone every ZB reader, resume, resource, or primitive diagnostic for other providers | **Rejected** | Similar output does not establish the same public contract. Keep these provider-local. |
| Provider-only `collect`, fused filter-map, `flattenMerge`, explicit `.async`, or alternate fold conveniences | **Rejected as fairness requirements** | They either change the named stage structure or are readability-only alternatives. |

## What the current diff already fixes

The current diff correctly:

1. Adds the high-level ZB ready-effect source and uses it in comparative async suites.
2. Removes Ox from measured ready-effect eval, pipeline, setup, and concurrent parameters.
3. Removes Ox-only `.buffer()` from those source helpers.
4. Uses Pekko `concatLazy` in all four disputed sequential/deep-concat locations.
5. Removes the unnecessary ZB `Stream.unwrap(Async.succeed(...))` from the comparative `flatMapPar` cell.
6. Uses native drain terminals rather than folds in drain cells.
7. Normalizes synchronous pipeline/setup sources around the same prebuilt `Vector`.
8. Uses FS2 `Pure` and Kyo pure-specialized operators in synchronous suites.
9. Prebuilds deep eval graphs outside timed evaluation.
10. Removes the unnecessary ActorSystem from `StreamSetupBench`.
11. Conditionally creates Pekko runtime state in the parameterized async suites.
12. Adds the correct bounded-fan-in caveat to concurrent-suite documentation.

These changes should remain.

## Implemented required edits

- The three Kyo ready-effect sources use `KyoStream.range` plus a typed already-ready `Int < Async` value.
- Both concurrent suites include Kyo `mapParUnordered` with `kyoBufferSize = 16`; Kyo is absent from dynamic `mergeAll`/`flatMapPar`, and validation rejects reintroducing those unsupported cells.
- The async concurrent ZIO Blocks cell uses native `mapPar`, and the provider-local parity `flatMapPar` returns its inner stream directly.
- Pekko's ActorSystem is absent from non-Pekko trials. Shared synchronous classes inject `PekkoBenchmarkRuntime` only into Pekko methods; parameterized classes initialize Pekko conditionally; `StreamEvalBench` builds only the selected provider's graphs.
- Benchmark comments use the contract taxonomy above, fan-in claims are narrow, and provider-local diagnostics are labeled and excluded by the manifest's provider-ranking allowlist.

Runtime isolation is prospective experimental hygiene, not proof that Pekko caused any historical slowdown. All affected historical timing data must be rerun.

## Deferred benchmark families

These are valuable but not prerequisites for publishing the corrected current matrices:

1. **Fixed-chunk/effectful-pull matrix:** common chunk size, pull count, and terminal. The ZB custom `AsyncReader` may participate here only after actual pull granularity is instrumented.
2. **Buffered-boundary matrix:** explicit capacity and equivalent producer/consumer boundary placement for every participating provider. Ox `.buffer()` belongs here.
3. **Nonblocking pending-callback matrix:** identical worker pool, registration point, handshake, parallelism, ordering, cancellation, and fold semantics for capable providers.
4. **Blocking direct-style matrix:** Ox and any other provider whose documented contract occupies a callback thread while waiting.
5. **Kyo provider-native fan-in:** benchmark public `collectAll` separately from bounded dynamic fan-in, without presenting fixed stripes as `mergeAll` or `flatMapPar`.
6. **Pekko detached concat:** separate from the selected sequential-demand `concatLazy` contract.

## Verification requirements

Before admitting new timing data:

1. Compile against the exact pinned dependencies and Scala 3.8.3.
2. Run the correctness entry point after updating its capability matrix.
3. Add untimed counters proving each ready-effect source callback is invoked exactly once per emitted source element.
4. For concurrent map correctness, verify:
   - checksum;
   - maximum observed in-flight work does not exceed `parallelism`;
   - repeated materialization;
   - Kyo’s configured buffer is recorded separately.
5. Smoke-test every JMH cell and parameter combination before full runs.
6. Confirm non-Pekko trials do not initialize or retain Pekko runtime state.
7. Rerun all results affected by source, concat, drain, graph-setup, runtime-isolation, or Kyo changes.

## Reporting rules

1. Never aggregate native-source, ready-effect, buffered, chunked, suspended, or blocking families into one “async” ranking.
2. Label the current async suites **one ready effect per source element**.
3. Report Ox as **N/A for that exact contract**, not as lacking asynchronous streaming support.
4. Report Kyo as supported for bounded unordered `mapPar` and N/A for bounded dynamic `mergeAll`/`flatMapPar`. Provider-native `collectAll` results, if added, must be reported separately.
5. Report `mergeAll` and `flatMapPar` heavy rows only as native bounded fan-in under inner-stream work. Do not claim equivalent CPU scheduling or scheduler scalability.
6. Exclude all `Parity`, `Micro`, primitive-specialization, reader, resume, and cooperative-yield classes from provider rankings through an explicit reporting allowlist.
7. Publish raw score, error interval, units, forks, warmup, measurement count, JVM/JDK, GC, CPU, OS, commit SHA, provider versions, `N`, parallelism, workload, Kyo buffer size, and Ox buffer policy.
8. Do not select buffer values, workloads, or `N` after viewing provider results.
9. Do not reuse results collected before provider-state isolation.

## Prioritized implementation plan

| Priority | Work | Effort |
|---:|---|---:|
| P0 | Replace all Kyo ready-source `Async.defer` calls | S |
| P0 | Restore Kyo `mapParUnordered`, fixed buffer policy, capability-aware dispatch, and correctness | M |
| P0 | Revert comparative ZB `mapParAsync` to native `mapPar` | S |
| P0 | Remove remaining ZB parity wrapper and add provider-local labels | S |
| P0 | Isolate Pekko runtime and provider-specific eval graphs | M/L |
| P1 | Correct taxonomy, capability, setup, and fan-in documentation | S |
| P1 | Run compile, correctness, instrumentation, and JMH smoke verification | M |
| P1 | Rerun and publish only under the reporting rules above | L |
| P2 | Design chunked, buffered, pending-callback, and blocking benchmark families | L |

After P0 and P1, the native and one-ready-effect-per-element matrices are defensible for comparative publication.
