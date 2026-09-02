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
