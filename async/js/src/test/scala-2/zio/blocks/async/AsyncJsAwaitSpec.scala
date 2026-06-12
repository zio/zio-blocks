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
 * `flatMap`/`map`/`catchAll` chain over the shared `Async` monad), so it runs
 * on JS unchanged. JavaScript cannot block, so — unlike the JVM
 * `AsyncAwaitBlockSpec` (which drives results with `.block`) — these assertions
 * drive the resulting `Async` through a `Future` and run as effectful ZIO
 * tests.
 *
 * Crucially this includes GENUINELY PENDING awaits (a `Completer` completed
 * from a queued microtask, never inline): they prove the macro-generated chain
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

  /**
   * An `Async[A]` that is genuinely pending: completed from a queued microtask.
   */
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
      test("`.await` of a succeeded pollable-as-value returns the pollable identity") {
        val inner: Pollable[Int] = new Pollable[Int] {
          def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
        }
        val prog = Async.async(Async.succeed(inner).await)
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.asInstanceOf[AnyRef] eq inner.asInstanceOf[AnyRef]))
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
      test("a throwing finalizer replaces the in-flight awaited failure (plain try/finally semantics)") {
        // Scala semantics: a throw from `finally` replaces the in-flight
        // exception.
        val finBoom            = new RuntimeException("fin")
        def finThrow(): Unit   = throw finBoom
        def failed: Async[Int] = Async.fail(Boom)
        val prog               = Async.async[Int] {
          try failed.await
          finally finThrow()
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(finBoom)))
      },
      test("a throwing finalizer replaces an in-flight null-cause awaited failure with the finalizer's throw") {
        // Same replacement law when the in-flight failure carries the logical
        // null cause: the block must fail with the finalizer's throw — never an
        // internal NullPointerException from combining the two.
        val finBoom                = new RuntimeException("fin")
        def finThrow(): Unit       = throw finBoom
        def failedNull: Async[Int] = Async.fail(null)
        val prog                   = Async.async[Int] {
          try failedNull.await
          finally finThrow()
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(finBoom)))
      },
      test("a throwing finalizer after a successful awaited body fails the block with the finalizer's throw") {
        // Success side of the replacement law: the body's value is discarded
        // and the finalizer's throw is the failure.
        val finBoom          = new RuntimeException("fin")
        def finThrow(): Unit = throw finBoom
        val prog             = Async.async[Int] {
          try Async.succeed(5).await
          finally finThrow()
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(finBoom)))
      },
      test("a throwing finalizer attaches the in-flight awaited failure as suppressed") {
        // Plain-Scala replacement semantics PLUS error-graph preservation: the
        // finalizer's throw wins and the original failure stays reachable via
        // getSuppressed (legal here: non-null, distinct) — same macro path as
        // the JVM 2.13 cell.
        val primary            = new RuntimeException("primary")
        val finBoom            = new RuntimeException("fin")
        def finThrow(): Unit   = throw finBoom
        def failed: Async[Int] = Async.fail(primary)
        val prog               = Async.async[Int] {
          try failed.await
          finally finThrow()
        }
        ZIO
          .fromFuture(_ => run(prog))
          .either
          .map(e => assertTrue(e == Left(finBoom), finBoom.getSuppressed.toList.contains(primary)))
      },
      test("a finalizer that awaits and then fails attaches the in-flight failure as suppressed") {
        // The awaiting finalizer routes through the async-finalizer machinery;
        // its suppression contract must match the synchronous arm.
        val primary              = new RuntimeException("primary")
        val finBoom              = new RuntimeException("fin")
        def failFin: Async[Unit] = Async.fail(finBoom)
        def failed: Async[Int]   = Async.fail(primary)
        val prog                 = Async.async[Int] {
          try failed.await
          finally {
            val _ = Async.succeed(1).await
            failFin.await
          }
        }
        ZIO
          .fromFuture(_ => run(prog))
          .either
          .map(e => assertTrue(e == Left(finBoom), finBoom.getSuppressed.toList.contains(primary)))
      },
      test("a finalizer that rethrows the in-flight cause itself propagates it without a self-suppression crash") {
        // When the finalizer's throw IS the in-flight cause the combiner must
        // surface the shared instance untouched (no self-suppression crash).
        val shared             = new RuntimeException("shared")
        def finThrow(): Unit   = throw shared
        def failed: Async[Int] = Async.fail(shared)
        val prog               = Async.async[Int] {
          try failed.await
          finally finThrow()
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(shared), shared.getSuppressed.isEmpty))
      },
      test("a finalizer that fails via an awaited Async replaces an in-flight null-cause failure") {
        // The finalizer's failure arrives through the async channel (an
        // awaited Async.fail), not a raw throw; the replacement law is the
        // same.
        val finBoom                = new RuntimeException("fin")
        def failFin: Async[Unit]   = Async.fail(finBoom)
        def failedNull: Async[Int] = Async.fail(null)
        val prog                   = Async.async[Int] {
          try failedNull.await
          finally failFin.await
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(finBoom)))
      },
      test("a finalizer that fails with a null cause replaces the in-flight failure with the logical null") {
        // The finalizer side can carry the logical null too: the block fails
        // with null (decoded), never an internal error from combining the two
        // failures.
        def failFinNull: Async[Unit] = Async.fail(null)
        def failed: Async[Int]       = Async.fail(Boom)
        val prog                     = Async.async[Int] {
          try failed.await
          finally failFinNull.await
        }
        ZIO
          .fromFuture(_ => run(prog))
          .either
          .map(e => assertTrue(AsyncTestSupport.unwindFutureEither(e) == Left(null)))
      },
      test("a Nothing-typed awaited body under try/finally propagates the cause and runs the finalizer once") {
        // `Async.fail(t).await` types as Nothing; with no widening catch arm
        // the try expression itself is Nothing-typed. The finalizer
        // materialization must not require inference of a Nothing lambda
        // parameter on the JS Scala 2 cell either.
        var fin                           = 0
        def failedNothing: Async[Nothing] = Async.fail(Boom)
        val prog                          = Async.async[Int] {
          try failedNothing.await
          finally fin += 1
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), fin == 1))
      },
      test("a Nothing-typed awaited body under try/catch/finally recovers and runs the finalizer once") {
        var fin                           = 0
        def failedNothing: Async[Nothing] = Async.fail(Boom)
        val prog                          = Async.async {
          try failedNothing.await
          catch { case t: Throwable if t eq Boom => 42 }
          finally fin += 1
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42, fin == 1))
      },
      test("nested try/finally over a Nothing-typed awaited body runs both finalizers once, inner first") {
        var trace                         = List.empty[String]
        def failedNothing: Async[Nothing] = Async.fail(Boom)
        val prog                          = Async.async[Int] {
          try {
            try failedNothing.await
            finally trace ::= "inner"
          } finally trace ::= "outer"
        }
        ZIO
          .fromFuture(_ => run(prog))
          .either
          .map(e => assertTrue(e == Left(Boom), trace == List("outer", "inner")))
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
      },
      test("for-comprehension guard (withFilter) is honored before the await") {
        val prog = Async.async {
          for {
            i <- List(1, 2, 3, 4)
            if i % 2 == 0
          } yield pending(i * 10).await
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(20, 40)))
      }
    ),
    // `Option` HOFs: at most one element, so the eager/lazy distinction collapses
    // to a single `Some`/`None` branch. Driven through genuinely-pending awaits.
    suite("Option HOFs with .await in the closure")(
      test("Option.map over a Some, pending await") {
        val prog = Async.async(Option(5).map(i => pending(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(50)))
      },
      test("Option.map over a None short-circuits") {
        val prog = Async.async((None: Option[Int]).map(i => pending(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
      },
      test("Option.map propagates a failing await") {
        val prog = Async.async(Option(5).map(_ => pendingFail[Int](Boom).await))
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      },
      test("Option.flatMap over a Some, pending await") {
        val prog = Async.async(Option(5).flatMap(i => Option(pending(i * 10).await)))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(50)))
      },
      test("Option.foreach over a Some, pending await") {
        val prog = Async.async {
          var acc = 0
          Option(5).foreach(i => acc += pending(i).await)
          acc
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 5))
      },
      test("multi-generator for-comprehension over Options") {
        val prog = Async.async {
          for {
            i <- Option(2)
            j <- Option(3)
          } yield pending(i + j).await
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(5)))
      },
      test("for-comprehension over Options short-circuits on a None generator") {
        val prog = Async.async {
          for {
            i <- Option(2)
            j <- (None: Option[Int])
          } yield pending(i + j).await
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
      }
    ),
    // `Vector`/`Set` HOFs: LAZY/sequential map/flatMap, result collection type
    // preserved (Set deduplicates the awaited values). Driven through pending
    // awaits.
    suite("Vector / Set HOFs with .await in the closure")(
      test("Vector.map preserves order, pending awaits") {
        val prog = Async.async(Vector(1, 2, 3).map(i => pending(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(10, 20, 30)))
      },
      test("Vector.map is lazy: a failing await short-circuits the rest") {
        var seen = List.empty[Int]
        val prog = Async.async {
          Vector(1, 2, 3).map { i =>
            seen = i :: seen
            if (i == 2) pendingFail[Int](Boom).await else pending(i).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
      },
      test("Vector.flatMap accumulates, preserving Vector") {
        val prog = Async.async(Vector(1, 2, 3).flatMap(i => Vector(pending(i).await, i * 10)))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 10, 2, 20, 3, 30)))
      },
      test("for-comprehension over Vectors") {
        val prog = Async.async {
          for {
            i <- Vector(1, 2)
            j <- Vector(10, 20)
          } yield pending(i + j).await
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(11, 21, 12, 22)))
      },
      test("Set.map deduplicates awaited results") {
        val prog = Async.async(Set(1, 2, 3, 4).map(i => pending(i % 2).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(0, 1)))
      },
      test("Set.flatMap accumulates into a Set") {
        val prog = Async.async(Set(1, 2).flatMap(i => Set(pending(i).await, i + 10)))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(1, 11, 2, 12)))
      },
      test("Map.map over a pair-returning closure awaits values, preserving Map") {
        val prog = Async.async {
          Map(1 -> 1, 2 -> 2, 3 -> 3).map { case (k, v) => (k, pending(v * 10).await) }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Map(1 -> 10, 2 -> 20, 3 -> 30)))
      },
      test("Map.flatMap accumulates into a Map") {
        val prog = Async.async {
          Map(1 -> 1, 2 -> 2).flatMap { case (k, v) => Map(k -> pending(v).await, (k + 10) -> v) }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Map(1 -> 1, 11 -> 1, 2 -> 2, 12 -> 2)))
      },
      test("Map.map propagates a failing await") {
        val prog = Async.async(Map(1 -> 1).map { case (k, v) => (k, Async.fail(Boom).await) })
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      },
      test("Map.map over a non-pair closure widens to an Iterable") {
        val prog = Async.async {
          Map(1 -> 10, 2 -> 20, 3 -> 30).map { case (k, v) => pending(k + v).await }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toSet == Set(11, 22, 33)))
      },
      test("List.exists short-circuits on the first match") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3, 4).exists { i => seen = i :: seen; Async.succeed(i == 2).await }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r, seen == List(2, 1)))
      },
      test("List.forall short-circuits on the first false") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3, 4).forall { i => seen = i :: seen; Async.succeed(i < 3).await }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(!r, seen == List(3, 2, 1)))
      },
      test("List.find returns the first match") {
        val prog = Async.async(List(1, 2, 3, 4).find(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(2)))
      },
      test("exists propagates a failing await") {
        val prog = Async.async(List(1, 2, 3).exists(i => Async.fail(Boom).await))
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
      },
      test("Vector.exists over a generic receiver") {
        val prog = Async.async(Vector(1, 2, 3).exists(i => Async.succeed(i == 3).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
      },
      test("Option.exists over a Some") {
        val prog = Async.async(Option(4).exists(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
      },
      test("Map.find over entries returns the matching pair") {
        val prog = Async.async(Map(1 -> 10, 2 -> 20).find { case (_, v) => Async.succeed(v == 20).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some((2, 20))))
      },
      test("Option.find returns the value when the predicate matches") {
        val prog = Async.async(Option(4).find(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(4)))
      },
      test("Option.find returns None when the predicate fails") {
        val prog = Async.async(Option(3).find(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
      }
    ),
    suite("foldLeft with .await in the op closure")(
      test("threads the accumulator over ready awaits") {
        val prog = Async.async(List(1, 2, 3, 4).foldLeft(0)((acc, x) => acc + Async.succeed(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
      },
      test("threads the accumulator over genuinely-pending awaits") {
        val prog = Async.async(List(10, 20, 30).foldLeft(0)((acc, x) => acc + pending(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 60))
      },
      test("supports a result type that differs from the element type") {
        val prog = Async.async(List(1, 2, 3).foldLeft(List.empty[Int])((acc, x) => pending(x * 10).await :: acc))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(30, 20, 10)))
      },
      test("a failing await short-circuits the remaining elements (lazy)") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).foldLeft(0) { (acc, x) =>
            seen = x :: seen
            if (x == 2) acc + (Async.fail(Boom).await: Int) else acc + Async.succeed(x).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
      },
      test("folds over a Vector receiver") {
        val prog = Async.async(Vector(1, 2, 3).foldLeft(0)((acc, x) => acc + Async.succeed(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      }
    ),
    suite("filter / filterNot with .await in the predicate")(
      test("List.filter keeps matching elements") {
        val prog = Async.async(List(1, 2, 3, 4).filter(i => pending(i % 2).await == 0))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(2, 4)))
      },
      test("List.filterNot keeps non-matching elements") {
        val prog = Async.async(List(1, 2, 3, 4).filterNot(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 3)))
      },
      test("Vector.filter preserves the Vector type") {
        val prog = Async.async(Vector(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(2, 4)))
      },
      test("Set.filter preserves the Set type") {
        val prog = Async.async(Set(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(2, 4)))
      },
      test("Map.filter over entries preserves the Map") {
        val prog = Async.async(Map(1 -> 10, 2 -> 20, 3 -> 30).filter { case (_, v) => Async.succeed(v > 10).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Map(2 -> 20, 3 -> 30)))
      },
      test("Option.filter over a Some that matches keeps it") {
        val prog = Async.async(Option(4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(4)))
      },
      test("Option.filter over a Some that fails the predicate yields None") {
        val prog = Async.async(Option(3).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
      }
    ),
    suite("takeWhile / dropWhile with .await in the predicate")(
      test("List.takeWhile keeps the leading run, stopping at the first failure") {
        val prog = Async.async(List(1, 2, 3, 4, 1).takeWhile(i => pending(if (i < 3) 0 else 1).await == 0))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 2)))
      },
      test("List.dropWhile drops the leading run, keeping the rest unconditionally") {
        val prog = Async.async(List(1, 2, 3, 4, 1).dropWhile(i => Async.succeed(i < 3).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(3, 4, 1)))
      },
      test("takeWhile is lazy: a failing await short-circuits the remaining elements") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).takeWhile { i =>
            seen = i :: seen
            if (i == 2) (Async.fail(Boom).await: Boolean) else Async.succeed(i < 3).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
      },
      test("Vector.takeWhile preserves the Vector type") {
        val prog = Async.async(Vector(1, 2, 3, 4).takeWhile(i => Async.succeed(i < 3).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 2)))
      },
      test("Vector.dropWhile preserves the Vector type") {
        val prog = Async.async(Vector(1, 2, 3, 4).dropWhile(i => Async.succeed(i < 3).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(3, 4)))
      }
    ),
    suite("reduce / reduceLeft with .await in the op closure")(
      test("List.reduce folds left-to-right over ready awaits") {
        val prog = Async.async(List(1, 2, 3, 4).reduce((acc, x) => acc + Async.succeed(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
      },
      test("List.reduce over genuinely-pending awaits") {
        val prog = Async.async(List(1, 2, 3, 4).reduce((acc, x) => acc + pending(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
      },
      test("List.reduceLeft behaves identically to reduce") {
        val prog = Async.async(List(2, 3, 4).reduceLeft((acc, x) => acc * Async.succeed(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 24))
      },
      test("reduce over an empty receiver fails with UnsupportedOperationException") {
        val prog = Async.async(List.empty[Int].reduce((acc, x) => acc + Async.succeed(x).await))
        ZIO
          .fromFuture(_ => run(prog))
          .either
          .map(e => assertTrue(e.left.exists(_.isInstanceOf[UnsupportedOperationException])))
      },
      test("reduce: a failing await short-circuits the remaining elements") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3, 4).reduce { (acc, x) =>
            seen = x :: seen
            if (x == 3) (Async.fail(Boom).await: Int) else acc + Async.succeed(x).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(3, 2)))
      }
    ),
    suite("foldRight with .await in the op closure")(
      test("List.foldRight is right-associative over ready awaits") {
        val prog = Async.async(List(1, 2, 3).foldRight("z")((x, acc) => s"($x+${Async.succeed(acc).await})"))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == "(1+(2+(3+z)))"))
      },
      test("List.foldRight over genuinely-pending awaits") {
        val prog = Async.async(List(1, 2, 3).foldRight(0)((x, acc) => x + pending(acc).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("foldRight runs the op right-to-left") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).foldRight(0) { (x, acc) =>
            seen = x :: seen
            x + Async.succeed(acc).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6, seen == List(1, 2, 3)))
      },
      test("foldRight supports a result type that differs from the element type") {
        val prog = Async.async(List(1, 2, 3).foldRight(List.empty[Int])((x, acc) => Async.succeed(x).await :: acc))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 2, 3)))
      },
      test("Vector.foldRight folds over a Vector receiver") {
        val prog = Async.async(Vector(1, 2, 3).foldRight(0)((x, acc) => x + Async.succeed(acc).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("foldRight: a failing await short-circuits the remaining elements (right-to-left)") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3, 4).foldRight(0) { (x, acc) =>
            seen = x :: seen
            if (x == 2) (Async.fail(Boom).await: Int) else x + Async.succeed(acc).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 3, 4)))
      }
    ),
    suite("collect with .await in a case body")(
      test("List.collect keeps matching elements, mapping them through an awaited body") {
        val prog = Async.async(List(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i * 10).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 30)))
      },
      test("List.collect over genuinely-pending awaits") {
        val prog = Async.async(List(1, 2, 3, 4).collect { case i if i % 2 == 0 => pending(i * 100).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(200, 400)))
      },
      test("collect tries multiple cases in order") {
        val prog = Async.async {
          List(1, 2, 3, 4, 5).collect {
            case i if i % 2 == 0 => Async.succeed(s"even$i").await
            case i if i == 5     => Async.succeed("five").await
          }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List("even2", "even4", "five")))
      },
      test("Vector.collect preserves the Vector type") {
        val prog = Async.async(Vector(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 3)))
      },
      test("Set.collect preserves the Set type") {
        val prog = Async.async(Set(1, 2, 3, 4).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(20, 40)))
      },
      test("collect: a failing await in a matched body short-circuits the rest") {
        var seen = List.empty[Int]
        val prog = Async.async {
          List(1, 2, 3).collect {
            case i =>
              seen = i :: seen
              if (i == 2) (Async.fail(Boom).await: Int) else Async.succeed(i).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
      },
      test("Option.collect keeps a matching Some") {
        val prog = Async.async(Option(2).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(20)))
      },
      test("Option.collect returns None when the Some does not match") {
        val prog = Async.async(Option(3).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
      },
      test("Map.collect with a non-pair body widens to an Iterable") {
        val prog = Async.async(Map(1 -> 10, 2 -> 20).collect { case (k, v) => Async.succeed(k + v).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toSet == Set(11, 22)))
      }
    ),
    // Strict immutable `Seq` receivers (`Queue` / `ArraySeq`) reuse the generic
    // `iterable` HOF rewrites, so `.await` works in their closures on JS too,
    // with the collection family preserved.
    suite("immutable Queue / ArraySeq HOF closures with .await")(
      test("Queue.map preserves the Queue type") {
        val prog = Async.async(scala.collection.immutable.Queue(1, 2, 3).map(i => Async.succeed(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(10, 20, 30)))
      },
      test("Queue.filter preserves the Queue type") {
        val prog = Async.async(scala.collection.immutable.Queue(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(2, 4)))
      },
      test("Queue.foldLeft folds over a Queue receiver with a pending await") {
        val prog =
          Async.async(scala.collection.immutable.Queue(1, 2, 3).foldLeft(0)((a, x) => a + pending(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("Queue.collect preserves the Queue type") {
        val prog = Async.async {
          scala.collection.immutable.Queue(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(1, 3)))
      },
      test("ArraySeq.map preserves the ArraySeq type") {
        val prog = Async.async(scala.collection.immutable.ArraySeq(1, 2, 3).map(i => Async.succeed(i * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(10, 20, 30)))
      },
      test("ArraySeq.filter preserves the ArraySeq type") {
        val prog =
          Async.async(scala.collection.immutable.ArraySeq(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(2, 4)))
      },
      test("ArraySeq.foldLeft folds over an ArraySeq receiver") {
        val prog =
          Async.async(scala.collection.immutable.ArraySeq(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("ArraySeq.collect preserves the ArraySeq type") {
        val prog = Async.async {
          scala.collection.immutable.ArraySeq(1, 2, 3, 4).collect {
            case i if i % 2 == 1 => Async.succeed(i * 10).await
          }
        }
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(10, 30)))
      }
    ),
    // `Array`: `map` eager, `flatMap` lazy / sequential, result always `Array[B]`
    // (via `ArrayOps` + an implicit `ClassTag[B]`). Same macro rewrite as the
    // JVM, exercised here over `Future` (including a genuinely-pending await).
    suite("Array HOF closures with .await")(
      test("Array.map preserves the (primitive) element type") {
        val prog = Async.async(Array(1, 2, 3).map(i => Async.succeed(i.toLong * 10).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(10L, 20L, 30L)))
      },
      test("Array.flatMap concatenates and preserves the Array type") {
        val prog = Async.async(Array(1, 2, 3).flatMap(i => Array(Async.succeed(i).await, i * 10)))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 10, 2, 20, 3, 30)))
      },
      test("Array.foldLeft over a genuinely-pending await") {
        val prog = Async.async(Array(1, 2, 3).foldLeft(0)((a, x) => a + pending(x).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
      },
      test("Array.filter preserves the Array type") {
        val prog = Async.async(Array(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(2, 4)))
      },
      test("Array.takeWhile preserves the Array type") {
        val prog = Async.async(Array(1, 2, 3, 1).takeWhile(i => Async.succeed(i < 3).await))
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 2)))
      },
      test("Array.collect preserves the Array type") {
        val prog = Async.async(Array(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await })
        ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 3)))
      }
    )
  )
}
