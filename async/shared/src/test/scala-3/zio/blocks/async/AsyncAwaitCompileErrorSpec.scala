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
 * Enforces the lexical restriction on direct-style `.await`: it may only appear
 * directly inside an `Async.async { ... }` block.
 *
 * This holds on every platform and Scala version, by different mechanisms:
 *   - Scala 2: `.await` is a plain `@compileTimeOnly` marker method.
 *   - Scala 3: `.await` is an `inline` macro that aborts unless rewritten by
 *     the enclosing `Async.async` (dotty-cps-async on the JVM / older Scala 3
 *     JS, native `js.await` on Scala 3.8+ JS).
 *
 * Scala 3 only: `.await` there is a typer-phase `inline` macro, so ZIO Test's
 * `typeCheck` (which runs the typer) observes the failure. On Scala 2 `.await`
 * is a `@compileTimeOnly` marker enforced in the later refchecks phase, which
 * `typeCheck` does not run — so the negative case cannot be probed this way.
 * The assertion is the portable "does not compile" (`isLeft`); error-message
 * wording is intentionally not asserted.
 */
object AsyncAwaitCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("AsyncAwaitCompileErrorSpec")(
    test("`.await` outside `Async.async` does not compile") {
      typeCheck {
        """
        import zio.blocks.async._
        val n = Async.succeed(1).await
        n
        """
      }.map(result => assert(result)(isLeft))
    }
    // The positive case (`.await` inside `Async.async` compiles and runs) is
    // covered by `AsyncAwaitBlockSpec` and `AsyncRewriteSpec`; it is not probed
    // via `typeCheck`, whose synthetic single-pass compilation does not fire the
    // dotty-cps-async `Async.async` rewrite the inline `.await` macro depends on.
  )
}
