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
 * Cross-platform, cross-version semantics for the ZIO-style idiom layer built
 * on top of the core combinators: `.as`, `.unit`, `.*>`, `.<*`, `flatten`,
 * `when`, `unless`. These are all expressed in terms of `map`, `flatMap`, and
 * `zipWith`, so the suite mirrors their happy-path AND their suspended-input
 * behaviour.
 */
object AsyncIdiomsSpec extends ZIOSpecDefault {

  private final class Ready[A](value: A) extends Pollable[A] {
    def poll(waker: Waker): Async[A] = Async.succeed(value)
  }

  def spec = suite("AsyncIdiomsSpec")(
    suite(".as")(
      test("replaces the value (ready)") {
        val r = Async.succeed(1).as("x").block
        assertTrue(r == "x")
      },
      test("replaces the value (suspended)") {
        val r = (new Ready(1): Async[Int]).as("x").block
        assertTrue(r == "x")
      },
      test("propagates failure") {
        val r      = Async.fail(new RuntimeException("boom")).as("x")
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.exists(_.getMessage == "boom"))
      }
    ),
    suite(".unit")(
      test("discards the value (ready)") {
        val r = Async.succeed("anything").unit.block
        assertTrue(r == (()))
      },
      test("discards the value (suspended)") {
        val r = (new Ready(42): Async[Int]).unit.block
        assertTrue(r == (()))
      }
    ),
    suite(".*>")(
      test("returns rhs (both ready)") {
        val r = (Async.succeed(1) *> Async.succeed(2)).block
        assertTrue(r == 2)
      },
      test("returns rhs (lhs suspended)") {
        val l: Async[Int] = new Ready(1)
        val r             = (l *> Async.succeed(2)).block
        assertTrue(r == 2)
      },
      test("returns rhs (rhs suspended)") {
        val rh: Async[Int] = new Ready(2)
        val r              = (Async.succeed(1) *> rh).block
        assertTrue(r == 2)
      },
      test("propagates lhs failure") {
        val boom            = new RuntimeException("boom")
        val lhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = lhs *> Async.succeed(2)
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("propagates rhs failure") {
        val boom            = new RuntimeException("boom")
        val rhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = Async.succeed(1) *> rhs
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      }
    ),
    suite(".<*")(
      test("returns lhs (both ready)") {
        val r = (Async.succeed(1) <* Async.succeed(2)).block
        assertTrue(r == 1)
      },
      test("returns lhs (lhs suspended)") {
        val l: Async[Int] = new Ready(1)
        val r             = (l <* Async.succeed(2)).block
        assertTrue(r == 1)
      },
      test("returns lhs (rhs suspended)") {
        val rh: Async[Int] = new Ready(2)
        val r              = (Async.succeed(1) <* rh).block
        assertTrue(r == 1)
      },
      test("propagates lhs failure") {
        val boom            = new RuntimeException("boom")
        val lhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = lhs <* Async.succeed(2)
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("propagates rhs failure") {
        val boom            = new RuntimeException("boom")
        val rhs: Async[Int] = Async.fail(boom)
        val r: Async[Int]   = Async.succeed(1) <* rhs
        val thrown          = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      }
    ),
    suite("flatten")(
      test("flattens an Async[Async[A]] (both ready)") {
        val nested: Async[Async[Int]] = Async.succeed(Async.succeed(7))
        val r                         = nested.flatten.block
        assertTrue(r == 7)
      },
      test("flattens when inner is a Pollable") {
        val inner: Async[Int]         = new Ready(11)
        val nested: Async[Async[Int]] = Async.succeed(inner)
        val r                         = nested.flatten.block
        assertTrue(r == 11)
      }
    ),
    suite("when / unless")(
      test("when(true) runs the effect") {
        var ran = false
        when(true)(Async.succeed { ran = true }).block
        assertTrue(ran)
      },
      test("when(false) does not run the effect") {
        var ran = false
        when(false)(Async.succeed { ran = true }).block
        assertTrue(!ran)
      },
      test("unless(true) does not run the effect") {
        var ran = false
        unless(true)(Async.succeed { ran = true }).block
        assertTrue(!ran)
      },
      test("unless(false) runs the effect") {
        var ran = false
        unless(false)(Async.succeed { ran = true }).block
        assertTrue(ran)
      }
    )
  )
}
