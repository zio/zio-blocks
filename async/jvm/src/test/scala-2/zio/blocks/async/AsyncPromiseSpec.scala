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
 * Scala-2-specific surface test for the `Async.promise { }` / `succeed(...)` /
 * `fail(...)` sugar.
 *
 * Scala 2 takes the completer through an `implicit` lambda parameter, so the
 * body reads `Async.promise[String] { implicit c => succeed("hello") }`. The
 * Scala 3 equivalent (context function) is in `scala-3/`.
 */
object AsyncPromiseSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  def spec = suite("AsyncPromiseSpec (Scala 2)")(
    test("promise + succeed with synchronous completion") {
      val r = Async.promise[String](implicit c => succeed("hello")).block
      assertTrue(r == "hello")
    },
    test("promise + fail with synchronous completion") {
      val a      = Async.promise[String](implicit c => fail(Boom))
      val thrown = scala.util.Try(a.block).failed.toOption
      assertTrue(thrown.contains(Boom))
    },
    test("promise body that completes then throws lets the throw escape (eager body)") {
      // The body completes the completer and then throws synchronously. The
      // completer is one-shot so the value is already settled, but `promise`
      // does not capture a throwing body — the throw escapes the construction
      // call (eager evaluation, like the rest of `Async`).
      val caught =
        try { Async.promise[Int] { c => c.succeed(1); throw Boom }; None }
        catch { case t: Throwable => Some(t) }
      assertTrue(caught.contains(Boom))
    }
  )
}
