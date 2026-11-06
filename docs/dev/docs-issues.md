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

### `reference/sql/db-codec.md`: `DbCodec.derivedOpaque` and `DbCodec.dbCodecFromAs` are not "selected automatically"

Found 2026-11-05 by the sql Scaladoc rollout (Task 2), base `0c0021d61`; same at `v0.0.56`. Verified with entry A's reproducer.

- `db-codec.md:272` ("`DbCodec.derivedOpaque` — Opaque type derivation"): "The compiler selects it automatically when `A` is an opaque type and no explicit `DbCodec[A]` is in scope." False: the compiler selects `DbCodec.derived`, which never calls the companion's `apply` (library entry A). The example at `:290-295` (`val codec = DbCodec[ProductId]` with the comment "resolved automatically — no explicit given needed") gets `derived`; its output `codec.columns` is the same either way, so the page doesn't show it.
- `db-codec.md:306` ("`DbCodec.dbCodecFromAs`"): "It enables opaque types and newtype wrappers to receive a `DbCodec` automatically when their underlying type already has one and an `As[A, B]` instance is provided", and the `mdoc:compile-only` block at `:327` ("DbCodec[EmailAddress] is then resolved automatically — no explicit given needed"). False for any type `DbCodec.derived` can derive, which includes every opaque type: the `As` is ignored.
- Fix: say that both must be declared by name (`given DbCodec[Sku] = DbCodec.derivedOpaque[Sku]`), show a read that the companion or the `As` rejects, and name library entry A as a known bug in 0.0.56. The Scaladoc on the `sql-scaladoc` branch shows the working pattern.

### `reference/sql/db-value.md`: `DbArray` is covered by `SqlDialect#typeName`, and `DbCodec` never matches on `DbNull` when decoding

Found 2026-11-05 by the sql Scaladoc rollout (Task 2), base `0c0021d61`; same at `v0.0.56`. Read from source.

- `db-value.md:37` (table row `DbArray`, "*(not in typeName)*") and `:39`: "`DbArray` ... not currently covered by `SqlDialect#typeName`". False: `SqlDialect.scala:48` maps it to `TEXT[]` for PostgreSQL and `:76` to `TEXT` for SQLite.
- `db-value.md:39`: "`DbCodec` matches on it [`DbNull`] to decode nullable columns, throwing `IllegalStateException` if a non-optional codec hits it." False: decoding never sees a `DbValue`; codecs read from a `DbResultReader` and detect `NULL` with `isNull`/`wasNull` (`DbCodec.scala:196-228`). `DbNull` is only produced on the write side (`toDbValues` of `None`). A non-optional codec does not throw on `NULL` either, except `BigDecimal` and a derived `Char`: `Int` reads `0`, `String` reads `null` (as `db-codec.md:537` already says).
- Fix: put `TEXT[]` / `TEXT` in the `DbArray` row, and say `DbNull` is what `toDbValues` produces for `None`/`Maybe.absent`.

### `reference/sql/repo.md`: `insertReturning` with a placeholder ID does not return a database-assigned key

Found 2026-11-05 by the sql Scaladoc rollout (Task 2), base `0c0021d61`; same at `v0.0.56`.

- `repo.md:441-456` says `insertReturning` "is most useful when the database assigns the primary key — for example, via an auto-increment or sequence column", and the example comments "Pass a placeholder ID; the returned entity carries the database-assigned key." False: `Repo#insert` and `insertReturning` always write every column, the ID included (`Repo.scala:201` builds the `INSERT` from `table.columns`), so the database never assigns the key: the placeholder `0` is stored, and a second placeholder insert violates the key. See also library entry C (the key is read from the wrong column).
- Reproducer (Verified; Scala 3.9.0, `zio-blocks-sql` 0.0.56, `sqlite-jdbc` 3.53.2.1), `Repro.sc`, run with `scala-cli run --server=false Repro.sc`:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.2.1

import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
xa.connect {
  sql"CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL)".update
  println(repo.insertReturning(User(0, "Bob", "bob@example.com")))
  println(repo.insertReturning(User(0, "Ann", "ann@example.com")))
}
// User(0,Bob,bob@example.com)
// Exception in thread "main" org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_PRIMARYKEY] A PRIMARY KEY constraint failed (UNIQUE constraint failed: users.id)
```

- Fix: drop the auto-increment framing; say `insertReturning` inserts the entity with its own ID and reads the row back, and name entry C as a known bug.

### `reference/sql/table.md`: `Table#columns` is not O(1) and not built once

Found 2026-11-05 by the sql Scaladoc rollout (Task 2), base `0c0021d61`; same at `v0.0.56`. Read from source.

- `table.md:184` ("`columns` — Column names in codec order"): "Access is O(1) since the sequence is built once during construction." False: `Table#columns` is a `def` (`Table.scala:48`) that maps `validatedColumnsMeta` to names on every call, O(n) in the column count. The same paragraph says the names are "in the same order as the underlying `DbCodec[A]`"; they come from `columnsMeta`, which differs from the codec for nested and collection fields (the page's own caution under `createTable`). The old Scaladoc said "delegates to `codec.columns`", also false; fixed on the `sql-scaladoc` branch.
- Fix: drop the O(1) sentence, and say the names come from `columnsMeta`.

### `reference/sql/transactor.md`, `sql-logger.md` and `transactor-zio.md`: three statements that contradict sql 0.0.56

Found 2026-11-05 while fact-checking the `sql` Scaladoc against the reference pages (sql Scaladoc rollout, Task 1), on branch `sql-scaladoc` at base `0c0021d61` (same at `v0.0.56`).

1. **`transactor.md`, "Construction / Creating Instances":** "In every case the third constructor parameter `sqlLogger` defaults to `SqlLogger.noop`, so it can be omitted unless query logging is required." The factory methods `fromUrl`, `fromDataSource`, `postgres` and `sqlite` take no logger and always use `SqlLogger.noop` (`sql/jvm/src/main/scala/zio/blocks/sql/JdbcTransactor.scala:234-278`); the only way to log is the constructor, `new JdbcTransactor(factory, dialect, logger)`, as `sql-logger.md` shows. The class signature at the top of the page also shows `val sqlLogger: SqlLogger` without its default. Correct: "The factory methods always use `SqlLogger.noop`; to log statements, call the constructor with a `SqlLogger` as the third argument (it defaults to `SqlLogger.noop`)."
2. **`sql-logger.md`, opening paragraph:** "Every statement calls either `onSuccess` or `onError`." Only the statements that `Frag` and `Repo` operations run report events. The savepoint statements of nested transactions (`JdbcConnection.scala:57-76`), the `PRAGMA busy_timeout` that `JdbcTransactor#connect` runs on SQLite (`JdbcTransactor.scala:36`), and commit and rollback report nothing; a `Repo` batch reports one event per batch with empty `params` (`Repo.scala:239`, `:308`). A probe with a printing logger and a nested `summon[DbTx].transact` printed the `INSERT` lines and no `SAVEPOINT` line. Correct: "Each `Frag` operation, and each `Repo` operation (one event for a whole batch, with empty `params`), calls either `onSuccess` or `onError`; savepoint, commit and rollback statements report nothing."
3. **`transactor-zio.md`, opening paragraph:** "It wraps a `JdbcTransactor` ... and lifts its connection and transaction lifecycle management into the ZIO effect system." Only `connect` and `transact` go through the wrapped `JdbcTransactor`; `connectZIO` and `transactZIO` open connections from the factory themselves (`sql-zio/src/main/scala/zio/blocks/sql/zio/TransactorZIO.scala:93`, `:144`), so on SQLite they skip its busy timeout and `IMMEDIATE` mode (library entry "sql-zio: `transactZIO` and `connectZIO` skip the SQLite settings"). Correct: "`connect` and `transact` delegate to a `JdbcTransactor`; `connectZIO` and `transactZIO` open their own connections from the same connection factory."

### async reference: "Direct Style" says every collection closure with `await` is lazy and sequential, but `List.map` and `Array.map` run every closure first

Found 2026-11-05 while fact-checking the Scala 2 macro comments (`AsyncMacros.emitHofMap`, `emitArrayMap`) in the async Scaladoc rollout (Task 1b, branch `async-scaladoc` on `3ac1121a9`). Same at tag `v0.0.56`. Verified by running on Scala 2.13.18 and 3.9.0 (JVM), with the same output, and for `List.map` on Scala.js 3.9.0.

**Summary.** `docs/reference/async.md:709` says: "Each is lazy and sequential: the closure for element *n+1* runs only after element *n*'s `await` has completed, so a `List(a, b, c).map(fetch(_).await)` performs three fetches one after another rather than at once". For `List.map` and `Array.map` the opposite holds, on purpose: both Scala 2 (`AsyncMacros.emitHofMap`, `emitArrayMap`) and Scala 3 (dotty-cps-async's `ListAsyncShift`) apply the closure to every element first and only then sequence the awaits, fail-fast. The tests assert it on Scala.js (`AsyncJsAwaitSpec`: on Scala 3 "List.map closure applies eagerly, then awaits sequence fail-fast"; on Scala 2, suite "List.map with .await in the closure", "the closure applies eagerly to all elements, then awaits sequence fail-fast"). So the page's own example, `List(a, b, c).map(fetch(_).await)`, calls `fetch` three times before the first `await` resolves; if `fetch` starts work when called (a `Future`-backed call, for example), the three run at once. `Vector.map` and the other builder-backed receivers are lazy and sequential as the page says.

**Reproduction** (save as `EagerMapRepro.scala`, run `scala-cli run --server=false EagerMapRepro.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-async:0.0.56

import zio.blocks.async._

import scala.collection.mutable.ListBuffer

object EagerMapRepro {
  def main(args: Array[String]): Unit = {
    val calls = ListBuffer.empty[String]
    def fetchPrice(sku: Int): Async[Int] = {
      calls += s"fetch $sku"
      if (sku == 2) Async.fail(new IllegalStateException(s"sku $sku: price service down"))
      else Async.succeed(sku * 10)
    }
    def run(label: String)(body: => Async[Any]): Unit = {
      calls.clear()
      val result = body.either.block
      println(s"$label: $result; ${calls.mkString(", ")}")
    }
    run("List.map  ")(Async.async(List(1, 2, 3).map(sku => fetchPrice(sku).await)))
    run("Array.map ")(Async.async(Array(1, 2, 3).map(sku => fetchPrice(sku).await).toList))
    run("Vector.map")(Async.async(Vector(1, 2, 3).map(sku => fetchPrice(sku).await)))
  }
}
// List.map  : Left(java.lang.IllegalStateException: sku 2: price service down); fetch 1, fetch 2, fetch 3
// Array.map : Left(java.lang.IllegalStateException: sku 2: price service down); fetch 1, fetch 2, fetch 3
// Vector.map: Left(java.lang.IllegalStateException: sku 2: price service down); fetch 1, fetch 2
```

The same file with `//> using scala 3.9.0`, `//> using options -experimental` and `dev.zio::zio-blocks-next-async:0.0.56` prints the same three lines.

**Expected.** The paragraph names the exception: `map` over a `List` or an `Array` applies the closure to every element first (so every call in it happens before the first `await` resolves) and then awaits the results left to right, failing fast; every other listed method and receiver is lazy and sequential. The example should use a receiver that is lazy (for example `Vector`) or say that the `List` version starts all three calls.

**Root cause.** The page generalises the lazy/sequential behaviour of `foreach`, `flatMap` and the builder-backed receivers to `List.map` and `Array.map`, which the implementation (`async/shared/src/main/scala-2/zio/blocks/async/internal/AsyncMacros.scala:1089-1101` and `:1394-1464` at v0.0.56 = 3ac1121a9, and dotty-cps-async on Scala 3) makes eager to match `List.map` without `await`.

**Impact.** A reader who wants calls made one at a time picks `List(...).map(x => call(x).await)` and gets all calls started together, or relies on a failed call stopping the later ones and finds that the later calls already ran.

**Test gap.** The eager order is tested (`AsyncJsAwaitSpec`, named in the summary), but nothing checks the page's prose, and its mdoc example (`List(1, 2, 3).map(id => fetchName(id).await)` with `fetchName = Async.succeed(...)`, `docs/reference/async.md:700-704`) awaits only ready values, so its output cannot show that every closure runs first.

**Suggested fix.** Rewrite the paragraph at `docs/reference/async.md:709` as in Expected, and add a small example that shows the order (the reproducer above, trimmed). Keep "use `Async.collectAll` when you want them overlapped" for the lazy receivers.

### docs/reference/async.md "Depth Limits on Waiting Values": an iterative `map`/`flatMap` chain over a waiting value does not overflow the stack

Found 2026-11-05 during the async Scaladoc rollout (worktree `async-scaladoc`, base `3ac1121a9`), while fact-checking the `collectAll` Scaladoc against the reference page. `async/` at the base equals v0.0.56 apart from an import change. Verified by running.

**Summary.** `docs/reference/async.md:204` says that a chain built on a value that is still waiting "runs out of stack somewhere around 50,000–100,000 on a default JVM", and that "How you wrote the loop makes no difference — a recursive `def loop(n) = src.flatMap(_ => loop(n - 1))` and an iterative `fa = fa.flatMap(_ => src.flatMap(…))` build the same stack of wrappers". At 0.0.56 an iterative chain of 1,000,000 `map` or `flatMap` calls over a pending `Completer`, whose continuations return plain or ready values, completes without a `StackOverflowError`: `Async.slowPath.ContinuationInterpreter` polls an accumulated `map`/`flatMap` spine in a loop. The recursive shape, where each continuation builds the next wrapper, still overflows. So how the loop is written does make a difference, and the table row "Direct calls | Limited by the call stack" and the paragraph after it overstate the limit for the iterative case.

**Reproduction** (`DepthRepro.scala`, `scala-cli run --server=false DepthRepro.scala`; JDK 25, default stack):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-async:0.0.56

import zio.blocks.async._

object DepthRepro {
  def attempt(label: String)(fa: => Async[Int]): Unit =
    println(label + ": " + (try fa.block.toString catch { case e: StackOverflowError => "StackOverflowError" }))

  def main(args: Array[String]): Unit = {
    // Iterative chain over a value that is still waiting.
    attempt("iterative map x1,000,000") {
      val c = new Completer[Int]
      var fa: Async[Int] = c.peek
      var i = 0
      while (i < 1000000) { fa = fa.map(_ + 1); i += 1 }
      c.succeed(0)
      fa
    }
    attempt("iterative flatMap x1,000,000") {
      val c = new Completer[Int]
      var fa: Async[Int] = c.peek
      var i = 0
      while (i < 1000000) { fa = fa.flatMap(n => Async.succeed(n + 1)); i += 1 }
      c.succeed(0)
      fa
    }
    // Recursive chain: each step's continuation builds the next wrapper.
    attempt("recursive flatMap+map x200,000") {
      val gate = new Completer[Unit]
      def loop(n: Int): Async[Int] =
        if (n == 0) Async.succeed(0) else gate.peek.flatMap(_ => loop(n - 1)).map(_ + 1)
      val l = loop(200000)
      gate.succeed(())
      l
    }
  }
}
// iterative map x1,000,000: 1000000
// iterative flatMap x1,000,000: 1000000
// recursive flatMap+map x200,000: StackOverflowError
```

On Scala 3.9.0 (`zio-blocks-next-async:0.0.56`, `-experimental`) the iterative `map` chain of 1,000,000 also prints `1000000` and the recursive one also overflows.

The page's own iterative shape, where each continuation returns `src.flatMap(...)` over the still-pending `src`, does not overflow either, but its time grows with the square of the length: 4,000 steps take about 0.56 s and 16,000 about 8.8 s (2.13.18, JDK 25); 64,000 steps took 148 s in a separate run (2.13.18, JDK 25), and 100,000 did not finish in 10 minutes. That is a separate library performance bug; see the entry "async: a `flatMap` chain whose continuations return new pending wrappers is driven in quadratic time" for its reproducer.

**Expected.** The page states the limit as it is: an iterative `map`/`flatMap` chain over a waiting value, whose continuations return plain or ready values, is safe at any tested length (1,000,000); a chain whose continuations return new pending wrappers grows quadratically in time; recursion through `flatMap` (each continuation building the next layer) still overflows. `collectAll` and an `Async.async` `while` loop are the safe forms for long waiting work.

**Root cause.** The page was written before `ContinuationInterpreter` (`async/shared/src/main/scala/zio/blocks/async/Async.scala`, `private[async] object ContinuationInterpreter`, lines 2442-2877 at v0.0.56 = 3ac1121a9), which replaces the native call stack for a spine of `map`/`flatMap` wrappers; its Scaladoc says so ("Polls an accumulated map/flatMap spine without recursively invoking each wrapper's poll").

**Impact.** Readers rewrite safe iterative code into `collectAll` or direct style for no reason, and the page's explanation of why `Async` has a depth limit (no trampoline) no longer matches the implementation.

**Test gap.** `AsyncBlockingSpec` (JVM, `async/jvm/src/test/scala/zio/blocks/async/AsyncBlockingSpec.scala:136-174` at base) already drives iterative pending spines of 200,000 `flatMap` and 200,000 `map` steps ("iterative flatMap accumulation is depth-safe over ready and pending sources", "iterative map and alternating accumulation are depth-safe over a pending source"), beyond the page's stated limit; `AsyncSpec` "deep tap / ensuring / as spines over a PENDING source drive to the correct value below the depth bound" stays below an assumed bound. No test covers the recursive overflow or the quadratic `src.flatMap` shape.

**Suggested fix.** Rewrite the "On a value that is still waiting" bullet and the table: iterative `map`/`flatMap` spines are interpreted in a loop; a continuation that returns a new pending wrapper makes each step re-walk the chain (quadratic time, until the library bug is fixed); recursion through `flatMap` (each continuation building the next layer) still grows the stack and overflows somewhere above 100,000 levels. Replace the page's `src.flatMap(…)` example accordingly.

### docs/reference/async.md "Completer": `Completer#peek` does not return the `Completer` once it is complete, and a promise that completes inline still allocates one

Found 2026-11-05 during the async Scaladoc rollout (worktree `async-scaladoc`, base `3ac1121a9`), while fact-checking the `Completer#peek` and `Async.promise` Scaladoc. Same at v0.0.56. Verified by running and by reading `Async.promiseInternal`.

**Summary.** Two sentences of the "Completer" section are wrong. `docs/reference/async.md:1203` says "`Completer#peek` returns the `Completer` itself as an `Async[A]`"; that holds only while the completer is pending. Once it is complete, `peek` returns the value (or the `Failure`), not the completer. `docs/reference/async.md:1088` says that when the `Async.promise` body completes the completer before returning, "the result collapses to a plain ready value and no `Pollable` is allocated at all"; the result is a ready value, but `Async.promise` always allocates the `Completer` (a `Pollable`) before running the body.

**Reproduction** (`PeekRepro.scala`, `scala-cli run --server=false PeekRepro.scala`; JDK 25):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-async:0.0.56

import zio.blocks.async._

object PeekRepro {
  def main(args: Array[String]): Unit = {
    val c = new Completer[Int]
    println("peek before completion is the Completer: " + (c.peek.asInstanceOf[AnyRef] eq c))
    c.succeed(7)
    println("peek after completion is the Completer: " + (c.peek.asInstanceOf[AnyRef] eq c))
    println("peek after completion: " + c.peek.asInstanceOf[Any])
  }
}
// peek before completion is the Completer: true
// peek after completion is the Completer: false
// peek after completion: 7
```

**Expected.** "`Completer#peek` returns the outcome if the completer is already complete, and the `Completer` itself, still pending, otherwise." and "the result is a plain ready value: drivers and combinators see no `Pollable`" (or drop the allocation claim).

**Root cause.** `Completer#peek` (`async/shared/src/main/scala/zio/blocks/async/Completer.scala:106-111`, lines at v0.0.56 = 3ac1121a9) returns `this` only when the state is empty or waiting, and the stored value otherwise. `Async.promiseInternal` (`async/shared/src/main/scala/zio/blocks/async/Async.scala:1705-1709`) runs `val c = new Completer[A]; body(c); c.peek`.

**Impact.** Low: a reader who keeps the result of `peek` expecting a `Completer` (for example to call `poll` on it) gets a plain value; the allocation claim misleads performance reasoning.

**Test gap.** None needed for the library; the page has no test.

**Suggested fix.** Replace the two sentences as in "Expected".

### `reference/html.md`: the `html""` example `id="user-$age"` does not work

**Where found.** `docs/reference/html.md:613-622` (section "`html""` Interpolator"), 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

**Summary.** The comment says "age is in attribute position → ToAttrValue[Int]", but the preceding text ends with `user-`, not `=`, so `$age` is content and the attribute renders with control characters (`id="user-\u0000\u00010\u0000\u0001"`); the library entry on control characters in quoted attribute values has the reproducer. The block is `mdoc:compile-only`, so the site never shows the output.

**Suggested fix.** Use `id=$age` (whole value) in the example and say that a value inside a quoted attribute value is not supported (a known bug).

### `reference/html.md`: the "Template Composition" example throws at run time

**Where found.** `docs/reference/html.md:1242-1260`, 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

**Summary.** The template starts and ends with a newline, so `html"""..."""` sees three top-level nodes and throws `IllegalArgumentException` (the usability-trap entry on whitespace around the root has the reproducer). `mdoc:compile-only` hides it.

**Suggested fix.** Start the template with `<html>` on the first line and end it right after `</html>`, run the block with plain `mdoc`, and mention the whitespace rule next to the single-root requirement.

### `reference/html.md`: `Dom#filter` does not replace failing nodes with `Dom.Empty`

**Where found.** `docs/reference/html.md:240`, 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

**Summary.** "Non-matching nodes are replaced with `Dom.Empty`, and their children are lost" is false for children: a failing child is removed from its parent's children; only a failing root returns `Dom.Empty`. `Repro.scala`, run with `scala-cli run --server=false Repro.scala`:

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-html:0.0.56
import zio.blocks.html._

object Repro {
  def main(args: Array[String]): Unit = {
    val tree = div(p("A"), span("B", em("C")), p("D"))
    println(tree.filter { case el: Dom.Element => el.tag != "span"; case _ => true }.render)
    println(tree.filter { case el: Dom.Element => el.tag == "p"; case _ => true })
  }
}
// <div><p>A</p><p>D</p></div>
// Empty
```


**Suggested fix.** "A failing child is removed together with its subtree; when the root itself fails, the result is `Dom.Empty`."

### `reference/html.md`: multi-valued attributes, override and `accept`

**Where found.** `docs/reference/html.md:345-361`, 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

**Summary.** (a) `accept` is listed with `class` and `rel` as a multi-valued attribute, but it is an `AttributeKey`: `div(accept += "image/png")` fails to compile with "value += is not a member of zio.blocks.html.AttributeKey" (2.13.18: "did you mean :=?"; 3.9.0: "did you mean accept.!=? or perhaps accept.:=?"). (b) "`:=` (override) — Replaces any previous value. Last assignment wins" does not hold after `+=`: `className += "sale", className := "card"` renders `class="card sale"` (a known trap).

**Suggested fix.** Drop `accept` from the list (or make it a `MultiAttributeKey` with a comma separator), and say that `:=` replaces earlier `:=` values while `+=` values are always appended.

### `reference/html.md`: garbled JavaScript escape descriptions

**Where found.** `docs/reference/html.md:700-701`, `:1153-1155`, `:1168-1169`, 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

**Summary.** The Unicode escapes were turned into the characters themselves somewhere along the way. Line 1153 reads "`<` → backslash-u-0-0-3-c (the six-character Unicode escape sequence `<`)" (lines 1154-1155 likewise for `>` and `&`), and line 701 says "with < and > characters escaped as < and > in actual output". The real output of `js"let check = $userInput"` with `userInput = "if (x < y) alert('<script>');"` contains `\u003c`, `\u003e` and `\'`:

```
let check = "if (x \u003c y) alert(\'\u003cscript\u003e\');"
```

The code comments at lines 700 and 1168 also show unescaped output. (Line 1169 reads "(with < and > characters escaped as Unicode in actual output)", which is right.)

**Suggested fix.** Show the real `println(code.value)` output (run the blocks with plain `mdoc`), and write the escapes in code spans as `\u003c`, `\u003e`, `\u0026`.

### `reference/html.md`: smaller errors

**Where found.** `docs/reference/html.md`, 2026-11-05, base `a7014433f`. Same at v0.0.56: yes.

- Line 50: `case class Css.Declaration(property: String, value: String) extends Css` — `Css.Declaration` does not extend `Css` (`Css.scala:82`), and its constructor is private.
- Lines 147-149: "The Five Node Types" lists six headings (`Text`, `Generic`, `Script`, `Style`, `Doctype`, `Empty`) and leaves out `Dom.Element.Void`, which is a node type of its own (described at line 490).
- Line 92: "depends transitively on `zio-blocks-schema`": `html` depends on `schema` and `chunk` directly (`build.sbt`, `lazy val html ... .dependsOn(chunk, schema)`).
- Line 1069: "structured CSS ... prevents CSS injection": declaration values are not checked (Scala 2) or only quote-escaped (Scala 3; a known bug).
- Line 704-713 (`selector""`): no warning that the result never matches in `Dom#select` (existing #1765).
- Lines 893-897: the note on what `DomSelection` cannot match misses void elements, nested combinators and the attribute reading (all known bugs).

**Suggested fix.** Correct each line as stated.

### `reference/schema/built-in-codecs/xml.md`: the `@xmlAttribute` and `@xmlNamespace` examples do not do what the page says

**Where found.** `docs/reference/schema/built-in-codecs/xml.md` (Attributes, Namespaces, "Complete Example with Attributes and Namespaces", "RSS Feed Parsing", "Atom Feed with Advanced Features"), 2026-11-05, commit a7014433f.

**Summary.** The annotations have no effect (see the library entry "`@xmlAttribute` and `@xmlNamespace` have no effect"). The `mdoc` blocks that print output (`val xml = codec.encodeToString(person)` at about line 325, the two Feed blocks at about 358 and 378, `xmlStr` at about 835) render plain child elements without attributes or namespaces, contradicting the prose ("Encode the person with attributes", "Encode the feed with a namespace prefix"). The RSS example's `codec.decode(feedXml)` fails (`guid` is an attribute in the input, a child element for the codec).

**Fix.** Until the library is fixed, show `@Modifier.config("xml.attribute", "")` / `@Modifier.config("xml.namespace.uri", ...)` and `@Modifier.config("xml.namespace.prefix", ...)`, name the bug, and re-check the rendered output; also state that decoding ignores namespaces.

### `reference/schema/built-in-codecs/xml.md`: the `XmlSelection` and `XmlPatch` paths start one level too high

**Where found.** `docs/reference/schema/built-in-codecs/xml.md` ("XmlSelection > Navigation", "XmlPatch > Creating Patches", "Applying Patches", "Composing Patches", "Navigation and Transformation"), 2026-11-05, commit a7014433f.

**Summary.** `xml.select` already selects the root element, and `get(name)` looks at children, so `xml.select.get("library").get("books")` on a `<library>` document is empty, and so is every value derived from it (`firstBook`, `title`, `titles`, `books`). `books.get("book")(0)` would not pick the first book either: `apply(index)` returns the child at that index of every selected element (here the `<title>` of each book). `XmlPatch` paths also start at the root's children: `XmlPatch.setAttribute(p".person", ...)` on `<person>...</person>` returns `Left(Element 'person' not found)`, and `p".library.books"` on `<library>` fails the same way. Probe `/tmp/claude-1000/xml-2a/p1` (both Scala versions):

```
11 select.get(library) => 0
11b select.get(books) => 1
11c get(book)(0) => Chunk(Element(title,Chunk(),Chunk(Text(T1))),Element(title,Chunk(),Chunk(Text(T2))))
12 patch p.person => Left(zio.blocks.schema.SchemaError: Element 'person' not found)
12b patch root => Right(<person active="true"><name>Alice</name></person>)
```

The `mdoc` blocks that print `title` and `allTitles` render whatever the empty selection gives; the `compile-only` patch blocks are never run.

**Fix.** Drop the root name from selection chains (`xml.select.get("books").get("book")`), use `DynamicOptic.root` for the root and `p".books.book"`-style paths below it, and use `XmlSelection#apply` only with its real meaning (or pick from `toChunk`). Run the patch blocks with `mdoc` so their results show.

### `reference/schema/built-in-codecs/xml.md`: smaller errors

**Where found.** `docs/reference/schema/built-in-codecs/xml.md`, 2026-11-05, commit a7014433f.

- XmlName section: `simple.namespace // ""` and `simple.prefix // ""` — both are `Option[String]`, so the values are `None`.
- XmlBuilder section: `XmlBuilder.cdata("<![CDATA[raw content]]>")` puts the markers inside the content, so it prints `<![CDATA[<![CDATA[raw content]]>]]>`, which is malformed; pass `"raw content"`. The "Result:" comment shows indented output for a value that is never pretty-printed.
- WriterConfig section: `val compact = WriterConfig` binds the companion object; it should be `WriterConfig.default`.
- Supported Types / XmlCodec sections: "Variants (sealed traits)" and "Options" are listed without the limits (a variant below the top level and an `Option` inside a collection do not round-trip; see the library entries).
- Error Handling: "All decoding operations return `Either[SchemaError, A]` or `Either[SchemaError, A]`" repeats itself; "Error information includes parse location" holds only for malformed XML (reader errors carry `(line: L, column: C)`), not for decoding errors, which carry a path such as `at: .qty`.

### `reference/schema/built-in-codecs/toon.md`: misstates config scope, primitive count and the reader/writer

Found 2026-11-05 while fact-checking the schema-toon Scaladoc against the reference page (Task 2b), commit `a7014433f`; the page is the same at the 0.0.56 docs. Evidence: probes `p1`, `p2`, `p5` under `/tmp/claude-1000/task2b-toon/` (all against 0.0.56).

1. **`toon.md:84-94`** ("How They Work Together", steps 6 and 7) says `WriterConfig` controls "Array format selection" and `ReaderConfig` controls "Delimiter selection for inline arrays". The array format is a deriver setting (`ToonCodecDeriver.withArrayFormat`); `WriterConfig` has no such field (`WriterConfig.scala`, five fields: indent, delimiter, keyFolding, flattenDepth, discriminatorField). `ReaderConfig.withDelimiter` has no effect on decoding arrays, as the page itself says at `:624` (probe `p2`: `ReaderConfig.withDelimiter(Delimiter.Pipe)` still decodes `[2]: a,b` as `List(a, b)`). Steps 6 and 7 should also say that key folding, the delimiter and the discriminator field reach only the `DynamicValue` codec. Fix: drop "Array format selection" from step 6 and "Delimiter selection" from step 7; add "(`DynamicValue` codec only)" to the other DynamicValue-only items.
2. **`toon.md:120-121`** ("Type Relationships") repeats it: `WriterConfig` "(indentation, delimiters, discriminators)" and `ReaderConfig` "(path expansion, strict mode, delimiters)". Fix: "indentation for every codec; delimiter, key folding and discriminator field for the `DynamicValue` codec" and "indentation and strict mode for every codec; path expansion and discriminator field for the `DynamicValue` codec".
3. **`toon.md:6`, `:54`, `:440`, `:461`** say "27 primitive types". `ToonCodecDeriver#derivePrimitive` maps 30 (`ToonCodecDeriver.scala:137-167`: `Unit` through `UUID`), and the page's own list at `:462-465` names 30 (8 numeric, 3 logical, 16 temporal, 3 special). Fix: "30".
4. **`toon.md:1006`** says "`ToonCodecDeriver` drives all codec derivation via `ToonCodecDeriver.derive(schema)`". No such method exists (`Deriver` has only the `deriveX` hooks, `schema/shared/src/main/scala/zio/blocks/schema/derive/Deriver.scala:100-200`); derivation is `Schema#derive(ToonCodecDeriver)` or `Schema#derive(ToonFormat)`. Fix the call.
5. **`toon.md:738-742`, `:784-788`** call `ToonReader` a "low-level binary parser" with "error reporting at the byte level" and `ToonWriter` a "binary encoder" into "TOON binary format". TOON is text: the reader decodes the input as UTF-8 and splits it into lines (`ToonReader.scala:93-104`), and errors carry a path (`at: .port`), not a byte offset. Fix: "line-oriented text parser/writer".
6. **`toon.md:51`** ("Handle errors — Catch `SchemaError`"): the `decode` methods return `Either[SchemaError, A]` and catch the exceptions themselves (`ToonCodec.scala:234-236`); nothing needs catching. Fix: "Handle the `Left(SchemaError)`".
7. **`toon.md:865-907`** (DiscriminatorKind) and **`:688-690`** (known bugs) do not mention the two new `DiscriminatorKind.Field`/`None` bugs (B2, B3) or the `Array` field bug (B1). Once filed, add them to the page's known-bugs admonitions next to #1778-#1780.

### `reference/schema/built-in-codecs/yaml.md`: error paths are shown as `.field[0]`, but YAML errors render indexes as `.at(0)`

Found 2026-11-05 during the schema-yaml Scaladoc rollout (base a7014433f; page last changed in `4d774b674`). Same at 0.0.56: yes. Verified by running.

**Summary.** `docs/reference/schema/built-in-codecs/yaml.md` says "Errors render as paths like `.field[0].nested.value`" (line 582) and, in the pipeline diagram, "Shows path (.field[index].nested)" (line 94). A YAML decoding error renders a sequence index as `.at(1)`, the `DynamicOptic` form, so a reader who searches logs for `[1]` finds nothing.

**Reproducer** (save as `Repro.sc`, run `scala-cli run --server=false Repro.sc`; probe directory `/tmp/claude-1000/yaml-2c/docs-path`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Team(name: String, members: List[String])
object Team { implicit val schema: Schema[Team] = Schema.derived }

println(Team.schema.derive(YamlFormat).decode("name: core\nmembers:\n  - ada\n  - lead: grace\n"))
// Left(zio.blocks.schema.SchemaError: Expected scalar for string at: .members.at(1))
```

(Output of the same shape from `fc1`: `Left(zio.blocks.schema.SchemaError: Expected scalar for string at: .tags.at(1))`.)

**Expected page text.** "Errors render as paths like `.members.at(1)` or `.inner.level`" and, in the diagram, "Shows path (.field.at(index).nested)".

**Suggested fix.** Edit the two lines; optionally add an mdoc example whose failure is inside a list, so the rendered output shows `.at(n)`.

### `reference/jwt.md`: says no values are dropped during parsing

Found 2026-11-05 while fact-checking the jwt Scaladoc (branch `jwt-scaladoc`, base `3ac1121a9` = v0.0.56 for jwt sources), in `docs/reference/jwt.md:98` ("Extra claims"). The same sentence is at `v0.0.56` (`docs/reference/jwt.md:79`).

**Summary.** `docs/reference/jwt.md`, "Extra claims": "Objects and arrays nest arbitrarily and are preserved exactly; no values are dropped during parsing." Two parts are false: an object that repeats a field name keeps only the last value (`{"role":"reader","role":"admin"}` parses as `Map(role -> Str(admin))`, reproducer below), and nesting is bounded by `JwtLimits.maxDepth` (default 32). The `JwtValue` Scaladoc said the same ("no values are silently dropped") and is fixed on this branch.

**Reproduction** (Scala 3.9.0, `zio-blocks-jwt` 0.0.56, `Repro.sc`, run with `scala-cli run --server=false Repro.sc`; the same output with `//> using scala 2.13.18`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-jwt:0.0.56
import zio.blocks.jwt._

println(JwtClaims.parse(Base64Url.encode("""{"role":"reader","role":"admin"}""".getBytes("UTF-8"))).map(_.extra))
// Right(Map(role -> Str(admin)))
```

**Expected.** The page says what parsing keeps: objects and arrays nest up to `JwtLimits.maxDepth` (default 32), and a repeated field name keeps its last value.

**Root cause.** The page text, not the code: the parser stores each field with `fields.update(key, value)` (`jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:622` and `:671`, lines at v0.0.56 (= 3ac1121a9)), so a later duplicate replaces an earlier one, and it throws `TooDeep` past `maxDepth` (`:602`, `:642`, `:658`).

**Impact.** Low. A reader who relies on "no values are dropped" may accept a payload that repeats a field and miss that one value was discarded.

**Test gap.** No test parses a payload with a repeated field name (`git grep -n -i "duplicate\|repeated" 3ac1121a9 -- 'jwt/*/src/test'` finds nothing).

**Suggested fix.** "Objects and arrays nest up to `JwtLimits.maxDepth` and keep their values, except that a repeated field name keeps its last value." Also, in "Limits and Options", "Resource limits are conservative and checked before allocation" is vague; the next paragraph already says what runs when, so the first sentence can go.

### `reference/jwt.md`: states the JVM curve check more broadly than tested

Found 2026-11-05 while fact-checking the jwt Scaladoc (branch `jwt-scaladoc`, base `3ac1121a9` = v0.0.56 for jwt sources), in `docs/reference/jwt.md:168` ("Supported Algorithms and Key Strength") and the `InvalidKey` row of the "Error Handling" table (`docs/reference/jwt.md:208`). At `v0.0.56` the line-168 claim is at `docs/reference/jwt.md:145` ("EC curve mismatches (e.g. `ES256` with `secp384r1`)"); that version has no `InvalidKey` table row.

**Summary.** The page says that on the JVM "EC keys on the wrong curve (e.g. `ES256` with `secp384r1`) are also rejected with `InvalidKey`", and the error table lists "wrong EC curve" under `InvalidKey`. What was tested is narrower: a P-256, P-384 or P-521 key used with an `ES*` algorithm for one of the other two curves. Each of those six cases gives `InvalidKey`. The page should claim only that.

**Reproduction** (Scala 3.9.0, `zio-blocks-jwt` 0.0.56, `Repro.sc`, run with `scala-cli run --server=false Repro.sc`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-jwt:0.0.56
import zio.blocks.jwt._
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

JvmJwtCryptoBackend.install()
def key(curve: String) = { val g = KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec(curve)); g.generateKeyPair().getPrivate.getEncoded }
val claims = JwtClaims(sub = Some("user-42"))
for ((alg, curve) <- Seq(Algorithm.ES256 -> "secp384r1", Algorithm.ES256 -> "secp521r1", Algorithm.ES384 -> "secp256r1", Algorithm.ES384 -> "secp521r1", Algorithm.ES512 -> "secp256r1", Algorithm.ES512 -> "secp384r1"))
  println(s"${alg.name} with $curve: " + Jwt.sign(claims, key(curve), alg).left.map(_.getClass.getSimpleName))
// ES256 with secp384r1: Left(InvalidKey)
// ES256 with secp521r1: Left(InvalidKey)
// ES384 with secp256r1: Left(InvalidKey)
// ES384 with secp521r1: Left(InvalidKey)
// ES512 with secp256r1: Left(InvalidKey)
// ES512 with secp384r1: Left(InvalidKey)
```

**Expected.** The page names the tested cases only, as the Scaladoc on this branch does.

**Root cause.** The page text generalises from the tested cases.

**Impact.** Low; a reader may rely on a check that the page states more broadly than tested.

**Test gap.** `JwtJvmSecuritySpec.scala:228-245` covers `ES256` with P-384, `ES384` with P-256 and `ES512` with P-256 only.

**Suggested fix.** Line 168: "On the JVM, RSA keys under 2048 bits and a P-256, P-384 or P-521 key used with the wrong `ES*` algorithm (e.g. `ES256` with `secp384r1`) are also rejected with `InvalidKey`". Error table: "on JVM, RSA under 2048 bits or a P-256/P-384/P-521 key with the wrong `ES*` algorithm".

### `reference/schema/built-in-codecs/avro.md`: claims a configurable sum-type encoding and calls a format property a bug

Found 2026-11-05 while fact-checking the Avro Scaladoc against this page (schema formats Scaladoc rollout, Task 1), on `main` at `c2e4a39fd`. Same at v0.0.56.

**Summary and fixes.**
- Line 17, "Configurable sum type handling (union fields with discriminators)": `AvroCodecDeriver` has no configuration at all (`object AvroCodecDeriver extends Deriver[AvroCodec]`, no options); a variant is always a union of the case records, written as the branch index and the case (`AvroCodecDeriver.scala:180-231`). Replace with "Sum types as Avro unions, one record per case".
- Lines 8, 19 and 178, "recursive data structures with cycle detection" / "automatic cycle detection": the deriver handles recursive *types* (a record refers to itself by name in the schema, `recursiveRecordCache`), but there is no cycle detection for *values*; a cyclic value cannot exist for case classes anyway. Say "recursive types, such as trees".
- Lines 43 and 323-325: step 4 says "Catch `SchemaError`", but `decode` returns `Either[SchemaError, A]` and never throws it; say "Handle the `Left`". Line 323 says decoding bytes of another type "is a known bug in 0.0.56, not a choice of the format", but Avro binary has no type information, so this is inherent to the format (the library-issues entry says so in its root cause). Reword: "Avro binary carries no type information, so the codec cannot detect this; zio-blocks adds no check of its own (a known limitation in 0.0.56)."
- Missing: `decode(ByteBuffer)` on a sliced heap buffer reads the wrong bytes (see the new library entry), worth one sentence in "Decoding Trusts the Reader's Schema".

### `reference/schema/built-in-codecs/thrift.md`: wrong primitive count, invented features, and no word on missing fields

Found 2026-11-05 while fact-checking the Thrift Scaladoc against this page (schema formats Scaladoc rollout, Task 1), on `main` at `c2e4a39fd`. Same at v0.0.56.

**Summary and fixes.**
- Lines 6, 45, 309 and 330 say 27 primitive types; `ThriftCodecDeriver.derivePrimitive` maps all 30 `PrimitiveType`s (`ThriftCodecDeriver.scala:50-79`), and the list under line 330 itself names 30 (it includes `BigInt`, `Unit`, `Currency`, `UUID`). Say 30.
- Line 14, "Automatic schema generation from Scala types", and line 45, "automatically generates Thrift-compatible formats": the module generates no Thrift IDL or schema object (`ThriftCodec` has no schema member, unlike `AvroCodec#avroSchema`). Drop the line or say "No `.thrift` file needed".
- Line 73, "Automatic format detection and optimization": nothing detects a format; `TBinaryProtocol` is fixed (`ThriftCodec.scala:86-88`, `:97-99`). Drop it.
- Lines 12 and 101, 376 say "ZIO Schema"; this module uses ZIO Blocks Schema (`zio.blocks.schema`), a different library. Replace.
- Lines 17 and 157, "automatic cycle detection": as for Avro, recursive types, not cycle detection.
- Line 43, "Catch `SchemaError`": `decode` returns `Either`.
- Lines 180 and 355, "discriminated variants with type information" / "union types with discriminators": the encoding is a struct with one field whose id is the case index plus one (`ThriftCodecDeriver.scala:241-246`); the case name is not written. Say that.
- Missing, and the most important for users: a record field absent from the input decodes as `null`/`0`/`false`; the field's wire type is not checked; `DynamicValue` cannot be decoded; trailing bytes are ignored; `Float` is written as a Thrift `double`; most `java.time` types, `UUID` and `Currency` are Thrift strings. See the new library entries; the Scaladoc of `ThriftCodec` and `ThriftCodecDeriver` now states these.

### `reference/schema/built-in-codecs/bson.md`: claims about timestamps, skipping absent values and error reporting that the code does not meet

Found 2026-11-05 while fact-checking the `schema-bson` Scaladoc against `docs/reference/schema/built-in-codecs/bson.md` at `c2e4a39fd` (same at `v0.0.56`).

- Motivation, first bullet: "Full BSON type support (documents, arrays, strings, numbers, ObjectId, Decimal128, timestamps, etc.)". The codecs never write BSON `DATE_TIME` or `TIMESTAMP`: `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration` and the other `java.time` types are written as ISO-8601 `STRING`s (`BsonTypes.scala:448-561`), `UUID` as a `STRING` (`:328-331`), and `BsonCodec.json` fails to decode a `DATE_TIME` with `Unsupported BSON type for Json: DATE_TIME` (`:440`). Verified: `{"inst": "2026-11-05T10:15:30Z"}`, BSON type `STRING`. Suggested text: list what is supported and add a type table (Scala type → BSON type), as `avro.md` has; say plainly that dates are strings, so MongoDB date queries and TTL indexes don't apply to them.
- Introduction, step 4 (`bson.md:46`): "**Handle errors** — Catch `BsonDecoder.Error` with location traces ...". `decode` and `fromBsonValue` return it in a `Left` (`BsonTypes.scala:112-122`); only the `*Unsafe` methods throw it. Suggested: "Handle errors — `decode` and `fromBsonValue` return a `BsonDecoder.Error` in a `Left`, with the path to the bad value".
- `BsonEncoder[A]` › Overview: "It supports skipping absent values". `isAbsent` exists but no code in the module calls it (`grep -rn isAbsent schema-bson/src/main` finds only its definition and `contramap`), so no field is ever skipped (transient fields are dropped by `Modifier.transient`, not by `isAbsent`). Suggested: drop the sentence or say `isAbsent` is unused.
- Motivation: "Precise error reporting with location traces showing the path to errors". True only for `BsonDecoder.Error`; malformed strings and (through `decode`) wrong BSON types throw instead (library entry 1 above). Suggested: add "with a known bug in 0.0.56: ..." once entry 1 is filed.

### `reference/schema/built-in-codecs/csv.md`: Introduction step 4 tells readers to catch `CsvError`; the codec never throws it

Found 2026-11-05 while fact-checking the `schema-csv` Scaladoc against `docs/reference/schema/built-in-codecs/csv.md` at `c2e4a39fd` (same at `v0.0.56`).

- Introduction, step 4: "Handle errors — Catch `CsvError` for malformed input, and `SchemaError` for a cell that doesn't convert". Nothing throws `CsvError`: `CsvReader` returns it in a `Left`, and `CsvCodec#decode` turns malformed CSV into a `SchemaError` whose message is the `CsvError`'s `getMessage` (`CsvCodecDeriver.scala:182`), for example `Left(SchemaError: CSV error at row 1, column 14: Unclosed quoted field at: .)`. Suggested: "Handle errors — `CsvReader` returns a `CsvError` in a `Left`; `CsvCodec#decode` returns a `SchemaError` in a `Left` for a malformed row, a wrong number of cells or a bad cell."
- CsvCodecDeriver › Record Type Support: "Each field becomes a CSV column with the field name as the header" does not say that `Modifier.rename` and `Modifier.transient` are ignored (library entry 3), and "Flat case classes ... are supported" does not warn that an `AnyVal` case class field counts as a nested record (amendment 5). Suggested: one sentence each, naming them as known bugs in 0.0.56.

### `reference/schema/built-in-codecs/messagepack.md`: ext support, variant layout, primitive count and error-path format contradict the codec

Found 2026-11-05 while fact-checking the `schema-messagepack` Scaladoc against `docs/reference/schema/built-in-codecs/messagepack.md` at `aa8164217` (module sources identical to v0.0.56). Verified on Scala 2.13.18 against published 0.0.56.

**Summary.** Four claims on the page are false:

1. Lines 13 and 420: "Full MessagePack type support (all fixint, fixarray, fixmap, ext types, ...)" and the reader handles "all MessagePack types (... ext ...)". No reader path accepts an extension type: the typed readers fail with `Expected ... value, got: 212`, `MessagePackReader#skipValue` with `Unknown MessagePack type: ...` (`MessagePackReader.scala:304`), and the `DynamicValue` codec with `Unsupported MessagePack type for DynamicValue: 212` (`MessagePackCodecDeriver.scala:559`).
2. Line 370: "Sealed traits and sum types are encoded as MessagePack maps with discriminator fields". A variant is the case index as an int followed by the case's value (`MessagePackCodecDeriver.scala:327-331`); only `Either` is a map (`{"left": ...}` / `{"right": ...}`), and `Option` is an array of 0 or 1 element. (That layout is itself a known bug; see the Library bugs entry "a derived variant is written as two MessagePack values".)
3. Lines 6, 50, 324 and 345: "27 primitive types". The page's own list under line 345 names 30, and `MessagePackCodecDeriver#derivePrimitive` maps 30 `PrimitiveType`s (`MessagePackCodecDeriver.scala:43-74`).
4. Lines 82, 109 and 480: errors "render as paths like `.field[0].nested`". A sequence index renders as `.at(1)` (`.emails.at(1)`), a map value as `.atKey(...)`, a case as `.when[Case]`.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` and `zio-blocks-schema-messagepack` 0.0.56, run with `scala-cli run --server=false RefPage.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-schema-messagepack:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

sealed trait Status
object Status {
  case class Active(since: String)    extends Status
  case class Inactive(reason: String) extends Status
  implicit val schema: Schema[Status] = Schema.derived
}

case class Contact(emails: Seq[String])
object Contact { implicit val schema: Schema[Contact] = Schema.derived }

object RefPage {
  def hex(bytes: Array[Byte]): String = bytes.map(b => "%02x".format(b & 0xff)).mkString(" ")

  def main(args: Array[String]): Unit = {
    // A variant is the case index (01) followed by the case's map, not a map with a discriminator
    println(hex(Schema[Status].derive(MessagePackFormat).encode(Status.Inactive("x"))))
    // 01 81 a6 72 65 61 73 6f 6e a1 78

    // ext types (fixext1 d4 here) are rejected by every reader path
    println(Schema[Int].derive(MessagePackFormat).decode(Array[Byte](0xd4.toByte, 1, 1)))
    // Left(zio.blocks.schema.SchemaError: Expected int value, got: 212 at: .)
    println(Schema[DynamicValue].derive(MessagePackFormat).decode(Array[Byte](0xd4.toByte, 1, 1)))
    // Left(zio.blocks.schema.SchemaError: Unsupported MessagePack type for DynamicValue: 212 at: .)

    // error paths render as .emails.at(1), not .emails[1]
    val bytes = Array(0x81, 0xa6, 'e', 'm', 'a', 'i', 'l', 's', 0x92, 0xa1, 'a', 0x01).map(_.toByte)
    println(Schema[Contact].derive(MessagePackFormat).decode(bytes))
    // Left(zio.blocks.schema.SchemaError: Expected string header, got: 1 at: .emails.at(1))
  }
}
```

**Expected.** The page states what the codec does: no ext support, the index-then-value variant layout (with the interop warning), 30 primitives, and paths such as `.emails.at(1)`.

**Root cause.** The page was written from the MessagePack spec and the JSON page, not from `MessagePackCodecDeriver`.

**Impact.** Medium: a reader planning interop with another MessagePack library is told variants are self-describing maps and ext types work.

**Suggested fix.** Correct the four claims; add a "Wire format" table (record = map by field name, exactly one entry per field; `Option` = array of 0/1; `Either` = one-entry map; other variants = index then value; sequences = arrays; maps = maps with codec-encoded keys); link the Library bugs entries on variants and duplicate keys by GitHub issue once filed.

### `reference/http-model/schema-codecs.md` and `schema.md`: overrides do not apply to record fields; UUIDs need not be in the standard form

Found 2026-11-05 while fact-checking the http-model-schema Scaladoc against the reference pages, on `aa8164217`; the module sources are the same at `v0.0.56`. Verified against the published 0.0.56 on Scala 3.9.0.

1. `docs/reference/http-model/schema-codecs.md`, "Overriding a Single Type": "The override applies wherever that type appears, so the custom rendering is used on both sides". It applies only when the overridden type is the whole schema; a record field of that type keeps the built-in rules (reproducer and root cause in NEW 3: `QueryParams((number,42))` instead of `number=p-42`). The page's example uses a top-level `Schema[Int]`, so mdoc does not catch it. Suggested text: "The override applies when the overridden type is the whole schema. In 0.0.56 a record's fields ignore it, a known bug."
2. `docs/reference/http-model/schema.md`, "UUID": "error on invalid format (must be standard UUID format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)". On the JVM, `java.util.UUID.fromString` also accepts short groups:

   ```scala
   //> using scala 3.9.0
   //> using options -experimental
   //> using dep dev.zio::zio-blocks-next-http-model-schema:0.0.56

   import zio.http.QueryParams
   import zio.http.schema._

   object ShortUuid {
     def main(args: Array[String]): Unit =
       println(QueryParams("order" -> "1-2-3-4-5").query[java.util.UUID]("order"))
   }
   // Right(00000001-0002-0003-0004-000000000005)
   ```

   (`ShortUuid.scala`, `scala-cli run --server=false ShortUuid.scala`; Scala.js not probed.) Suggested text: "Parsed with `java.util.UUID.fromString`; on the JVM it also accepts shortened groups such as `1-2-3-4-5`."

### `reference/combinators.md`: `Choices` does not exist, `Concat` is not Scala-2-only, and other errors

**Where found.** Fact-checking `docs/reference/combinators.md` against the sources while writing the combinators Scaladoc, 2026-11-05, base `c2e4a39fd` (same at `v0.0.56`). Probed with scala-cli against `zio-blocks-combinators:0.0.56` on 2.13.18 and 3.9.0 (`/tmp/claude-1000/impl1/comb/C2.scala`, `C3.scala`).

- **`Choices`** (Overview, Installation "Supported platforms", the `Concat` section "Unlike `Choices`", the Feature Matrix row, Migration Path steps 3 and 5) names a module that does not exist in `combinators` (`git grep -n "Choices" -- 'combinators/*'` is empty). `docs/dev/backlog.md:371` and `:449` (§8 "Doc fixes to do alongside") and `docs/dev/research.md:2657` already note `Choices`, the "Scala 2 only" label and `Concat.Concat.WithOut`; this entry gives every place and adds the other errors, so the §8 row can point here.
- **"Concat (Scala 2 Only)"** and "**Concat**: Scala 2.13 only": `Concat` is in the shared sources and exists on Scala 3 too, where `derive` gives `Out = L | R` (`combinators/shared/src/main/scala-3/.../ConcatCompanionPlatform.scala:19-30`); `Stream#concat` uses it on both versions.
- **`Concat.Concat.WithOut[...]`** (four lines in the `Concat` section): the type is `Concat.WithOut[L, R, O]` (`Concat.scala:103`); `Concat.Concat` does not exist.
- **The Scala 2.13 tab** of "Tuples: Version Differences" says `Tuples.combine((1, 2), (3, 4))` fails to compile with "Type mismatch". It compiles and returns `(1,2,(3,4))` (the known Scala 2 nesting bug, already filed). The "Quick Example" `val userTuple: Tuple3[String, Int, String] = Tuples.combine(username, Tuples.combine(userId, email))` does not compile on Scala 2 for the same reason.
- **`t2.separate((1, "hello")) // ((1), "hello")`**: the result is `(1,hello)` (a pair of `1` and `"hello"`).
- **`// (1, "hello", true, 3.14)j`**: stray `j`.
- **"Path-Dependent Types": "The `Out`, `Left`, and `Right` type members"**: the type classes have only `Out`.
- **See Also, "The Schema module uses `Eithers` canonicalization for encoding sealed trait hierarchies"**: `schema` does not use `Eithers` (`git grep -n "Eithers\." -- 'schema/*/src/main'` is empty).
- **"Eithers: Full Cross-Version Support ... No version-specific behavior"** is true for the shapes; the page could add that on Scala 2 `implicitly[Eithers.Eithers[L, R]]` loses `Out` (the whitebox macro's declared type is `Eithers[L, R]`), so `separate` is usable only through an implicit parameter, as in the Scaladoc examples. Same for `Tuples` and the `summon[...]` examples (Scala 3 only).

Suggested fix: remove `Choices`; describe `Concat` on both versions; correct the paths and outputs; mark the right-tuple examples Scala 3 only (or show the Scala 2 nesting); drop the schema claim.

### `reference/maybe.md`: `isPresent`/`isAbsent` described as null checks, Scala 2 `Option` conversion, `toSeq` output

**Where found.** Fact-checking `docs/reference/maybe.md` while writing the maybe Scaladoc, 2026-11-05, base `c2e4a39fd` (same at `v0.0.56`). Probed with `/tmp/claude-1000/impl1/maybe/Probe.scala` on 2.13.18 and 3.9.0.

- **"isPresent, isDefined, nonEmpty: All three are equivalent—return true if the value is non-null"** and **"isAbsent, isEmpty: Both return true if the value is `null`"**: absence is the `Absent` object, not `null`; `Maybe.present(null: String).isPresent` is `true` and `.isAbsent` is `false` on both versions (`Maybe.scala:28-32` Scala 2, `:153-157` Scala 3 compare with `Absent`).
- **"Integration Points": "a `Conversion[Option[A], Maybe[A]]` is provided for implicit conversions"**: Scala 3 only; on Scala 2 the conversion is `optionToMaybe` and needs `import Maybe._` (library entry 2).
- **`println(seq)  // Seq("hello")`** (`toSeq`): prints `List(hello)`.
- **`maybe.md:64`** ("only present-of-absent values allocate a `Present` wrapper") and the "Nested allocation" row at `:84` ("**1** (`Present(Absent)` only)") leave out `Maybe.present(Present(x))`, which is `Present(Present(x))` on Scala 3 (probe: `present(Present(1)) Present(Present(1))`); `:90` calls `Maybe.present(null)` "present-of-absent", but it is a present `null`.
- **Cross-Version Parity / "Behavior Differences"**: add that `Maybe(Maybe.absent)` is absent on Scala 3 and present on Scala 2, and that `toString`/`==` differ (`Maybe.present(8080)` prints `8080` on Scala 3 and equals `8080` only when compared as `Any` (a direct `Maybe.present(8080) == 8080` does not compile there), prints `Present(8080)` on Scala 2; `Maybe.absent` prints `zio.blocks.maybe.Absent$@<hash>` on Scala 3 and `Absent` on Scala 2).

### `reference/endpoint/endpoint.md` says the first `out` and `outError` widen from `Unit` to the schema type

**Where found.** `docs/reference/endpoint/endpoint.md:199` and `:236`, 2026-11-06, `07f71de18`.

**Summary.** ":199 The output type widens from `Unit` to the first schema type, then to a nested `Either` for each additional alternative." and ":236 Each `Endpoint#outError` call widens `Err` by one nested `Either` layer." The real types are `Either[A, Unit]` after the first call and `Either[Either[B, A], Unit]` after the second (see the endpoint library entry on `Either[A, Unit]` outputs). The page's `orOutError` example (`:248-252`) is correct.

**Fix.** Either fix the library bug "the first `out` / `outError` keeps the empty `Unit` output as an alternative", or state the real types and link the bug in plain text ("a known bug in 0.0.56").

### `reference/endpoint/bulk-creation.md` promises auto-names that do not compile, and gives the wrong Scala/artifact rule

**Where found.** `docs/reference/endpoint/bulk-creation.md:8`, `:42-46`, `:96`, 2026-11-06, `07f71de18`.

**Summary.**
- `:42-46` list "`v{major}` for `~` concat segments" and "`.unused` renders as `{name}`" as auto-names; a `~` segment stops the compile wherever it is, and `.unused` after a literal does too (see the endpoint entry on `endpoints { ... }` auto-names). Two literals (`GET /notes/latest`), the most common REST shape, also fail and are not mentioned.
- `:8` and `:96` say the DSL needs "Scala 3.8+ with `-experimental` ... also works on Scala 3.7 with `-experimental`". For 0.0.56, `zio-blocks-endpoint_3` does not contain `endpoints` at all (a 3.9.0 build against it reports "Not found: endpoints"), so it is available only from `zio-blocks-next-endpoint` on Scala 3.9+ with `-experimental`; built from `main` on Scala 3.9.0 it compiles without `-experimental` (the `scaladoc-check` run on 3.9.0 compiles the new `endpoints` examples without the flag), so the page needs the rule of the release it documents.

**Fix.** List only the shapes that work (one literal or capture after the method; one literal then one capture), name the bug, and use the install rule of the other pages.

### `reference/endpoint/auth-type.md` and `http-codec.md` claims that the Scaladoc corrected

**Where found.** 2026-11-06, `07f71de18`.

- `auth-type.md:134` "By default, `AuthType` returns `Status.NotFound` when the client does not meet the auth requirement": nothing in the module returns a status; `unauthorizedStatus` is a value for a server to use. Suggested: "`AuthType#unauthorizedStatus` is `Status.NotFound` by default; a server built on the endpoint should answer with it ...".
- `http-codec.md:221` "surfacing a `SchemaError` if the scheme does not match": true; it could add that the message names the class of the parsed header (`... but found Bearer`, or `Unparsed` for an unknown scheme), see `HttpCodec.basicAuth` Scaladoc.
- `route-tree.md:62` "HEAD requests automatically fall back to the GET subtree if no HEAD handler is registered": the fallback also happens when a `HEAD` root exists but has no match for the path (`RouteTree.scala:57-59`); and the `RoutePattern#decode` side differs for method sets (the endpoint entry on `HEAD` and `GET #| POST`).

### `reference/projection/testing.md`: in-memory counters behave as in SQLite

Found 2026-11-06 while writing the `projection` Scaladoc, at commit 07f71de18 (same at v0.0.56).

**Summary.** `docs/reference/projection/testing.md`, section InMemoryProjectionStore (line 81 at 07f71de18): "A `FieldUpdate` is applied by decoding the stored row into a `DynamicValue`, changing the field, and encoding it back, so counters behave as they do in SQLite." They do not for row types with an `Option` field (entry 2: a missing row cannot be created, `Set` of the field fails, `Increment` of a `None` field does nothing), nor for unknown column names (SQLite dies, entry 1; the in-memory store ignores them), nor for a `Set` with a value of the wrong type (SQLite stores it and reads back `0` for an `Int` column; the in-memory store fails with `Expected Int`).

**Reproducer.** Entry 2's `Repro.sc` for the `Option` cases.

**Expected.** The page lists the differences, or says that the in-memory store matches SQLite only for rows without `Option` fields and with valid column names.

**Suggested fix.** Replace "so counters behave as they do in SQLite" with a short list of the differences, until entries 1 and 2 are fixed.

### `reference/htmx/attribute-values.md`: "Extending with Custom Types" claims a custom `ToHtmxValue` makes a type usable in the DSL; it can't be

Found 2026-11-06 while writing the htmx Scaladoc (branch `scala3-web-modules-scaladoc`, base `07f71de18`). The `htmx/` sources at `07f71de18` are identical to tag `v0.0.56` (`git diff v0.0.56 07f71de18 -- htmx/shared/src/main` is empty), so line numbers hold for both.

**Summary.** `docs/reference/htmx/attribute-values.md:280-331` says "Define your own `ToHtmxValue` instance to make custom domain types work in the HTMX DSL" and "If you get an error like 'No `ToHtmxValue` instance found for type X,' implement the type class". Each `HtmxAttrKey` is built with a fixed value type and its instance (`HtmxAttrKey.stringValue[A]`, private to the package); `:=` takes `A`, with no implicit. A custom instance can't make a type assignable to any key, and users can't build keys. The page's own example never uses the `Priority` instance (it interpolates `Priority.High.toString()` into a `Js`). The same false claims were in the Scaladoc of `HtmxAttributes` and `ToHtmxValue` (fixed on this branch).

**Reproducer.** `Repro.scala`, run with `scala-cli run --server=false Repro.scala`:
```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-htmx:0.0.56

import zio.blocks.html._
import zio.http.htmx._

enum Priority { case Low, High }
object Priority {
  given ToHtmxValue[Priority] = new ToHtmxValue[Priority] {
    def toHtmxValue(p: Priority): String = p.toString.toLowerCase
  }
}

object Repro {
  def main(args: Array[String]): Unit = {
    println(ToHtmxValue[Priority].toHtmxValue(Priority.High))
    println(button(hxConfirm := Priority.High)("Create").render)
  }
}
// -- [E007] Type Mismatch Error: Repro.scala:17:41
// 17 |    println(button(hxConfirm := Priority.High)("Create").render)
//    |                                ^^^^^^^^^^^^^
//    |                                Found:    (Priority.High : Priority)
//    |                                Required: String
// 1 error found
```

**Expected.** The page says what the instances are for (rendering the built-in value types; `ToHtmxValue[A].toHtmxValue(a)` by hand) and drops the extension section, or the library adds a public way to define keys.

**Root cause.** Documentation. `htmx/shared/src/main/scala/zio/http/htmx/HtmxAttrKey.scala:30-43` (private constructor and factory, `:=` without an implicit).

**Impact.** Readers write instances that do nothing and then hit a type mismatch the page tells them an instance would fix.

**Suggested fix.** Replace the section with a short note on the closed key set; remove the "No `ToHtmxValue` instance found" advice. Also `index.md:47` ("Extensible type class `ToHtmxValue` lets custom domain values render themselves").

### htmx reference: smaller errors in `hx-swap.md`, `hx-target.md`, `index.md` and `attribute-values.md`

Found 2026-11-06 while writing the htmx Scaladoc (branch `scala3-web-modules-scaladoc`, base `07f71de18`). The `htmx/` sources at `07f71de18` are identical to tag `v0.0.56` (`git diff v0.0.56 07f71de18 -- htmx/shared/src/main` is empty), so line numbers hold for both. Checked against htmx 2.0.11 (`htmx.js`) and the published 0.0.56 module.

**Summary and evidence.**
- `hx-swap.md:136`: "`focusScroll(enabled)` ... Pass `true` to focus the swapped element, `false` to disable focus movement". htmx's `focus-scroll` controls whether the browser scrolls to a focused input after the swap (`preventScroll`, htmx.js:2018), not whether focus moves; and htmx 2 ignores the library's `focusScroll:` token altogether (see the library entry "`HxSwap#focusScroll` renders `focusScroll:`").
- `hx-target.md:102` and `:127`: "`next(selector)` targets the next sibling matching the selector" (and the same for `previous`). htmx 2 scans the whole document in order from the element (`scanForwardQuery`/`scanBackwardsQuery`, htmx.js:1211-1232), so `next .row` can be a non-sibling. The bare forms are siblings (`nextElementSibling`).
- `index.md:247`: "Attributes that accept selectors (hxTarget, hxSelect, hxDisabledElt, hxIndicator) accept `CssSelector`". `hxTarget` takes `HxTarget` (`HtmxAttributes.scala:50`); `hxTarget := CssSelector.id("x")` does not compile.
- `attribute-values.md:122`: "Use `HxSwapOob(true)` or `HxSwapOob(false)` for boolean flags". `false` still swaps out of band (library entry "`HxSwapOob.False` renders ...").

**Reproducer.** The rendered values and browser runs for `focusScroll` and `HxSwapOob(false)` are in the two library entries named above. For `index.md:247`, `Repro.scala`, run with `scala-cli run --server=false Repro.scala`:
```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-htmx:0.0.56

import zio.blocks.html._
import zio.http.htmx._

object Repro {
  def main(args: Array[String]): Unit =
    println(div(hxTarget := CssSelector.id("x")).render)
}
// -- [E007] Type Mismatch Error: Repro.scala:9:42
// 9 |    println(div(hxTarget := CssSelector.id("x")).render)
//   |                            ^^^^^^^^^^^^^^^^^^^
//   |                            Found:    zio.blocks.html.CssSelector.Id
//   |                            Required: zio.http.htmx.HxTarget
// 1 error found
```

**Root cause.** Documentation.

**Suggested fix.** Reword each line as above.

### datastar reference: `attributes.md` says `DatastarAttrKey`'s constructor is `private[datastar]`, but it is public

Found 2026-11-06 while fact-checking `DatastarAttrKey` Scaladoc, on branch `scala3-web-modules-scaladoc` at `07f71de18`.

**Where.** `docs/reference/datastar/attributes.md:261`: "Since the constructor is `private[datastar]`, reach it through a helper that returns a bare key."

**Fact.** `datastar/shared/src/main/scala/zio/blocks/datastar/DatastarAttrKey.scala:30` declares `final class DatastarAttrKey(val name: String)` with a public constructor; only the companion's `apply` (`:40`) is `private[datastar]`. `new DatastarAttrKey("data-custom") := js"1"` compiles against 0.0.56 and renders `<span data-custom="1"></span>` (run with scala-cli, Scala 3.9.0, `zio-blocks-next-datastar` 0.0.56). The page's own intro two lines up says "you can construct one for an attribute the module has no helper for yet", which contradicts the sentence.

**Fix.** Replace the sentence with: "`new DatastarAttrKey(name)` makes a key for an attribute that has no helper; the name is written as given, so use names from your own code. The bare helpers `dataAttr`, `dataClass`, ... return keys too:".

### datastar reference: `events.md` calls `threshold(pct)` a "visibility fraction", but Datastar reads a percentage

Found 2026-11-06 while fact-checking `DataOnIntersect#threshold`, on branch `scala3-web-modules-scaladoc` at `07f71de18`.

**Where.** `docs/reference/datastar/events.md:162`: "| `threshold(pct)` | Require an explicit visibility fraction |".

**Fact.** Datastar's attribute reference documents `__threshold` as a percentage ("`.25` – Triggers when 25% of the element is visible"); `threshold(0.25)` renders `__threshold.0.25`, `threshold(25)` renders `__threshold.25` (see the library entry "`DataOnIntersect#threshold` takes a fraction-looking `Double`").

**Fix.** "| `threshold(pct)` | Require `pct` percent visibility, as a whole number such as `25` |".

### datastar reference: `sse.md` describes `Outer` and `Inner` as replacing, but Datastar morphs

Found 2026-11-06 while fact-checking `ElementPatchMode`, on branch `scala3-web-modules-scaladoc` at `07f71de18`.

**Where.** `docs/reference/datastar/sse.md`, `ElementPatchMode` table: "`Outer` | Replace the target element itself *(default)*", "`Inner` | Replace the target's children", "`Replace` | Replace using replacement semantics".

**Fact.** Datastar's SSE reference: outer "Morphs the outer HTML of the elements. This is the default (and recommended) mode."; inner "Morphs the inner HTML of the elements."; replace "Replaces the outer HTML of the elements." The difference (morphing keeps element state such as focus and input values, replacing does not) is what a reader needs to choose between `Outer` and `Replace`, and the page's wording makes them sound the same.

**Fix.** "`Outer` | Morph the target element into the new content *(default)*", "`Inner` | Morph the target's children", "`Replace` | Replace the target element without morphing".

### `reference/telemetry/common/index.md`: `AttributeKey` is not a case class, and a wrong key name or type is not a compile error

Found 2026-11-06 while fact-checking the telemetry Scaladoc (Task 1) against `docs/reference/telemetry/common/index.md`, on `main` at `121cb1024`. Same at tag `v0.0.56`.

**Summary.** The "Design" list says "`AttributeKey` is a small case class carrying the name and the value type, so a typo in a name or a mismatched type is a compile error rather than a silently separate series." `AttributeKey` is a sealed trait whose cases are private case classes (`telemetry/shared/src/main/scala/zio/blocks/telemetry/AttributeKey.scala:29-32`, `:84-114`). A typo in a key name compiles and reads a different attribute, and a key of the wrong type compiles and returns a wrongly typed value (see the Usability traps entry "`Attributes#get` ignores the key's type"; run output: `Some(503)` for a `String` key over a `Long` attribute, then `ClassCastException`).

**Suggested fix.** Replace the bullet with: "`AttributeKey` ties a name to a value type, so `Attributes#get` returns a typed `Option`. Define each key once as a `val` and reuse it: two keys with the same name read the same attribute whatever their type."

### `reference/telemetry/metrics/metric-data.md` and `metrics/index.md`: a histogram point's `bucketCounts` and `boundaries` are fresh copies, not the instrument's arrays

Found 2026-11-06 while fact-checking the telemetry Scaladoc (Task 2) against `docs/reference/telemetry/metrics/`, on `main` at `121cb1024`. Same at tag `v0.0.56`.

**Summary.** `metric-data.md:12` says "Each collection builds fresh points, though a histogram point exposes its `boundaries` and `bucketCounts` arrays as-is, so read them and don't write to them", and `index.md:99` says "a histogram point hands you its `boundaries` and `bucketCounts` arrays directly, so treat them as read-only". `Histogram.collect` copies both arrays into each point (`telemetry/shared/src/main/scala/zio/blocks/telemetry/Histogram.scala:87-88`, `state.bucketCounts.clone()` and `boundaries.clone()`), so writing to a point's arrays changes nothing in the histogram or in later points. Verified with this program (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 25, `scala-cli run --server=false HistogramArrays.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package shop

import zio.blocks.telemetry._

object HistogramArrays {
  def firstPoint(h: Histogram): HistogramDataPoint =
    h.collect() match {
      case MetricData.HistogramData(points) => points.head
      case other                            => throw new IllegalStateException(other.toString)
    }

  def main(args: Array[String]): Unit = {
    val latency = MeterProvider.builder.build().get("shop").histogramBuilder("http.latency").build()
    latency.record(42.0)

    val first = firstPoint(latency)
    first.bucketCounts(0) = 99L
    first.boundaries(0) = -1.0

    val second = firstPoint(latency)
    println(second.bucketCounts.toList.take(5))
    println(second.boundaries.toList.take(3))
    println(first.bucketCounts eq second.bucketCounts)
  }
}
// List(0, 0, 0, 0, 1)
// List(0.0, 5.0, 10.0)
// false
```

(The array that is shared is the one passed to `Histogram(name, description, unit, boundaries)`: the histogram keeps it without copying, so changing it afterwards changes the bucketing. That is a different array from the one in the data points.)

**Suggested fix.** In `metric-data.md:12`, replace the sentence with: "Each collection builds fresh points; a histogram point holds its own copies of the bucket counts and boundaries." In `index.md:99`, replace "— a fresh value each call, though a histogram point hands you its `boundaries` and `bucketCounts` arrays directly, so treat them as read-only." with "— a fresh value on each call, including fresh copies of a histogram's `boundaries` and `bucketCounts` arrays."

### `reference/telemetry/otel/index.md` and `custom-exporter.md`: `ExporterConfig#timeout` is never read, and the internal batch processor retries in place rather than re-queuing

Found 2026-11-06 while fact-checking the otel Scaladoc (Task 3) against `docs/reference/telemetry/otel/`, on `main` at `121cb1024`. Same at tag `v0.0.56`.

**Summary.** Four statements on the two otel reference pages do not match the code.

1. `index.md:39` says "`timeout` bounds a single send attempt". Nothing in the module reads `ExporterConfig#timeout`: the exporters take an `HttpSender` built separately and use only `endpoint`, `headers`, `maxQueueSize`, `maxBatchSize` and `flushIntervalMillis` (`git grep -n "config\." -- otel/src/main` lists `config.endpoint`, `config.maxQueueSize`, `config.maxBatchSize`, `config.flushIntervalMillis` and `config.headers` in `OtlpJsonExporter.scala:22`, never `config.timeout`). The timeout that bounds a send is the one given to `HttpSender.jdk(timeout)`.
2. `custom-exporter.md:202` says the internal `BatchProcessor` "re-queues a batch whose `ExportResult` was `Failure(retryable = true)`". It never re-queues: `exportWithRetry` retries the same batch in place up to 5 more times (6 attempts), sleeping 1 s, 2 s, 4 s, 8 s and 16 s on the exporting thread (`otel/src/main/scala/zio/blocks/telemetry/otel/BatchProcessor.scala:96-129`, the sleep at `:124`), then drops it with a line on standard error; it also stops retrying once `shutdown` has begun (`:112`, `:119`). An exception from the export counts as a retryable failure (`:97-101`). The `otel/test` run prints `[zio-blocks-telemetry] BatchProcessor export failed after 6 attempts: HTTP 429. Dropping 1 items.`
3. `index.md:129` says "`extract` returns `None` when the header is absent or malformed". Two known bugs make this untrue: `W3CTraceContextPropagator.extract` accepts some malformed `traceparent` values (extra characters after the flags, upper-case hex, a `+` sign) and `B3Propagator.single.extract` returns `Some` of an invalid context for a sampling-only `b3` header. The page could say "returns `None` for a missing header or one it cannot parse; check `SpanContext#isValid`, since `B3Propagator.single` returns an invalid context for a sampling-only header".
4. `custom-exporter.md:73` says "`trace.collectedSpans` returns everything recorded so far". It returns the last 1024 ended spans, oldest first (Task 2's probe R1), and after `trace.install` or `trace.removeAll()` it gets no new spans except from tracers taken with `trace.get` before (a known bug). A flush loop built on it loses spans beyond 1024 between flushes.

Minor: the encoding table at `custom-exporter.md:54` says control characters are escaped "including lone surrogates"; the encoder escapes every UTF-16 surrogate, paired or not (`OtlpJsonEncoder.scala:114`), so an emoji is written as an escaped pair. The output is valid JSON either way.

**Suggested fix.** In `index.md:39`, replace "and `timeout` bounds a single send attempt" with "and `timeout` is the value to pass to `HttpSender.jdk`, which bounds a single send attempt (nothing else reads it)". In `custom-exporter.md:202`, replace the bullet with "**Retry on retryable failures.** It retries a batch whose `ExportResult` was `Failure(retryable = true)` (or whose export threw) up to five more times, waiting 1, 2, 4, 8 and 16 seconds on the flushing thread, and drops it after that or at once when the failure is not retryable." In `index.md:129`, apply the wording in item 3. In `custom-exporter.md:73`, replace "returns everything recorded so far" with "returns the last 1024 ended spans". In `custom-exporter.md:54`, replace "including lone surrogates" with "and every UTF-16 surrogate".

### `reference/telemetry/logging/` and `tracing/index.md`: four statements about the global `log` and `trace` that the code does not keep

Found 2026-11-06 while fact-checking the telemetry Scaladoc against `docs/reference/telemetry/`, on `main` at `121cb1024`. Same at tag `v0.0.56`.

**Summary.** Four statements on the logging and tracing reference pages do not match the code.

1. `logging/logger.md:14` says of the name passed to `LoggerProvider#get`: "That name travels with every record". Records from the global `log.*` calls keep the instrumentation scope `zio.blocks.telemetry.log` after `log.install(provider.get("com.shop.payments", "2.1.0"))`; only records from the `Logger`'s own methods carry the name (run output of Task 1's Usability traps entry "`log.*` records ignore the name and version of the logger given to `log.install`": `through log -> zio.blocks.telemetry.log None`, `through the logger -> com.shop.payments Some(2.1.0)`). Cause: `telemetry/shared/src/main/scala/zio/blocks/telemetry/log.scala:23-24`.
2. `logging/index.md:150` says `withMinSeverity(severity) { … }` "lowers the floor for just that block and restores it afterward — no cleanup needed". The floor applies to every thread while the block runs, not only to the caller, and when the block ends it puts back the whole earlier state, so a processor, writer, logger or prefix override added inside the block stops working (Task 1's Library bugs entry "`log.withMinSeverity` undoes every logging change made inside its block"; restore at `log.scala:80`).
3. `logging/log-formatter.md:17` says the two built-in formatters are "both safe to share across threads, with `TextLogFormatter` caching its timestamp prefix for the current second". The cache is two separate `@volatile` fields written one after the other (`telemetry/shared/src/main/scala/zio/blocks/telemetry/LogFormatter.scala:49-50`, writes at `:71-72` and `:156-157`), so two threads formatting records from different seconds can print a line with the other record's date and time (entry 2 below, run output: 379 and 561 wrong lines of 400000 in two runs).
4. `tracing/index.md:120` says that "once you call `trace.install(...)`, they stop seeing new spans" (`collectedSpans` and `clearSpans`). Spans from tracers taken with `trace.get` before the install still land in the buffer, because those tracers keep the default provider and its in-memory processor (review probe: after `trace.install`, a span from a tracer taken before appeared in `trace.collectedSpans` as `List(early-span)`; `telemetry/shared/src/main/scala/zio/blocks/telemetry/trace.scala:53`, `install` replaces only the provider reference).

**Suggested fix.**

1. In `logger.md:14`, replace "That name travels with every record" with "That name travels with every record the logger's own methods emit; records from the global `log.*` calls keep the scope `zio.blocks.telemetry.log` even after `log.install` (a known bug in 0.0.56)".
2. In `index.md:150`, replace "lowers the floor for just that block and restores it afterward — no cleanup needed" with "lowers the floor, for every thread, while the block runs, then puts back the logging set-up from before the block. A known bug in 0.0.56: anything the block itself changes, such as a processor added with `log.addProcessor`, is undone too".
3. In `log-formatter.md:17`, replace "both safe to share across threads, with `TextLogFormatter` caching its timestamp prefix for the current second" with "both can be shared across threads; `TextLogFormatter` caches its timestamp prefix for the current second, and a known bug in 0.0.56 lets a line logged at a second boundary from another thread carry the wrong second".
4. In `tracing/index.md:120`, replace "they stop seeing new spans" with "they stop seeing new spans, except from tracers taken with `trace.get` before the install".

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

### `scaladoc-coverage.py`: a multi-line annotation hides the Scaladoc, and `--drift` pairs members of different types

**Where found.** Running the coverage and drift gates for the maybe/combinators/context rollout, 2026-11-05, base `c2e4a39fd`.

- **Multi-line annotation.** `trait ContextHas` (`context/shared/src/main/scala/zio/blocks/context/ContextHas.scala`) has a full Scaladoc above `@implicitNotFound(` / `"..."` / `)`, but `_doc_before` skips only single-line annotations (`_is_annotation_line`), so the trait counts as undocumented: context reports 36/37 with every member documented. The annotation string is longer than `maxColumn`, so scalafmt keeps it multi-line.
- **Drift keying.** `_keyed` keys members by `"<kind> <name>"` and occurrence index within the file, ignoring the enclosing type. In `Maybe.scala`, Scala 2 has one `def apply` (`Maybe.apply`) while Scala 3 has two (`Present.apply`, then `Maybe.apply`), so `--drift` compares Scala 2 `Maybe.apply` with Scala 3 `Present.apply` and reports `def apply` although both `Maybe.apply` copies have identical text. (The same keying paired `MaybeOps.map` with `WithFilter.map`; those docs were made identical, see the report.)

Suggested fix: in `_doc_before`, walk back over a balanced multi-line annotation (a line starting with `@name(` up to its closing `)`); in `scan`, record the enclosing type names and key drift by `(enclosing path, kind name, index)`, falling back to the current key when the paths differ only by a version-specific wrapper. Add both cases to `test_scaladoc_coverage.py`.

### jwt Scaladoc: the `Jwt` object example ends in a bare expression, so `scaladoc-check` warns "pure expression does nothing"

Found 2026-11-06 by the maybe/combinators/context session while running `scaladoc-check` on `main` `07f71de18` (after #1947). The `Jwt` object's "Minimal usage" block (`jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala`, the `{{{` block above `object Jwt`) ends with `decoded.sub // Some(user-42)`. In the generated snippet (`Snippet_jwt_shared_scala_Jwt_L335`) that line is a statement whose value is unused, so every Scala version warns ("A pure expression does nothing in statement position; you may be omitting necessary parentheses", twice on 2.13). The check still passes, but the warning shows up in every module's gate log. Suggested fix: `println(decoded.sub) // Some(user-42)`, or bind it (`val subject = decoded.sub // Some(user-42)`), matching how the other modules end their examples.

### schema-xml Scaladoc: the `syntax` examples give 18 unused-import warnings in `scaladoc-check`

Found 2026-11-06 by the maybe/combinators/context session while running `scaladoc-check` on `main` after #1948 (`04b56bc92`). The examples in `schema-xml/shared/src/main/scala-3/zio/blocks/schema/xml/syntax.scala` and its `scala-2.12-2.13` copy (snippets `Snippet_schema_xml_shared_scala_3_syntax_L31`, `_L53`, `_L64`, `_L92`, `_L107`, `_L122` and the matching `scala_2_12_2_13_syntax` ones) each import something they don't use, giving 18 unused-import warnings across the three Scala versions. The check still passes, but the warnings appear in every module's gate log and hide new ones. Suggested fix: drop the unused imports in those examples (the wrapper already imports `zio.blocks.schema.xml._`), keeping the two copies identical.

### schema Scaladoc: eight files still point readers to `docs/dev/issues.md`

Found 2026-11-06 by the schema core Scaladoc session on `main` `07f71de18`. The Scaladoc of `CompanionOptics`, `DerivedOptics`, `IntoVersionSpecific`, `PathInterpolator`, `SelectorMacros`, `MigrationCompanionVersionSpecific`, `DynamicSchema` and `Into` says "see docs/dev/issues.md". That file is now only an index (the entries live in `docs/dev/library-issues.md` and `docs-issues.md`), the website excludes `docs/dev/`, and the style guide forbids `docs/dev/` references in Scaladoc, so the pointer leads nowhere for API readers. Suggested fix: replace each with plain text ("a known bug in 0.0.56") or a GitHub issue link, keeping version copies identical (a comment-only follow-up PR).

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
- `jwt.md`: audience mismatch documented as `InvalidToken` (it is `InvalidClaim("aud", ...)`), missing `InvalidClaim`/`InvalidKey` rows, no key-format guidance, no frontmatter. Fixed in #1789 (H-37).
- `sql-transactions.md`: the Hikari recipe, "JDK 25+" virtual-thread claim and "pool size equals database max connections" advice were replaced by a link to H-17's guide (virtual threads are final in JDK 21, JEP 444; JEP 491 shipped in JDK 24; pool sizing must keep the sum of all pools under `max_connections`). Also fixed: `.query[Int].toOne` (no such method; `queryOne[Long]`) and the unsupported "read-only avoids WAL writes" claim. Fixed in #1790 (H-17).
- `codec.md` never mentioned `Deriver#withInstance`/`withModifier`, and its BinaryCodec/TextCodec paragraph was wrong (`CsvCodec` extends `TextCodec`; XML and YAML extend `BinaryCodec`; `BsonCodec` extends neither). New "Overriding a Type on a Deriver" subsection and fix in #1784 (merged).
- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. In PR #1773 (open).
- `patch.md`: every snippet was a plain uncompiled fence; five sections did not compile (`editString` with `Vector`, `modifyKey` with a forward-referenced implicit, `Patch[Data] ++ Patch[Counter]`, `val new`, a non-existent `JsonEncoder`), and the Strict, Clobber, `Patch#apply`, Prism-`set`, LazyList and string-delta claims contradicted the source. The snippets are now mdoc blocks and the claims are corrected; see the H-07 entry "docs: `patch.md` has six snippets that don't compile and several claims the source contradicts". Fixed in #1828.
- `into.md` said that the numeric widening instances "cannot lose information" (`Int` and `Long` to `Float`, and `Long` to `Double`, round large values) and that `Into.derived` matches coproduct cases by name only (it falls back to field signature and then to position; see the entry on `Into.derived` and case positions). Both sentences are corrected. Fixed on the wave 7 schema Scaladoc branch `scaladoc-wave-7`.
