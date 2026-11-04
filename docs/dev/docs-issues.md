# ZIO Blocks: documentation issues

Documentation errors (pages, Scaladoc and examples that contradict the code) and docs-tooling problems (mdoc, the docs build, the example projects) found while writing the tutorials, guides and Scaladoc in `docs/dev/backlog.md`. Library bugs and usability traps are in [`library-issues.md`](library-issues.md). When an entry is fixed, add the PR and move it to [Resolved](#resolved).

"Verified" means a session reproduced the problem by running code. "From source" means it was found by reading the code and has not been run.

**Who edits this file:** only the coordinating session. Writing sessions send complete entries to it (see AGENTS.md).

Contents:

- [Documentation errors](#documentation-errors)
- [Docs tooling and examples](#docs-tooling-and-examples)
- [Resolved](#resolved)

## Documentation errors

Pages that contradict the source. None of these were copied into the new guides.

### `config-source.md` gives the wrong reason why `SysPropSource` is empty on Scala.js

Found 2026-11-03 while unifying the JVM and Scala.js Scaladoc of `SysPropSource` (config Scaladoc rollout, task 1), on `scaladoc-config` at `593c7f344`. Same at tag `v0.0.56`. Verified by running on Scala.js 1.22.0 under Node.js.

**Summary.** The "Scala.js behavior" note in `docs/reference/config/config-source.md:76-78` says "`SysPropSource` always returns empty results, since JS has no system properties." The first half is true; the reason is not. Scala.js emulates `java.lang.System.setProperty` and `getProperty`, and `SysPropSource` ignores them: it is hard-coded to return nothing on Scala.js. A reader who sets a property with `System.setProperty` would reasonably expect the source to see it.

**Reproduction** (`SysProp.scala`, run with `scala-cli run --server=false --js-cli-on-jvm SysProp.scala`):

```scala
//> using scala 2.13.18
//> using platform scala-js
//> using jsVersion 1.22.0
//> using dep dev.zio::zio-blocks-config::0.0.56

import zio.blocks.config._

object SysProp {
  def main(args: Array[String]): Unit = {
    System.setProperty("app.port", "9090")
    println(System.getProperty("app.port"))
    println(SysPropSource.get("app.port").toOption)
  }
}
// 9090
// None
```

**Expected.** "`SysPropSource` always returns empty results on Scala.js, even for properties set with `System.setProperty`."

**Root cause.** `config/js/src/main/scala/zio/blocks/config/PlatformConfigSource.scala:85-87` returns `Maybe.absent` and `Map.empty` unconditionally. The Scaladoc of the JS copy carried the same false reason and is corrected in the config Scaladoc PR.

Flags read `System.getProperty` and do see such properties on Scala.js (a `StaticFlag` resolves from one, source `SystemProperty`); only `SysPropSource` ignores them.

**Impact.** Small: a wrong explanation, but it hides the fact that the emulated properties are not read.

**Suggested fix.** Replace the sentence with the expected text above. (If the library should read the emulated properties instead, that is a one-line change to the JS `SysPropSource`.)

### `reference/http-model/model.md`, `headers.md` and `server-sent-event.md`: eleven statements that contradict http-model 0.0.56

Found 2026-11-04 while fact-checking the reference pages against the source for the http-model Scaladoc (branch `http-model-scaladoc`, base `main` `5eb809c8f`; the http-model sources are identical at tag `v0.0.56`). Each item was checked with the reproduction below or by compiling the snippet.

**Items** (page:line on `5eb809c8f`, then the correct statement):

1. `model.md:505`: "Returns: Either[ParseError, URL]". There is no `ParseError`; `URL.parse` returns `Either[String, URL]`.
2. `model.md:567`: `Path.fromEncoded("%2Fapi%2Fv1%2Fusers") // Parse from percent-encoded string` suggests the path `/api/v1/users`. `fromEncoded` splits at `/` before decoding, so this gives one segment `/api/v1/users` and no leading slash. Use `Path.fromEncoded("/api/v1/caf%C3%A9")` to show decoding.
3. `model.md:590`: "`QueryParams` is an immutable, case-insensitive multi-map". Keys are compared exactly: `QueryParams("Page" -> "1").get("page")` is `None`.
4. `model.md:293-297`: "`Status` is an opaque type alias for `Int`". Only on Scala 3. On Scala 2.13 it is `final case class Status(code: Int) extends AnyVal`, and `toString` gives `Status(200)` instead of `200`. Say both.
5. `model.md:1349-1379` ("Lazy Header Parsing"): `request.headers.get("authorization")` and `headers.get("content-type")` do not compile; `Headers#get` takes a `Header.Codec[A]`, not a name (compile error below). Use `headers.get(Header.Authorization)` and `headers.get(Header.ContentType)`, or `rawGet` for the strings. The comment "parsed and cached as: ContentType(mediaType=ApplicationJson, charset=UTF8)" names no real value either.
6. `headers.md:569`: the catalog row for `Header.Trailer` says `value: String`; the field is `headers: Chunk[String]` (`Header.scala:768`).
7. `headers.md:314`: "the class is immutable and the arrays are never mutated in place". A typed read stores the parsed value in the entry's cache slot in place (the page's own "The Parse Cache" section, `:262-272`, says so). Say that the names and values are never changed in place.
8. `server-sent-event.md:110`: "Four methods produce a modified copy", followed by a table of five (`event`, `clearEvent`, `id`, `retry`, `clearRetry`).
9. `server-sent-event.md:276`: "two envelopes are equal when they would render identically". Equality compares the payloads with `==`, so `ServerSentEvent("a")` and `ServerSentEvent(Chunk("a"))` render the same text but are not equal. Say "equal payloads and equal metadata".
10. `model.md:293`: "predefined constants for all standard codes (1xx–5xx)". There is no constant for 208, 226, 423, 424, 506, 508 and 510, and `Status(c).text` gives `Unknown` for each (run on 3.9.0 and 2.13.18). Say "constants for the common codes".
11. `model.md:285`: `m.ordinal // ordinal position for sorting`. `Method.ANY` and every `Method.Methods` have ordinal -1. Say "0 to 8 for the standard methods, -1 for `ANY` and `Methods`".

**Reproduction** (Scala 3.9.0, `zio-blocks-http-model` 0.0.56), `DocsRepro.scala`, run with `scala-cli run --server=false DocsRepro.scala`:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-model:0.0.56
import zio.http._
import zio.blocks.chunk.Chunk

object DocsRepro {
  def main(args: Array[String]): Unit = {
    val parsed: Either[String, URL] = URL.parse("https://example.com/api/users") // compiles: the error type is String
    println(parsed.isRight)
    println(Path.fromEncoded("%2Fapi%2Fv1%2Fusers").segments)
    println(Path.fromEncoded("%2Fapi%2Fv1%2Fusers").hasLeadingSlash)
    println(QueryParams("Page" -> "1").get("page"))
    println(Header.Trailer(Chunk("expires", "digest")).renderedValue)
    println(ServerSentEvent("a") == ServerSentEvent(Chunk("a")))
    println(ServerSentEvent("a").render == ServerSentEvent(Chunk("a")).render)
    println(Status.Ok.toString)
  }
}
// true
// Chunk(/api/v1/users)
// false
// None
// expires, digest
// false
// true
// 200
```

With `//> using scala 2.13.18` the last line prints `Status(200)`.

Item 5, `GetString.scala`, run with `scala-cli compile --server=false GetString.scala`:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-model:0.0.56
import zio.http._
object GetString { val headers = Headers("authorization" -> "Bearer abc123"); val t = headers.get("authorization") }
// -- [E007] Type Mismatch Error: GetString.scala:4:98
// Found:    ("authorization" : String)
// Required: zio.http.Header.Codec[A]
```

**Impact.** Items 2, 3 and 5 mislead readers about behaviour they rely on (case-insensitive query keys, decoding of `%2F`, a header read by name); item 5's block is a plain fence, so mdoc never compiled it. The others are small inaccuracies.

**Suggested fix.** Apply the corrections above; turn the "Lazy Header Parsing" block into `mdoc:compile-only` so it is compiled.

## Docs tooling and examples

Docs build, mdoc and example-project problems (moved here from the old "Test and CI" section).

### mdoc: `mdoc:fail` on Scala 3 ignores earlier `:reset` sections

Seen 2026-10-20 while writing H-02. A `mdoc:fail` block after a `mdoc:silent:reset` reports bogus "already defined" errors for names from before the reset, so the expected compile error cannot be shown. H-02 described that error in prose instead. Pages that rely on `mdoc:fail` (H-31) should check their rendered output.

### mdoc: `mdoc:fail` and `mdoc:warn` also compile earlier `compile-only` blocks

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31). This is docs tooling (mdoc), not a library bug. Reproduced with this repo's `docs/mdoc`.

**Summary.** A `mdoc:fail` or `mdoc:warn` block is checked by compiling a program made of every earlier evaluated block *plus every earlier `compile-only` block*, pasted in without their own scope. A `compile-only` fix that redefines a name from the same section compiles fine on its own, but a later `mdoc:warn` then fails with no reason given. Later `mdoc:fail` blocks can fail for the clash instead of their intended error.

**Reproduction** (`sbt "++3.9.0; docs/mdoc --in docs/guides/zz-trap.md --out /tmp/zz-trap.md"`):

````markdown
```scala mdoc:silent:reset
class Book(val title: String)
```

```scala mdoc:compile-only
final case class Book(title: String)
```

```scala mdoc:warn
val two = { 1; 2 }
```
````

```
error: zz-trap.md:15:1: Expected compile warnings but program failed to compile
val two = { 1; 2 }
^^^^^^^^^^^^^^^^^^
info: Compiled in 17.99s (1 error)
```

Changing the second fence to `scala mdoc:silent:reset` makes the run pass.

**Root cause.** mdoc's fail/warn instrumentation builds one program from the earlier sections and inlines `compile-only` code into its section's class, so the two `Book` definitions clash. Run `docs/mdoc --verbose` to see it: the hidden program is the `FailSection("...")` string in the instrumented output.

**Workaround.** Start any fix block that redefines a name with `mdoc:silent:reset`, not `compile-only`. Always read the rendered `// error:` lines of every `mdoc:fail` block.

### `schema-examples` has no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### Example projects' tests never run in CI

Found 2026-10-20 while writing H-47. CI's "Compile example projects" step (`project/CiWorkflow.scala:360`, generated into `.github/workflows/ci.yml`) runs `sbt compile` in each `*-examples/` build, so `src/test` sources are never compiled or run. `sql-examples` is the first examples build with tests (H-47's `BookshopSpec`, 4 tests, passing locally against 0.0.56). Switching the step to `sbt test` (and regenerating `ci.yml`) would cover them. This is a CI change for the maintainer to decide; H-47's attempt to make it was blocked by a permission check, so it was left alone.

### Three example builds are not registered in the root build

Found 2026-10-20 while recording H-37. `config-examples` (T-05), `sql-examples` (T-07) and `html-examples` (T-09) are standalone builds with their own `build.sbt` but no `lazy val ... = RootProject(file("..."))` line or root `aggregate(...)` entry in `build.sbt`, unlike the other standalone `*-examples/` builds. (`projection-examples` is deliberately a root subproject, `lazy val \`projection-examples\` = project` at `build.sbt:560`, with no `build.sbt` of its own, so it is not affected.) CI's examples loop still compiles them (it looks for a `build.sbt`), so the gap shows up in the root aggregate and IDE import. AGENTS.md (updated on the `jwt` branch, H-37) now says a new examples build needs both. Add the three missing entries.

### Docs build memory with many sbt servers

Seen 2026-10-20: a full `sbt check` was OOM-killed while several worktrees each ran an sbt server (about 4 GB free). Shut down idle servers in finished worktrees with `sbt --client shutdown`.

### `schema-examples` and `streams-examples` have no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. The same gap exists in `streams-examples` (seen while writing M-04). Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### A subagent killed every sbt server on the machine

Seen 2026-10-20 around 23:40 (+0330) while H-30 was written: its examples-builder subagent ran `pkill -f sbt-launch`, which stops the sbt servers of every session on the machine, not only its own. Any session that lost its server around then should rerun its last sbt step. H-30 added a "never kill sbt by pattern" rule to the docs-examples skill; stop a server with `sbt --client shutdown` from its own worktree instead.

### htmx examples: the `zioBlocks` helper in `zio-blocks-htmx-examples/build.sbt` used the 3.10 artifact rule for 0.0.56

Found 2026-10-23 while writing T-10 (`docs/guides/add-interactivity-with-htmx.md`), from `origin/main` `a643b7cb0`. Same at tag `v0.0.56`.

**Summary.** The helper chose `zio-blocks-next-*` only for Scala 3.10 and later. Version 0.0.56 publishes the `next` artifacts for Scala 3.9 and later, which is the rule `http-model-examples` uses. On Scala 3.9.0 the examples therefore resolve `zio-blocks-http-htmx` instead of the `next` artifact.

**Reproduction.** Compare the `minor >= 10` line in `zio-blocks-htmx-examples/build.sbt` on main with the `minor >= 9` line in `http-model-examples/build.sbt`. The sonnet and Haiku runs both changed this line in their branches; the resolution output was not captured on main.

**Expected.** Both example builds use the same rule.

**Root cause.** `zio-blocks-htmx-examples/build.sbt`, the `zioBlocks` helper.

**Impact.** A latent artifact mismatch for the htmx examples on Scala 3.9.x. CI compiles the examples but cannot show which artifact name resolved.

**Test gap.** None: CI does not check which artifact is used.

**Suggested fix.** Change `minor >= 10` to `minor >= 9` in that helper, as the one-line fix in the T-10 branches does.

**Update (2026-10-29, H-57).** The comparison no longer holds: on `main` `9d8bb9d70`, `http-model-examples/build.sbt:23` also reads `minor >= 10`, the same rule as htmx. On Scala 3.9.0 both resolve the plain `zio-blocks-*_3:0.0.56` artifacts, which compile and run (the H-57 examples do), so nothing is broken today. The real gap is the docs-wide install-rule pass planned for the next release (see backlog, "Install-rule pass after the next release").

### streams-examples does not compile on Scala 2.13: `NioBinaryTelemetryWalkthrough.scala` uses `import zio.blocks.streams.*`

**Status (2026-10-29).** Fixed in #1897 (merged 2026-10-29). The scope was 18 files with `import x.*`, plus 12 Scala 3-only files that `build.sbt` now skips on Scala 2.x: 11 call zio-sbt-source 0.8.6 `ExprEval.show`, whose macro expands on 2.13 to the `private[sbt]` `SourceReader.commentsAbove` ("cannot be accessed"); `MixedFeedAuditWalkthrough` uses union types. The files stay in place because docs embed them by path.

Found 2026-10-26 while writing H-26 (`docs/guides/run-streams-without-blocking.md`), on `main` at `7f551627c`.

**Summary.** The AGENTS.md recipe for checking an example on Scala 2.13 (`sbt "++2.13.18!" compile` inside the examples build) fails for `streams-examples`. `streams-examples/src/main/scala/nio/NioBinaryTelemetryWalkthrough.scala` has the Scala 3-only `import zio.blocks.streams.*`, which 2.13 reads as "object * is not a member of package zio.blocks.streams", and every use after it fails too (`Stream.fromChunk`, `NioSinks`, `NioStreams`, `NioWriters`, `NioReaders`).

**Reproduction.** `cd streams-examples && sbt "++2.13.18!; compile"`.

**Expected.** The build compiles on 2.13, or Scala 3-only files are excluded on 2.13.

**Impact.** No example package in `streams-examples` can be checked on 2.13 without hiding that file first (H-26 used a local, uncommitted `Compile / unmanagedSources / excludeFilter` to compile its own package, which passed). CI doesn't compile the examples on 2.13, so nothing catches it. Only this file was confirmed; others may fail too.

**Suggested fix.** Change `.*` to `._` in that file (or add a Scala 3-only source filter), then compile the whole build on 2.13 once.

### docs build: `docs/mdoc` ran out of heap and still reported success, leaving `website/docs` incomplete

Found 2026-10-28 in one local run by the `remove-stale-datastar` session (worktree on `main` at `4e288c5f2`; sbt 2.0.8 launcher, JDK 25, sbt server heap `-Xmx6g`). Not seen on other machines or in CI so far.

**Summary.** `sbt --client -Dsbt.color=false "++3.9.0; docs/mdoc"` hit `java.lang.OutOfMemoryError: Java heap space` (with `[warn] In the last 10 seconds, 85.875 (908.9%) were spent in GC. [Heap: 0.02GB free of 6.00GB, max 6.00GB]`) after 785 s, and the run was reported as a success. `website/docs/reference/sql/*` and `website/docs/reference/streams/core/*` were never written, and a later `yarn build` failed with "These sidebar document ids do not exist", which hides the real cause.

**Caveat.** The run went through the short-lived `sbtq` wrapper (since removed), which printed the exit status. Whether plain `sbt --client` also returns 0 here, or whether mdoc swallows the error, isn't verified.

**Expected.** Any exception during `docs/mdoc` fails the command with a non-zero exit code.

**Impact.** A docs build can look green with pages missing, and the failure shows up later as an unrelated site-build error. CI's Build Docs job runs on a larger runner, so it may never hit this, but nothing guards against it.

**Test gap.** Nothing checks that every page under `docs/` (outside `docs/dev/`) gets an output file in `website/docs`.

**Suggested fix.** First reproduce with plain `sbt --client` and a small heap (`SBT_OPTS=-Xmx2g`) to find who swallows the error. Then make `docs/mdoc` fail on it, or add a post-mdoc check, for example in `docs/checkDocsOnFreshWebsite` or a website unit test, that every source page has an output.

## Resolved

- Nine standalone example builds that didn't compile on Scala 2.13 (found 2026-10-29 by #1897): fixed in #1936 (merge `0c0021d61`, 2026-11-04), one commit per build. endpoint, http-model, mux, smithy, scope, schema, async, openapi and telemetry now pass `sbt "++2.13.18!" compile` (config, html, jwt and streams already did). Milad's decision: no example code was rewritten; each `build.sbt` skips Scala 3-only files on 2.x (the #1897 mechanism: a content regex plus per-file and per-package sets), so endpoint and scope compile almost nothing on 2.13 (nearly every file uses `@main`), and the generated smithy/openapi apps compile nothing on 2.x. Correction to the entry: "expected start of definition" came from `@main def`, not `import x.*`. The real 0.0.56 API gaps behind the other failures were already filed (telemetry `log.*` and `GlobalLogState`, `json"..."`, `yaml"..."`, `MigrationBuilder`); the async `Async.promise` difference is a new Usability traps entry (the Scala 2 body must bind the completer). Not done: a 2.13 compile step for the examples in CI (needs Milad's OK); trying the codegen's `EmitterConfig.scala2` to make the generated smithy/openapi apps 2.13-compatible. Opus review 8 findings, 3 applied, the rewrite-`@main` finding declined per Milad's decision.
- `wire.md` had no mdoc-checked code blocks (found 2026-10-30 by #1917): fixed in #1934 (merge `5eb809c8f`, 2026-11-04) for all six resource-management pages. Plain ```` ```scala ```` fences 63 → 48 (wire 17 → 8, resource 19 → 18, scope 18 → 13); the rest are signatures, sbt lines, pseudocode and 5 deliberate-error fences that stay plain because `mdoc:fail` on this page also compiles the earlier blocks and reports "Conflicting definitions" (the known mdoc entry above); their error messages were confirmed in an isolated probe page. Compiling them found three `wire.md` errors, fixed in the same PR: the `Wire#isShared`/`Wire#shared` examples used `Wire.shared[String]` ("Cannot derive Wire for scala.Predef.String: not a class"); the `Wire#toResource` example passed a `Context[Config]` where `Context[String]` was needed; the motivation's manual-wiring example passed a scoped value to a constructor, which the scope checker rejects. Also fixed a Scala 3.9 pattern-narrowing warning at `scope.md:914`. Review: 0 blocking, 7 minor, 6 nits, all applied. Merged while GitHub Actions was backed up, so its PR CI never ran.
- `typeid.md`: four claims that contradicted the library (found 2026-11-04 by the typeid Scaladoc rollout, #1924) fixed in #1933 (merge `6ee89c6b8`, 2026-11-04): (1) derived `TypeParam`s record name, index and variance only, always `Kind.Type` and unbounded (a known bug in 0.0.56), with a `TypeParam.higherKinded` example and the Kind table fixed; (2) `normalize` follows aliases only and leaves opaque types as they are, with an example; (3) `construct` builds `List`, `Vector`, `Set`, `Seq`, `IndexedSeq` and `Map`, while `Chunk`, `Array` and `ArraySeq` give `Left`, with an example; (4) `unapplied(TypeId.of[List[Int]])` equals `TypeId.list` on Scala 3. Probed against 0.0.56; Opus review 6 findings, 6 applied. Merged while GitHub Actions was down, so its PR CI never ran.
- JdkHttp adapter: an `Error` in a handler (e.g. `ExceptionInInitializerError`, `UnsupportedClassVersionError`) closed the connection with no response and no log line, because the adapter caught only `NonFatal` (found 2026-10-29 by H-57). Fixed in #1923 (merge `aa7023da6`, 2026-11-04): both catch sites are now `case error @ (NonFatal(_) | _: LinkageError) =>`, so the error is logged and answered with 500; `VirtualMachineError` and `InterruptedException` still propagate. Applied to all 10 copies (8 `JdkHttp.scala`, 2 `StreamingJdkHttp.scala` in http-model, jwt, capstone, htmx and datastar examples) and the inline code in 5 guides; the serving guide explains it. Verified with the entry's reproducer: before, `GET /price -> Failure(IOException: HTTP/1.1 header parser received no bytes)`; after, `Handler failed: java.lang.ExceptionInInitializerError` and `GET /price -> Success(500 Internal Server Error)`. **Known limit, kept on purpose (Milad):** the 500 body is built with http-model classes (`Response.text` → `Body.fromString` → `Stream`), so when the broken class is http-model or streams itself (the http-model 0.0.56 jars on JDK 17, `UnsupportedClassVersionError`), building the 500 throws again and the client still sees an empty reply; the log line now names the cause. The serving guide states this. A JDK-only 500 fallback would close it but adds code to every copy; not done.
- Three scope reference pages that contradicted the code and the fact-checked scope Scaladoc (#1916) fixed in #1917 (merge `31563a0e1`, 2026-11-03), each confirmed first against source and with scala-cli probes on 0.0.56 (Scala 3.9.0, 2.13.18, Scala.js 1.22.0): `resource.md` no longer says `Resource.from[T]` always registers `close()` (the no-argument form skips an `AutoCloseable` whose constructor takes a `Finalizer` or `Scope`, a known bug; the wires form does close it) and no longer calls `Resource.shared` "lock-free" or its initializer "exactly once"; `wire.md` no longer promises a fresh instance from every unique wire (Scala 3 treats an explicit `Wire.unique` as shared), says `fromFunction` never closes an `AutoCloseable` result, and its four `Context.empty[Any]` blocks are now compiled `mdoc:compile-only` blocks; `scope.md` states the Scala.js behaviour of `Scope.global` and `isOwner`. `guides/resource-and-di-migration.md:441` got the same `Finalizer`/`Scope` exception. Still open: the rest of `wire.md` has no mdoc-checked blocks (Docs tooling entry).
- Four `docs/reference/chunk.md` errors found by the chunk Scaladoc fact-check (#1908) fixed in #1913 (merge `c5cc1ec96`, 2026-10-30): `ChunkBuilder` was described as a buffer that feeds a tree; `dropUntil` was said to keep the matching element; the `reduceMapLeft`/`reduceMapRight` examples reduced a one-element chunk (now `NonEmptyChunk(10, 20, 30, 40)` and `(1, 2, 3, 4)`); the "Bitwise Operations" section documented private methods (removed). The same PR replaced the 23 Zionomicon sample-data lines in the chunk Scaladoc and changed `scaladoc-style.md:58` to the new sample-data rule.
- Batch 5 doc errors fixed in #1914 (merge `610a89803`, 2026-10-30), each confirmed first by a fresh-context Opus fact-check with probes on 0.0.56: (1) `instrument-a-program-with-logs-traces-and-metrics.md:562` said exporting to an OpenTelemetry Collector "isn't possible yet" (it is, from the public `OtlpJsonEncoder` and `HttpSender`; the page now links the custom-exporter reference); `telemetry-guide.md` Part 5 now uses only public API (the package-declaration trick survives only in a warning), and its uncompilable plain block, its Part 4 `new OtlpJsonMetricExporter`, the shutdown order and the FAQ were fixed; (2) `NioBinaryTelemetryWalkthrough` section 6 no longer says its replay-window bug was "since fixed" (it still fails on 0.0.56, a known library bug), and three references to a nonexistent `NioSpec` test were removed; (3) `reference/http-model/model.md` cookies now use ZIO Blocks' `Chunk` and render the header, say `SameSite` has no default attribute, and describe `Lax` as cross-site top-level GET only.
- `netlify.toml` redirects never reached zioblocks.com (the deploy job uploads the build without the repo, so `[[redirects]]` were never sent), so `/docs`, `/docs/*` and the old install-guide URL returned 404 (found 2026-10-30 after the post-deploy check). Fixed in #1912 (merge `a47f4502a`): every rule now lives in `website/static/_redirects`, a unit test guards them, and `site.yml` checks one redirect after each deploy. Deployed 2026-10-30 (run 41050926189, which passed its new redirect check); live: `/docs` → 301 `/overview`, `/docs/guides/your-first-schema` → 301 `/guides/your-first-schema`, `/guides/add-zio-blocks-to-your-build` (with and without `/`) → 301 `/getting-started/installation`.
- `guides/validated-newtypes.md:362` ("Use Scala 3 Opaque Types") and `schema-examples/.../validatednewtypes/OpaqueTypesExample.scala:27` built `given Schema[Isbn] = Schema[String].transform(...)` inside the opaque scope, so on Scala 3.3 `Schema[String]` resolved to the given itself and the example hung (found by H-58, 2026-10-29). Fixed in #1906 (H-58, merge `b574f40f5`): both use `Schema.string.transform(`, with one sentence explaining why; verified on 3.3.7. The general trap stays in library-issues.md → Usability traps.
- Fixed in #1890 (docs cleanup, merge `0959c8abb`): `reference/sql/repo.md` presented `Repo` as a public concrete class (it is abstract with a protected constructor) and said its SQL is built once at construction (only identifiers are cached; each call builds its statement). `query-dsl-sql.md` had unbalanced parentheses in two hard-coded output comments and an incomplete `ArithmeticOperator` list. `query-dsl-fluent-builder.md` pointed at files the page doesn't use. The Scope tutorial had a malformed mdoc fence. Three `undocumented-report.md` figures were wrong. `ddl.md` and `repo.md` now call the missing `PRIMARY KEY` a known bug in 0.0.56, and the object-hash outputs in `db-codec.md`, `db-codec-deriver.md`, `toon.md`, `ddl.md` and `repo.md` are gone.
- `docs/guides/write-joins-and-inspect-generated-sql.md` (H-19) gave an unnamed dump file as `_local_<Object>__query-<dialect>.sql`; the real name has three underscores (`_local_<Object>___query-<dialect>.sql`). Fixed in #1889 (merge `bafc31bef`), which also added the `SqlQuery`, `Rel` and `Dump` reference pages and names the four query-layer bugs as known bugs in 0.0.56.
- Scaladoc fixed in #1891 (merge `fa76f487f`): the `Stream#repeated` Scaladoc (`Stream.scala:713`) said `Stream.eval` runs once "with both terminal families"; it now says once under the blocking terminals and every cycle under the asynchronous ones, and that an empty cycle loops forever only in a blocking pull. The `Json` (`Json.scala:40`), `JsonCodec.bigIntCodec` (`:919`) and `bigDecimalCodec` (`:955`) Scaladoc stated the digit limit off by one; they now say 308 or more digits are rejected and up to 307 are accepted. With these, the Documentation errors section has no open entries.
- `reader.md`/`writer.md` contradicted the code on `isClosed` after `reset()`, the `AnyRef` lane, error-closed writers, `Writer.single` and `concat` (seven items, from the wave 2 streams Scaladoc): fixed in #1881. Its one leftover, that the `NioWriters.fromChannel` Scaladoc should describe the close-after-failed-write leak, was already done on `main` (`NioWriters.scala:72-84`, checked 2026-10-28).
- Data doc errors fixed in #1887 (batch 4b, merge `5779e440d`, three review rounds): `db-codec.md` said non-optional types throw on SQL `NULL` (`String` gives `null`, numbers `0`); `sql-zio.md` and `transactor-zio.md` (JDBC calls on ZIO's default pool, undefined names, `:memory:` examples that can't work; both pages now compiled mdoc); `table.md` and `db-codec-deriver.md` on `createTable` with collection and nested-record fields; the `Frag#queryChunked` Scaladoc's `Transactor.connect(transactor)`; the "schema / optics and paths" bullets (`path-interpolator.md` escapes and `.one`, `OpticCheck`, map-key `toString`); the "schema-toon (`toon.md`)" bullets, including today's three (`KeyFolding.Safe`, `ReaderConfig.withDelimiter`, inline/tabular forms); and the `stream-files-and-sockets.md` "Scala 3.7" install line. The sql, optics and toon library bugs those pages name carry a Status line.
- Fixed in #1886 (merge `6462ef6e9`): a stale `docs/reference/datastar.md`, deleted by #1638 and re-added by the #1672 squash merge, shadowed `docs/reference/datastar/index.md`, so no Datastar overview fix since 2026-08-28 reached the site. `onDuplicateRoutes: 'throw'` didn't fire because Docusaurus compares routes as exact strings: `/reference/datastar` and `/reference/datastar/` differ only by the trailing slash, yet both write `reference/datastar/index.html` (the stale copy won in production). The PR deletes the file and adds `website/test/unit/page-beside-folder-index.test.mjs`, which fails on any `docs/**/X.md` next to `docs/**/X/index.md(x)` (140/141 before, 141/141 after). Checked in the PR's CI `website-artifact`: `reference/datastar/index.html` now contains `zio-blocks-next-datastar` and "Scala 3.x only". Goes live with the next deploy.
- Runtime doc errors fixed in #1885 (batch 4a, merge `ac8a1d76a`, three review rounds): config `formats.md` (infinite HOCON numbers are kept as written strings; the 1 MiB `fromFile` limit and `fromFileWithLimits`; the include-callback and `HoconError` position claims, plus warnings for the config-hocon known bugs) and the flags/rollouts bullets (`rollout.md` share and sum-above-100 wording, `flags.md` exception wording, the `DynamicFlag` Scaladoc example); the scope bullets and the Scope tutorial entry (signatures, `andThen`, error text, `wire.md`, `finalization.md`, thread ownership, Scala 3 `Unscoped` derivation, single-use `Resource.from`, plus leftover cycle-detection claims in two guides); `context.md` `Wire.make`; the telemetry JDK 21, 3.9 artifact and double-printing items (and the `HistogramDataPoint` boundaries Scaladoc); `telemetry-guide.md` `log.removeAll()` teardown; the `B3Propagator.multi` Scaladoc header list; the async Scala 3 `Completer` warning; and the T-12 entry in the `docs/index.md` Guides list. The library entries those pages now name carry a Status line.
- Streams doc errors fixed in #1881 (batch 1, merge `c8c65babe`, three review rounds): `stream.md` (`repeated` described as rematerializing, a warning box quoting replaced `mapPar` Scaladoc, the wrong `fromAcquireRelease` output, the eager `FileReader`, an unreachable Scala.js merge path, the `bufferSize` claim, and no Long/Double count note in "The Reader Protocol"); the worker-thread-name, virtual-thread-fallback, Scala.js `ReadableStream`, `NioReaders.fromChannel`, `NioWriters.fromByteBuffer` and full-buffer `NioSinks` statements in `platform-differences.md`, `reader.md`, `writer.md` and `sink.md` (the thread-name text changes again once that library bug is fixed); `zero-boxing.md` saying `Sink` has unboxed `write` methods (it now also names the accumulator boxing as a known bug); `streaming-library-migration.md` telling readers to pick a sentinel on every lane; and the `SinkSentinelGuardExample`/`SinkSentinelLimitationExample` pair, replaced by `streams-examples/src/main/scala/sink/SinkSentinelExample.scala`, with a `NioStreamsSinksSpec` case that writes `Long.MaxValue` and `Double.MaxValue`. The same PR documents the two `Stream#start` findings from H-53.
- Already fixed on `main`, moved here on 2026-10-28 after checking each page: `docs/reference/mux.mdx` registry and single-consumer wording (#1870, H-29); the `openapi.md` signature, install, round-trip, `Parameter`/`Operation` and extensions bullets (H-39); the `http-model/schema.md` `RequestSchemaOps`/`ResponseSchemaOps` and "Supported Types" bullets (#1879); the `binding.md`, `registers.md`, `binding-resolver.md` and `Registry#bind`/`StructuralReflection` Scaladoc bullets (schema Scaladoc wave 3).
- Schema-core doc errors fixed in #1877 (batch 2a, merge `edd42b4a2`, three review rounds): `maybe.md` said flat primitive `Maybe`s allocate nothing (they are stored as JVM boxes; only cached small values are free); `typeid.md:1927` listed named tuples under `TypeRepr.Tuple` (a named tuple is an applied `NamedTuple`, labels `None`); `as.md` called dropping a one-sided `Option` field a clean round trip (`Some(v)` comes back `None`; `into.md` qualified too); `migration.md` and the ZIO Schema migration guide called `DynamicMigration` serializable or "plain data" (no `Schema`, so no codec); `dynamic-schema.md` and three `schema-examples` comments said a `DynamicSchema` can be stored as JSON and that `BindingResolver.defaults` covers `Option` (now a compiled Avro storage subsection, a compiled `Option` binding, and the `toSchema`/`check` JSON caveat); `data-migration.md` promised that `fence()` stops producers and that `complete()` migrates every queued row, and its Full Example did not compile (rewritten after H-23, run against PostgreSQL 16 and SQLite). `schema.md` gained a compiled "Tuples" subsection and the named-tuple `given` rule and known bugs; those library entries stay open with a Status line.
- Codec doc errors fixed in #1878 (batch 2b, merge `13423e664`): `avro.md` now has the Scala-to-Avro type table and the decode-trusts-the-reader-schema note, and names the three Avro library bugs; `yaml.md` (now evaluated mdoc) no longer shows `YamlOptions` output from `encodeToString` or a `---` line it never prints, and documents kebab-case keys; `csv.md` no longer promises document-level error rows and columns, marks `CsvError.TypeError` and `CsvConfig.nullValue` as unused, and states the nested-record behaviour; the whole "schema / JSON" list (the `require*` encoder effect in `customize-json-encoding.md`, `transient` on decode in `modifier.md`, `schema-error.md` messages and first-failure behaviour, `syntax.md` `show` output, `validation.md` rewritten as evaluated mdoc, `json-schema.md:366` and `pattern` anchoring, `json/index.md:66-67`, the `json"..."` Scala 2.13 note and `toKV` output in `json.md`, and `json-selection.md:92`); the `Schema.fromJsonSchema` Scaladoc was already correct on main. Also fixed there: `schema-bson/README.md` showed class-level `@Modifier.rename`/`alias` on a record, which the BSON deriver ignores (probed against 0.0.56: a record with class-level `rename("NewName")` still encodes as `{"x": 1}`; on a sealed-trait case, `rename("Round")` gives `{"Round": {...}}` and `alias("Circle")` decodes), and the README now says `withNativeObjectId` has no effect and shows the `Option` sub-document shape. The library-bug entries for Avro, YAML, CSV and BSON stay open.
- Web doc errors fixed in #1879 (batch 3, merge `1a5561f65`): `reference/datastar/index.md` claimed Scala 2.13 support (the module is Scala 3 only) and lacked the 3.9+ `zio-blocks-next-datastar` + `-experimental` install line (live only after #1886 removed the stale `docs/reference/datastar.md`); `reference/htmx/response-headers.md` paired `HX-Redirect` with `HX-Trigger-After-Settle`, which htmx 2 never fires after a redirect (browser-verified); `reference/htmx/index.md` never said its syntax targets htmx 2.x; and the `JdkHttp` adapter (7 copies plus the 2 `StreamingJdkHttp` copies) wrote the response outside its `catch`, so a failed write dropped the connection silently (now a logged 500). The same PR documented the htmx `HX-Boosted`/history-restore traps, the html shadowing and `search` ambiguity traps, and the docs side of the `HxLocation` and four http-model-schema bugs (those entries stay open with a Status line).
- `docs/index.md` described `Maybe` as "Low-allocation optional values backed by `null`"; since #1532 absence is an `Absent` singleton and `Maybe.present(null)` is present. Fixed in #1866 (H-51, commit `1dfba0647`); regenerate the README.
- `telemetry-guide.md` propagation examples started a new trace instead of continuing the caller's: the HTTP server example took its tracer before `trace.install`, and `traceIncoming` extracted the parent but never scoped it. The "Span-log correlation" line called the `private[telemetry]` `new TracerProvider(...)`, and `reference/telemetry/otel/index.md` listed `X-B3-ParentSpanId` and `X-B3-Flags` as headers `B3Propagator.multi` writes (it writes only TraceId, SpanId and Sampled). Fixed in #1853 (H-43, commit `4df2713bc`). Still open: the same wrong header list in the Scaladoc at `otel/src/main/scala/zio/blocks/telemetry/otel/B3Propagator.scala:41-42` (backlog doc-fix follow-up).
- `reference/http-model/headers.md:660` said `ServerSentEvent` uses `Headers` indirectly through `Response`; it renders a `String` and touches neither. Fixed in #1842 (H-41), which also links the SSE guide from that sentence.
- JSON number docs fixed in wave 8b of the schema Scaladoc (#1843, `JsonReader`/`JsonWriter`): `json.md:18` and `:26` said `Json.Number` has "arbitrary precision" and preserves precision for financial and scientific data, but `Json.parse` and the `BigDecimal` codec round to 34 significant digits (`MathContext.DECIMAL128`) and reject 308 or more digits (`Json.scala:2351`, `JsonReader.scala:3125`, `:5243-5254`, `JsonCodec.scala:875`; `Json.parse("1234567890123456789012345678901234567890")` prints `1.234567890123456789012345678901235E39`). The Scaladoc of `JsonReader.bigIntDigitsLimit`/`bigDecimalDigitsLimit` (308) and the `digitsLimit` parameters called 308 "the maximum number of digits", but the check is `len >= digitsLimit`, so 308 digits are rejected and the scale limit 6178 is exclusive too (`JsonReader.scala:2922`, `:3020`, `:3062`, `:3126`). `JsonWriter` Scaladoc: `writeKey(x: Instant)` was described as writing a `Duration`, `writeVal(x: Char)` as writing "a JSON key", and `writeObjectStart`/`writeObjectEnd` as array markers. `JsonReader` Scaladoc: `readStringAsBigDecimal` said it reads "a JSON number value" (it reads a string), and `readBigInt(digitsLimit)`/`readStringAsBigInt(digitsLimit)` said they throw on "the default limit" (they use the given one).
- JSON reference pages and Scaladoc fixed in wave 8a of the schema Scaladoc (#1841): `json-config.md` taught `implicit val nameMapper` in the companion, which nothing reads (now `@Modifier.fieldNaming`, with `withFieldNameMapper` named as the other way), listed only three `NameMapper`s, gave `preferredCharBufSize` a default of `8192` (it is `4096`) and left out the other three `ReaderConfig` options, showed `withCheckForEndOfInput(false)` as allowing trailing whitespace (whitespace is always allowed; it ignores text after the value), said `checkForEndOfInput` "adds one comparison", and listed `Strict`, `Right` and `Left` merge strategies, which do not exist (`MergeStrategy.scala` has `Auto`, `Shallow`, `Concat`, `Replace` and `Custom`). `json-patch.md` said that `JsonPatch.diff` emits `Op.Nested` and array `Modify` steps; it emits neither (`JsonDiffer.scala`). The `Json` Scaladoc said numbers are stored as a `String` (they are a `BigDecimal`), the `JsonSelection` Scaladoc used removed `asArrays`/`asStrings` methods (already fixed on `main` at `67a9be9ff`), and the `ValidationOptions` Scaladoc said formats are annotation-only by default (they are checked). In the wave 8a review round: `json.md` said objects use a `Vector` (a `Chunk`), that `Auto` and `Shallow` concatenate arrays and that a `Deep` strategy exists (`Auto` merges arrays by index, `Shallow` takes nested values from the right), and showed the wrong out-of-bounds message (`Index 5 out of bounds (size: 1) at: [5]`); `json-selection.md` showed `"field not found"` (`Key 'age' not found`); `json-patch.md` and `json-differ.md` called the diff round trip unconditional (it fails for duplicate keys, see Library bugs).
- streams overview (`streams/index.md`) marked `fromInputStream` and `fromJavaReader` as JVM-only, but both run on Scala.js. Fixed in #1796 (H-24).
- docs: `zio-blocks-next-*` needs Scala 3.9+ (plus `-experimental`), not 3.7+. Fixed across 20 guides and 14 example builds in #1810 (H-01). Leftovers on `main`: `guides/stream-files-and-sockets.md:80` still says "Scala 3.7 and newer", and 14 example builds still turn on `-experimental` at `minor >= 8`.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. Fixed in #1773.
- `json-patch.md:608` said that converting a `JsonPatch` to RFC 6902 is a matter of encoding it with its `Schema` and reformatting. The encoded patch holds number deltas and string edits that RFC 6902 cannot express, so the conversion needs the source document to compute `replace` values and must track list indices as earlier operations shift them. No other page or Scaladoc claims RFC 6902 compatibility. The sentence now says this and links the H-07 guide's converter. Fixed in #1815 (H-07).
- `transactor-zio.md` mentioned `JdbcTransactor.postgresLayer` without saying it is an extension that needs `import zio.blocks.sql.zio.*` (found while writing H-17). Fixed on the H-21 branch `docs/h21-sql-from-zio`.
- `type-class-derivation.md` "Customizing Derivation with Instance and Modifier Overrides": all four examples were uncompiled plain blocks, one with a `// ... AST overrides ...` placeholder and undefined types. Each is now a self-contained `mdoc:compile-only` block with the real JSON AST overrides. Fixed in #1787.
- `jwt.md`: audience mismatch documented as `InvalidToken` (it is `InvalidClaim("aud", ...)`), missing `InvalidClaim`/`InvalidKey` rows, no key-format guidance, no frontmatter. Fixed in #1789 (H-37). The `JwtDecodeOptions` Scaladoc (`Jwt.scala:131-132`) still says `InvalidToken`; see the jwt entry above.
- `sql-transactions.md`: the Hikari recipe, "JDK 25+" virtual-thread claim and "pool size equals database max connections" advice were replaced by a link to H-17's guide (virtual threads are final in JDK 21, JEP 444; JEP 491 shipped in JDK 24; pool sizing must keep the sum of all pools under `max_connections`). Also fixed: `.query[Int].toOne` (no such method; `queryOne[Long]`) and the unsupported "read-only avoids WAL writes" claim. Fixed in #1790 (H-17).
- `codec.md` never mentioned `Deriver#withInstance`/`withModifier`, and its BinaryCodec/TextCodec paragraph was wrong (`CsvCodec` extends `TextCodec`; XML and YAML extend `BinaryCodec`; `BsonCodec` extends neither). New "Overriding a Type on a Deriver" subsection and fix in #1784 (merged).
- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. In PR #1773 (open).
- `patch.md`: every snippet was a plain uncompiled fence; five sections did not compile (`editString` with `Vector`, `modifyKey` with a forward-referenced implicit, `Patch[Data] ++ Patch[Counter]`, `val new`, a non-existent `JsonEncoder`), and the Strict, Clobber, `Patch#apply`, Prism-`set`, LazyList and string-delta claims contradicted the source. The snippets are now mdoc blocks and the claims are corrected; see the H-07 entry "docs: `patch.md` has six snippets that don't compile and several claims the source contradicts". Fixed in #1828.
- `into.md` said that the numeric widening instances "cannot lose information" (`Int` and `Long` to `Float`, and `Long` to `Double`, round large values) and that `Into.derived` matches coproduct cases by name only (it falls back to field signature and then to position; see the entry on `Into.derived` and case positions). Both sentences are corrected. Fixed on the wave 7 schema Scaladoc branch `scaladoc-wave-7`.
