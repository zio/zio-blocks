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

import zio.test._

/**
 * JVM Scala 3 semantics for the direct-style [[Async.async]] block. Every
 * `.await` inside the block is rewritten by dotty-cps-async into a non-blocking
 * `flatMap`/`map` chain; the resulting `Async` is driven with [[Async.block]]
 * (the escape hatch) at the test boundary.
 *
 * JVM-only because it uses `.block` to extract results synchronously. The JS
 * direct-style path (DCA on Scala 3.3.7, native `js.async`/`js.await` on Scala
 * 3.8+) cannot block, so it is covered by the Future-based `AsyncJsAwaitSpec`.
 * The truly-pending / non-blocking-rewrite proof lives in `AsyncRewriteSpec`.
 *
 * Scala 3 only: the Scala 2 macro arrives in a later phase, so the shared
 * cross-version specs (`AsyncSpec`, `AsyncErrorSpec`, …) never use `.await`.
 */
object AsyncAwaitBlockSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  def spec = suite("AsyncAwaitBlockSpec")(
    suite("plain .await positions")(
      test("`.await` of a succeeded Async returns the value") {
        val r = Async.async {
          val x = Async.succeed(7).await
          x + 1
        }.block
        assertTrue(r == 8)
      },
      test("`.await` of a failed Async rethrows the cause, captured as Async.fail") {
        val a = Async.async {
          Async.fail(Boom).await
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      },
      test("a body that throws becomes Async.fail") {
        val a      = Async.async[Int]((throw Boom): Int)
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    ),
    suite("sequential .awaits in source order")(
      test("two awaits compose in order") {
        val r = Async.async {
          val a = Async.succeed(2).await
          val b = Async.succeed(3).await
          a * b
        }.block
        assertTrue(r == 6)
      },
      test("a failing await aborts the rest of the body") {
        var laterRan = false
        val a        = Async.async[Int] {
          val _ = Async.fail(Boom).await
          laterRan = true
          99
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom), !laterRan)
      }
    ),
    suite("if / else with .await in branches")(
      test("then-branch await") {
        val cond = true
        val r    = Async.async {
          if (cond) Async.succeed(1).await
          else 0
        }.block
        assertTrue(r == 1)
      },
      test("else-branch await") {
        val cond = false
        val r    = Async.async {
          if (cond) 0
          else Async.succeed(2).await
        }.block
        assertTrue(r == 2)
      }
    ),
    suite("while loop with .await in body")(
      test("counts up via repeated awaits") {
        var sum = 0
        val r   = Async.async {
          var i = 0
          while (i < 5) {
            sum += Async.succeed(i + 1).await
            i += 1
          }
          sum
        }.block
        assertTrue(r == 15, sum == 15)
      }
    ),
    suite("try / catch / finally with .await")(
      test("catches a Throwable from a failed await") {
        val r = Async.async {
          try Async.fail(Boom).await
          catch { case _: Throwable => 42 }
        }.block
        assertTrue(r == 42)
      },
      test("finally block runs even when an await fails") {
        var ran = false
        val r   = Async.async {
          try Async.fail(Boom).await
          catch { case _: Throwable => 0 }
          finally ran = true
        }.block
        assertTrue(r == 0, ran)
      }
    ),
    suite("match with .await")(
      test("match on a value, await inside an arm") {
        val k = 1
        val r = Async.async {
          k match {
            case 0 => 100
            case 1 => Async.succeed(50).await
            case _ => -1
          }
        }.block
        assertTrue(r == 50)
      },
      test("match on the result of an awaited Async") {
        val r = Async.async {
          Async.succeed(2).await match {
            case 1 => "one"
            case 2 => "two"
            case _ => "other"
          }
        }.block
        assertTrue(r == "two")
      }
    ),
    suite("throw inside async block")(
      test("a raw throw becomes Async.fail") {
        val a = Async.async[Int] {
          if (Async.succeed(true).await) throw Boom else 0
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom))
      }
    )
  )
}
