//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept1ReadyValuesExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept1ReadyValuesExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Ready Values: `Async.succeed`, `map`, and
 * `block`
 *
 * Wraps an available value with `Async.succeed`, transforms it with
 * `Async#map`, and drives it to its result with `Async#block`.
 */
object Concept1ReadyValuesExample {
  def main(args: Array[String]): Unit = {
    val result: Int = Async.succeed(42).map(_ * 2).block
    println(s"Ready mapped: $result")
  }
}
