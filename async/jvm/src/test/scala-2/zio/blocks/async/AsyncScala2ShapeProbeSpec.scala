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
 * ADVERSARIAL PROBE (Category 2 — Scala 2 macro shapes that SHOULD compile).
 * Each snippet is legitimate direct-style code that contains a `.await`
 * somewhere, plus an inner construct (a local def / lazy val / class) that does
 * NOT itself await. The macro only rejects those constructs when they CONTAIN
 * an await (see `AsyncMacros.precheck`), so non-awaiting ones must be supported.
 * We assert `isRight`; a `Left` (or crash) means the macro rejects/crashes on
 * legitimate code.
 *
 * Root cause: `AsyncMacros.transformBlock` only special-cases `ValDef`
 * statements; a `DefDef` / `ClassDef` / `ModuleDef` statement that does not
 * await falls through to `transform` -> `bind`, which wraps it as
 * `val tmp = <decl>` — invalid for a def/class/object declaration.
 *
 * Outcomes on current code (Scala 2.13):
 *   - local non-awaiting `def`   => FAILS (Left): spurious compile error
 *     [BUG-ASYNC-003]
 *   - local non-awaiting `class` => FAILS (crash): macro "assertion failed"
 *     compiler crash [BUG-ASYNC-004]
 *   - local non-awaiting `lazy val` => PASSES (convergence; ValDef path)
 *   - string interpolation w/ await => PASSES (convergence; apply spine)
 *
 * Parity: Scala 3 (dotty-cps-async) compiles and runs BOTH the def and class
 * shapes correctly, so these are Scala-2-only defects.
 */
object AsyncScala2ShapeProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2ShapeProbeSpec")(
    test("a local (non-awaiting) def alongside an await should compile") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          def double(n: Int) = n * 2
          val x = Async.succeed(5).await
          double(x)
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("a local (non-awaiting) lazy val alongside an await should compile") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          lazy val k = 21
          val x = Async.succeed(2).await
          k * x
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("a local (non-awaiting) class alongside an await should compile") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          class Box(val n: Int)
          val x = Async.succeed(3).await
          new Box(x).n
        }
        a
      """).map(r => assert(r)(isRight))
    },
    test("string interpolation embedding an awaited value should compile") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          s"value=${Async.succeed(7).await}"
        }
        a
      """).map(r => assert(r)(isRight))
    }
  )
}
