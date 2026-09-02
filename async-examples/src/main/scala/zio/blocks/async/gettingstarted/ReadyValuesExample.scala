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
