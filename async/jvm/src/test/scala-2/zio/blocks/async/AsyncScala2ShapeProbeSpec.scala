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
 * an await (see `AsyncMacros.precheck`), so non-awaiting ones must be
 * supported. We assert `isRight`; a `Left` (or crash) means the macro
 * rejects/crashes on legitimate code.
 *
 * Regression coverage: `AsyncMacros.transformBlock` must keep local member
 * definitions (`def` / `class` / `object` / `type`) and imports verbatim rather
 * than treating them as ANF-bindable values. Binding them as `val tmp = <decl>`
 * is invalid for `def` / `type` declarations and can crash the compiler for
 * local classes/objects.
 *
 * Parity: Scala 3 (dotty-cps-async) compiles and runs these shapes, so Scala 2
 * must continue accepting them too.
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
    test("a lazy val whose initializer is a nested Async.async block should compile") {
      // Scala 2 expands macros bottom-up: the inner block is already rewritten
      // (no `.await` tokens left) by the time the outer macro's lazy-val check
      // runs, so the rejection must not fire (parity with the Scala 3 cells).
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          lazy val inner = Async.async(Async.succeed(20).await + 1)
          inner.await * 2
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
    },
    // A block-local `var` whose initializer contains an `.await` is legitimate
    // direct-style code: both Scala 3 backends (dotty-cps-async on 3.3.x and the
    // 3.8+ native transform) compile and run `var a = succeed(1).await; ...`
    // correctly. The Scala 2 macro's `boxVars` pre-pass rewrites the `var` into
    // `IntRef.create(<rhs>)` BEFORE the CPS transform extracts the await from
    // `<rhs>`, producing a malformed tree that fails to retypecheck with
    // `class scala.runtime.IntRef is not a value`. Parity demands this compile.
    test("a var whose initializer awaits should compile (Scala 3 parity)") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          var x = Async.succeed(1).await
          x = x + 10
          x
        }
        a
      """).map(r => assert(r)(isRight))
    }
  )
}
