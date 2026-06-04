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
 * Cross-platform, cross-version core semantics for the async runtime.
 *
 * Every assertion holds identically under Scala 2.13 / Scala 3, JVM / Scala.js.
 * Only synchronously-resolvable scenarios are tested here (raw value, ready
 * pollable, or completer that fires inside `poll`). Truly asynchronous,
 * thread-parking behavior is tested in the JVM-only `AsyncBlockingSpec`.
 *
 * Calls use the surface sugar (`.map` / `.flatMap` / `.block`)
 * directly. Because `Async[+A]` exposes no `>: A` bound, Scala 2 infers
 * `A := A` from the receiver without widening, so no explicit type arguments
 * are needed.
 */
object AsyncSpec extends ZIOSpecDefault {

  /**
   * A `Pollable` that returns its value on the first poll. Used to verify the
   * suspended path of `map` / `flatMap` without crossing a thread boundary.
   */
  private final class Ready[A](value: A) extends Pollable[A] {
    def poll(waker: Waker): Async[A] = Async.succeed(value)
  }

  /**
   * A `Pollable` that pretends to be pending the first `pollsNeeded` calls and
   * then returns its value. Each pending poll invokes the waker synchronously
   * to drive the scheduler forward without needing another thread.
   */
  private final class ReadyAfter[A](value: A, pollsNeeded: Int) extends Pollable[A] {
    private var remaining            = pollsNeeded
    def poll(waker: Waker): Async[A] =
      if (remaining <= 0) Async.succeed(value)
      else {
        remaining -= 1
        waker.wake()
        this
      }
  }

  /** Lift a `Pollable[A]` into an `Async[A]` (subtype relationship). */
  private def fromPollable[A](p: Pollable[A]): Async[A] = p

  def spec = suite("AsyncSpec")(
    suite("Async.succeed")(
      test("await on a succeeded value returns the value with no suspension") {
        val r = Async.succeed("hi").block
        assertTrue(r == "hi")
      },
      test("map over a succeeded value applies the function") {
        val r = Async.succeed(2).map(_ * 3).block
        assertTrue(r == 6)
      }
    ),
    suite(".map")(
      test("maps a ready value synchronously") {
        val r = Async.succeed(1).map(_ + 1).block
        assertTrue(r == 2)
      },
      test("maps over a Ready pollable") {
        val r = fromPollable(new Ready(10)).map(_ * 2).block
        assertTrue(r == 20)
      }
    ),
    suite(".flatMap")(
      test("chains two ready values synchronously") {
        val r = Async.succeed(3).flatMap(x => Async.succeed(x + 4)).block
        assertTrue(r == 7)
      },
      test("chains ready -> pollable") {
        val r = Async.succeed(5).flatMap(x => fromPollable(new Ready(x * 10))).block
        assertTrue(r == 50)
      },
      test("chains pollable -> ready") {
        val r = fromPollable(new Ready(2)).flatMap(x => Async.succeed(x + 100)).block
        assertTrue(r == 102)
      },
      test("chains pollable -> pollable") {
        val r = fromPollable(new Ready(7)).flatMap(x => fromPollable(new Ready(x + 1))).block
        assertTrue(r == 8)
      }
    ),
    // Uses the package-private `promiseInternal` (rather than the public
    // `Async.promise`) so the same explicit `c => ...` lambda compiles under
    // both Scala 2 (`Completer[A] => Unit`) and Scala 3 (which uses a `?=>`
    // body for the public `Async.promise`). Public-surface coverage of
    // `Async.promise` lives in the version-specific `AsyncPromiseSpec`.
    suite("Completer / Async.promise internals")(
      test("synchronous completion awaits to the value") {
        val r = Async.promiseInternal[Int](c => c.succeed(7)).block
        assertTrue(r == 7)
      },
      test("double succeed is ignored (first wins)") {
        val r = Async
          .promiseInternal[Int] { c =>
            c.succeed(1)
            c.succeed(2)
          }
          .block
        assertTrue(r == 1)
      },
      test("completion that happens during poll (waker-driven) resumes") {
        // A pollable that delays completion by a few polls, each time waking
        // synchronously. The scheduler must keep looping until done.
        val r = fromPollable(new ReadyAfter("ok", pollsNeeded = 3)).block
        assertTrue(r == "ok")
      }
    ),
    suite("Pollable")(
      test("re-poll after suspension yields the value") {
        val r = fromPollable(new ReadyAfter(99, pollsNeeded = 2)).block
        assertTrue(r == 99)
      },
      test("flatMap over a suspended pollable consumes its left side exactly once") {
        // Verify FlatMapPollable.stage memoization: once `pa` produces a value,
        // we must never re-poll it (so a leaf like a socket read isn't retried).
        var leftPolls = 0
        val left      = new Pollable[Int] {
          def poll(w: Waker): Async[Int] = {
            leftPolls += 1
            if (leftPolls < 2) {
              w.wake()
              this
            } else Async.succeed(11)
          }
        }
        val v = fromPollable(left).flatMap(x => Async.succeed(x + 1)).block
        assertTrue(v == 12, leftPolls == 2)
      }
    )
  )
}
