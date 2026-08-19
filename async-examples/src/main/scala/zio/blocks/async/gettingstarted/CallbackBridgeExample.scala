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
 * Section 5: Callback Bridging
 *
 * Demonstrates how to lift a callback-based API into an `Async` using
 * `Async.promise` and a `Completer`. The body of `Async.promise` is a Scala 3
 * context function: `summon[Completer[A]]` retrieves the completer, which can
 * then be captured and called from any thread.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.CallbackBridgeExample"
 * }}}
 */
object CallbackBridgeExample {
  def main(args: Array[String]): Unit = {
    val result: String = Async
      .promise[String] {
        val c = summon[Completer[String]]
        new Thread {
          override def run(): Unit = {
            Thread.sleep(10)
            c.succeed("hello from callback")
          }
        }.start()
      }
      .block
    println(s"Promise resolved: $result")
  }
}
