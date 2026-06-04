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

/**
 * Scala 3 / Scala.js direct-style `Async.async { ... .await ... }` semantics.
 *
 * JavaScript cannot block, so unlike the JVM `AsyncAwaitBlockSpec` (which
 * drives the result with `.block`), these assertions drive the resulting
 * `Async` through a `Future` and run as effectful ZIO tests. This single spec
 * covers both JS Scala 3 cells:
 *   - Scala 3.3.7 → dotty-cps-async `flatMap`-chain rewrite;
 *   - Scala 3.8+ → native `js.async` / `js.await`.
 *
 * The same source compiling and passing on both cells is what keeps the JS
 * direct-style API honest across the DCA and js-native implementations.
 */
object AsyncJsAwaitSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  private def run[A](fa: Async[A]): Future[A] =
    AsyncInterop.toFuture(fa)(JSExecutionContext.queue)

  def spec = suite("AsyncJsAwaitSpec")(
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
    },
    test("List.map with ready awaits in the closure") {
      val prog = Async.async(List(1, 2, 3).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 20, 30)))
    },
    test("List.map closure applies eagerly, then awaits sequence fail-fast") {
      // Strict `List.map` applies the closure to every element (so `seen`
      // observes 1,2,3), producing `List[Async[B]]`; the awaits are then
      // sequenced left-to-right and the i==2 failure short-circuits the result.
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).map { i =>
          seen = i :: seen
          if (i == 2) Async.fail(Boom).await else Async.succeed(i).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(3, 2, 1)))
    }
  )
}
