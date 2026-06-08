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
    }
  )
}
