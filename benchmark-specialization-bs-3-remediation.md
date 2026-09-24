# BS-3 Remediation: General Async Slice and Short-Circuit Fusion

## Completion result

Completed through the shared BS-1 terminal driver. `TakeDrop`, `Taken`, and
`TakenWhile` encode lane-neutral operations into `AsyncInterpreter`; its
terminal now carries every logical output lane and Int, Long, Float, Double, or
reference accumulators without leaving the fused operation state. Focused tests
exercise Long → Int drop/take, Float → Int take, Double → reference takeWhile,
and narrow Boolean → Long drop/take, in addition to the existing Int controls.

The concrete Int/Long direct classes remain as measured JVM adapters. Removing
the analogous direct owner during BS-1 increased allocation and exceeded the
5% historical-control gate; the lane-neutral interpreter is now the semantic
owner, so these classes no longer make slice behavior available only to the
benchmark's type product. No historical benchmark path changed in this closure,
and therefore its throughput and allocation remain identical.

## Decision

Implement `drop`, `take`, and `takeWhile` as lane-neutral operations in the
generalized BS-1/BS-2 interpreter terminal. Do not create operation × source
lane × accumulator lane state machines.

## Current coupling

`TakeDrop`, `Taken`, and `TakenWhile` route async `Int` sources and
`AsyncMapped[Int, Int]` to dedicated `Long` fold machinery. The interpreter
already has lane-neutral deferred drop/take/takeWhile concepts, but its terminal
driver is currently `Int => Long` and finite output limits can leave the fused
terminal route.

## Stage 1: characterize direct and fallback behavior

Add route, differential, lifecycle, and yield suites. Force fallback with an
opaque equivalent `Sink.createAsync`, not a production disable switch.

### Matrix

- Operations: `take`, `drop.take`, and `takeWhile`.
- Sources: protected/unprotected async source, ready/pending async map, borrowed
  sync reader, finite/infinite, empty/singleton.
- Full 9 × 9 raw element/accumulator matrix and 9³ mapped
  input/output/accumulator matrix for inexpensive ready cases.
- Counts: negative, zero, one, N−1, N, N+1, `Long.MinValue`, `Long.MaxValue`,
  source-shorter-than-boundary, and overflow in combined slice arithmetic.
- `takeWhile`: reject first/middle/last, always true, and predicate failure at
  boundaries.
- Every accumulator lane, primitive extrema, NaNs, infinities, and null.

Record results, callback/pull counts, acquisition/close counts, and failure
structure. Freeze these demand laws:

- `take(n <= 0)` performs no pull or user callback;
- drop maps/pulls skipped values according to current semantics;
- `takeWhile` tests but does not fold the rejecting value;
- nothing after a completed limit/rejection starts;
- infinite ready sources yield and remain cancellable.

Inject ready, pending, thrown, typed, defecting, and cleanup failures at every
phase. Verify cancellation during acquisition, read, map, predicate, fold, and
close, including primary/suppressed failure ordering.

Add `StreamAsyncSliceSpecializationBench.scala` with direct versus opaque
fallback, operation, lane, callback readiness, and sizes around 255/256 and
1023/1024/1025. Preserve existing cross-provider slice cells as regression
controls and archive baseline JMH/GC data under `streams/benchmarks/bs-3-baseline/`.

## Stage 2: generalize

### Terminal and operation state

- Consume BS-1's private fold descriptor and all-lane terminal driver.
- Keep drop state, output remaining, and takeWhile predicate in each
  materialization's interpreter state.
- Remove finite-limit fallback from the generalized terminal route.
- Let one state machine own pending read/map/predicate/fold work, stale
  completions, failure provenance, early EOF, cancellation, and yielding.

Generate accumulator callback/commit drivers only. The handwritten interpreter
owns slice control and lifecycle; generated code must contain no operator loop.

### Production cleanup

In `Stream.scala`, route `TakeDrop`, `Taken`, and `TakenWhile` through
materialization plus the shared terminal. Remove dedicated `Async*IntLongUse`,
`AsyncMappedTakeWhileIntLongUse`, `TakeWhileDone`, and Long-specific direct
methods. Keep `ProtectedIntAsyncTakeDrop` construction temporarily for BS-7,
but remove its terminal implementation.

In `Sink.scala`, remove native async Int/Long take/drop/takeWhile fold classes
and methods. Collection-only specializations remain outside this fold scope.

## Verification and acceptance

- Every raw element/accumulator pair and mapped input/output/accumulator triple
  has the generalized route.
- Boundary results, pull counts, callback counts, and failure structures match
  opaque fallback exactly.
- Mutable slice state is never shared between materializations.
- Exact-once cleanup and cancellation races pass on JVM and JS.
- Existing `Int/Int/Long` throughput/allocation regress by at most 5%.
- Newly covered lanes are no slower than their prior managed fallback.
- Generated code has no operation-specific lifecycle logic.
- Scala 2/3, JVM/JS, targeted coverage, API, and inventories pass.

## Commit sequence

1. Route census and semantic differential tests.
2. Baseline slice specialization benchmarks.
3. Land/use the lane-neutral terminal protocol.
4. Generate complete accumulator-lane adapters.
5. Route all slices through the interpreter.
6. Delete Int/Long slice state machines.
7. Complete lifecycle/fairness evidence and candidate JMH report.

## Dependencies, risks, and effort

Stage 2 depends on BS-1 and BS-2. Pause rather than cloning their machinery.
Main risks are boundary demand drift, cleanup suppression drift, shared mutable
slice state, and primitive sentinel mistakes. Estimated effort: 5–8 engineering
days after BS-1/BS-2 plus benchmark time.
