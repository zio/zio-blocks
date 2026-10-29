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

### docs: the telemetry tutorial says shipping to a collector "isn't possible yet", and `telemetry-guide.md` reaches the private exporters by declaring a package

Found 2026-10-29 by the backlog-refresh research, on `main` at `bafc31bef`. Same at `v0.0.56`.

**Summary.** Two pages describe exporting telemetry inaccurately:
1. `docs/guides/instrument-a-program-with-logs-traces-and-metrics.md:562` (T-12) says: "Shipping it to a backend such as an OpenTelemetry Collector, Jaeger or Grafana isn't possible yet, because the module's OTLP exporters are not public." But the public API in 0.0.56 can do it: `docs/reference/telemetry/otel/custom-exporter.md` builds an exporter from the public `OtlpJsonEncoder` (`otel/src/main/scala/zio/blocks/telemetry/otel/OtlpJsonEncoder.scala:52`), `HttpSender.jdk` (`HttpSender.scala:49`) and `ExportResult` (`ExportResult.scala:27`). Only the built-in exporters are `private[otel]`.
2. `docs/guides/telemetry-guide.md:724-850` (Part 5) has readers put their bootstrap code in package `zio.blocks.telemetry.otel`, so that it can reach the `private[otel]` exporters. That compiles, but it relies on a package-private API that can change without notice, and the page doesn't say so.

**Expected.** (1) "The built-in OTLP exporters aren't public yet; you can build one from the public encoder and sender: see Building an Exporter", linking `reference/telemetry/otel/custom-exporter.md`. (2) Part 5 says plainly that declaring the package is an unsupported workaround, and points to `custom-exporter.md` as the supported route.

**Impact.** Readers of the tutorial think they can't export telemetry at all, while readers of the deep dive copy a trick that can break on any release.

**Suggested fix.** Reword both places as in "Expected", and run mdoc on both pages. This is also input for the X-01 decision (making the built-in exporters public).

### docs/examples: `NioBinaryTelemetryWalkthrough` section 6 says its window-reset bug was "since fixed"

Found 2026-10-29 (PR #1897's session), on `main` at `fa76f487f`. `streams-examples/src/main/scala/nio/NioBinaryTelemetryWalkthrough.scala` section 6 says the replay-window bug it exercises "Originally exposed BUG-R8-03 (since fixed)", and it cites a test "NioSpec 'origin, window and accuracy (round 8)' [AdversarialWindowResetSpec]". Neither is true: the bug is still open (Library bugs entry "streams: `NioStreams.fromByteBuffer{Int,Long,Double,Float}(...).take(n).drop(m).repeated` replays the wrong window ..."), and no such test exists. The example therefore prints a FAIL on 0.0.56 and on `main`. The expected value in the code is correct.

**Suggested fix.** Until the library is fixed, reword the comment to say section 6 shows a known bug in 0.0.56 and on `main`, and drop the dangling test reference. Once the library bug is fixed, restore the wording.

### reference/http-model/model.md: the cookie sections use ZIO's `Chunk` and misdescribe `SameSite`

Found 2026-10-29 while writing H-57 (`docs/guides/protect-pages-with-basic-auth-or-a-session-cookie.md`), on `main` `9d8bb9d70`.

**Summary.** Three errors in `docs/reference/http-model/model.md`:

1. "Sending Cookies" (`model.md:926`, `mdoc:compile-only`) builds `zio.Chunk(RequestCookie(...), ...)`. That is ZIO's `Chunk`, not `zio.blocks.chunk.Chunk`; it compiles only because `sql-zio` puts ZIO on the docs classpath. A reader without ZIO gets "value Chunk is not a member of zio". The block also ends with a comment "Render as Cookie header value (see Header operations)" and never renders it (`Cookie.renderRequest(cookies)` or `Request#addCookie` does).
2. "SameSite Attribute" (`model.md:992`) says `SameSite.Strict` is the "default". The default is no attribute: `ResponseCookie.sameSite` defaults to `None` (`http-model/shared/src/main/scala/zio/http/Cookie.scala:44`), and browsers then apply their own default (Lax in Chrome).
3. The same block says `SameSite.Lax` allows "Top-level navigations allowed (links, forms)". Lax sends the cookie on cross-site top-level `GET` navigations (links), not on cross-site form `POST`s; that is the protection Lax gives.

**Suggested fix.** `zio.blocks.chunk.Chunk(...)` plus `Cookie.renderRequest(cookies)`; "(no attribute by default; Strict is the safest)"; "cross-site links (top-level GET) only, not cross-site form posts".

### validated-newtypes.md: the "Use Scala 3 Opaque Types" example hangs on Scala 3.3

Found 2026-10-29 while writing guide H-58, on main `9d8bb9d70`; the page section is the same at v0.0.56's docs. Verified with `scala-cli run --server=false Repro.scala` against the published 0.0.56: Scala 3.3.7 hangs, while 3.7.4, 3.8.3 (`zio-blocks-schema`) and 3.9.0 (`zio-blocks-next-schema` + `-experimental`) print the page's output. 3.4 to 3.6 were not tested.

**Summary.** `docs/guides/validated-newtypes.md:362` defines `given Schema[Isbn] = Schema[String].transform(...)` inside `object catalog`, where `Isbn` is `String`. On Scala 3.3.7, `Schema[String]` resolves to that given itself. The compiler warns `Infinite loop in function body`, and the program hangs at the first use of `Schema[Book]`, because the given is a lazy val that initializes itself. The page's install section covers Scala 3.3 to 3.8 with `zio-blocks-schema`, so readers on the 3.3 LTS copy a hanging example. mdoc and schema-examples run only on 3.9.0, which is why nothing caught it.

**Reproducer** (`Repro.scala`, the page's block verbatim plus a `start` marker):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56

import zio.blocks.schema._
import zio.blocks.schema.json._

object catalog {
  opaque type Isbn = String

  object Isbn {
    def parse(input: String): Either[SchemaError, Isbn] = {
      val digits = input.filterNot(c => c == '-' || c == ' ')
      if (digits.length == 13 && digits.forall(_.isDigit)) Right(digits)
      else Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    }

    given Schema[Isbn] = Schema[String].transform(s => parse(s).fold(e => throw e, identity), identity)

    extension (isbn: Isbn) def value: String = isbn
  }
}

final case class Book(isbn: catalog.Isbn, title: String) derives Schema

@main def run(): Unit = {
  println("start")
  println(Schema[Book].jsonCodec.decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala"}""").map(_.isbn.value))

  Schema[Book].jsonCodec
    .decode("""{"isbn":"978-ZIO","title":"Zionomicon"}""")
    .left
    .foreach(e => println(e.message))

  Json
    .parse("""{"isbn":"978-ZIO","title":"Zionomicon"}""")
    .flatMap(json => Schema[Book].fromDynamicValue(json.toDynamicValue))
    .left
    .foreach(e => println(e.message))
}
// -- Warning: ./Repro.scala:17:25
// 17 |    given Schema[Isbn] = Schema[String].transform(s => parse(s).fold(e => throw e, identity), identity)
//    |                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//    |Infinite loop in function body
//    |zio.blocks.schema.Schema.apply[String](catalog.Isbn.given_Schema_Isbn).transform
// start
// (no further output; `timeout 60 scala-cli run ...` exits 124)
```

On 3.7.4, 3.8.3 and 3.9.0 the same file prints `start`, `Right(9781617290657)` and `ISBN must have 13 digits, got '978-ZIO' at: .isbn.wrapped` twice.

**Expected.** The example runs on every Scala 3 version the page's install section lists.

**Root cause.** See the library-issues Usability traps entry "schema (Scala 3): `Schema[<base>]` inside an opaque type's companion summons the companion's own instance": inside the opaque scope `Isbn =:= String`, and Scala 3.3 still treats a given as a candidate in its own right-hand side.

**Impact.** A reader on Scala 3.3 to 3.6 who copies the section gets a program that hangs with no error. Only a compiler warning hints at it.

**Test gap.** mdoc and schema-examples compile only on Scala 3.9.0.

**Suggested fix.** On the page and in `schema-examples/src/main/scala/validatednewtypes/OpaqueTypesExample.scala:27`, replace `Schema[String].transform(` with `Schema.string.transform(`. Add one sentence: "Inside `object catalog`, `Isbn` is a `String`, so `Schema[String]` would find this given itself on older Scala 3 versions; `Schema.string` names the base schema directly." Re-run the scala-cli repro on 3.3.7 to confirm the fix.

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

### Nine more standalone example builds don't compile on Scala 2.13

Found 2026-10-29 by the `streams-examples-213` session (PR #1897), which ran `sbt "++2.13.18!" compile` in every standalone `*-examples/` build on `main` at `fa76f487f`. AGENTS.md documents this as the way to check an example on 2.13, and CI compiles the examples on Scala 3 only, so nothing catches it.

**Pass on 2.13:** config, html, jwt, and streams once #1897 merges.

**No `_2.13` artifact, so skipped:** datastar, htmx, sql, projection (a root subproject), and capstone and zio-blocks-examples (they need `zio-blocks-sql_2.13`).

**Fail, with the first error:**
- async: `could not find implicit value for parameter c: Completer[String]` (`AsyncShowcaseExample.scala:177`); `not found: value summon` (`gettingstarted/CompleteExample.scala:23`).
- endpoint, http-model, mux, scope, smithy: `expected start of definition` at line 7 or later of the first file. Probably `import x.*`.
- schema: `';' expected but '.' found` (`BindingResolverSchemaRegistryExample.scala:49`); `';' expected but identifier found` (`Step4PackagingExample.scala:14`).
- openapi: `';' expected but identifier found` in the generated `bookstore-app` `Bookstore.scala:16`. The generator emits Scala 3 syntax.
- telemetry: `object GlobalLogState in package telemetry cannot be accessed as a member of package zio.blocks.telemetry` (`correlatelogsandtraces/CompleteExample.scala:21`).

**Impact.** Scala 2.13 users who copy these examples get compile errors, and sessions can't follow the AGENTS.md 2.13 check for these builds.

**Suggested fix.** One PR per build, done the way #1897 did it: `._` imports, `implicit` instead of `given`, `implicitly` instead of `summon`, and skipping genuinely Scala 3-only files on 2.x. Look at the telemetry `GlobalLogState` and async `Completer` errors separately: they may be real 2.13 API gaps, not syntax. Then consider adding a 2.13 compile step for the supporting example builds to CI (needs Milad's approval).

### JdkHttp adapter: an `Error` in a handler (e.g. `ExceptionInInitializerError`, `UnsupportedClassVersionError`) closes the connection with no response and no log line

Found 2026-10-29 while writing H-57 (`docs/guides/protect-pages-with-basic-auth-or-a-session-cookie.md`), on `main` `9d8bb9d70`, when the H-57 examples were run on JDK 17. The adapter is the same in every copy listed below.

**Summary.** `JdkHttp.handler` catches only `NonFatal` throwables. `scala.util.control.NonFatal` excludes `LinkageError` (and `VirtualMachineError`, `InterruptedException`, `ControlThrowable`), so an `ExceptionInInitializerError` from an `object` whose initializer throws, a `NoClassDefFoundError`, or an `UnsupportedClassVersionError` (the http-model 0.0.56 jars on JDK 17) escapes the handler. The `finally` closes the exchange, the JDK server swallows the error, and the client sees only `java.io.IOException: HTTP/1.1 header parser received no bytes`. Nothing is printed on the server, so the reader has no hint of the cause. The server keeps serving other requests.

**Reproduction** (Scala 3.9.0, `zio-blocks-http-model` 0.0.56, JDK 21). Copy the adapter next to the script and change its package: `sed '1s/.*/package repro/' http-model-examples/src/main/scala/servehttpmodelrequests/JdkHttp.scala > JdkHttp.scala`, then save as `Repro.scala` and run `scala-cli run --server=false .`:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-model:0.0.56
//> using options -experimental
//> using jvm 21
//> using file JdkHttp.scala
package repro

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.util.Try
import zio.http._

object Prices {
  val table: Map[String, Int] = sys.error("prices.csv is missing") // fails on first use
}

object Repro {
  def main(args: Array[String]): Unit = {
    val app: Request => Response = request =>
      if (request.path.encode == "/price") Response.text(Prices.table("espresso").toString)
      else Response.text("ok")
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    JdkHttp.withServer(app) { port =>
      def get(path: String): Unit = {
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(3)).build()
        println(s"GET $path -> " + Try(client.send(request, HttpResponse.BodyHandlers.ofString())).map(r => s"${r.statusCode} ${r.body}"))
      }
      get("/price")
      get("/health")
    }
  }
}
// GET /price -> Failure(java.io.IOException: HTTP/1.1 header parser received no bytes)
// GET /health -> Success(200 ok)
```

Nothing else is printed: no "Handler failed" line, no stack trace. The same happens with the H-57 `Step3UnprotectedAdminExample` under `sbt --java-home ~/.jdks/ms-17.0.15 "runMain ..."`, where the real cause is `java.lang.UnsupportedClassVersionError: zio/blocks/streams/Stream$ has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0` (seen only with a direct `scala-cli run --jvm 17` probe).

**Expected.** The adapter logs the error (`Handler failed: java.lang.ExceptionInInitializerError ...`) and answers `500 Internal Server Error`, as it does for an exception.

**Root cause.** `http-model-examples/src/main/scala/servehttpmodelrequests/JdkHttp.scala:79` (`case NonFatal(error) =>` around `app(request)`) and `:85` (around `writeResponse`); `:92` `finally exchange.close()` then closes the exchange with no response.

**Impact.** Every copy of the adapter: `http-model-examples/.../servehttpmodelrequests/JdkHttp.scala`, `.../validatequeryparametersandheaders/JdkHttp.scala`, `.../protectpageswithbasicauthorasessioncookie/JdkHttp.scala` (H-57), `jwt-examples/.../protectendpointswithjwt/JdkHttp.scala`, `capstone-examples/.../smalljsonbackendfromblocks/JdkHttp.scala`, `zio-blocks-htmx-examples/.../returnhtmxfragments/JdkHttp.scala` and `.../addinteractivitywithhtmx/JdkHttp.scala`, `datastar-examples/.../buildalivedashboardwithdatastar/JdkHttp.scala`; the inline `mdoc:invisible` copies in `serve-http-model-requests.md`, `validate-query-parameters-and-headers.md`, `protect-endpoints-with-jwt.md`, `small-json-backend-from-blocks.md` and H-57; probably the two `StreamingJdkHttp.scala` copies too (not checked). A reader who runs an example on JDK 17, or whose handler hits a failing initializer, gets a client-side I/O error and no clue.

**Suggested fix.** At the handler boundary, catch `Throwable` except `VirtualMachineError` and `InterruptedException` (e.g. `case error: LinkageError =>` added next to `NonFatal`), log it, and answer 500 when no headers have been sent. Change the serving guide's copy and its prose first, then every copy and inline block above in one sweep. H-57 keeps the adapter identical to the serving guide's copy and does not change it.

## Resolved

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
