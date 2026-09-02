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
