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
 * Scala 2: `orElse` widens the two success types through `combinators.Concat`.
 * Disjoint types (no shared meaningful supertype) widen to `Either[A, B]` (the
 * Scala 3 sibling produces the native union `A | B` instead). The explicit
 * `Async[Either[Int, String]]` ascription only compiles if `orElse` yields that
 * type, and the `Left`/`Right` projection is exercised by the disjoint path.
 */
object AsyncOrElseEitherSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom

  def spec = suite("AsyncOrElseEitherSpec")(
    test("disjoint success types widen to Either; left value becomes Left") {
      val a: Async[Int]                 = Async.succeed(1)
      val b: Async[String]              = Async.succeed("x")
      val r: Async[Either[Int, String]] = a.orElse(b)
      assertTrue(r.block == Left(1))
    },
    test("on failure, falls back to the right value as Right") {
      val a: Async[Int]                 = Async.fail(boom)
      val b: Async[String]              = Async.succeed("x")
      val r: Async[Either[Int, String]] = a.orElse(b)
      assertTrue(r.block == Right("x"))
    },
    test("same-type orElse stays that type (identity-like, no Either)") {
      val r: Async[Int] = (Async.fail(boom): Async[Int]).orElse(Async.succeed(2))
      assertTrue(r.block == 2)
    }
  )
}
