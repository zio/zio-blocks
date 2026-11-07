# BS-6 Remediation: General Strict Indexed Sources

## Decision

Replace the concrete `Vector[Int]` / `Long` product with one internal immutable
strict-indexed source family. Initially allowlist `Vector` and immutable
`ArraySeq`; do not optimize arbitrary `IndexedSeq`, whose mutation and traversal
semantics are not controlled.

Blocking strict-source kernels should generalize across lanes. Async consumption
must remain on the existing budgeted/cancellable interpreter.

## Current coupling

`Stream.fromIterable` and `Reader.fromIterable` recognize only `Vector[Int]`.
The reader repeatedly indexes the Vector, and source-specific slice/takeWhile
fusion exists only for `Long` accumulation. This exactly matches native
benchmarks built from `(0 until N).toVector` and checksummed to `Long`.

## Stage 1: characterize source strategies

Add `StreamStrictIndexedSourceBench.scala`, correctness preflight, benchmark
manifest validation, and a decision report under
`streams/benchmarks/bs6-strict-indexed/`.

### Matrix

- Sources: Vector, immutable ArraySeq, List, a delegating Iterable that forces
  generic iteration, custom IndexedSeq negative control, Chunk, and Range.
- Sizes 0, 1, 31/32/33, 1023/1024/1025, 10,000, 32,768, and 1,000,000.
- Every element and accumulator lane, including extrema, NaN, signed zero, and
  null.
- Construction/materialization, drain, fold, take, drop/take, takeWhile, and
  callback failures at first/middle/last positions.
- Raw Vector strategy comparison: repeated indexing, iterator, slice iterator,
  and iterator-drop plus explicit remaining count.

Use at least three JMH forks, GC profiling, and representative perfasm/JFR.
Checksums and callback counts must be independently validated.

The expected policy is Vector iterator traversal with an explicit remaining
count and direct ArraySeq indexing. Confirm this empirically; do not add a size
threshold unless it wins stably across boundaries and allocation profiles.

## Stage 2: generalize

### Shared source model

Add `internal/StrictIndexedSource.scala` with source classification, immutable
length, saturating slice arithmetic, reader position/end/reset invariants, lazy
Vector cursor state, and direct ArraySeq cursor state. Construct exactly one
stream/source node; avoid per-operation descriptor objects.

### Generated carriers

Generate from one lane table:

- strict readers for every primitive and reference lane;
- scalar and supported bulk read methods using existing sentinel protocols;
- fold callback carriers for every accumulator lane;
- separate ordinary and takeWhile loops to avoid an optional-predicate branch.

Do not generate source-kind × operation × terminal classes. Source strategy,
slice state, and terminal carrier must remain independent.

### Integration

- Replace `Reader.FromVectorInt` and `Stream.FromVectorIntStream` with the family.
- Classify concrete immutable source kind before lane selection.
- Use reader skip/limit for take/drop and a strict-reader predicate capability
  for takeWhile.
- Replace exact Int/Long sink matches with strict-source capabilities.
- Keep arbitrary Iterable, Chunk, and Range behavior separate and unchanged.
- Update generated-source drift checks and specialization inventories.

## Verification and acceptance

- Vector and immutable ArraySeq × all element/accumulator lanes.
- Plain, slices, takeWhile, drain, collect, scalar/bulk reads, reset, close, and
  repeated/concurrent materialization.
- Negative/huge counts, both skip/limit orders, custom Iterable failures, widened
  types, null, and exact float/double bit preservation.
- Callback error provenance and no callbacks after failure.
- Async parity, cancellation, and cooperative yielding remain unchanged.
- No remaining `FromVectorInt`, `FromVectorIntStream`, `foldSliceLong`, or
  `foldTakeWhileLong` symbols.
- Setup allocates no additional source object; historical Vector Int/Long cells
  regress by at most 5% and generalized large-source lanes by at most 10%.
- Drop/take cost is independent of skipped prefix and callback-free drain is O(1)
  where semantics permit.

## Implemented result

`Stream.fromIterable` and `Reader.fromIterable` now classify every immutable
`Vector` into one lane-aware strict source family. The reader keeps the shared
O(1) skip/limit/reset state and implements exact Boolean, Byte, Short, Char,
Int, Long, Float, Double, and reference pulling, including Long/Double bulk
pulls used by typed sinks. Source-owned slice and take-while loops are generic
in the element lane; alternate accumulator lanes use the same strict reader
through the existing typed sink drivers. The former `FromVectorInt` and
`FromVectorIntStream` products no longer exist.

`StrictVectorLaneSpec` covers every physical lane, Long/reference slicing with
Int/Long/Double accumulators, and skip/limit/reset bounds. The existing Int
specialization suite remains green.

Three-fork N=10,000 controls measured 94,584 ops/s for take, 94,215 ops/s for
drop/take, and 165,787 ops/s for takeWhile. Against the preceding three-fork
direct-slice controls (93,955, 93,984, and 159,700 ops/s), this is +0.7%, +0.2%,
and +3.8%; allocations remain constant-size. Results are archived at
`streams/benchmarks/specialization/bs-6/candidate-vector-slices.json`.

## Commit sequence

1. Characterization benchmark and semantic controls.
2. Record source traversal policy and baseline.
3. Introduce generated strict indexed readers.
4. Generalize slicing and takeWhile.
5. Generalize fold carriers across accumulator lanes.
6. Record candidate results and close documentation/inventory work.

## Risks and effort

Risks include accidentally optimizing mutable/unknown IndexedSeq semantics,
slow repeated Vector indexing, code-size explosion, and using blocking kernels
from async execution. Estimated effort: 3–5 engineering days after 1–2 days of
characterization, plus benchmark time.
