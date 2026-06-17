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
import zio.test.Assertion._

/**
 * ADVERSARIAL SWEEP (Category 2 — Scala 2 macro). Diagnostic sweep of
 * legitimate direct-style shapes; each asserts the EXPECTED compile outcome so
 * the file documents convergence (passing) and pins down any defect (a
 * SHOULD-COMPILE snippet that comes back `Left`/crashes).
 */
object AsyncScala2ShapeSweepSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2ShapeSweepSpec")(
    test("nested Async.async + await compiles") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          Async.async { Async.succeed(1).await }.await
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("try/finally with awaited body compiles") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          try Async.succeed(1).await
          finally ()
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("try/finally with a Nothing-typed awaited body compiles and propagates the cause") {
      // A bare `Async.fail(t).await` (or any `Nothing`-typed expression) under
      // `try`/`finally` with no widening catch arm is legitimate straight-line
      // Scala and compiles on every Scala 3 cell; the finalizer
      // materialization must not require inference of a `Nothing` lambda
      // parameter. Probed by direct compilation (this very test) rather than
      // `typeCheck`: `c.typecheck` types macro-expanded trees more strictly
      // than the real compiler and rejects shapes that compile normally.
      var fin = false
      val a: Async[Int] = Async.async[Int] {
        try Async.fail(AsyncTestSupport.boom).await
        finally fin = true
      }
      val thrown = scala.util.Try(a.block).failed.toOption
      assertTrue(thrown.contains(AsyncTestSupport.boom), fin)
    },
    test("an unascribed Nothing-typed await in an if branch compiles and propagates at runtime") {
      // The branch is `Nothing`-typed with no user ascription; the element-type
      // tracking must not require one (direct compilation, like the try/finally
      // probe above).
      val a: Async[Int] = Async.async[Int] {
        if (Async.succeed(true).await) Async.fail(AsyncTestSupport.boom).await
        else 0
      }
      assertTrue(scala.util.Try(a.block).failed.toOption.contains(AsyncTestSupport.boom))
    },
    test("an unascribed Nothing-typed await in a match arm compiles and propagates at runtime") {
      val a: Async[Int] = Async.async[Int] {
        Async.succeed(1).await match {
          case 1 => Async.fail(AsyncTestSupport.boom).await
          case _ => 0
        }
      }
      assertTrue(scala.util.Try(a.block).failed.toOption.contains(AsyncTestSupport.boom))
    },
    test("await in the right operand of && short-circuits (right await is not run when left is false)") {
      // `&&`'s right operand is by-name; the macro must preserve short-circuit
      // evaluation, not hoist the right `.await` before the boolean test.
      var rightRan      = false
      val a: Async[Boolean] = Async.async {
        val left = Async.succeed(false).await
        left && { val v = Async.succeed(true).await; rightRan = true; v }
      }
      assertTrue(a.block == false, !rightRan)
    },
    test("await in the right operand of || short-circuits (right await is not run when left is true)") {
      var rightRan      = false
      val a: Async[Boolean] = Async.async {
        val left = Async.succeed(true).await
        left || { val v = Async.succeed(false).await; rightRan = true; v }
      }
      assertTrue(a.block == true, !rightRan)
    },
    test("await in a while condition with an awaiting body and var mutation evaluates left-to-right") {
      val a: Async[Int] = Async.async {
        var i   = 0
        var sum = 0
        while (Async.succeed(i < 4).await) {
          val v = Async.succeed(i * 10).await
          sum += v
          i += 1
        }
        sum
      }
      assertTrue(a.block == 60) // 0 + 10 + 20 + 30
    },
    test("await in a match scrutinee whose case bodies also await selects and runs the right arm") {
      val a: Async[String] = Async.async {
        Async.succeed(2).await match {
          case 1 => Async.succeed("one").await
          case 2 => Async.succeed("two").await
          case _ => Async.succeed("other").await
        }
      }
      assertTrue(a.block == "two")
    },
    test("await inside string interpolation evaluates each interpolated await") {
      val a: Async[String] = Async.async {
        s"v=${Async.succeed(7).await}-${Async.succeed(8).await}"
      }
      assertTrue(a.block == "v=7-8")
    },
    test("await of a method chain compiles") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          Async.succeed(1).map(_ + 1).await
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("await in while condition compiles") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          while (Async.succeed(false).await) { () }
          0
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("val pattern binding from an awaited Option compiles") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          val Some(x) = Async.succeed(Option(1)).await
          x
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("await as a curried-function argument compiles") {
      typeCheck("""
        import zio.blocks.async._
        def add(a: Int)(b: Int): Int = a + b
        val a = Async.async {
          add(Async.succeed(1).await)(Async.succeed(2).await)
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("two positional awaited arguments evaluate left-to-right") {
      val log                    = new scala.collection.mutable.ListBuffer[String]
      def f(a: Int, b: Int): Int = a + b
      val r = Async.async {
        f(
          { val v = Async.succeed(1).await; log += "a"; v },
          { val v = Async.succeed(2).await; log += "b"; v }
        )
      }
      assertTrue(r.block == 3, log.toList == List("a", "b"))
    },
    test("reordered named awaited arguments evaluate in textual order") {
      val log                    = new scala.collection.mutable.ListBuffer[String]
      def f(a: Int, b: Int): Int = a * 10 + b
      val r = Async.async {
        f(
          b = { val v = Async.succeed(2).await; log += "b"; v },
          a = { val v = Async.succeed(1).await; log += "a"; v }
        )
      }
      // Scala evaluates named args in textual order: b then a.
      assertTrue(r.block == 12, log.toList == List("b", "a"))
    },
    test("await in the RHS of an assignment to an outer var inside a nested block") {
      val r = Async.async {
        var x = 0
        locally {
          x = Async.succeed(41).await + 1
        }
        x
      }
      assertTrue(r.block == 42)
    },
    test("for-comprehension over a pattern-binding generator awaits each yield") {
      val r = Async.async {
        for {
          (x, y) <- List((1, 2), (3, 4))
        } yield Async.succeed(x + y).await
      }
      assertTrue(r.block == List(3, 7))
    }
  )
}
