# BS-9 Remediation: Lane-General Async `mapPar`

## Oracle decision

Share the concurrent execution lifecycle rather than copying
`ReadyIntLongFold` across type products or routing every lane through the slow
scalar fallback. Primitive reads, callback adapters, value carriers, and
accumulator fields may remain specialized, but selector ownership, handoff,
cancellation, completion, pending-fold continuation, and yielding must have one
semantic owner.

The scope includes `mapParAsync`, `mapPar` over asynchronous upstreams, all
output and accumulator lanes, and scalar/bulk/terminal consumption. Pool policy
for `mergeAll` and `flatMapPar` remains BS-10.

## Characterization before implementation

There are three independently observable eligibility boundaries:

1. `AsyncMapPar.runFoldLongDirect` requires stable Int input, Int output, and a
   Long accumulator and bypasses ordinary materialization.
2. `foldLongIntDirect` selects `ReadyIntLongFold` only for Int upstream/output.
3. Ordinary concurrent reads avoid initial selector installation only for Int
   output.

Use path observations to compare:

- the public optimized path;
- the same concurrent reader with an opaque scalar terminal that cannot
  redispatch into native Int/Long folding; and
- a scripted semantic model independent of both loops.

Do not use the existing `genericFold` helper as a control if it can redispatch
through `Sink.foldNativeAsyncIntLong`.

### Priority correctness witness

Before extraction, test EOF arriving before the last pending fold completes:

1. Use one input and parallelism 2.
2. Gate the map callback until upstream EOF has been accepted.
3. Complete the map and make its fold return a second gate.
4. Assert the terminal remains pending until that fold completes.
5. Assert its final accumulator or failure is observed.
6. Repeat with cancellation and cleanup.

Oracle's static trace found that the current handler may set `completed` after
freeing the final worker while `pendingFold` is still present, then check
completion first. If reproduced, fix and commit this correctness defect before
using the state transitions as the generalized model.

### Type and operation matrix

- Short success coverage across Boolean, Byte, Char, Short, Int, Long, Float,
  Double, and reference input/output/accumulator lanes.
- Focused asymmetric triples: Long→Int→Long, Int→Long→Long,
  Int→Int→Double, Long→Double→Int, reference→Int→reference, and
  Int→reference→reference.
- Ready and suspended folds; convenience overloads, generic `runFoldAsync`,
  sink forms, mapped sinks, drain/count/foreach/collect, short circuits, scalar
  reads, bulk reads, partial reads, and downstream operators.
- Extrema, narrow values, signed zero, infinities, NaNs, null references, and
  reference values implementing `Pollable`.
- Boundaries 255/256/257 and 1023/1024/1025; parallelism 1, 3, 7, 8, and 16.

### Lifecycle invariants

- At most `n` callbacks are active and exactly one source pull is armed.
- Each accepted source value maps once; each selected output folds once.
- At most one fold is pending and must commit before another starts or success
  is published.
- Completion requires source exhaustion, no worker output, no pending consumer,
  and completion of required cleanup.
- Claimed handoffs have exactly one commit/rejection path; stale generations
  cannot publish.
- Typed stream errors, callback defects, cancellation, and cleanup failure
  precedence remain unchanged.
- Logical lane affects representation, never scheduling or lifecycle policy.

## Target ownership

Keep `ConcurrentReader` responsible for operation registration, generations,
cancellation, terminal commit, and cleanup. Keep `AsyncSelector` responsible
for reservations, replacement, round-robin selection, and shutdown handoffs.
Introduce one lane-neutral concurrent consumer driver for ready batching,
pending consumer continuation, completion, and cooperative yielding.

Physical adapters may own primitive fields, exact reads, logical narrow-lane
decoding, callback invocation, and ready-result extraction. They must not own
selectors, cancellation, pending-fold continuations, budgets, scheduling, or
terminal precedence.

## Staged implementation

1. Add characterization suites, path observations, diagnostic JMH, and the EOF
   witness; archive baseline and fix any confirmed correctness defect.
2. Extract common orchestration behind one driver while retaining historical
   dispatch eligibility.
3. Route all output and accumulator lanes through the common driver and remove
   Int-only selector policy.
4. Route scalar, bulk, fold, and built-in terminal consumption through shared
   map-operation transitions; reuse only the consumer protocol for fan-in.
5. Remove obsolete lifecycle classes and verify all platforms and versions.

Likely files are `Stream.scala`, `Sink.scala`, and
`internal/AsyncConcurrentReaders.scala`; extract a small private terminal
capability only if needed. Do not move the concurrent engine wholesale into the
interpreter or generate an operation × lane × accumulator class product.

## Performance gates and definition of done

- No unresolved semantic divergence or orphaned cleanup.
- No reproducible regression greater than 5% in historical or asymmetric cells.
- No new per-element allocation slope in existing primitive fast paths.
- No weakened deterministic fairness/cancellation bound.
- Three-fork paired JMH with allocation evidence and exact dispatch observation.
- All lanes and public terminal forms use one lifecycle; `ReadyIntLongFold` no
  longer owns scheduling, handoffs, suspension, or completion.
- Scala 2/3 JVM and supported JS tests, coverage, downstream checks, and format
  pass with no public API change.

## Completed implementation and measurements

The dedicated `ReadyIntLongFold` lifecycle was removed. One selector-aware
`MapParFold` now owns source/worker handoff, fairness, pending folds, completion,
failure, cancellation, and yielding for every element and accumulator lane.
`ConcurrentAccumulator` implementations retain only typed accumulator storage
and callback observation. Eager representation validation also preserves setup
defect classification.

JFR identified an initially introduced 8-byte-per-element slope as the extra
capture of `onComplete` in each pending-source continuation. The continuation
now returns to the poller without capturing that callback, for both primitive
and generic direct sources.

Three-fork JMH (`N=1,000,000`, parallelism 8, light workload):

| Metric | Baseline | Generalized | Change |
|---|---:|---:|---:|
| Throughput | 3.905 ± 0.140 ops/s | 3.900 ± 0.092 ops/s | -0.1% (intervals overlap) |
| Allocation | 552,002,624 B/op | 552,002,760 B/op | +136 B/op fixed cost; no per-element slope |

Artifacts: `streams/benchmarks/specialization/bs-9/candidate-final-generalized.json`
and the archived baseline in the clean comparison worktree.
