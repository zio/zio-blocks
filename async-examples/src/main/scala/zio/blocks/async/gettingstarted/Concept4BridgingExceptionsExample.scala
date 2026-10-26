//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept4BridgingExceptionsExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept4BridgingExceptionsExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Bridging Exceptions: `Async.attempt`
 *
 * Turns a throwing block into an `Async`: a well-formed string parses to a
 * ready value, a malformed one becomes a failure that `Async#either` reifies.
 */
object Concept4BridgingExceptionsExample {
  def main(args: Array[String]): Unit = {
    val good: Int = Async.attempt("42".toInt).block
    println(s"Parsed: $good")

    val bad: Either[Throwable, Int] = Async.attempt("oops".toInt).either.block
    println(s"Failed parse: $bad")
  }
}
