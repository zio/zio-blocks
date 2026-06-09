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
 * ADVERSARIAL PROBE (Category AA — Scala 2 macro shape gaps). Each snippet is
 * legitimate direct-style code in an application position the macro CLAIMS to
 * support generically (the ANF application-spine transform). We assert the
 * EXPECTED compile outcome. A SHOULD-COMPILE snippet that comes back `Left`
 * (or crashes) is a macro robustness defect; an UNSUPPORTED shape that comes
 * back with a non-specific diagnostic is a DX defect.
 */
object AsyncScala2GapProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2GapProbeSpec")(
    // --- varargs --------------------------------------------------------------
    test("await as varargs arguments should compile") {
      typeCheck("""
        import zio.blocks.async._
        def f(xs: Int*): Int = xs.sum
        val a = Async.async {
          f(Async.succeed(1).await, Async.succeed(2).await)
        }
        a
      """).map(r => assert(r)(isRight))
    },
    // --- named arguments (in declaration order) -------------------------------
    test("await as in-order named arguments should compile") {
      typeCheck("""
        import zio.blocks.async._
        def add(a: Int, b: Int): Int = a + b
        val r = Async.async {
          add(a = Async.succeed(1).await, b = Async.succeed(2).await)
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- named arguments (reordered: triggers synthetic-val desugaring) -------
    test("await as reordered named arguments should compile") {
      typeCheck("""
        import zio.blocks.async._
        def add(a: Int, b: Int): Int = a + b
        val r = Async.async {
          add(b = Async.succeed(2).await, a = Async.succeed(1).await)
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- default arguments ----------------------------------------------------
    test("await with an omitted default argument should compile") {
      typeCheck("""
        import zio.blocks.async._
        def f(a: Int, b: Int = 10): Int = a + b
        val r = Async.async {
          f(Async.succeed(5).await)
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- await in a `new` constructor argument --------------------------------
    test("await in a constructor argument should compile") {
      typeCheck("""
        import zio.blocks.async._
        class Box(val n: Int)
        val r = Async.async {
          new Box(Async.succeed(3).await).n
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- nested supported HOFs (map inside map) -------------------------------
    test("await inside a nested List.map closure should compile") {
      typeCheck("""
        import zio.blocks.async._
        val r = Async.async {
          List(1, 2).map(x => List(10, 20).map(y => Async.succeed(x + y).await))
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- f-interpolation ------------------------------------------------------
    test("await inside an f-interpolation should compile") {
      typeCheck("""
        import zio.blocks.async._
        val r = Async.async {
          f"${Async.succeed(7).await}%d"
        }
        r
      """).map(r => assert(r)(isRight))
    },
    // --- await in a curried by-name 2nd param list: must be REJECTED clearly --
    // The fix for by-name forcing must also catch by-name params in a LATER
    // (curried) parameter list, not just the first. If this comes back Right,
    // the awaited effect is forced eagerly (silent miscompile of laziness).
    test("await in a curried by-name 2nd-list argument is rejected with a by-name diagnostic") {
      typeCheck("""
        import zio.blocks.async._
        def foo(cond: Boolean)(b: => Int): Int = if (cond) b else 0
        val r = Async.async {
          foo(false)(Async.succeed(1).await)
        }
        r
      """).map {
        case Left(msg) => assertTrue(msg.toLowerCase.contains("by-name"))
        case Right(_)  => assertTrue(false)
      }
    }
  )
}
