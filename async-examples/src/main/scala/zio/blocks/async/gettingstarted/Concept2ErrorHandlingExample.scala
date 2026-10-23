//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept2ErrorHandlingExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept2ErrorHandlingExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Error Handling: `Async.fail`, `catchAll`, and
 * `either`
 *
 * Recovers a failed `Async` with `Async#catchAll`, then observes a failure as
 * data with `Async#either`.
 */
object Concept2ErrorHandlingExample {
  def main(args: Array[String]): Unit = {
    val recovered: String = Async
      .fail(new Exception("oops"))
      .catchAll(_ => Async.succeed("default"))
      .block
    println(s"Recovered: $recovered")

    val observed: Either[Throwable, Int] = Async
      .fail(new Exception("error"))
      .either
      .block
    println(s"Observed: $observed")
  }
}
