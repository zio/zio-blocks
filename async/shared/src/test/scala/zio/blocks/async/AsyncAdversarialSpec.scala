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

import scala.util.Try

/**
 * Adversarial-derived regression probes for the cross-platform `Async` surface,
 * locking in behavior that hostile inputs revealed.
 */
object AsyncAdversarialSpec extends ZIOSpecDefault {

  def spec = suite("AsyncAdversarialSpec")(
    // ------------------------------------------------------------------
    // `ensuring` documents that a finalizer failure is "suppressed (the
    // original outcome wins)". Per the Java/Scala convention for "suppressed",
    // the finalizer's cause must remain reachable via `Throwable.addSuppressed`
    // on the primary failure rather than being dropped silently.
    // ------------------------------------------------------------------
    suite("ensuring attaches a failing finalizer as a suppressed exception")(
      test("a failing finalizer is attached to the primary failure as suppressed") {
        val primary = new RuntimeException("primary")
        val fin     = new RuntimeException("finalizer")
        val a       = Async.fail(primary).ensuring(Async.fail(fin))
        val thrown  = Try(a.block).failed.toOption
        assertTrue(
          thrown.contains(primary),
          thrown.exists(_.getSuppressed.toList.contains(fin))
        )
      }
    ),
    // ------------------------------------------------------------------
    // Convergence probes — currently-correct behavior locked in as regression
    // coverage.
    // ------------------------------------------------------------------
    suite("CONVERGENCE: collectAll fail-fast")(
      test("collectAll does not drive a Pollable input that follows a failure") {
        val boom             = new RuntimeException("boom")
        var polledAfter      = false
        val tail: Async[Int] = new Pollable[Int] {
          def poll(w: Waker): Async[Int] = { polledAfter = true; Async.succeed(0) }
        }
        val r      = Async.collectAll(List[Async[Int]](Async.succeed(1), Async.fail(boom), tail))
        val thrown = Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom), !polledAfter)
      }
    ),
    suite("CONVERGENCE: documented eager-evaluation gotcha")(
      test("a throw inside map on a ready value escapes eagerly and is NOT captured") {
        // Documented behavior: errors thrown by user code inside map/flatMap are
        // NOT turned into a Failure on the eager fast path.
        val boom   = new RuntimeException("eager")
        val caught =
          try { Async.succeed(1).map[Int](_ => throw boom); Option.empty[Throwable] }
          catch { case e: Throwable => Some(e) }
        assertTrue(caught.contains(boom))
      }
    ),
    suite("CONVERGENCE: monad / functor laws on ordinary values")(
      test("functor identity: succeed(a).map(identity) == succeed(a)") {
        assertTrue(Async.succeed(42).map(identity).block == 42)
      },
      test("functor composition: map(f).map(g) == map(g compose f)") {
        val f = (x: Int) => x + 1
        val g = (x: Int) => x * 3
        assertTrue(
          Async.succeed(5).map(f).map(g).block == Async.succeed(5).map(g compose f).block
        )
      },
      test("left identity: succeed(a).flatMap(f) == f(a)") {
        val f = (x: Int) => Async.succeed(x * 10)
        assertTrue(Async.succeed(4).flatMap(f).block == f(4).block)
      },
      test("right identity: m.flatMap(succeed) == m") {
        assertTrue(Async.succeed(7).flatMap(Async.succeed(_)).block == 7)
      },
      test("associativity: m.flatMap(f).flatMap(g) == m.flatMap(x => f(x).flatMap(g))") {
        val f   = (x: Int) => Async.succeed(x + 1)
        val g   = (x: Int) => Async.succeed(x * 2)
        val lhs = Async.succeed(3).flatMap(f).flatMap(g).block
        val rhs = Async.succeed(3).flatMap((x: Int) => f(x).flatMap(g)).block
        assertTrue(lhs == rhs)
      }
    )
  )
}
