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
 * Section 4: Bridging Exceptions
 *
 * Demonstrates how `Async.attempt` evaluates a block that may throw and
 * captures any exception as an async failure rather than propagating it as a
 * JVM exception. A successful evaluation produces a ready-value `Async`; a
 * thrown exception produces a failed `Async` recoverable with `.catchAll`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.AttemptExample"
 * }}}
 */
object AttemptExample {
  def main(args: Array[String]): Unit = {
    val good: Int = Async.attempt("42".toInt).block
    println(s"Parsed: $good")

    val bad: Either[Throwable, Int] = Async.attempt("oops".toInt).either.block
    println(s"Failed parse: $bad")
  }
}
