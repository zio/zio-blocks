# BS-2 Remediation: Lane-General Linear Operation Fusion

## Implementation progress

The benchmark-shaped total-chain cutoff at exactly 10 operations has been
removed. Maps-only programs now use one loop at every depth. Filtered programs
use fixed power-of-two kernels: an eight-operation compact kernel and a
16-operation prefix followed by the same generic remainder loop at every longer
depth. Filter metadata populates both the first-word mask and the extended mask,
so crossing 64 operations does not change the first 64 dispatches.

The three-fork historical controls pass: `filterMap` is −1.1% and
`filterMapChain` −3.6%, while five maps improve 38.8%, ten maps improve 34.6%,
and 100 maps improve 2.0%; allocation is unchanged.

The shared `AsyncInterpreter` is now the semantic owner of linear map/filter
programs for every logical and physical lane and every terminal accumulator.
The Int direct evaluator remains a JVM adapter for the hottest physical lane;
it has no distinct operation semantics, lifecycle, cancellation, or terminal
implementation. New Long and Double chains at the former 10/11 and 64/65
boundaries, plus reference→Int→Long transitions, exercise the shared owner.
Thus the exact-10 coupling and lane-exclusive semantics are both remediated.

## Decision

Delete the benchmark-shaped ≤10 evaluator boundary and make
`AsyncInterpreter`'s lane-generic `OpTag` program plus the generalized BS-1
terminal the semantic owner. Retain a physical Int evaluator only if controlled
measurements require it, and only as an operation evaluator—not as a copy of the
scheduler, lifecycle, or terminal state machine.

## Current coupling

- `useLinearIntFoldLongDirect` and `useLinearIntFlatMapLongDirect` recognize
  only `Int` map/filter chains ending in a `Long` fold.
- `IntLinearContext.evaluate` changes implementation at ten operations, exactly
  matching `chainedMaps10` and `filterMapChain`.
- Packed filter state introduces another discontinuity at 64 operations.
- The existing interpreter already models the five physical lane carriers and
  all lane transitions through operation tags.

## Stage 1: characterize the cliffs

Add a semantic/route/allocation benchmark harness over:

- chain lengths 0, 1, 5, 9, 10, 11, 64, 65, and 100;
- maps-only, pass-all filters, reject-first filters, alternating orders, and
  clustered map/filter groups;
- every logical element and accumulator lane;
- same-lane and heterogeneous cycles such as
  `Long -> Double -> reference -> Int`;
- protected/unprotected async roots and ready-effect `mapAsync` roots;
- plain fold, linear-before-flatMap, and linear-after-flatMap boundaries.

Compare each result and exact callback trace to a pure `Vector` model and to a
reader-consumed unfused route. A rejected element must skip every downstream
operation and terminal callback. Record static and dynamic dispatch, physical
reads, effect class, JFR allocation stacks, bytes/run, bytes/element,
ns/source-element, and ns/executed-callback.

Required files/artifacts:

- `StreamLinearFusionBenchmarkSupport.scala`
- `StreamLinearFusionEvalBench.scala`
- `StreamLinearFusionSetupBench.scala`
- generated benchmark cases from `project/GenerateLinearFusionBenchmarks.scala`
- correctness additions and baseline reports under
  `streams/benchmarks/bs-2-linear-fusion/`

Keep existing cross-library parameter sets unchanged as frozen controls.

## Stage 2: generalize

### Foundation

Consume BS-1's terminal descriptor and physical accumulator registers. If BS-1
has not landed, build the foundation jointly rather than creating a second
terminal protocol.

### Operation program

- Materialize `Mapped` and `Filtered` nodes into the existing iterative
  interpreter operation program.
- Attach the generalized terminal descriptor and execute maps, filters, reads,
  and terminal folding in the same operation state.
- Keep all mutable registers and counters materialization-local so stream graphs
  remain concurrently reusable.
- Preserve callback error classification, short-circuit ordering, cleanup,
  cancellation, and the cooperative work budget.

Generate only terminal/map/filter callback adapters from one lane table. Do not
generate source × operation × terminal products. Narrow logical primitives may
share the int physical carrier but must preserve their logical callback types.

### Remove benchmark-shaped machinery

Delete `IntLinearContext`, `useLinearIntFoldLongDirect`,
`useLinearIntFlatMapLongDirect`, and the `SyncLinear*IntLong*` implementations.
Linear-before-flatMap must feed the interpreter PUSH machinery; non-linear child
traversal remains BS-4's responsibility.

Initially use one interpreter loop at every length. If measurements justify
unrolling, compare fixed factors 2, 4, and 8 by geometric mean across all depths,
mixtures, and lanes. Never restore a total-chain `length <= K` branch.

## Verification and acceptance

- Complete depth/mixture/lane semantics, including extrema, NaN, null, and all
  type transitions.
- Exact source/map/filter/fold failure provenance and cleanup suppression.
- Cancellation during pending read/fold/child work; exact-once close.
- Concurrent reuse and stack safety through depth 10,000.
- Every eligible chain length 1–100 uses one engine; no 10/11 or 64/65 cliff.
- No per-element operation-kernel allocation.
- Existing controls regress by at most 5% individually and 2% geometric mean.
- Scala 2/3, JVM/JS, targeted coverage, API, and specialization inventories pass.

## Implemented result

All chain lengths use depth-independent evaluators: maps-only programs use one
loop, filtered programs use fixed power-of-two kernels with a generic remainder,
and filter metadata remains stable across the 64-word boundary. There is no
branch keyed to a benchmark chain length. Non-Int and type-changing programs
materialize into the same lane-general async operation program and BS-1 terminal
driver. The retained Int evaluator accelerates that semantic implementation and
does not own effects, readers, scheduling, cleanup, or terminal behavior.

`AsyncOwnershipLaneSpec` now covers 11-operation Long, 65-operation Double, and
reference→Int→Long linear programs. Together with
`AsyncMappedPrimitiveRouteSpec`, it verifies the generic physical routes,
failure classification, cancellation, and source ownership. The three-fork
performance evidence is archived in
`streams/benchmarks/specialization/bs-2/candidate-power-of-two-final.json`.

## Commit sequence

1. Characterize routes, semantics, and cliff boundaries.
2. Record JMH/GC/JFR baseline.
3. Generalize fused terminal state across lanes (BS-1 dependency).
4. Route linear folds through the shared interpreter.
5. Remove all `IntLinearContext` and `SyncLinear*IntLong*` paths.
6. Record candidate results and update documentation.

## Risks and effort

Risks: lifecycle regressions, incorrect narrow-primitive adaptation, code-size
growth, and accidentally absorbing BS-4 traversal scope. Estimated effort:
4–7 engineering days after BS-1, plus 8–12 hours of benchmark runtime.
