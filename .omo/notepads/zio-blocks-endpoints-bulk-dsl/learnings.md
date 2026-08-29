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

