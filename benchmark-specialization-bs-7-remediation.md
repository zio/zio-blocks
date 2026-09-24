# BS-7 Remediation: Ownership-Preserving Async Graph Construction

## Decision

Represent protected/unprotected acquisition ownership independently from element
lanes. Generalize source-aware map/filter/take/drop graph construction through
one immutable operation spine and the BS-1/BS-2/BS-3 interpreter kernel. Generate
thin lane adapters only—never source × operation × ownership state machines.

Stage 1 is independent. Stage 2 waits for BS-1 through BS-3.

## Current coupling

- `fromReaderAsync` selects `ProtectedIntAsyncSource` only for Int.
- Mapping that source can produce `ProtectedIntAsyncMappedInt` or
  `ProtectedIntAsyncMapped`, retaining raw acquisition without a source wrapper.
- Only its `drop.take` shape gets `ProtectedIntAsyncTakeDrop`.
- Only mapped Int output gets `IntMapped`; other outputs use generic/inferred
  mapped nodes.
- Other lanes, ownership modes, and operation orders retain wrapper chains even
  though acquisition ownership is orthogonal to value representation.

Graph construction and terminal execution routes must be measured separately;
an allocation-minimal graph node does not prove specialized execution.

## Stage 1: characterize construction and routing

Add `AsyncOwnershipGraphConformanceSpec.scala` and a separate lifecycle/reuse
suite.

### Matrix

- Protected and unprotected acquisition.
- Every input lane and all 9² map input/output products.
- Source, map, filter, take, drop, drop/take, all ordered two-operation pairs,
  representative mixed chains, and `mapAsync(identity).map` boundary control.
- Construction allocation versus preconstructed execution.
- Ready all-lane runs; first-read pending all-lane runs; per-element pending for
  representative physical lanes.
- Sizes 0, 1, 1000, and 2049.

Record concrete graph-node category, whether the temporary source wrapper stays
reachable, logical/physical lane, direct/interpreter/fallback route, physical
reader method, allocation, throughput, and acquisition/read/callback/close
counts. Prove construction invokes no acquisition or callback and preserves
exact element representation.

Inject acquisition/read/callback/close failures and cancellation races. Run one
immutable graph concurrently at least 16 times, requiring distinct readers and
one close each; cancel half the runs in a second test to expose shared state.

Add ZB-only append-to-root and whole-graph allocation diagnostics to async setup,
matching sync controls, and preconstructed execution diagnostics to async micro
benchmarks. Archive JMH/GC/JFR baseline under
`streams/benchmarks/specialization/`.

## Stage 2: generalize

### Ownership root and operation spine

Introduce a lane-independent async source root carrying raw acquisition,
element representation, protected/unprotected mode, and render metadata.
Protected acquisition remains callback-normalized; unprotected trusted internal
acquisition remains direct. Both transfer reader cleanup ownership to the same
interpreter bracket.

Add one immutable source-owned linear graph representation for map, filter,
take, and drop. Each combinator appends one ordered tagged node. Materialization
walks the spine iteratively into BS-2's lane-neutral program and BS-3's slice
state. Never commute slices around map/filter, and keep all counters and pending
state materialization-local.

### Generated output adapters

Replace `GenericMapped`/`IntMapped` asymmetry with adapters for all nine output
lanes, for ordinary and source-owned mapped nodes. These adapters hard-code only
logical representation and callback field shape; they may not read, schedule,
cancel, or close. If thin classes produce code-cache/megamorphism issues, use
generated singleton lane descriptors instead.

### Shared execution and cleanup

Materialize source-owned graphs entirely into the BS-1/BS-2/BS-3 interpreter:
lazy acquisition, linear operations, ordered slices, terminal descriptor, and a
single lifecycle owner. Delete `ProtectedIntAsyncSource`,
`ProtectedIntAsyncMapped`, `ProtectedIntAsyncMappedInt`,
`ProtectedIntAsyncTakeDrop`, `IntMapped`, and dedicated dispatch branches.

## Verification and acceptance

- Public API, overloads, rendering, known length, laziness, and error trust are
  unchanged.
- Every materialization acquires/closes once; cancellation is safe at every
  phase; concurrent reuse has no shared mutable state.
- Protected and unprotected runs differ only in acquisition error classification.
- No BS-7-specific executor, terminal state machine, or ownership subclass pair.
- Generated code contains only a bounded all-lane adapter family.
- Existing protected Int and IntMapped controls regress by at most 5% in
  throughput/bytes per operation.
- Construction object count is lane-independent and the first source-owned
  operation allocates one graph node with no separate operation object.
- Former generic lanes use the shared specialized physical-lane route.

## Implemented result

Protected acquisition is now represented by the lane-independent
`ProtectedAsyncSource`. Source-owned map and drop/take nodes retain its raw
acquisition closure and explicit `ElementRepresentation` for every lane, so
graph ownership no longer depends on `Int`. Their materialization delegates to
the shared async interpreter and its single lifecycle owner. The concrete
Int-to-Int mapped node remains only as a callback-field/JVM adapter; it delegates
all semantics to the same generic mapped graph.

`AsyncOwnershipLaneSpec` verifies construction laziness, one acquisition per
materialization, mapped representation transitions, and source-owned slicing
for Long, Float, Double, Boolean, and reference lanes. The existing exhaustive
Int pending/failure/cancellation suite remains green.

The three-fork `nativeReady` control at N=1000 measured 500,503 ops/s and
232.014 B/op. The preceding three-fork current-state control measured 437,703
ops/s, so ownership generalization did not regress the protected Int path
(+14.3% throughput, lower allocation). The result is archived at
`streams/benchmarks/specialization/bs-7/candidate-native-ready.json`.

## Commit sequence

1. Characterize graph semantics, lifecycle, routes, and concurrent reuse.
2. Add construction and prebuilt-execution benchmark matrices.
3. Record baseline route/allocation report.
4. Generate output-lane adapters and drift checks.
5. Encode acquisition ownership independently.
6. Preserve ownership through the linear graph spine.
7. Complete cancellation/reuse/cross-platform tests.
8. Remove Int-only ownership graph nodes.
9. Record candidate results and update inventory/docs.

## Dependencies, risks, and effort

Stage 2 depends on BS-1 terminal/lifecycle ownership, BS-2 linear operations,
and BS-3 ordered slice state. Risks are operation reordering, eager acquisition,
double cleanup, shared graph mutation, and adapter code growth. Estimated effort:
8–12 engineering days plus benchmark/cross-build time.
