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
