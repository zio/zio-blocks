# BS-5 Remediation: General Blocking Singleton FlatMap Collapse

## Completion result

Completed. Every `Stream.succeed` overload now participates in an internal
resource-free scalar-node protocol, and `FlatMapped.compileBlocking` lazily
collapses homogeneous or lane-changing scalar spines before handing one reader
to the ordinary sink machinery. Tests cover a full Boolean → Byte → Short →
Char → Int → Long → Float → Double → reference transition, a depth-10,000 Long
spine with an Int accumulator, fallback to a multi-element child, and closing a
compiled reader without evaluating callbacks.

The historical Int/Long terminal adapter was deliberately retained after a
controlled experiment. Routing that cell only through the generalized reader
made each callback-produced `SingletonInt` escape HotSpot scalar replacement:
allocation rose from about 40,081 B/op to 200,050 B/op and throughput fell from
20,210 to 13,711 ops/s. With the adapter retained, the generalized candidate
measured 19,923 ops/s and 40,080 B/op over three forks (−1.4% throughput,
allocation unchanged). The adapter is therefore a representation-level JVM
optimization, no longer the sole semantic owner of singleton collapse.

## Decision

Move deep singleton-flatMap collapse into blocking compilation. Produce one
scalar reader after collapse and let existing sinks handle every terminal and
accumulator lane. Do not add more terminal-specific `runFold` overrides.

Scope is blocking execution, arbitrary blocking sinks, and `start`; async
flatMap traversal remains BS-4.

## Current coupling

Only an exact `SingletonInt` root, a homogeneous `Int => Int` flatMap spine,
exact `SingletonInt` children, and the `runFold(Long)` overload receive complete
collapse. Other lanes, terminals, wrappers, and child shapes fall back; deep
fallback changes machinery around the existing depth cutoff.

## Stage 1: characterize dispatch and semantics

Add `DeepFlatMapCollapseSpec.scala` on the JVM and expand existing sync
interpreter tests.

### Matrix

- Depths 0, 1, 32, 99, 100, 101, 1024, 10,000, and representative lane cases
  at 100,000.
- Every primitive/reference lane and rotating cross-type spines.
- Exact singleton, opaque singleton, empty, two/three-element, failing,
  defecting, managed/finalized, async-boundary, null reference, and null-stream
  child shapes.
- Fallback at root, first callback, midpoint, and final callback.
- Collect, drain, count, head/last, predicates, foreach, every accumulator lane,
  custom short-circuit sink, and `start`.

Use an opaque-root graph as the semantic reference. Compare full result/order,
callback traces and counts, exact output cardinality, short-circuit demand,
typed versus callback failure provenance, inner-first exact-once finalization,
cleanup suppression, floating raw behavior, and stack safety without a larger
thread stack.

Keep `StreamEvalBench.nested_flatMap` unchanged as the historical control. Add
`StreamDeepFlatMapCollapseBench.scala` with tractable sweeps for depth, lanes,
terminals, exact versus opaque singleton, fallback position, and cross-type
spines. Capture throughput and GC allocation before production changes.

## Stage 2: generalize structurally

### Singleton protocol

Introduce a private sealed scalar-node protocol carrying logical `JvmType` and
lossless scalar extraction. Keep `SingletonInt` if useful but make it implement
the protocol; add packed primitive and nullable-reference singleton nodes.

### Blocking trampoline

Add `internal/BlockingSingletonFlatMap.scala`:

1. Peel the static flatMap spine iteratively.
2. Require an exact resource-free scalar root.
3. Carry tag, primitive raw bits, and reference value in one mutable cursor.
4. Invoke callbacks according to logical lane and permit cross-lane transitions.
5. If every child is scalar, return one scalar reader.
6. At the first non-scalar child, stop immediately, iteratively rebase remaining
   frames, and delegate to existing stack-safe compilation without eager work.

Invoke this from `FlatMapped.compileBlocking` and delete
`FlatMapped.runFoldLongBlocking`. Accumulator and terminal generality then comes
from ordinary sink dispatch. Generate only scalar invocation adapters if a
measured generic switch regresses the historical Int control by more than 5%.

## Verification and acceptance

- Every scalar lane and cross-lane spine collapses for every blocking terminal.
- Mixed children retain exact laziness, cardinality, order, errors, and cleanup.
- Depth 100,000 is stack-safe.
- No per-level reader/interpreter materialization on all-scalar spines.
- No public API/MiMa change.
- Existing nested-flatMap throughput/allocation regress by at most 5%; newly
  covered lanes materially improve at depth 10,000.
- No performance discontinuity at depths 99/100/101.
- Scala 2/3 JVM tests and shared-source JS tests pass; async semantics remain
  unchanged.

## Commit sequence

1. Characterize dispatch, depth, lane, terminal, and lifecycle behavior.
2. Record dedicated JMH/GC baseline.
3. Unify internal singleton scalar nodes.
4. Collapse scalar flatMap spines during blocking compilation.
5. Remove the Long-terminal override and complete fallback tests.
6. Record candidate benchmark evidence and update inventories/docs.

## Risks and effort

The major risks are evaluating callbacks beyond the first non-scalar child,
incorrect narrow-lane invocation, sentinel use for NaN/null, and incorrect
fallback rebasing/ownership. Estimated effort: 4–7 engineering days plus
benchmark and cross-build time.
