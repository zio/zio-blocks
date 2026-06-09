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
 * attachment). Companion to BUG-ASYNC-006: the self-suppression guard fixed the
 * SAME-instance case but the same `addSuppressed` call site
 * (`AsyncSlowPath.EnsuringPollable`, ~line 294) is still unguarded against a
 * NULL finalizer cause.
 *
 * `Async.fail(cause)` accepts a `Throwable` whose value may be `null` at the
 * type level. When the primary fails with a real `boom` and the finalizer fails
 * with a `null` cause, the guard `if (primaryCause ne finalizerCause)` is true
 * (`boom ne null`), so `boom.addSuppressed(null)` runs and — per the
 * `Throwable.addSuppressed` contract — throws `NullPointerException("Cannot
 * suppress a null exception.")`. That NPE REPLACES the primary outcome.
 *
 * Oracle: the documented `ensuring` contract — "propagate the original outcome
 * ... the original outcome wins". Expected: `block` rethrows `boom`. Actual: it
 * throws `NullPointerException`.
 */
object AsyncEnsuringNullCauseSpec extends ZIOSpecDefault {

  def spec = suite("AsyncEnsuringNullCauseSpec")(
    test("ensuring with a null finalizer cause surfaces the primary, not an addSuppressed NPE") {
      val boom   = new RuntimeException("boom")
      val a      = Async.fail(boom).ensuring(Async.fail(null))
      val thrown = Try(a.block).failed.toOption
      assertTrue(thrown.contains(boom))
    }
  )
}
