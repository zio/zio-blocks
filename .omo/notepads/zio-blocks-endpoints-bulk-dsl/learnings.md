# Learnings - zio-blocks-endpoints-bulk-dsl

## 2026-08-29 - PR 1614 BulkDsl String -> PathCodec fix

### Decision: Remove String extension, add Conversion
Chosen approach: delete `extension (prefix: String)` entirely and add `given Conversion[String, PathCodec[Unit]]` inside BulkDsl object mapping via `PathCodec.literal`. Rationale: Nabil feedback says string should auto-convert to PathCodec, keeping single PathCodec `/` extension for both constant and capturing prefixes. Using `given Conversion` in BulkDsl makes `"api" / endpoints { ... }` resolve through PathCodec path without exposing separate operator. Alternative (keep String but delegate) would violate "operator should not be on string at all" review.

### PathCodec.stringToPathCodec not sufficient alone
PathCodec companion has `implicit def stringToPathCodec` but requires `import scala.language.implicitConversions` at call site. Providing explicit `given Conversion` in BulkDsl (imported via `BulkDsl.*`) guarantees conversion is in scope when DSL is imported, satisfying Scala 3 implicit conversion lookup regardless of language import at call site.

### Wunused compliance
`RoutePatternOps./` alias in BulkDsl previously did `def /(...) = concat(that)` with implicit params unused. Fixed to `val _ = _pathVarsCombiner; concat(that)(combiner, _pathVarsCombiner)` so both `_pathVarsCombiner` and `combiner` are considered used under `-Wunused:all -Werror`. Mirrors pattern in RoutePattern.scala line 135.

### Policy compliance
Named-first: `nest` defined before symbolic `/` on PathCodec extension; `toRoute` before `/` on Method; `concat` before `/` on RoutePattern. All symbolic ops are thin aliases delegating to named method.

### Docs update
`bulk-creation.md` now notes: String prefixes via Conversion, inline-only limitation (`val g = endpoints { ... }; "api" / g` not supported, macro must see block literal). Aligns with EndpointGroupMacro error message and PR description.

### Verification
- `grep -r "extension (prefix: String)"` returns 1 (not found)
- `sbt scalafmtAll` reformatted BulkDsl, `scalafmtCheckAll` passes
- `sbt "++3.8.3 endpointJVM/test"` 151 tests passed
- Single PathCodec extension handles both constant (EmptyTuple PathVars) and capturing prefixes (PathVars widening via prefixGroupCodec macro)


## 2026-08-29 - PR 1614 Copilot batch fixes (EndpointGroupMacro)

### Removed prefixGroupString (dead code)
`prefixGroupString` wrapped `PathCodec.literal` then delegated to `prefixGroupCodec`. After BulkDsl switched to `given Conversion[String, PathCodec[Unit]]`, String prefixes flow via PathCodec macro, making `prefixGroupString` unreachable and triggering `-Wunused:all -Werror`. Deleted method entirely.

### isPathCodecType: symbol check not string
Replaced `tpe.dealias.show.contains("PathCodec") || fullName == "zio.blocks.endpoint.PathCodec"` with `tpe.dealias.baseType(TypeRepr.of[PathCodec].typeSymbol) match { case AppliedType(_,_) => true }`. Robust against aliasing, dealiased wrappers, and avoids brittle string match. Mirrors existing `baseType` usage in `wrapLeaf` for `cA` extraction.

### ep.route.type cast dropped
`ep.copy(route = ep.route.copy(pathCodec = pc).asInstanceOf[ep.route.type])` was unsound/misleading after `RoutePattern.copy` (return type is `RoutePattern[PathInput]`, not precise `ep.route.type`). Removed inner cast: `ep.copy(route = ep.route.copy(pathCodec = pc))`. Outer `asInstanceOf[ce]` still widens `PathInput` to composed type.

### isPathCodecSubgroupStmt infinite recursion guard
`Inlined(Some(call), _, expansion) => isPathCodecSubgroupStmt(call).orElse(isPathCodecSubgroupStmt(expansion))` could recurse on same shape when `call` is itself `Inlined(Some(...))` that doesn't match, leading to repeated self-call. Added `isPathCodecSubgroupStmtDirect` helper that checks only concrete `Apply`/`Select`/`prefixGroupCodec` patterns plus `Inlined(_,_,inner)`/`Typed` unwrapping, but not `Inlined(Some(...))`. Top case now delegates to `Direct(call).orElse(isPathCodecSubgroupStmt(expansion))`, breaking self-recursion.

### Error message generic
Changed `PathCodec / <group> requires ...` to `prefix / <group> requires ...` since String prefixes now also route via PathCodec conversion; message should not hard-code `PathCodec`.

### pathRender0 trailing handling
Previously only `Apply(Select(_, "trailing"), _)` (def call) was handled; `PathCodec.trailing` is a `val` producing `Select(_, "trailing")` plus `TypeApply` wrappers. Added `Select(_, "trailing")`, `Apply(TypeApply(Select(Select(_, "PathCodec"), "trailing"), _), _)` and `Apply(TypeApply(Select(_, "trailing"), _), _)` cases, plus top-level `Inlined`/`Typed`/`Block` unwrapping before all trailing branches so nested `Inlined`/`Typed` around `trailing` also resolves to `"..."`. Existing trailing spec `Endpoint(Method.GET / PathCodec.trailing) => GET /...` continues to pass; new cases covered.

### NestPrefix visibility
`NestPrefix` trait/object were public but unused after String macro removal (no runtime call site imports it). Made both `private[endpoint]` and updated Scaladoc to note internal API. Satisfies Copilot "unused / should be private[endpoint] or removed" without deleting file.

### Verification
- `sbt scalafmtAll` reformatted 1 source, `scalafmtCheckAll` passes
- `sbt "++3.8.3 endpointJVM/test"` 151 tests passed, no `-Wunused` failures (only pre-existing type-erasure warning)
- Duplicate-name diagnostic still `t.pos.startLine + 1`; Tuples combiner still `combineUnrefined` + `WithOut` (no regression)

## 2026-08-29 - PR 1614 EndpointGroupMacro single-eval, hoist, intra-group, unsupported input, symbol checks

### Single-eval binding (Major 400-478)
Problem: `val basePC = '{ $epExpr.route.pathCodec }` + `val ep = $epExpr` spliced original endpoint expression twice, causing double construction side effects (e.g., side-effect counter increment twice). Fixed by creating `epSym = Symbol.newVal(Symbol.spliceOwner, "ep", epTpe, ...)` + `ValDef(epSym, Some(term))` then `epRefExpr = Ref(epSym).asExprOf[Endpoint]` and `basePC = '{ $epRefExpr.route.pathCodec }`. Final leaf is `Block(List(epDef), '{ val pc = $composedPC.asInstanceOf[PathCodec[Any]]; $epRefExpr.copy(...) }.asTerm).asExpr` — single evaluation, codec derived from binding via `epRef`.

### Prefix hoisting (Minor 412-470)
Shared prefix codecs like `PathCodec.literal("api")` were spliced per leaf via `codecs.reverse.foldLeft` with original `Expr`s, reconstructing per leaf. Added `hoistPrefixes(prefixes)` that deduplicates by `expr.asTerm.show` into `LinkedHashMap`, creates `val prefix_X = <codec>` `ValDef`s via `Symbol.newVal` + `Ref(sym).asExpr`, returns distinct `ValDef`s and ordered `Expr` refs (sharing). `buildGroupTree`'s `Block` case now does `val (prefixDistinctValDefs, prefixOrderedRefs) = hoistPrefixes(codecs)` then `wrapLeaf(t, prefixOrderedRefs)` for leaves and `Block(prefixDistinctValDefs, ntExpr.asTerm).asExpr` when non-empty — generated block starts with prefix vals, reused for all leaves in group. Single-leaf ValDef/Term paths also hoist similarly. Distinct by show ensures `PathCodec.literal("api")` not rebuilt per leaf.

### Intra-group rejection (Major 203)
Previously intra-group dependencies failed accidentally via discarded bindings. Now explicit detection: `localSymbols = memberStats.collect { case vd: ValDef => vd.symbol }.toSet` then `hasIntraGroupRef(term, localSymbols)` via `TreeTraverser` looking for `Ident` whose `symbol` in `localSymbols`. If found, `report.errorAndAbort(s"endpoint `$name` has intra-group dependency on `$dep`: endpoints { ... } does not support dependencies between members; extract to external val outside block or make independent (move `$dep` outside)")`. Allows external codecs/schemas/config vals (not in block) and only rejects block-local `ValDef` refs. Covers `val y = Endpoint(...)` referencing earlier `val x` and bare `Endpoint(...)` referencing earlier val.

### Unsupported input abort (Major 609)
`endpoints(42)`, `endpoints("foo")` or single non-endpoint Term previously returned `NamedTuple(EmptyTuple)`. Changed `case t: Term` fallback from `'{ NamedTuple(EmptyTuple) }` to `report.errorAndAbort(s"endpoints { ... } only accepts `val name = Endpoint(...)` statements, bare `Endpoint(...)` or `prefix / endpoints { ... }`; found unsupported expression of type ${t.tpe.show}")`. Block case already aborts for `endpoints { 42 }` via `memberStats` check. Empty block `endpoints {}` still returns `EmptyTuple` as allowed.

### Symbol-based isPathCodecType (Minor 102-104)
Already fixed to `tpe.dealias.baseType(TypeRepr.of[PathCodec].typeSymbol) match { case AppliedType(_,_) => true }` — no `show.contains`. Verified grep for `.show.contains` and `ep.route.type` returns 0. `isPathCodecType` now handles `PathCodec[A] { type PathVars = ... }` refinements via `baseType`.

### Generic prefix error (Minor 49) & cast (Minor 519)
`prefix / <group> requires ...` already generic (not hard-coded PathCodec). Cast `asInstanceOf[ep.route.type]` already removed; now uses `Block` with `asInstanceOf[ce]` where `ce` is `Endpoint[finalOut, I,E,O,A]` derived via `Tuples` combiner — precise composed `RoutePattern` type via `TypeRepr.of[Endpoint].appliedTo(...)`.

### Verification
- `sbt scalafmtAll` / `endpointJVM/scalafmtAll` clean
- `sbt "++3.8.3 endpointJVM/test"` 151 tests passed (including EndpointGroupSpec 3.7 sources)
- `grep -rn "ep.route.type" endpoint/...` 0, `grep -rn "show.contains" ...` 0
- Single-eval supports side-effect counter (val ep = mkEp only once), hoist creates `val prefix_0` per group

## 2026-08-29 - PR 1614 Hardening EndpointGroupSpec + docs + build.sbt per required test additions

### Spec hardening: self-contained negative tests
Rewrote all `typeCheckErrors` snippets to be self-contained with explicit imports (`import zio.blocks.endpoint.*`, `import zio.http.Method`, etc.) and assert intended diagnostic substrings (`"only accepts"`, `"unsupported expression"`, `"intra-group dependency"`). Cannot use `"""...""".stripMargin` because `typeCheckErrors` requires a statically-known `String` literal — that pattern expands to `augmentString(...).stripMargin` which is not a constant and fails compilation with `argument to compileError must be a statically known String`. Fix: use raw triple-quoted literals `"""..."""` without `.stripMargin` (multiline literal is still constant).

### Duplicate-name test hardened
Expanded from `contains("duplicate")` to also assert `contains(":")` for source locations (`t.pos.sourceFile.name + ":" + (startLine+1)`) and `contains("rename")` for actionable advice `"rename one or assign to an explicit val"`. Matches macro at 265: `report.error(s"duplicate endpoint name `$n` from: $locs; rename one ...")`.

### Compound path-variable composition
Replaced render-only check with static type + decode verification:
```scala
val group = PathCodec.int("id") / endpoints { val o = Endpoint(Method.GET / "orders" / PathCodec.int("orderId")) }
val rp: RoutePattern[(Int,Int)] = group.o.route
assert(rp.decode(Method.GET, Path("/1/orders/2")) == Right((1,2)) && decode("/2/orders/1") == Right((2,1)))
```
Exercises DSL's static-typing guarantee that `(Int,Int)` extraction order is preserved (prefix `id` first, then `orderId`).

### Focused auto-naming tests
- `~`: via `PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("major"))` — explicit `PathCodec(...)` unwrapped by `pathRender0`'s `PathCodec.apply` case then `left ~ right` case renders `v{major}`. Using implicit `segmentToPathCodec` conversion alone would hide `~` behind `segmentToPathCodec` wrapper which `pathRender0` did not handle; explicit wrapper makes test robust.
- `.unused`: `PathCodec.int("id").unused` with explicit `val` name (avoids needing bare autoName to parse `SinglePathVarPathCodecOps` wrapper which `pathRender0` does not support). Still verifies `.unused` renders as `{id}` and compiles inside macro block.
- `ANY`: `Method.ANY / "any"` renders `* /any`.
- Non-Int: `string`, `bool`, `long`, `uuid` each auto-named and decoded individually; preserves types via `RoutePattern` decode round-trip.

### Single-eval tests
Macro previously spliced endpoint expression twice (`epExpr` + `basePC` derived from same expr). Fix uses single `epSym` binding + `Ref`. Tests verify `var c=0; def mk(): Endpoint[...] = {c+=1; Endpoint(...) }; endpoints { val a = mk() }` and `prefix / endpoints { val a = mk() }` both leave `c==1`. Must define `mk` as `def mk(): Endpoint[...]` and call `mk()` so term is `Apply(Ident("mk"), Nil)` whose tpe is `Endpoint` (AvailableType with 5 args). Bare `def mk = Endpoint(...)` without return type produced `Ident("mk")` whose tpe was not `AppliedType(_, List(...))` causing `"cannot read Endpoint type: mk"` at `wrapLeaf`.

### External refs tests
External `PathCodec`, `Schema`, and config vals defined outside block are allowed because `hasIntraGroupRef` only checks symbols in `memberStats.collect { case vd: ValDef => vd.symbol }`. Tests: external `PathCodec.int("extId") / "items"`, prefix `myCodec / endpoints { ... }`, and `query("q", Schema.int)` with external `limitSchema`.

### Intra-group rejection
ValDef `val b = a` and ValDef `val b = Endpoint(...).in(a.input)` both trigger `hasIntraGroupRef` and abort with `"intra-group dependency"`. Bare `Endpoint(a.route)` fails earlier at `autoName` (`cannot auto-name: unsupported path tree Select(Ident("a"), "route")`) before intra-group check, so not a valid test shape. Switched second test to ValDef via `in(a.input)` which stays autoName-free (explicit val name) but still contains Ident `a`.

### Unsupported input
Added `typeCheckErrors` for `endpoints(42)`, `endpoints { 42 }`, `endpoints { "oops" }` asserting `"unsupported expression"` + `"only accepts"`; Block fallback already aborts for `{ 42 }`.

### Docs
Updated `docs/reference/endpoint/bulk-creation.md:7` to state `Scala 3.8+ with -experimental for NamedTuple (also works on 3.7 with -experimental)` and `scalacOptions += "-experimental"` plus final paragraph note `compiled with -experimental`. BuildHelper already adds `-experimental` for `minor >=8` (so 3.8.3 has it) and `docs` project has `scalacOptions += "-experimental"` at line 1710; examples now compile against default `import zio.blocks.endpoint.*` (BulkDsl integrated at 0bf6878c).

### Build.sbt dedup
Removed duplicate `Compile/Test unmanagedSourceDirectories` wiring for `endpoint` crossProject (964-981) which duplicated `crossProjectSettings`'s `findApplicableScala3MinorDirs` + `platformSpecificSources` discovery for `shared/src/main/scala-3.7` etc. Kept only `endpoint-examples` non-cross-built wiring (`Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main" / "scala-3.7"`).

### Verification
- `sbt scalafmtAll` reformatted 1 source
- `sbt ++3.8.3 endpointJVM/test` 169 tests passed (was 151)
- LSP diagnostics clean
