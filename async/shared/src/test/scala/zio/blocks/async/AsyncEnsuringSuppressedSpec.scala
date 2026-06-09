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
 * ADVERSARIAL PROBE (Category L / M — error integrity of `ensuring`'s suppressed
 * attachment).
 *
 * `ensuring` documents: "propagate the original outcome. A failure in finalizer
 * is suppressed (the original outcome wins)." The suspended-path implementation
 * (`AsyncSlowPath.EnsuringPollable`) attaches a failing finalizer to the primary
 * via `primary.cause.addSuppressed(finalizer.cause)`.
 *
 * Adversarial input: the primary failure and the finalizer failure carry the
 * SAME `Throwable` instance (a legitimate, common pattern — a shared error val,
 * or a cleanup path that re-raises the very error it is reacting to).
 * `Throwable.addSuppressed(self)` is contractually required to throw
 * `IllegalArgumentException("Self-suppression not permitted")`. The combinator
 * must still surface the PRIMARY failure (`e`) — instead it crashes with an
 * unrelated `IllegalArgumentException`, replacing the primary outcome.
 *
 * Oracle: the documented contract (original outcome wins). Expected: `block`
 * rethrows `e`. Actual: it throws `IllegalArgumentException`.
 */
object AsyncEnsuringSuppressedSpec extends ZIOSpecDefault {

  def spec = suite("AsyncEnsuringSuppressedSpec")(
    test("ensuring with the same exception as primary and finalizer surfaces the primary, not a self-suppression crash") {
      val e      = new RuntimeException("shared")
      val a      = Async.fail(e).ensuring(Async.fail(e))
      val thrown = Try(a.block).failed.toOption
      assertTrue(thrown.contains(e))
    }
  )
}
