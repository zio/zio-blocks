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
 * Section 2: Error Handling
 *
 * Demonstrates how to create failed async computations with `Async.fail`,
 * recover from failures using `.catchAll`, and reify both outcomes as
 * `Either[Throwable, A]` using `.either`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ErrorHandlingExample"
 * }}}
 */
object ErrorHandlingExample {
  def main(args: Array[String]): Unit = {
    val recovered: String = Async
      .fail(new Exception("oops"))
      .catchAll(_ => Async.succeed("default"))
      .block
    println(s"Recovered: $recovered")

    val observed: Either[Throwable, Int] = Async.fail(new Exception("error")).either.block
    println(s"Observed: $observed")
  }
}
