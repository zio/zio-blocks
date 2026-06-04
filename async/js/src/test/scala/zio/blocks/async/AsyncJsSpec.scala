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
 * Scala.js-only: a `Pollable` that does NOT complete synchronously inside the
 * `poll` it was given must cause `await` to throw, because JavaScript has no
 * way to block the single thread of execution.
 */
object AsyncJsSpec extends ZIOSpecDefault {

  /** A pollable that never completes — always returns itself, never wakes. */
  private final class NeverReady extends Pollable[Int] {
    def poll(waker: Waker): Async[Int] = this
  }

  def spec = suite("AsyncJsSpec")(
    test("await on a truly async pollable throws IllegalStateException") {
      val async  = new NeverReady: Async[Int]
      val thrown = scala.util.Try(async.block).failed.toOption
      assertTrue(
        thrown.exists(_.isInstanceOf[IllegalStateException]),
        thrown.map(_.getMessage.contains("cannot block")).getOrElse(false)
      )
    },
    test("await on an already-ready value still works on JS") {
      val r = Async.succeed(99).block
      assertTrue(r == 99)
    }
  )
}
