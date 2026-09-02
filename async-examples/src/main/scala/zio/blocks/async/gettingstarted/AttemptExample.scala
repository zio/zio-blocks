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
