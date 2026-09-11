# BS-10 Remediation: Lane-General Async Fan-In

## Oracle decision

Treat BS-10 as specialization of scheduling and lifecycle, not merely missing
primitive arrays. Replace the Int-only merge pool and `MergeIntLongFold` with:

1. one merge coordinator owning acquisition, selector arbitration, slot reuse,
   cancellation, and cleanup;
2. one concurrent terminal protocol, shared with BS-9, owning fold suspension,
   continuation, and cooperative yielding; and
3. lane-specific storage and callback adapters beneath those owners.

Do not copy the pool and state machine once per primitive lane.

## Characterization before implementation

Separate the existing paths:

| Element and terminal | Functional pool | Direct fold |
|---|---:|---:|
| Eligible Int inner, Int→Long terminal | Yes | Yes |
| Eligible Int inner, other terminal | Yes | No |
| Other element lanes | No | No |

Pool attachment additionally depends on the inner reader being an eligible
native async-to-sync bridge. Sync-backed and native-async Int readers can
therefore differ despite yielding identical values. Parallelism 1 is a
sequential `flatMap` identity and does not measure the pool.

### Priority correctness witnesses

- **Prefix withholding:** an inner produces one value, then waits for its first
  downstream callback before producing another. The current full-batch read may
  wait for the second value before publishing the first, creating a cycle.
- **Fairness multiplication:** the direct fold's selection budget can drain an
  entire pooled batch per selection, so its nominal 1,024 selections can become
  roughly `1024 × batchSize` callbacks before yielding.
- **Buffer policy:** Int lanes silently use at least 1,024 slots even when the
  configured stream buffer is 64, while other lanes do not.

Reproduce and record these before extraction. Preserve the documented buffer,
progress, and fairness contract—not accidental overbuffering or withholding.

### Required matrix

- Both `mergeAll` and `flatMapPar`.
- All logical primitive/reference element and accumulator lanes for short
  correctness cases; focus performance on Int→Long, Int→Int, Int→reference,
  Long→Double, Double→Long, Float→Float, reference→Long, and
  reference→reference.
- Sync-backed, native-ready async, high-level ready-effect, and genuinely
  pending inner readers.
- Pure, ready-async, and suspended folds.
- Buffers 1, 2, 16, 64, 256, 1024, and 4096; inner lengths around each boundary.
- Parallelism 1, 2, 8, 16, and 64; empty, singleton, ragged, skewed, and hot
  inner streams.
- Scalar/bulk reads, collect, drain, foreach, short circuits, downstream
  operators, resources, cancellation, failure, and repeated materialization.

Assert values, multiplicity, per-inner ordering, callback counts, active-inner
bounds, exact acquisition/close traces, and allowed concurrent histories—not
only checksums.

For attribution, benchmark the same Int→Long program in a private diagnostic
four-way matrix: pool off/on × direct terminal off/on. Do not add a public flag
or benchmark-sensitive production branch.

## Required invariants

- Acquiring, running, and closing inners count against `maxOpen`; a slot cannot
  be reused before its previous close completes.
- One owner closes each acquired reader exactly once.
- Producer-filled, published, and handoff-held values obey one configured
  capacity; there is no hidden per-lane minimum or second batch.
- Available prefixes are published without waiting for a batch to fill.
- Producers cannot overwrite leased arrays; tokens reject stale releases,
  reads, and wakeups; consumed reference cells are cleared.
- Per-inner order and selector rotation are preserved; folds are not reassociated
  or parallel-reduced.
- A fold callback runs once and a pending callback resumes rather than reruns.
- Fairness budgets count actual callback/lifecycle work, not selections whose
  work scales with batch size.
- Cancellation owns pending replacement/acquisition work and attempts cleanup
  for every resource while preserving primary failure precedence.

## Target architecture

The shared merge coordinator owns outer demand, capacity, acquisition transfer,
inner installation, close-before-reuse, and terminal cleanup. A pool assignment
owns a pending read, bounded prefix publication, batch lease/release, assignment
token, and worker termination. The BS-9 terminal controller owns one fold at a
time, continuation, accumulator commitment, and cooperative budget.

Lane adapters own arrays, exact physical reads, logical narrow conversion,
reusable carriers, and typed accumulator fields only. On JVM, use lazily started
persistent workers with a lane-neutral prefix-producing pump. On JS, reuse the
coordinator and pump semantics without threads or blocking bridges.

## Staged implementation

1. Add semantic and lifecycle characterization plus a provider-local diagnostic
   JMH suite; archive pool/direct-terminal ablations.
2. Unify reader emission and direct terminal event/slot ownership in
   `AsyncConcurrentReaders.scala`, using the BS-9 terminal protocol.
3. Replace `IntAsyncMergePool` with a lane-general JVM owner and physical
   storage adapters; revise private platform capabilities without changing API.
4. Route all accumulator lanes and terminal forms through the common protocol.
5. Integrate intermediate/protected materialization paths, tune carriers rather
   than semantics, remove obsolete Int-only owners, and document results.

## Performance gates and definition of done

- No reproducible throughput regression greater than 5% in equivalent cells.
- No reproducible p99 first-element, fairness, or cancellation regression above
  10%, plus deterministic progress assertions.
- No fresh primitive value wrapper or accumulator boxing per element.
- Worker count is bounded and returns to baseline; reference batches/readers are
  not retained after cleanup.
- Buffering, prefix progress, ordering, failures, cancellation, and cleanup pass
  the full semantic matrix.
- Pool eligibility and lifecycle no longer depend on Int→Long or a concrete
  bridge class; remaining lane specialization is representation-only.
- Original cross-provider workloads remain unchanged; diagnostics stay excluded
  from rankings.
- Scala 2/3 JVM, supported JS, coverage, downstream checks, and formatting pass
  with no public API change.

## Completed implementation and measurements

The specialized `IntAsyncMergePool`, `PooledIntReader`, and
`MergeIntLongFold` were removed. The former pool was not merely an Int carrier:
it silently replaced the requested default buffer of 64 with 1,024 and owned a
second scheduling lifecycle. One lane-neutral selector lifecycle and one
`MergeFold` now serve every physical lane and accumulator while preserving the
configured buffer policy. A deterministic pending-second-element test verifies
that an available prefix reaches the fold before its successor is released.

The original 1,024-prefetch baseline is not semantically comparable. For the
paired gate, the baseline's single hidden-minimum line was changed from
`max(bufferSize, 1024)` to `bufferSize`, without applying any candidate code.
Three-fork JMH (`N=1,000,000`, parallelism 8, light workload) then showed:

| Operation | Corrected baseline | Generalized | Throughput change | Allocation change |
|---|---:|---:|---:|---:|
| `mergeAll` | 3.141 ± 0.016 ops/s | 6.129 ± 0.035 ops/s | +95.1% | 877,183,047 → 480,986,742 B/op |
| `flatMapPar` | 2.825 ± 0.010 ops/s | 5.848 ± 0.085 ops/s | +107.0% | 880,017,016 → 478,249,227 B/op |

Artifacts: `streams/benchmarks/specialization/bs-10/baseline-buffer64-corrected.json`
and `candidate-final-generalized.json`.
