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
 * ADVERSARIAL PROBE (Category 2 — Scala 2 macro miscompiles by-name semantics).
 *
 * `Async.async { ... }` is direct-style Scala: the body must evaluate exactly
 * as the equivalent straight-line code, except that `.await` suspends instead
 * of blocking. By-name evaluation is a core language contract — `Some(x)
 * .getOrElse(d)` must NOT evaluate `d` when the `Option` is a `Some`.
 *
 * The Scala 2 `AsyncMacros` ANF/CPS transform (`transformApplySpine`) hoists
 * every `.await` out of the enclosing application's argument list and evaluates
 * it eagerly BEFORE the call — even when the parameter is by-name and the call
 * would never force it. This silently miscompiles the body.
 *
 * Scala-2-ONLY: on Scala 3 the dotty-cps-async backend REJECTS the same source
 * at compile time (no `AsyncShift` for `Some.getOrElse`, "cannot shift ..."),
 * so this file cannot compile on Scala 3 — the divergence is itself the
 * finding: Scala 2 silently miscompiles where Scala 3 gives a compile error.
 *
 * Oracle: language by-name contract + Scala-2-vs-Scala-3 parity. Expected: the
 * by-name default is never forced (result 42 / side effect never runs). Actual:
 * the awaited default runs eagerly (failure surfaces / side effect runs).
 *
 * These tests FAIL on current code (they document the defect). A correct fix
 * (preserve by-name laziness, or reject `.await` in a by-name position with a
 * clear diagnostic as Scala 3 does) makes them pass / become a compile error.
 */
object AsyncScala2ByNameProbeSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  def spec = suite("AsyncScala2ByNameProbeSpec")(
    test("Some.getOrElse must not evaluate the by-name default (awaited) — laziness preserved") {
      // Straight-line Scala: Some(42).getOrElse(throw) == 42 (default never forced).
      val a      = Async.async {
        Some(42).getOrElse(Async.fail(Boom).await)
      }
      val result = scala.util.Try(a.block)
      assertTrue(result == scala.util.Success(42))
    },
    test("Some.getOrElse must not run the by-name default's side effects") {
      var ran = false
      val a   = Async.async {
        Some(7).getOrElse {
          ran = true
          Async.succeed(0).await
        }
      }
      val r = a.block
      assertTrue(r == 7, !ran)
    }
  )
}
