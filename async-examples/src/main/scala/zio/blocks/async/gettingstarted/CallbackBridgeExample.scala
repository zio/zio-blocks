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
