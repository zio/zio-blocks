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
 * Scala 3: `orElse` widens the two success types through `combinators.Concat`
 * to the native union `A | B` rather than their least upper bound. The explicit
 * `Async[Int | String]` ascriptions are part of the test — they only compile if
 * `orElse` actually yields the union type.
 */
object AsyncOrElseUnionSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom

  def spec = suite("AsyncOrElseUnionSpec")(
    test("disjoint success types widen to a union; left value passes through") {
      val a: Async[Int]          = Async.succeed(1)
      val b: Async[String]       = Async.succeed("x")
      val r: Async[Int | String] = a.orElse(b)
      assertTrue(r.block == 1)
    },
    test("on failure, falls back to the right value as the union type") {
      val a: Async[Int]          = Async.fail(boom)
      val b: Async[String]       = Async.succeed("x")
      val r: Async[Int | String] = a.orElse(b)
      assertTrue(r.block == "x")
    },
    test("same-type orElse stays that type (identity-like, no widening)") {
      val r: Async[Int] = (Async.fail(boom): Async[Int]).orElse(Async.succeed(2))
      assertTrue(r.block == 2)
    }
  )
}
