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
 * Scala-2-only: the `internal.AsyncMacros` rewrite preserves a `val`'s declared
 * type tree, so `val n: Long = intAsync.await` keeps `n` typed as `Long` after
 * the CPS transform and overload resolution / widening behaves exactly as in
 * straight-line Scala.
 *
 * This is intentionally NOT part of the cross-version `AsyncAwaitBlockSpec`:
 * dotty-cps-async (Scala 3) pushes the val's expected type INTO `.await`, so
 * the same `val n: Long = intAsync.await` elaborates as `await[Long](intAsync)`
 * and does not compile. The Scala 2 macro and DCA therefore diverge here by
 * design; each is exercised in its own spec.
 */
object AsyncAwaitValAscriptionSpec extends ZIOSpecDefault {

  // Overloaded helpers (local method overloading is illegal, so object scope).
  private def selected(i: Int): String  = "int"
  private def selected(l: Long): String = "long"

  sealed private trait Base
  private final case class Sub() extends Base
  private def picked(b: Base): String = "base"
  private def picked(s: Sub): String  = "sub"

  def spec = suite("AsyncAwaitValAscriptionSpec")(
    test("awaited val type ascription drives numeric widening and overload resolution") {
      val r = Async.async {
        val n: Long = Async.succeed(1).await
        selected(n)
      }.block
      assertTrue(r == "long")
    },
    test("synchronous val type ascription before a later await is preserved") {
      val r = Async.async {
        val n: Long = 1
        val _       = Async.succeed(()).await
        selected(n)
      }.block
      assertTrue(r == "long")
    },
    test("awaited val supertype ascription drives reference overload resolution") {
      val r = Async.async {
        val b: Base = Async.succeed(Sub()).await
        picked(b)
      }.block
      assertTrue(r == "base")
    }
  )
}
