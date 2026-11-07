# BS-11 Remediation: Operation-Neutral Concat Traversal

## Oracle decision

Generalize concat traversal at the stream materialization boundary rather than
adding more fold-specific overrides. Introduce one private iterative concat-leaf
cursor, expose it through existing reader/compiler paths, and retain primitive
specialization only for leaf reads and terminal invocation.

Changing element type, accumulator type, terminal, or equivalent public entry
point must no longer determine whether concat structure is traversed directly.

## Characterization before implementation

The current Int/Long path allocates a pending array from cached `leafCount`,
pushes right before left, and delegates each leaf to its direct Long fold. The
generic path is already stack-safe and has reader-level flattening; the benefit
to characterize is avoided materialization, wrappers, and transitions—not
unique stack safety.

Compare equivalent APIs over identical concat trees:

- the JVM Long `runFold` overload;
- explicit generic `runFold[Long]`;
- `run(Sink.foldLeft[Int, Long])`; and
- mapped/composed sink forms.

### Required matrix

- All primitive and reference element/accumulator categories on small irregular
  trees, using asymmetric values and noncommutative folds.
- Collect, drain, count, foreach, head/last, exists/forall/find, `Sink.take`, and
  a custom sink.
- Left-, right-, balanced-, and irregular trees at depths 99, 100, 101, 1,000,
  10,000, and a larger stress depth.
- Empty and unequal leaves, repeated subtree references, compact shared trees
  consumed with `take(1)`, and multi-element leaves.
- Operations inside and outside concat boundaries, including map/filter,
  drop/take across leaves, stateful scan, wrapping, recovery, and finalizers.
- Exact primitive lanes, extrema, NaNs, signed zero, reference identity/null,
  scalar and bulk reads, partial reads, skip/reset where supported.
- Acquisition/read/map/fold/close failures, primary plus cleanup failures,
  early termination, rematerialization, mixed sync/async leaves, and cancellation
  during pull, acquisition, and cleanup.

Use an independent sequence/iterator model. Assert exact values, callback/event
logs, acquisition and close timing, and nonzero-seed fold results; comparing two
production paths or sum checksums is insufficient.

Add a dedicated concat traversal JMH suite while retaining the original
cross-library cell unchanged. Hold emitted element count constant when isolating
tree shape; independently scale node count. Prebuild stream descriptions in
trial setup and create execution state per invocation.

## Target ownership

The cursor owns only pending stream descriptions:

- expand concat nodes iteratively;
- push right before left;
- yield the next opaque leaf; and
- clear consumed/pending references on abandonment.

Use a growable depth-based stack rather than allocation proportional to total
leaf count, especially for right-associated trees and early termination. The
reader owns the active leaf and all resources. A terminal consumes one logical
reader and one terminal state; do not run an arbitrary sink separately per leaf.

Flatten concat nodes only. `ensuring`, recovery, maps, scans, deferred
construction, asynchronous effects, and representation-normalization boundaries
remain opaque unless independently proven safe. Repeated references must execute
repeatedly.

Primitive adapters may retain exact reads, collision-free EOF handling,
unboxed accumulator invocation, and allocation-free singleton execution. They
must consume the shared structural cursor rather than own another tree walk.

## Staged implementation

1. Add characterization tests and JMH controls; archive throughput and allocation
   baseline and resolve any semantic disagreement first.
2. Extract the private concat cursor while temporarily retaining current dispatch.
3. Route blocking compilation, stack compilation, and generic concat
   materialization through the cursor-backed reader owner, preserving guards and
   async cleanup.
4. Preserve primitive leaf/terminal adapters over the common cursor and remove
   the independent blocking Int/Long tree walker.
5. Verify all versions/platforms/downstreams, record candidate results, and
   update the audit.

Likely files are `Stream.scala`, optionally
`internal/ConcatTraversal.scala`, `io/Reader.scala`, and
`internal/SyncInterpreter.scala`. Change `Sink.scala` only if measurement
requires terminal integration. Do not delete `leafCount` until its async users
have also been addressed.

## Performance gates and definition of done

- Zero semantic regressions in ordering, demand, callbacks, errors, and resource
  ownership.
- No reproducible regression greater than 5% in original Int→Long and shallow
  non-concat controls.
- Newly generalized deep cells show structural materialization/allocation or
  throughput benefit without a new per-element allocation slope.
- Full traversal scales with visited nodes plus emitted elements; pending storage
  scales with depth, not total leaves; no larger JVM stack is required.
- One operation-neutral enumerator serves compiler/reader paths and retained
  adapters; no independent Int/Long-only tree walk remains.
- All element/accumulator categories receive structural traversal while physical
  leaf adapters remain separate.
- Scala 2/3 JVM, supported JS, coverage, downstream checks, and formatting pass
  with no public API or overload-resolution change.

## Completed implementation and measurements

`Concatenated` now delegates Int, Long, Double, and generic blocking folds to a
single iterative `ConcatLeafCursor`. The cursor expands only concat nodes,
retains left-to-right order, clears consumed references, and sizes pending
storage from traversal depth rather than total leaf count. Primitive leaf folds
remain typed representation adapters; they no longer own a separate tree walk.

Three-fork JMH for the unchanged 10,000-leaf benchmark:

| Metric | Baseline | Generalized | Change |
|---|---:|---:|---:|
| Throughput | 23,902.763 ± 226.139 ops/s | 27,411.720 ± 534.848 ops/s | +14.7% (significant) |
| Allocation | 40,024.294 B/op | 40,024.256 B/op | unchanged |

Artifact: `streams/benchmarks/specialization/bs-11/candidate-final-generalized.json`.
