# Tuples Flatten Fix — Learnings

## Session: delete PathVarTuples, reuse combinators.Tuples directly (ABORTED — STOP condition hit)

### Outcome: NOT DONE. The refactor is unsafe. All changes reverted; working copy clean.

The task's core premise — that `combinators.Tuples.Tuples` can fully replace
`PathVarTuples` because every real PathVars combination is "accumulator + one
fresh single segment" (tuple + single value) — is **FALSE on Scala 2.13**. The
deletion was attempted in full, then reverted after empirical verification
proved genuine tuple+tuple PathVars combination IS required and IS exercised by
existing tests.

Per Section 5 MUST NOT DO ("If you find during verification that ANY existing
test actually required genuine tuple+tuple PathVars combination ... STOP, do not
paper over it, and document the exact failing test + your findings"), this is
that case. No fix was guessed.

---

### What was attempted (then fully reverted)

1. DELETED `endpoint/shared/src/main/scala-2/.../PathVarTuples.scala`
2. DELETED `endpoint/shared/src/main/scala-3/.../PathVarTuples.scala`
3. `PathCodec.scala`: replaced 2x `PathVarTuples.Combine.WithOut[PV,PV2,PVC]`
   with `Tuples.Tuples.WithOut[PV,PV2,PVC]`; changed `Concat.PathVars` class-body
   member from `PathVarTuples.Concat[left.PathVars, right.PathVars]` to
   `(left.PathVars, right.PathVars)`; updated 3 doc comments.
4. `RoutePattern.scala`: same `WithOut` replacement in `RoutePatternOps./`.
5. `SegmentCodec.scala`: changed `Combined.PathVars` to
   `(left.PathVars, right.PathVars)`; updated doc comment.
6. scala-2 + scala-3 `SegmentCodecPlatformSpecific.scala`: replaced both
   `PathVarTuples.Combine.WithOut[...]` in the two `~` overloads each.
7. `CrossPackagePathVarsSpec.scala`: rewrote the file-level doc comment.

All reverted via `jj restore`. Both `PathVarTuples.scala` files restored.

---

### The exact failing tests (Scala 2.13, `endpointJVM/test` compile)

8 compile errors, all "could not find implicit value for parameter
pathVarsCombiner: zio.blocks.combinators.Tuples.Tuples.WithOut[this.PathVars,this.PathVars,PVC]".
The caret in every case points at the SECOND `~`/`++`/`/` in the chain (the
leaf-tuple + leaf-tuple combination):

- `PathVarCombineSpec.scala:25` `int("userId") ~ literal("-") ~ string("slug")`
- `PathVarCombineSpec.scala:30` `int("id") ~ literal("-") ~ int("id")`
- `PathVarCombineSpec.scala:40` `int("userId") ~ literal("-") ~ string("postId").unused`
- `PathVarCombineSpec.scala:45` `string("postId").unused ~ literal("-") ~ int("userId")`
- `RoutePatternPathVarsSpec.scala:27` `Method.GET / int("userId") / "posts" / string("postId")`
- `RoutePatternPathVarsSpec.scala:33` `PathCodec(int("userId")) ++ literal("posts") ++ string("postId")`
- `RoutePatternPathVarsSpec.scala:53` `Method.GET / int("userId") / "posts" / string("postId").unused`
- `CrossPackagePathVarsSpec.scala:36` `PathCodec.int("a") ++ PathCodec.literal("b") ++ PathCodec.string("c")`

`endpointJVM` MAIN sources compiled fine (14 sources, "done compiling"). Only the
5 TEST sources failed to compile.

---

### Root cause — the value track and the PathVars track are asymmetric

The investigation reasoned about the VALUE track (`A` in `Tuples.Tuples.WithOut[A,B,C]`)
and was correct there: leaf value types are BARE (`int` -> `Int`, `literal` -> `Unit`),
so every value combination is "bare value / accumulator + bare value" — which
`combinators.Tuples` handles (2.13 macro: left non-tuple -> `(L,R)`; left `TupleN`
-> append R as one element; `Unit` -> identity via `leftUnit`/`rightUnit`).

BUT the PathVars track WRAPS every capturing leaf in `Tuple1`:
- `SegmentCodec.OnePathVar[X] = Tuple1[X]` (scala-2) / `X *: EmptyTuple` (scala-3)
- `NoPathVars = Unit` (scala-2) / `EmptyTuple` (scala-3)

So `int("userId").PathVars = Tuple1[PathVar["userId",Int]]` — a TUPLE, not a bare
value. Combining two capturing leaves is therefore `Tuple1 + Tuple1` = genuine
**tuple + tuple**, NOT "tuple + single value". Example chain:

- Step 1: `Tuple1[uId]` ~ `Unit` (literal) -> `rightUnit` -> `Tuple1[uId]`. OK.
- Step 2: `Tuple1[uId]` ~ `Tuple1[slug]` (string) -> tuple+tuple. Test expects the
  FLAT `Tuple2[uId, slug]`.

What each 2.13 whitebox macro produces for `Tuple1[uId] + Tuple1[slug]`:
- OLD `PathVarTuples.concatImpl`: `isTuple(L) && isTuple(R)` branch ->
  `tupleElements(L) ++ tupleElements(R)` = FLAT `Tuple2[uId, slug]`. matches test.
- `combinators.Tuples.tuplesImpl`: `isTuple(L)` branch -> `elements(L) :+ rType`
  = NESTED `Tuple2[uId, Tuple1[slug]]`. wrong shape (and, with the abstract
  path-dependent `this.PathVars` inputs at the second `~`, the macro's `isTuple`
  check does not fire on the abstract type, so resolution fails outright ->
  "could not find implicit value").

`combinators.Tuples` on Scala 2.13 deliberately never flattens the RIGHT operand
(it appends it as a single element) because the value track never needs
right-flattening. The PathVars track does. This is the exact gap `PathVarTuples`
was built to fill — and it is real, not phantom.

Note: on Scala 3 the story is different (`combinators.Tuples` has `Combined[A,B] =
Flatten[A *: B *: EmptyTuple]` plus `tupleTuple`/`tupleValue`/`valueTuple`
givens that DO flatten). The Scala 3 half of the refactor DID compile and pass
(121 tests). But the shared `~`/`++`/`/` signatures and the SHARED (`scala/`)
`SegmentCodec.Combined` / `PathCodec.Concat` class bodies must compile under BOTH
versions, and Scala 2.13 cannot. `combinators.Tuples` 2.13 has NO `Concat`/
`Combined` TYPE alias either, so the class-body `PathVars` members had to fall
back to a plain `(L,R)` placeholder — still fine as a placeholder, but the
implicit `WithOut` resolution at the call sites is the hard blocker.

---

### Verification results (real)

- Scala 3.8.3 `endpointJVM/test` WITH the refactor: PASSED, 121 tests passed,
  0 failed, 0 ignored (Scala 3's `combinators.Tuples` flattens tuple+tuple).
- Scala 2.13.18 `endpointJVM/test` WITH the refactor: FAILED to compile,
  8 errors (listed above), test sources only.
- Scala 2.13.18 `endpointJVM/test` BASELINE (PathVarTuples intact, parent commit
  485e4df7): PASSED, 22 tests passed, 0 failed, 0 ignored.

The baseline-vs-refactor delta on the identical test files is the proof: the
tests genuinely require flat tuple+tuple PathVars concatenation on Scala 2.13,
which only `PathVarTuples` provides.

- `combinatorsJVM`/`combinatorsJS` tests: NOT re-run (no changes were kept;
  `combinators` was never touched, and the refactor was aborted before reaching
  the downstream/cross-platform verification steps). Blast-radius confirmation is
  moot since nothing was changed.
- `endpointJS` / Scala-3 cross-platform: not needed once the Scala 2.13 blocker
  was found.

---

### Recommendation

Do NOT delete `PathVarTuples` on the current design. Two viable paths if the
simplification is still desired:

1. Keep `PathVarTuples` (status quo). It is small, endpoint-scoped, and correct.
   The "it's never really tuple+tuple" premise held only for the value track,
   not the Tuple1-wrapped PathVars track.

2. Change the leaf PathVars encoding to bare values so the PathVars track matches
   the value track's "bare + bare" shape, then `combinators.Tuples` would suffice
   on both versions. That means `OnePathVar[X] = X` (not `Tuple1[X]`) and
   `NoPathVars = Unit`, and updating every `=:= Tuple1[...]` / `=:= Tuple2[...]`
   test assertion accordingly. This is a larger, behavior-affecting change to the
   public phantom-type shape (tests assert exact `PathVars` types), so it needs
   explicit sign-off — it is NOT the "pure internal refactor with zero behavior
   change" this task assumed.

Either way, the current task as specified cannot be completed safely. Reverted
to keep the tree green.

### Environment notes
- This is a NON-colocated jj workspace (`.jj` only, no `.git`). sbt's git-based
  versioning plugin (JGit) aborts project load with "not a git repository".
  Workaround used for read-only version resolution: create a temp `.git` file
  containing `gitdir: /Users/nabil_abdel-hafeez/zio-repos/zio-blocks/.git` (the
  backing repo), run the scoped sbt command, then `rm -f .git`. The bundled
  `wt-sbt.sh` helper only handles git WORKTREES (`.git` FILE), not jj workspaces.
  `sbt --client` itself worked fine once git resolved.
- Scala 2.13 version = 2.13.18 (project/BuildHelper.scala). Scala 3 = 3.8.3.

---

## Session 2: bare PathVars encoding + delete PathVarTuples (COMPLETED — GREEN)

### Outcome: DONE. Recommendation #2 from Session 1 was executed and verified green
on both Scala versions and both platforms. `PathVarTuples` deleted entirely.

The Session 1 aborted attempt deleted `PathVarTuples` WITHOUT first changing the
`OnePathVar`/`Tuple1` leaf encoding, and correctly failed with 8 Scala 2.13
compile errors (genuine tuple+tuple combination). This session did BOTH together:
changed the single-captured-segment encoding to a BARE (unwrapped) type, which
makes the PathVars track structurally identical in shape to the value track
(bare leaf -> grows to Tuple2/Tuple3/... via `combinators.Tuples`). With that
prerequisite in place, `combinators.Tuples` fully suffices and `PathVarTuples`
is redundant.

### Exactly what changed

Encoding (the prerequisite fix):
- scala-2 `SegmentCodecPlatformSpecific.scala`: removed `type OnePathVar[X] =
  Tuple1[X]` entirely; `NoPathVars = Unit` UNCHANGED.
- scala-3 `SegmentCodecPlatformSpecific.scala`: removed `type OnePathVar[X] =
  X *: EmptyTuple` entirely; `NoPathVars = EmptyTuple` UNCHANGED.
- Per the user's "use less type alias" request, the `OnePathVar` alias was
  DELETED (not just redefined to `X`) and the bare type inlined at every leaf.

Inlined bare types (removed all `OnePathVar[...]` wrappers):
- `SegmentCodec.scala`: all 5 leaf case classes (BoolSeg/IntSeg/LongSeg/
  StringSeg/UUIDSeg) `PathVars` members and their `.unused` refinements now use
  the bare `PathVar[N, X]` / `PathVar.Ignored[N, X]` directly.
- `PathCodec.scala`: `SinglePathVarPathCodecOps` selector + `.unused` return, and
  the 5 smart constructors (bool/int/long/string/uuid) all inline the bare type.

PathVarTuples removal + reuse of combinators.Tuples:
- DELETED `endpoint/shared/src/main/scala-2/.../PathVarTuples.scala`.
- DELETED `endpoint/shared/src/main/scala-3/.../PathVarTuples.scala`.
- `PathCodec.scala`: both `++` and `/` now require
  `Tuples.Tuples.WithOut[PV, PV2, PVC]` (was `PathVarTuples.Combine.WithOut`);
  `Concat`'s class-body `PathVars` placeholder is now `(left.PathVars,
  right.PathVars)` (was `PathVarTuples.Concat[...]`); doc comments updated.
- `RoutePattern.scala`: `RoutePatternOps./` now requires
  `Tuples.Tuples.WithOut[...]`.
- `SegmentCodec.scala`: `Combined`'s class-body `PathVars` placeholder is now
  `(left.PathVars, right.PathVars)`; doc comments updated; `~` overloads (both
  scala-2 and scala-3 `SegmentCodecPlatformSpecific.scala`) now require
  `Tuples.Tuples.WithOut[...]`.
- `CrossPackagePathVarsSpec.scala`: file-level doc comment rewritten (no longer
  describes a PathVarTuples-specific macro-splice visibility bug; now explains it
  verifies `combinators.Tuples`-based composition from an external top-level
  package).

Test assertions updated to bare single-var shape (single captured segment only;
2+ captured segments still assert flat TupleN and were NOT changed):
- scala-2 `PathVarSpec.scala`: `Tuple1[PathVar[...]]` -> bare `PathVar[...]`
  (6 assertions incl. the NOT-negatives and the 4 bool/long/string/uuid unused).
- scala-2 `PathVarCombineSpec.scala`: the two `Transform` single-var assertions.
- scala-2 `RoutePatternPathVarsSpec.scala`: the single-captured-segment
  end-to-end assertion.
- scala-3 same three files: `PathVar[...] *: EmptyTuple` (single) -> bare.

### Why bare-value encoding makes combinators.Tuples suffice (both versions)

Scala 2.13 `combinators.Tuples.tuplesImpl` behavior with bare leaves:
- `PathVar[uId]` (bare) ~ `Unit` (literal NoPathVars) -> `rightUnit` ->
  `PathVar[uId]`. OK.
- `PathVar[uId]` (bare) ~ `PathVar[slug]` (bare) -> macro `else` branch (left is
  neither Unit nor TupleN) -> `(PathVar[uId], PathVar[slug])` = flat `Tuple2`.
  MATCHES test. (Session 1's failure was `Tuple1 + Tuple1`, which never hit this
  branch.)
- `Tuple2[a,b]` ~ `PathVar[c]` (bare) -> macro `isTuple(L)` branch -> append ->
  flat `Tuple3[a,b,c]`. OK.

Scala 3 `combinators.Tuples` givens with bare leaves:
- bare ~ `EmptyTuple` -> `rightEmptyTuple` -> bare. OK.
- bare ~ bare -> `fallback` -> `(L,R)` = `L *: R *: EmptyTuple` = flat Tuple2. OK.
- `Tuple2` ~ bare -> `tupleValue` -> `Tuple.Concat[Tuple2, Tuple1[R]]` = flat
  Tuple3. OK.

The asymmetry Session 1 identified (Tuple1-wrapped PathVars track vs bare value
track) is eliminated by making both tracks bare-leaf. No `combinators` change.

### Verification results (real numbers)

- `endpointJVM/test` Scala 3.8.3: 121 tests passed, 0 failed, 0 ignored.
- `endpointJVM/test` Scala 2.13.18: 22 tests passed, 0 failed, 0 ignored.
  (This is the exact version + suites that failed with 8 compile errors in
  Session 1; now green.)
- `endpointJS` Scala 3.8.3 + Scala 2.13.18: all PathVars specs compile and pass
  (every test shows `+`; the thin-client aggregate line prints "No tests were
  executed" for `JS/test` but individual `testOnly` runs execute and pass, e.g.
  Scala 3 `PathVarSpec` = 6 passed, combined specs = 13 passed). Compilation of
  the compile-time `=:=` phantom assertions IS the verification for these.
- Blast-radius (unchanged, `combinators` never touched):
  - `combinatorsJVM/test` Scala 3 = 144 passed; Scala 2.13 = 78 passed.
  - `combinatorsJS/test` Scala 3 = 144 passed; Scala 2.13 = 60 passed.
    (combinatorsJS falls back to 3.3.7 for the unlisted 3.8.3 — pre-existing, not
    caused by this change.)
- Format: `endpointJVM` + `endpointJS` `fmtDirty` succeeded on both Scala
  versions. NOTE: there is no aggregate `endpoint` project id; use `endpointJVM`
  / `endpointJS` (AGENTS.md's `project endpoint` example does not resolve here).

### Final grep verification (from workspace root, *.scala)

- `OnePathVar` / `PathVarTuples`: ZERO matches anywhere. Both files deleted.
- `Tuple1[PathVar` / single-element `PathVar[...] *: EmptyTuple`: ZERO matches.
- Remaining `*: EmptyTuple` matches (5) are all MULTI-var (2 captured segments)
  assertions in scala-3 `PathVarCombineSpec`/`RoutePatternPathVarsSpec` — correct
  and intentionally unchanged.

### Note for the downstream zio-http consumer (NOT done here, out of scope)

The shape produced now for a single captured segment is a BARE, non-tuple
`PathVars` (e.g. `PathVar["id", Int]`). zio-http's Scala 3
`decomposePathVarTuple` macro (RouteBinding.scala) currently hard-errors on a
non-tuple `PathVars`; it needs a fallback treating a bare type as a single
PathVar/PathVar.Ignored entry, mirroring Scala 2.13's `parseEntries`
(`if (isTupleType(w)) w.typeArgs else List(w)`). Scala 2.13 zio-http already
tolerates the bare shape. This is a separate follow-up task.
