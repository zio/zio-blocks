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

### docs: `resource.md` says `Resource.from[T]` always closes an `AutoCloseable` and calls `Resource.shared` "lock-free" with an initializer that runs "exactly once"

**Fix in PR #1917** (open, CI green, 2026-10-30, session `scope-reference-fixes`); move to Resolved when it merges.

Found 2026-10-30 while writing the `scope` Scaladoc, on `main` at `ed2185ebe`. Same at `v0.0.56` (same line numbers). Verified on Scala 3.9.0 and 2.13.18 with `zio-blocks-scope` 0.0.56.

**Summary.** `docs/reference/resource-management/resource.md` has two errors:

1. Lines 451-453 say: "Derives a `Resource[T]` from `T`'s primary constructor, requiring no external dependencies. If `T` extends `AutoCloseable`, `Resource.from` automatically registers its `close()` method as a finalizer". Lines 474-478 repeat it: the macro "synthesizes a `Resource.shared` that instantiates `T` and — when `T` extends `AutoCloseable` — registers `close()` as a scope finalizer." That holds only when the constructor takes no `Finalizer` or `Scope` parameter. When it does (the pattern the docs teach for classes that clean up after themselves), `Resource.from[T]` never registers `close()`. This is the library bug "scope: `Resource.from[T]` does not close an `AutoCloseable` whose constructor takes a `Finalizer` or `Scope`". The macro has three branches. The `Finalizer`/`Scope` branch (`scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala:83-108`) only calls the constructor. The `AutoCloseable` branch (`:109-120`) is the only one that calls `scope.defer(instance.asInstanceOf[AutoCloseable].close())` (`:117`). Scala 2 has the same structure (`scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala:117-144`, then the `AutoCloseable` branch at `:145-153`). `Resource.from[T]()` (empty parentheses) does close it.
2. Line 376 says the mechanism is "**thread-safe and lock-free**, implemented using `AtomicReference` with atomic compare-and-swap operations to avoid contention", and line 378 says that "no matter how many threads try to allocate simultaneously, the underlying value initializes exactly once". (Line 63 repeats the first claim in the comparison table: "Lock-free atomic reference counting".) Neither is quite true:
   - It is not lock-free. While the first caller runs the initializer, the state is `Pending`, and every other caller spins on it (`scope/shared/src/main/scala/zio/blocks/scope/Resource.scala:217-218`, `PlatformScope.threadYield()`, which is `Thread.yield()` on the JVM, `scope/jvm/src/main/scala/zio/blocks/scope/PlatformScope.scala:23`). A slow or stuck initializer holds up every concurrent allocation, which is the property "lock-free" rules out. Line 378 itself says other threads "wait via spin-yield".
   - The value initializes exactly once only if the initializer succeeds. If it throws, the open scope is closed, the state goes back to `Uninitialized` and the exception propagates (`Resource.scala:209-213`), so the next allocation runs the initializer again.

**Reproducer 1** (`Repro.scala`, Scala 3.9.0, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// Repro.scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope.*

class Logger(implicit finalizer: Finalizer) extends AutoCloseable {
  def close(): Unit = println("Logger.close")
}

@main def repro(): Unit = {
  println("Resource.from[Logger]:")
  Resource.from[Logger].use(_ => ())
  println("Resource.from[Logger]():")
  Resource.from[Logger]().use(_ => ())
}
// Resource.from[Logger]:
// Resource.from[Logger]():
// Logger.close
```

The Scala 2.13.18 version (`//> using scala 2.13.18`, `import zio.blocks.scope._`, `object Repro extends App {` instead of the `@main` line) prints the same three lines.

**Reproducer 2** (`Shared.scala`, Scala 3.9.0, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// Shared.scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope.*

@main def shared(): Unit = {
  // 1. A failed initialisation runs again on the next allocation.
  var calls = 0
  val flaky = Resource.shared[String] { _ =>
    calls += 1
    if (calls == 1) throw new RuntimeException("first attempt fails")
    "ready"
  }
  println(scala.util.Try(flaky.use(identity)))
  println(s"${scala.util.Try(flaky.use(identity))}, initialiser calls = $calls")

  // 2. While one thread initialises, a second caller waits (spins) for it.
  val slow = Resource.shared[String] { _ => Thread.sleep(500); "slow" }
  val first = new Thread(() => { slow.use(_ => Thread.sleep(200)); () })
  first.start()
  Thread.sleep(50)
  val t0 = System.nanoTime()
  slow.use(_ => ())
  println("second caller waited about " + ((System.nanoTime() - t0) / 100000000) * 100 + " ms")
  first.join()
}
// Failure(java.lang.RuntimeException: first attempt fails)
// Success(ready), initialiser calls = 2
// second caller waited about 400 ms
```

**Expected (suggested wording).**

- Lines 451-453: "Derives a `Resource[T]` from `T`'s primary constructor, requiring no external dependencies. If `T` extends `AutoCloseable` and its constructor takes no `Finalizer` or `Scope` parameter, `Resource.from` registers its `close()` method as a finalizer. A known bug in 0.0.56: when the constructor does take a `Finalizer` or `Scope`, `close()` is not registered. Use `Resource.from[T]()` (with empty parentheses), which registers it, or register the cleanup inside the constructor with `finalizer.defer(...)`."
- Lines 474-478: "... and synthesizes a `Resource.shared` that instantiates `T`, passing the scope to any `Finalizer` or `Scope` parameter, and, when `T` extends `AutoCloseable` and takes no such parameter, registers `close()` as a scope finalizer. ..."
- Line 376: "This mechanism is thread-safe: the state is an `AtomicReference` updated with compare-and-swap, so concurrent allocations share one instance and one reference count. It is not lock-free: while the first caller runs the initializer, every other caller waits for it."
- Line 378, last sentence: "This state machine ensures that concurrent allocations share one value, and that cleanup happens once, when the last reference is released. If the initializer throws, the exception reaches the caller that ran it, the state returns to Uninitialized, and the next allocation runs the initializer again."
- Line 63 (table, Shared column): "Thread-safe atomic (CAS) reference counting".

**Impact.** (1) Readers who combine an implicit `Finalizer` with `AutoCloseable` and build the class with `Resource.from[T]` leak `close()` silently; the page tells them it is called. (2) Readers who put a slow or blocking initializer (a connection pool that dials out) in `Resource.shared` expect no contention and get every concurrent caller waiting; readers whose initializer has side effects (a counter, a registration) get them twice after a failure.

**Suggested fix.** Apply the wording above and run mdoc on the page. Remove the known-bug sentence when the library bug is fixed.

### docs: `wire.md` promises a fresh instance from every unique wire, and doesn't say `fromFunction` never closes an `AutoCloseable`

**Fix in PR #1917** (open, CI green, 2026-10-30, session `scope-reference-fixes`); move to Resolved when it merges.

Found 2026-10-30 while writing the `scope` Scaladoc, on `main` at `ed2185ebe`. Same at `v0.0.56` (there at lines 138, 185-212, 244-253 and 502-504). Verified on Scala 3.9.0 and 2.13.18 with `zio-blocks-scope` 0.0.56.

**Summary.** `docs/reference/resource-management/wire.md` has three errors:

1. **Unique wires inside `Resource.from`.** Line 145 says `Wire.unique[T]` is "Like `Wire.shared[T]`, but creates a fresh instance each time the wire is used." The table at lines 251-260 says, in the "Instance reuse" row, "Same instance across entire dependency graph" (Shared) and "New instance per allocation" (Unique), and lines 509-511 say "When `Resource.from` composes wires, it respects the sharing strategy: **Shared wires** → reference-counted (single instance in the graph); **Unique wires** → fresh per allocation". That holds for `wire.toResource(...)` (line 145's own example), but not when the wire is passed to `Resource.from`, which decides sharing from the wire's static type:
   - Scala 3 treats every explicit wire as shared, so a `Wire.unique[T]` builds `T` once and gives it to every dependent. This is the library bug "scope: on Scala 3, `Resource.from` treats an explicit `Wire.unique` as shared". `parseWireExpr` computes `isShared = wireTpe.baseType(TypeRepr.of[Wire.Shared[?, ?]].typeSymbol).typeSymbol != defn.NothingClass` (`scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala:173`), which is true for every wire type.
   - Scala 2 tests the static type's name (`scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala:213-215`). A `Wire.unique` is unique, as the page says, but a `Wire.shared` whose static type is `Wire[In, Out]` is treated as unique, so the Shared column's "Same instance across entire dependency graph" fails for it.
2. **`fromFunction` never closes the result.** The `Wire.Shared.fromFunction` section (lines 192-219) and the `Wire.Unique.fromFunction` section that follows it (lines 221-249, "Like `Wire.Shared.fromFunction`, but for unique wires") don't say that the wire registers no finalizer. Both only wrap the function (`scope/shared/src/main/scala-3/zio/blocks/scope/WireVersionSpecific.scala:93-94` and `:114-115`, `scope/shared/src/main/scala-2/zio/blocks/scope/WireVersionSpecific.scala:93` and `:114`, building the case classes at `scope/shared/src/main/scala/zio/blocks/scope/Wire.scala:144` and `:213`). The macro section at line 425 ("Does `T` extend `AutoCloseable`? — if yes, `close()` is automatically registered") is correct for `Wire.shared[T]`/`Wire.unique[T]`, so a reader concludes `fromFunction` behaves the same. This is the library bug "scope: `Wire.Shared.fromFunction` never closes an `AutoCloseable` result" (it holds for `Wire.Unique.fromFunction` too).
3. **`Context.empty[Any]` doesn't compile.** Lines 159, 187, 240 and 413 call `Context.empty[Any]`, but `Context.empty` is a `val` (`context/shared/src/main/scala/zio/blocks/context/Context.scala:214`, `val empty: Context[Any]`), so the four blocks fail with "value empty in object Context does not take type parameters". They are plain `scala` fences, so mdoc doesn't catch it.

**Reproducer 1** (`Repro.scala`, Scala 3.9.0, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// Repro.scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope.*

class RequestId { RequestId.created += 1 }
object RequestId { var created = 0 }
class Auth(val id: RequestId)
class Audit(val id: RequestId)
class Handler(val auth: Auth, val audit: Audit)

@main def repro(): Unit = {
  val sameInstance = Resource.from[Handler](Wire.unique[RequestId]).use(h => h.auth.id eq h.audit.id)
  println("Wire.unique[RequestId]: same instance = " + sameInstance + ", created = " + RequestId.created)
}
// Wire.unique[RequestId]: same instance = true, created = 1
```

The same file on Scala 2.13.18 (`//> using scala 2.13.18`, `import zio.blocks.scope._`, `object Repro extends App {` instead of the `@main` line) prints `Wire.unique[RequestId]: same instance = false, created = 2`.

**Reproducer 2** (`Static.scala`, Scala 2.13.18, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// Static.scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope._

class RequestId { RequestId.created += 1 }
object RequestId { var created = 0 }
class Auth(val id: RequestId)
class Audit(val id: RequestId)
class Handler(val auth: Auth, val audit: Audit)

object Static extends App {
  val shared: Wire[Any, RequestId] = Wire.shared[RequestId]
  val same = Resource.from[Handler](shared).use(h => h.auth.id eq h.audit.id)
  println("Wire.shared typed as Wire: isShared = " + shared.isShared + ", same instance = " + same + ", created = " + RequestId.created)
}
// Wire.shared typed as Wire: isShared = true, same instance = false, created = 2
```

**Reproducer 3** (`FromFn.scala`, Scala 3.9.0, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// FromFn.scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope.*
import zio.blocks.context.Context

final class Client extends AutoCloseable {
  def close(): Unit = println("Client.close")
}

@main def fromFn(): Unit = {
  println("Wire.Shared.fromFunction:")
  Wire.Shared.fromFunction[Any, Client]((_, _) => new Client).toResource(Context.empty).use(_ => ())
  println("Wire.Unique.fromFunction:")
  Wire.Unique.fromFunction[Any, Client]((_, _) => new Client).toResource(Context.empty).use(_ => ())
  println("Wire.shared[Client]:")
  Wire.shared[Client].toResource(Context.empty).use(_ => ())
  println("end")
}
// Wire.Shared.fromFunction:
// Wire.Unique.fromFunction:
// Wire.shared[Client]:
// Client.close
// end
```

The workaround in the suggested wording below works: a `Wire.Shared.fromFunction { (scope, _) => val client = new Client; scope.defer(client.close()); client }` wire, run the same way, prints `Client.close` then `end`.

**Reproducer 4** (`Unique.sc`: `wire.md` lines 148-170 verbatim under the two directives `//> using scala 3.9.0` and `//> using dep dev.zio::zio-blocks-scope:0.0.56`; `scala-cli run --server=false .`):

```
// -- Error: .../Unique.scala:20:26
// 20 |  val deps = Context.empty[Any]
//    |             ^^^^^^^^^^^^^^^^^^
//    |             value empty in object Context does not take type parameters
```

**Expected (suggested wording).**

- Line 145: "Like `Wire.shared[T]`, but its resource is unique: each allocation builds a fresh instance. Use for request-scoped or per-call services. A known bug in 0.0.56: on Scala 3, `Resource.from` treats a unique wire as shared and gives one instance to every dependent; on Scala 2 it follows the wire's static type, so keep it typed as `Wire.Unique` (or `Wire.Shared` for a shared wire), not `Wire`."
- Below the table at lines 251-260, a sentence: "The Instance reuse row describes `toResource`. Inside `Resource.from` in 0.0.56, Scala 3 shares every wire's instance across the graph (a known bug), and Scala 2 shares only wires whose static type is `Wire.Shared`."
- Lines 509-511: keep the two bullets and add: "A known bug in 0.0.56: on Scala 3, unique wires are also built once and shared across the graph. Scala 2 follows the wire's static type, so a shared wire stored as a plain `Wire[In, Out]` is built once per dependent."
- Line 194 (and the unique section at line 223): "Use this for custom construction logic when macro derivation doesn't fit. Unlike `Wire.shared[T]`, `fromFunction` registers no finalizer: if the function returns an `AutoCloseable`, register its cleanup with `scope.defer(client.close())` on the `Scope` the function receives." Ideally the example at lines 206-210 shows the `scope.defer` line.
- Lines 159, 187, 240, 413: `Context.empty` instead of `Context.empty[Any]`.

**Impact.** (1) Request-scoped state declared with `Wire.unique` is shared by every dependent on Scala 3, while the page tells the reader it is fresh; on Scala 2 a shared wire stored in a `Wire`-typed `val` is silently duplicated. Neither fails at compile time or at run time. (2) Readers who move from `Wire.shared[T]` to `Wire.Shared.fromFunction` for custom construction lose `close()` without any warning. (3) Readers who copy four of the page's examples get a compile error.

**Suggested fix.** Apply the wording above, and turn the plain fences that are meant to compile into `mdoc:compile-only` blocks so the next `Context` change is caught. Remove the known-bug sentences when the library bugs are fixed.

### docs: `scope.md` describes `Scope.global` closing and `isOwner` without the Scala.js behaviour

**Fix in PR #1917** (open, CI green, 2026-10-30, session `scope-reference-fixes`); move to Resolved when it merges.

Found 2026-10-30 while writing the `scope` Scaladoc, on `main` at `ed2185ebe`. Same at `v0.0.56` (same line numbers). Verified on Scala 3.9.0 with `zio-blocks-scope` 0.0.56, on the JVM and on Scala.js 1.22.0.

**Summary.** `docs/reference/resource-management/scope.md` describes JVM-only behaviour as universal in two places:

1. Line 546 (`Scope#isClosed`) says: "`Scope.global` returns `false` until JVM shutdown." On Scala.js `Scope.global` never closes: `PlatformScope.registerShutdownHook` is a no-op there (`scope/js/src/main/scala/zio/blocks/scope/PlatformScope.scala:20`; the JVM version adds a shutdown hook, `scope/jvm/src/main/scala/zio/blocks/scope/PlatformScope.scala:20-21`; `Scope.global` registers its finalizers through it at `scope/shared/src/main/scala/zio/blocks/scope/Scope.scala:308-314`). So `isClosed` is always `false`, and finalizers registered on `Scope.global` never run. The same page says the second half correctly at line 201 ("On Scala.js, global finalizers are not automatically invoked"), so line 546 contradicts it.
2. Lines 557-561 (`Scope#isOwner`) list the ownership rules: "`Scope.global`: always returns `true` (any thread may use it); Child scopes created via `scoped { }`: returns `true` only on the thread that entered the block; Child scopes created via `open()`: always returns `true` (unowned, usable cross-thread)". On Scala.js, `isOwner` is always `true` for every scope: `PlatformScope.isOwner` returns `true` (`scope/js/src/main/scala/zio/blocks/scope/PlatformScope.scala:26`; `Scope.Child#isOwner` delegates to it at `Scope.scala:394`). So the cross-thread misuse check described at line 563 ("Calling `scoped { }` on a scope you don't own throws `IllegalStateException`") never fires on Scala.js, not even from a later callback.

**Reproducer, Scala.js** (`ScopeJs.scala`; run with `scala-cli run --server=false . --js-cli-on-jvm`, because scala-cli's native Scala.js linker can't read the 0.0.56 IR):

```scala
// ScopeJs.scala
//> using scala 3.9.0
//> using platform scala-js
//> using jsVersion 1.22.0
//> using dep dev.zio::zio-blocks-scope::0.0.56
import zio.blocks.scope.*
import scala.scalajs.js.timers.setTimeout

@main def scopeJs(): Unit = {
  Scope.global.defer(println("global finalizer ran"))
  var captured: Scope = null
  Scope.global.scoped { scope =>
    captured = scope
    println("inside scoped: isOwner = " + scope.isOwner)
  }
  setTimeout(10) {
    println("timer callback: isOwner = " + captured.isOwner + ", Scope.global.isClosed = " + Scope.global.isClosed)
  }
  println("main done")
}
// inside scoped: isOwner = true
// main done
// timer callback: isOwner = true, Scope.global.isClosed = false
```

`global finalizer ran` is never printed.

**Reproducer, JVM, for contrast** (`ScopeJvm.scala`, Scala 3.9.0, `zio-blocks-scope` 0.0.56, `scala-cli run --server=false .`):

```scala
// ScopeJvm.scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-scope:0.0.56
import zio.blocks.scope.*

@main def scopeJvm(): Unit = {
  Scope.global.defer(println("global finalizer ran"))
  var captured: Scope = null
  Scope.global.scoped { scope =>
    captured = scope
    println("inside scoped: isOwner = " + scope.isOwner)
  }
  val t = new Thread(() =>
    println("other thread: isOwner = " + captured.isOwner + ", Scope.global.isClosed = " + Scope.global.isClosed)
  )
  t.start(); t.join()
  println("main done")
}
// inside scoped: isOwner = true
// other thread: isOwner = false, Scope.global.isClosed = false
// main done
// global finalizer ran
```

**Expected (suggested wording).**

- Line 546: "On the JVM, `Scope.global` returns `false` until JVM shutdown, when a shutdown hook closes it. On Scala.js it never closes, so it always returns `false` and its finalizers never run. Child scopes created with `scoped { }` are closed when the block exits, while those created with `open()` remain open until you call `close()`."
- Lines 557-561, after the list: "On Scala.js there is a single thread, and `isOwner` returns `true` for every scope, so the ownership check never fails there."

**Impact.** Scala.js users who rely on `Scope.global.defer` for cleanup, or on `isClosed` to detect shutdown, get neither. The ownership list suggests that misuse is detected on every platform; on Scala.js a scope captured into a callback that runs after the block passes `isOwner` and the `scoped` check.

**Suggested fix.** Apply the wording above and run mdoc on the page.

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

### `wire.md` has no mdoc-checked code blocks

Found 2026-10-30 by the scope reference fixes (#1917), on `main` `8d6216d7a`. From source; the blocks were not compiled.

**Summary.** Every code block on `docs/reference/resource-management/wire.md` was a plain `scala` fence, so mdoc never compiled any of them. That is how four `Context.empty[Any]` calls that don't compile (`Context.empty` is a `val`) reached the site. #1917 converts only the blocks it changes to `mdoc:compile-only`; about 17 plain fences remain unchecked (before #1917 at roughly lines 46-71, 77-85, 265-299, 309-397, 429-457 and 478-507).

**Impact.** Any API drift in those examples goes unnoticed, and readers copy code that may not compile.

**Suggested fix.** Convert the remaining blocks to `mdoc:compile-only` (or `mdoc:silent` where later blocks depend on them), run `docs/mdoc --include reference/resource-management/wire.md`, and fix whatever fails. Worth checking the other `resource-management` pages for plain fences at the same time.

## Resolved

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
