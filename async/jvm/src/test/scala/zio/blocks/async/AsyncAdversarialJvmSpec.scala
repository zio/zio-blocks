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
 * JVM-only adversarial probes (use `.block`, which cannot run on Scala.js).
 */
object AsyncAdversarialJvmSpec extends ZIOSpecDefault {

  /** A pollable that resolves on its first poll. */
  private final class Ready[A](value: A) extends Pollable[A] {
    def poll(waker: Waker): Async[A] = Async.succeed(value)
  }

  def spec = suite("AsyncAdversarialJvmSpec")(
    // ------------------------------------------------------------------
    // BUG-003 — a long chain of `.map` over a *suspended* Async builds a
    // left-nested tower of `FlatMapPollable`s whose `poll` recurses to a depth
    // equal to the chain length, overflowing the stack on `.block`. (A chain
    // over a ready raw value stays on the eager fast path and is fine; only the
    // suspended path is affected.)
    //
    // Oracle: stack-safety of sequential composition is not documented, so this
    // is Possible (and environment-sensitive — depends on the configured stack
    // size). A correct (trampolined) implementation returns the value.
    // ------------------------------------------------------------------
    suite("BUG-003: deep map chain over a suspended Async is not stack-safe")(
      test("blocking a 1,000,000-deep map chain returns the value rather than overflowing") {
        val n               = 1000000
        var a: Async[Int]   = new Ready(0)
        var i               = 0
        while (i < n) { a = a.map(_ + 1); i += 1 }
        val r =
          try a.block.toString
          catch { case e: Throwable => "ERROR:" + e.getClass.getSimpleName }
        assertTrue(r == n.toString)
      }
    )
  )
}
