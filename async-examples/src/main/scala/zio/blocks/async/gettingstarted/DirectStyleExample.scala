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
 * Section 3: Direct Style
 *
 * Demonstrates how to write sequential async code in direct style using
 * `Async.async { ... }` and `.await`. The compiler rewrites `.await` calls into
 * `flatMap` chains, so the code reads like straight-line imperative code
 * without explicit callback nesting.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.DirectStyleExample"
 * }}}
 */
object DirectStyleExample {
  def main(args: Array[String]): Unit = {
    val summary: String = Async.async {
      val user  = Async.succeed("Ada").await
      val order = Async.succeed(9001).await
      s"${user}'s order ${order}"
    }.block
    println(s"Summary: $summary")
  }
}
