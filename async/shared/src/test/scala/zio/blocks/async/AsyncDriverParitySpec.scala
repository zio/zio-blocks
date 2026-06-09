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

import java.util.concurrent.atomic.AtomicInteger

import zio._
import zio.test._

/**
 * Adversarial parity probe for the suspended `unsafeRunAsync` driver across the
 * JVM and Scala.js back ends.
 *
 * Oracle: `Async.unsafeRunAsync` promises at-most-once callback delivery and the
 * JS driver is documented to be "aligned to the JVM `awaitSuspended` semantic".
 * The JVM driver dedups multiple `wake()`s (its `Parker` collapses them into a
 * single `ready` flag and stops polling once a terminal value is returned), so a
 * pollable that fires its waker more than once is polled exactly twice (pending,
 * then ready). A correctly-aligned JS driver must agree: a completed pollable
 * must NOT be re-polled.
 */
object AsyncDriverParitySpec extends ZIOSpecDefault {

  /**
   * A pollable that, on its first poll, fires the waker TWICE (a legitimate
   * spurious / multi-source wakeup) and stays pending; on every later poll it is
   * ready. A driver that schedules one resumption per `wake()` and does not
   * guard against re-entry after completion re-polls it after it has already
   * settled.
   */
  private final class DoubleWake extends Pollable[Int] {
    val polls = new AtomicInteger(0)
    def poll(w: Waker): Async[Int] = {
      val n = polls.incrementAndGet()
      if (n == 1) { w.wake(); w.wake(); this }
      else Async.succeed(42)
    }
  }

  def spec = suite("AsyncDriverParitySpec")(
    test("a multi-wake pollable is not re-polled after completion (JVM/JS parity)") {
      val p = new DoubleWake
      for {
        result <- ZIO.async[Any, Throwable, Int] { k =>
                    Async.unsafeRunAsync[Int](p)(e => k(ZIO.fromEither(e)))
                    ()
                  }
        // Let any stray resumptions (extra microtasks on JS) drain before we read.
        _ <- Live.live(ZIO.sleep(50.millis))
      } yield assertTrue(result == 42, p.polls.get() == 2)
    }
  )
}
