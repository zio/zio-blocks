/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.async

import zio.ZIO
import zio.test._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext
import scala.scalajs.js

/**
 * Scala 2 / Scala.js direct-style `Async.async { ... .await ... }` semantics.
 *
 * The Scala 2 `internal.AsyncMacros` rewrite is platform-neutral (it emits a
 * `flatMap`/`map`/`catchAll` chain over the shared `Async` monad), so it runs on
 * JS unchanged. JavaScript cannot block, so — unlike the JVM `AsyncAwaitBlockSpec`
 * (which drives results with `.block`) — these assertions drive the resulting
 * `Async` through a `Future` and run as effectful ZIO tests.
 *
 * Crucially this includes GENUINELY PENDING awaits (a `Completer` completed from
 * a queued microtask, never inline): they prove the macro-generated chain
 * suspends and resumes via `AsyncInterop.toFuture`'s microtask polling loop
 * without ever blocking.
 *
 * The Scala 3 JS cells (DCA / native `js.await`) are covered by the sibling
 * `AsyncJsAwaitSpec` under `scala-3`; this file is the Scala 2 cell.
 */
object AsyncJsAwaitSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  private def run[A](fa: Async[A]): Future[A] =
    AsyncInterop.toFuture(fa)(JSExecutionContext.queue)

  /** An `Async[A]` that is genuinely pending: completed from a queued microtask. */
  private def pending[A](value: A): Async[A] =
    Async.promiseInternal[A] { c =>
      js.timers.setTimeout(0.0)(c.succeed(value))
      ()
    }

  /** A pending `Async` that fails from a queued microtask. */
  private def pendingFail[A](cause: Throwable): Async[A] =
    Async.promiseInternal[A] { c =>
      js.timers.setTimeout(0.0)(c.fail(cause))
      ()
    }

  def spec = suite("AsyncJsAwaitSpec (Scala 2)")(
    suite("ready awaits (synchronous fast path)")(
      test("`.await` of a succeeded Async returns the value") {
        ZIO.fromFuture(_ => run(Async.async(Async.succeed(7).await + 1))).map(r => assertTrue(r == 8))
      },
      test("sequential awaits compose in order") {
        val prog = Async.async {
          val a = Async.succeed(2).await
          val b = Async.succeed(3).await
          a * b
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("if/else with await in branches") {
        val prog = Async.async(if (true) Async.succeed(1).await else 0)
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 1))
      },
      test("while loop with await in body") {
        val prog = Async.async {
          var i   = 0
          var sum = 0
          while (i < 5) {
            sum += Async.succeed(i + 1).await
            i += 1
          }
          sum
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 15))
      },
      test("try/catch over a failed await recovers") {
        val prog = Async.async {
          try Async.fail(Boom).await
          catch { case _: Throwable => 42 }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42))
      },
      test("a failed await propagates as a failed Future") {
        val prog = Async.async(Async.fail(Boom).await)
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      },
      test("a body that throws propagates the throwable") {
        val prog = Async.async[Int]((throw Boom): Int)
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      }
    ),
    suite("pending awaits (non-blocking microtask resumption)")(
      test("a pending await suspends and resumes through the macro-generated chain") {
        var continued = false
        val prog      = Async.async {
          val x = pending(41).await
          continued = true
          x + 1
        }
        // Construction must not block / complete synchronously.
        val continuedBeforeRun = continued
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(!continuedBeforeRun, continued, r == 42))
      },
      test("sequential pending awaits resume in source order") {
        val prog = Async.async {
          val a = pending(2).await
          val b = pending(a + 3).await
          a * b
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
      },
      test("a pending failed await can be recovered after resumption") {
        val prog = Async.async {
          try pendingFail[Int](Boom).await
          catch { case t: Throwable if t eq Boom => 7 }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 7))
      },
      test("a pending failed await with no handler propagates as a failed Future") {
        val prog = Async.async(pendingFail[Int](Boom).await)
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      },
      test("a pending await mixed with synchronous work yields the combined result") {
        val prog = Async.async {
          val a = Async.succeed(10).await
          val b = pending(5).await
          a + b
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 15))
      }
    ),
    // EAGER `List.map` semantics: the closure applies to every element first
    // (building `List[Async[B]]`), then the awaits sequence left-to-right via
    // `Async.collectAll` (fail-fast). Identical to the Scala 3 JS cells.
    suite("List.map with .await in the closure")(
      test("maps over ready awaits") {
        val prog = Async.async(List(1, 2, 3).map(i => Async.succeed(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 20, 30)))
      },
      test("maps over genuinely-pending awaits, in order") {
        val prog = Async.async(List(1, 2, 3).map(i => pending(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 20, 30)))
      },
      test("the closure applies eagerly to all elements, then awaits sequence fail-fast") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).map { i =>
            seen = i :: seen
            if (i == 2) pendingFail[Int](Boom).await else pending(i).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(3, 2, 1)))
      }
    ),
    // LAZY/sequential `List.foreach` semantics: the closure for element n+1 runs
    // only after element n's await completes; a failure short-circuits the rest.
    suite("List.foreach with .await in the closure")(
      test("runs over genuinely-pending awaits in order") {
        var acc  = 0
        val prog = Async.async {
          List(1, 2, 3).foreach(i => acc += pending(i).await)
          acc
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("a failing await short-circuits the remaining elements (lazy)") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).foreach { i =>
            seen = i :: seen
            if (i == 2) pendingFail[Int](Boom).await else { val _ = pending(i).await; () }
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
      }
    ),
    // LAZY accumulating `List.flatMap`, and multi-generator for-comprehensions
    // (nested flatMap/map), with genuinely-pending awaits.
    suite("List.flatMap with .await in the closure")(
      test("accumulates genuinely-pending awaits in order") {
        val prog = Async.async {
          List(1, 2, 3).flatMap(i => List(pending(i * 10).await))
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 20, 30)))
      },
      test("multi-generator for-comprehension desugars to nested flatMap/map") {
        val prog = Async.async {
          for {
            i <- List(1, 2)
            j <- List(10, 20)
          } yield pending(i + j).await
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(11, 21, 12, 22)))
      }
    )
  )
}
