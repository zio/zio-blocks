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
import zio.test.Assertion._

/**
 * ADVERSARIAL PROBE (Category 2 — Scala 2 macro by-name semantics).
 *
 * `Async.async { ... }` is direct-style Scala: the body must evaluate exactly
 * as the equivalent straight-line code, except that `.await` suspends instead
 * of blocking. By-name evaluation is a core language contract — `Some(x)
 * .getOrElse(d)` must NOT evaluate `d` when the `Option` is a `Some`.
 *
 * The Scala 2 `AsyncMacros` ANF/CPS transform ANF-binds every argument (`val
 * tmp = arg`) before the call, which would force a by-name argument EAGERLY —
 * defeating its laziness and running the awaited effect unconditionally. Rather
 * than silently miscompile, the macro now REJECTS `.await` in a by-name
 * argument position at compile time, with a clear diagnostic, mirroring the
 * Scala 3 (dotty-cps-async) backend which also rejects the same source (no
 * `AsyncShift` for `Some.getOrElse`).
 *
 * Short-circuit `&&` / `||` are exempt: their by-name right operand is
 * rewritten to an `if`, which preserves laziness, so awaiting there remains
 * supported.
 *
 * These tests assert the compile-time rejection (parity with Scala 3).
 */
object AsyncScala2ByNameProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2ByNameProbeSpec")(
    test("`.await` in a by-name argument (getOrElse default) is rejected with a clear diagnostic") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          Some(42).getOrElse(Async.fail(new RuntimeException("boom")).await)
        }
        a
      """).map {
        case Left(msg) =>
          assertTrue(msg.toLowerCase.contains("by-name"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("`.await` in a by-name block argument (getOrElse default) is rejected") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          Some(7).getOrElse {
            Async.succeed(0).await
          }
        }
        a
      """).map {
        case Left(msg) =>
          assertTrue(msg.toLowerCase.contains("by-name"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("`.await` in the by-name right operand of && remains supported (short-circuit preserved)") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          Async.succeed(true).await && Async.succeed(false).await
        }
        a
      """).map(r => assert(r)(isRight))
    }
  )
}
