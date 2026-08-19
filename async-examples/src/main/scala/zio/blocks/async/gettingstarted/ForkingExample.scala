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
 * Section 6: Forking and Cancellation
 *
 * Demonstrates how to run a computation on a background worker with `.start`,
 * which returns an `Async.Running[A]` handle. The handle can be joined with
 * `.block` (blocks the caller until the computation completes) or cancelled
 * with `.cancel()` (stops the driver loop before the result is published).
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ForkingExample"
 * }}}
 */
object ForkingExample {
  def main(args: Array[String]): Unit = {
    // Fork and join
    val running: Async.Running[Int] = Async.succeed(42).map { x => println(s"Running in background: $x"); x * 2 }.start
    val joined: Int                 = running.block
    println(s"Joined: $joined")

    // Fork and cancel
    val running2: Async.Running[Int] = Async.start { Thread.sleep(100); 99 }
    running2.cancel()
    println("Cancelled running2")
  }
}
