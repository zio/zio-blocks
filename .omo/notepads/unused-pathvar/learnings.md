# unused-pathvar (zio-blocks side) — learnings

## What was built

1. **New marker type**: `zio.blocks.endpoint.PathVar.Ignored[Name <: String, Type]`
   - Location: `endpoint/shared/src/main/scala/zio/blocks/endpoint/PathVar.scala`
   - Lives in a new `object PathVar` companion, as a **sibling sealed trait**, NOT a subtype
     of `PathVar[Name, Type]`. This was a deliberate choice per the task's "clearly
     distinguishable at the type level" requirement — a type-level `PathVar[_, _]` match
     will NOT also match `PathVar.Ignored[_, _]`, so a consumer building a
     "declared but not consumed" warning off the `PathVars` tuple can positively
     distinguish "must be consumed" (`PathVar`) from "never expected to be consumed,
     no warning either way" (`PathVar.Ignored`).
   - Fully public (no `private[endpoint]`), same as `PathVar` itself — required for
     zio-http's macro (`handler(fn)`) to inspect it across the package boundary on
     Scala 2.13, per the known cross-package macro-splice visibility bug from the prior
     `route-pattern-typed-vars` plan (see `PathVarTuples`' own scaladoc/history).
   - Zero runtime footprint: `sealed trait`, never instantiated, exactly like `PathVar`.

2. **`.unused` builder method** added to all 5 leaf capturing segment case classes in
   `SegmentCodec.scala`: `BoolSeg`, `IntSeg`, `LongSeg`, `StringSeg`, `UUIDSeg`.
   - One method per case class (not a shared helper) — each is a one-line
     `this.asInstanceOf[...]` returning the exact same instance with a refined
     `PathVars` type: `OnePathVar[PathVar[N, T]]` → `OnePathVar[PathVar.Ignored[N, T]]`.
   - `A`, `Prefix`, `Suffix`, `doc`, `examples`, encode/decode — all 100% unchanged;
     `.unused` never allocates, never wraps, purely a type ascription. This matches the
     existing zero-cost phantom-refinement pattern already used throughout this file
     (e.g. `SegmentCodecOps.~`, `.transform`, `combineValidated`).
   - The existing (non-`.unused`) builders `bool`/`int`/`long`/`string`/`uuid` and their
     case classes were NOT modified in any way other than adding this new method —
     100% additive.

3. **`PathVarTuples` (`Concat`/`Combine`) required NO changes.** Both the Scala 2.13
   whitebox-macro implementation (`PathVarTuplesMacros.concatImpl`, tuple-arity
   concatenation) and the Scala 3 match-type implementation (`type Concat[L, R] = L
   match { case EmptyTuple => R; case h *: t => h *: Concat[t, R] }`) are fully generic
   over the tuple ELEMENT type — neither implementation inspects whether an element is
   `PathVar[_, _]` vs `PathVar.Ignored[_, _]` vs anything else. So `Ignored` markers
   already propagate through `~` (`SegmentCodec.Combined`), `/`/`++` (`PathCodec.Concat`),
   and `RoutePattern`'s `/`, in order, alongside regular `PathVar` markers, with **zero
   changes needed**. This was verified empirically with new tests (see below), not just
   assumed.

4. **`Transform` propagation** (`SegmentCodec.Transform`/`PathCodec.Transform`, both
   already `type PathVars = codec.PathVars`) was also confirmed via test to pass
   `Ignored` markers through completely unchanged — no code change needed, as expected.

5. **`PathCodec.int/long/string/bool/uuid` smart constructors were NOT touched.** They
   redeclare `PathVars = SegmentCodec.OnePathVar[PathVar[N, T]]` directly, but a
   downstream consumer who wants an "unused" `PathCodec`-level segment can just write
   `PathCodec(SegmentCodec.int("id").unused)` (or rely on the `segmentToPathCodec`
   implicit conversion, which is fully generic over `PathVars` and needs no change) —
   confirmed via test (`RoutePatternPathVarsSpec`: `Method.GET / SegmentCodec.int(...) /
   "posts" / SegmentCodec.string("postId").unused`). Adding a redundant `.unused` on the
   `PathCodec` smart constructors themselves was out of scope per the task's explicit
   deliverables list (only the 5 `SegmentCodec` leaf builders were required).

## Tests added

- `PathVarSpec.scala` (scala-2 and scala-3): `.unused` produces
  `OnePathVar[PathVar.Ignored[N, T]]` (positive `=:=` check) and is provably NOT
  `OnePathVar[PathVar[N, T]]` (negative `typeCheck`/`isLeft` check, mirroring the
  existing file's style).
- `PathVarCombineSpec.scala` (scala-2 and scala-3):
  - `int ~ literal ~ string.unused` → ordered 2-tuple with `PathVar` first, `Ignored`
    second.
  - `string.unused ~ literal ~ int` → ordered 2-tuple with `Ignored` FIRST, `PathVar`
    second (proves order-preservation is symmetric, not just "Ignored always last").
  - `Transform` on an `.unused` segment passes the `Ignored` marker through unchanged.
  - Runtime round-trip: `SegmentCodec.formatSegment`, `SegmentCodec.decodeCombined`,
    `SegmentCodec.kind`, `SegmentCodec.key` are asserted byte-for-byte/value-for-value
    identical between `SegmentCodec.int("id")` and `SegmentCodec.int("id").unused` —
    proves `.unused` has zero effect on `A`/wire format/runtime dispatch.
- `RoutePatternPathVarsSpec.scala` (scala-2 and scala-3): end-to-end
  `Method.GET / int(...) / "posts" / string(...).unused` produces the correct ordered
  `PathVars` tuple all the way through `PathCodec`/`RoutePattern` composition.

## Surprises / infra notes

- This jj workspace (`zio-blocks-unused-pathvar`) was NOT colocated with a git worktree
  when the task started (no `.git` at all in the workspace root), which broke `sbt`
  entirely (`sbt-git`/`sbt-ci-release` plugins shell out to `git describe`/`git
  rev-parse` during project loading, and fail hard with "not a git repository" +
  an interactive "(r)etry/(q)uit/(l)ast/(i)gnore?" prompt that hangs non-interactive
  sessions). Fixed by manually wiring git worktree metadata (mirroring what
  `git worktree add` does under the hood, since the target directory already had
  jj-managed files and `git worktree add` refuses to target a non-empty directory):
  1. `mkdir -p <main-repo>/.git/worktrees/<workspace-name>`
  2. write `HEAD` (commit sha, detached), `commondir` (`../..`), and `gitdir`
     (absolute path to `<workspace>/.git`) into that directory
  3. write `<workspace>/.git` as a file containing
     `gitdir: <main-repo>/.git/worktrees/<workspace-name>`
  4. `git read-tree HEAD` inside the workspace to populate the index (otherwise
     `git status` shows everything as a staged deletion)
  - If future tasks in this repo hit the same "not a git repository" / hung sbt-load
    prompt in a jj workspace, this is very likely the cause — check for a missing
    `.git` file/dir at the jj workspace root first.
- The sbt --client server connection dropped mid-command twice during this task
  (once before `endpointJVM/test` on Scala 2.13, once before `fmtDirty` on Scala 2.13)
  with no error, just "sbt server connection closed." — a plain retry of the exact
  same command succeeded both times. Likely resource contention from concurrent
  sibling-workspace sbt usage on this machine (many other `zio-blocks-*` jj workspaces
  exist and may be running sbt concurrently). Not related to this change.
- Scala 2.13 cross version for this repo at the time of this task: `2.13.18`
  (from `sbt 'show crossScalaVersions'`).

## For the downstream zio-http task

- Consume the marker as `zio.blocks.endpoint.PathVar.Ignored[Name <: String, Type]`
  (fully public, sibling type to `zio.blocks.endpoint.PathVar[Name, Type]`, NOT a
  subtype).
- A `SegmentCodec`'s (or, transitively, `PathCodec`'s/`RoutePattern`'s) `PathVars`
  tuple can now contain a mix of `PathVar[N, T]` and `PathVar.Ignored[N, T]` entries,
  in declaration order — same tuple encoding as before (`Tuple1`/`TupleN` on Scala
  2.13 via `OnePathVar`/`PathVarTuples.Combine`, `*: EmptyTuple` on Scala 3), just with
  some elements now typed as `Ignored` instead of plain `PathVar`. No change to how
  the tuple itself is discovered/walked — only to what you do when an element's head
  type is `Ignored` vs plain `PathVar` (e.g. via a match type / type-level fold that
  distinguishes the two, since `Ignored` is intentionally NOT a subtype of `PathVar`).
- Any macro logic that currently does "for each `PathVar[N, T]` in `PathVars`, require
  a handler parameter named `N` of type `T`, else warn" should be extended to: "for
  each `PathVar.Ignored[N, T]`, do nothing (no warning if consumed, no warning if not
  consumed)."
- Producing an `Ignored` entry: call `.unused` on any leaf capturing `SegmentCodec`
  builder result (`SegmentCodec.int("id").unused`, `.string(...).unused`, etc.) or via
  the corresponding `PathCodec`/`RoutePattern` DSL chain (`Method.GET / int("id").unused
  / ...`, `PathCodec.int("id").unused / ...` if you build one via
  `PathCodec(SegmentCodec.int("id").unused)`) — behaves identically at runtime to the
  non-`.unused` version.
