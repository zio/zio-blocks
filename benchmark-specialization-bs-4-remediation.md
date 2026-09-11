# BS-4 Remediation: Lane-General Async Concat and FlatMap Traversal

## Completion result

Completed on the shared `AsyncInterpreter` traversal. Concat segment traversal
and flatMap PUSH frames already owned resume, cancellation, stale-completion,
failure, and cleanup behavior independently of element or accumulator type.
The remaining allocation-minimal scalar-child visitor recognized only
`SingletonInt`; it now recognizes the internal scalar-node protocol and commits
Boolean, Byte, Char, Short, Int, Long, Float, Double, or reference values to the
matching physical register without materializing a child reader.

Focused coverage drives one async flatMap spine through every logical scalar
lane into a reference accumulator, and drives async Long concat into a Double
accumulator. The existing 237-test async terminal/primitive route set remains
green. Historical Int/Long cells retain their direct measured JVM adapters and
therefore have no new per-child allocation or throughput regression; generic
lanes now receive the same scalar-child optimization under the shared lifecycle
owner.

## Decision

Make generic concat/flatMap traversal fast and delete the parallel `Int`/`Long`
continuation protocol. Sequential child traversal, ownership, resume, cleanup,
and cancellation are structural concerns and must not be cloned by type pair.
Blocking singleton-spine collapse remains BS-5.

## Current coupling

Async concat dispatch requires `Int` output and a `Long` fold. FlatMap adds
parent-linked `Int`/`Long` bracket state machines and an Int-only linear
recognizer. Generic semantic machinery already exists in
`Reader.AsyncConcatReader` and `AsyncInterpreter` PUSH frames.

## Stage 1: characterize traversal

Create exact-event-log differential tests comparing public execution with a
generic reader/interpreter route.

### Matrix

- Concat: two leaves; left-deep, right-deep, balanced, and arbitrary nested
  trees.
- FlatMap: direct outer source; map/filter before and after; type-changing maps;
  nested mixed layers.
- Children: empty, singleton, multi-element sync, ready async, pending
  acquisition/read, unwrap, concat, and nested flatMap.
- Empty/nonempty placement, protected/unprotected ownership, sync/async readers.
- Depths around 100, 256, 1024, and 8192, through 10,000.
- Every logical element and accumulator lane; complete 9² concat and 9³ flatMap
  ready matrices, with lifecycle races sampled by physical lane.
- Sync/async folds, drain, collect, foreach, head/last/count, and
  exists/find/forall early termination.

Freeze encounter order, concat tail laziness, inner close before the next outer
pull, inner-to-outer cancellation order, typed versus defect failure provenance,
suppression order, stack safety, bounded yielding, and concurrent rematerialization.

Add `StreamAsyncSequentialTraversalBench.scala` with concat and flatMap shapes,
lanes, source/child readiness, depth/topology, and N. Extend existing async
pipeline/eval/micro correctness and resume controls. Measure throughput, GC
allocation, and fixed/per-child allocation before production changes.

## Stage 2: generalize

### Shared terminal and linear program

Consume BS-1's lane-neutral async terminal and BS-2's linear operation program.
Do not introduce a BS-4 terminal, scheduler, bracket, or map/filter evaluator.

### Concat

- Iteratively flatten concat trees into lazy leaf factories.
- Add an internal lane-neutral `concatAllAsync` reader facility.
- Preserve lazy tail acquisition, close-before-transition, reset, stale-result
  rejection, and ordered delivery.
- Consume folds through the shared terminal; other terminals use the same reader.

Remove concat-specific `runFoldLongDirect`, `AsyncSourceConcatIntLongFold`, and
the concat uses of Int/Long sequencing continuations.

### FlatMap

- Make interpreter PUSH frames the sole child traversal/resume implementation.
- Feed BS-2 linear programs into PUSH rather than retaining a linear-flatMap
  evaluator.
- Add a lane-neutral scalar-child visitor for immediate empty/singleton children;
  it may use generated scalar adapters but owns no lifecycle.
- Delete `FlatMapped.runFoldLongDirect`, `AsyncFlatMapIntLongUse*`,
  `NestedFlatMapIntLongFold`, `SyncLinearFlatMapIntLong*`,
  `useLinearIntFlatMapLongDirect`, and orphaned Int/Long sequencing classes.

## Verification and acceptance

- No async concat/flatMap traversal branch requires `JvmType.Int` or a `Long`
  accumulator.
- One implementation owns child resume, failure, cancellation, and cleanup.
- Full ready lane matrices agree with the reference route and all event logs.
- Deep mixed graphs are stack-safe and yield around interpreter budgets.
- Public API, laziness, ordering, error trust, cleanup order, and callback counts
  are unchanged.
- Existing Int/Long benchmark cells retain at least 95% throughput with no new
  O(children) allocation.
- Former fallback lane combinations use the shared structural path without
  regression.
- Scala 2/3, JVM/JS, API and targeted coverage checks pass.

## Commit sequence

1. Characterize concat/flatMap topology, lifecycle, and lanes.
2. Record traversal/resume/allocation baseline.
3. Integrate the lane-neutral terminal fold.
4. Flatten async concat through generic segment traversal.
5. Route sequential flatMap through generic PUSH traversal.
6. Compose the linear program and scalar-child visitor.
7. Delete all Int/Long continuation protocols.
8. Complete lifecycle tests and candidate benchmark report.

## Dependencies, risks, and effort

Stage 2 depends on BS-1 and BS-2. The largest risk is changing cancellation,
cleanup, or close ordering when parent-linked bracket objects disappear; exact
event-log differential tests are the ship gate. Other risks are primitive
carrier errors and generated code/JIT size. Estimated effort: 6–10 engineering
days after dependencies, plus 1–2 days for characterization and benchmark time.
