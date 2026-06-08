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
 * ADVERSARIAL PROBE (Category 2 — Scala 2 macro DX / missing diagnostic;
 * Possible, Low).
 *
 * A `while` loop with `.await` in its body is fully supported (see
 * `AsyncAwaitBlockSpec`), but its sibling `do { ... } while (...)` — a
 * legitimate Scala 2.13 loop — is not. The macro's `transform` does not match
 * the `do/while` `LabelDef` shape (only the two `while` shapes), so it falls
 * through to the generic catch-all:
 *
 *   "This expression position is not supported by the Scala 2 `Async.async` macro."
 *
 * That diagnostic is confusing: it neither names `do/while` nor suggests the
 * supported `while` form, leaving the user with no actionable guidance for a
 * trivially-rewritable loop. Per the adversarial-testing contract, an abort on
 * an unsupported shape is acceptable only with a CLEAR diagnostic; a generic
 * "not supported" message here is a DX defect.
 *
 * Oracle: a compile diagnostic is part of the API/DX contract; an unsupported
 * shape must fail with a message that identifies the shape and the remedy.
 *
 * This test FAILS now: it asserts the diagnostic identifies the `do/while`
 * construct (mentions "do" or "while"), which the current generic message does
 * not. Fix direction: either support the `do/while` `LabelDef` shape in
 * `transformWhile`, or emit a targeted abort ("`do/while` is not supported
 * inside `Async.async`; rewrite as a `while` loop").
 */
object AsyncScala2DoWhileProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2DoWhileProbeSpec")(
    test("do/while with .await fails with a diagnostic that names the construct") {
      typeCheck("""
        import zio.blocks.async._
        val a = Async.async {
          var i = 0
          do { i += Async.succeed(1).await } while (i < 3)
          i
        }
        a
      """).map {
        case Left(msg) =>
          // A clear diagnostic should identify do/while (or point at `while`).
          assertTrue(msg.toLowerCase.contains("do/while") || msg.toLowerCase.contains("do "))
        case Right(_) =>
          // If it ever compiles, that is also fine (the loop is supported).
          assertTrue(true)
      }
    }
  )
}
