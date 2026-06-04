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

import java.util.concurrent.{CompletableFuture, Executors, TimeUnit}

/**
 * JVM, cross-version semantics for the direct-style [[Async.async]] block.
 * Every `.await` inside the block is rewritten into a non-blocking
 * `flatMap`/`map` chain — by dotty-cps-async on Scala 3, by
 * `internal.AsyncMacros` on Scala 2 — and the resulting `Async` is driven with
 * [[Async.block]] (the escape hatch) at the test boundary.
 *
 * This single spec compiling and passing identically on both Scala 2.13 and
 * Scala 3 JVM is what keeps the direct-style API honest across versions.
 *
 * JVM-only because it uses `.block` to extract results synchronously. The JS
 * direct-style path (DCA on Scala 3.3.7, native `js.async`/`js.await` on Scala
 * 3.8+) cannot block, so it is covered by the Future-based `AsyncJsAwaitSpec`.
 * The truly-pending / non-blocking-rewrite proof lives in `AsyncRewriteSpec`.
 */
object AsyncAwaitBlockSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  /** A genuinely-pending [[Async]] that completes with `x` ~5ms later. */
  private def pending(x: Int): Async[Int] = {
    val cf = new CompletableFuture[Int]()
    scheduler.schedule(new Runnable { def run(): Unit = { cf.complete(x); () } }, 5, TimeUnit.MILLISECONDS)
    AsyncInterop.fromCompletionStage(cf)
  }

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
    ),
    // These stress the element-type tracking that ascribes each generated
    // `flatMap` lambda parameter (Scala 2) — interleaving DISTINCT element
    // types so any mis-ordering would surface as a `ClassCastException` or a
    // compile error rather than a silently-correct result.
    suite("interleaved distinct-typed awaits")(
      test("nested await (inner before outer) with distinct types") {
        val r = Async.async {
          val len: Int = Async.succeed(Async.succeed("hello").await.length).await
          len * 2
        }.block
        assertTrue(r == 10)
      },
      test("awaits as function arguments evaluate left-to-right with distinct types") {
        def combine(s: String, n: Int): String = s * n
        val r                                  = Async.async {
          combine(Async.succeed("ab").await, Async.succeed(3).await)
        }.block
        assertTrue(r == "ababab")
      },
      test("await in if-condition and both branches, distinct branch types collapse") {
        val r = Async.async {
          val flag: Boolean = Async.succeed(true).await
          if (flag) Async.succeed("yes").await
          else Async.succeed(0).await.toString
        }.block
        assertTrue(r == "yes")
      },
      test("sequential awaits of different types feeding a later expression") {
        val r = Async.async {
          val s: String = Async.succeed("xyz").await
          val n: Int    = Async.succeed(s.length).await
          val d: Double = Async.succeed(n.toDouble + 0.5).await
          s"$s/$n/$d"
        }.block
        assertTrue(r == "xyz/3/3.5")
      },
      test("await inside a match scrutinee and arm with distinct types") {
        val r = Async.async {
          Async.succeed(2).await match {
            case 1 => Async.succeed("one").await
            case 2 => Async.succeed("two").await
            case _ => Async.succeed("many").await
          }
        }.block
        assertTrue(r == "two")
      }
    ),
    // Semantics that a naive ANF transform would silently break (oracle review).
    suite("short-circuit and binding semantics")(
      test("`&&` does not evaluate its right operand when the awaited left is false") {
        var rhsRan = false
        val r      = Async.async {
          Async.succeed(false).await && { rhsRan = true; true }
        }.block
        assertTrue(!r, !rhsRan)
      },
      test("`||` does not evaluate its right operand when the awaited left is true") {
        var rhsRan = false
        val r      = Async.async {
          Async.succeed(true).await || { rhsRan = true; false }
        }.block
        assertTrue(r, !rhsRan)
      },
      test("`&&` with an awaited right operand short-circuits without awaiting it") {
        var rhsRan = false
        val r      = Async.async {
          Async.succeed(false).await && Async.succeed { rhsRan = true; true }.await
        }.block
        assertTrue(!r, !rhsRan)
      },
      test("tuple destructuring of an awaited value binds both components") {
        val r = Async.async {
          val (a, b) = Async.succeed((3, 4)).await
          a + b
        }.block
        assertTrue(r == 7)
      }
    ),
    // `.await` inside a `List.map` closure has EAGER semantics on every backend
    // (dotty-cps-async + native `js.await` on Scala 3, `internal.AsyncMacros` on
    // Scala 2): strict `List.map` applies the closure to every element first,
    // producing `List[Async[B]]`, and the awaits are then sequenced
    // left-to-right via `Async.collectAll` (fail-fast on the first failure).
    // This matches how `Array.map(async ...)` composes in real JavaScript.
    suite("List.map with .await in the closure")(
      test("maps over ready awaits") {
        val r = Async.async {
          List(1, 2, 3).map(i => Async.succeed(i + 1).await).sum
        }.block
        assertTrue(r == 9)
      },
      test("maps over genuinely-pending awaits") {
        val r = Async.async {
          List(10, 20, 30).map(i => pending(i).await).sum
        }.block
        assertTrue(r == 60)
      },
      test("preserves element order and result type") {
        val r = Async.async {
          List(1, 2, 3).map(i => Async.succeed(i * 10).await)
        }.block
        assertTrue(r == List(10, 20, 30))
      },
      test("the closure applies eagerly to all elements, then awaits sequence fail-fast") {
        var seen = List.empty[Int]
        val a    = Async.async {
          List(1, 2, 3).map { i =>
            seen = i :: seen
            if (i == 2) Async.fail(Boom).await else Async.succeed(i).await
          }
        }
        val thrown = scala.util.Try(a.block).failed.toOption
        assertTrue(thrown.contains(Boom), seen == List(3, 2, 1))
      }
    )
    // NOTE: `val`-type-ascription preservation is a Scala-2-macro-specific
    // behavior and lives in `AsyncAwaitValAscriptionSpec` (scala-2 only). On
    // Scala 3, dotty-cps-async pushes the val's expected type INTO `.await`
    // (`val n: Long = intAsync.await` elaborates as `await[Long](intAsync)` and
    // does NOT compile), so the cases cannot be asserted identically here.
  )
}
