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

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 1: Ready Values
 *
 * Demonstrates how to wrap an already-available value into an `Async` with
 * `Async.succeed`, transform it using `.map`, and drive it to a result with
 * `.block`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ReadyValuesExample"
 * }}}
 */
object ReadyValuesExample {
  def main(args: Array[String]): Unit = {
    val result: Int = Async.succeed(42).map(_ * 2).block
    println(s"Ready mapped: $result")
  }
}
