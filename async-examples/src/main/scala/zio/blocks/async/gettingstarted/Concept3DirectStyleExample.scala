//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept3DirectStyleExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept3DirectStyleExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Direct Style: `Async.async` and `await`
 *
 * Sequences two `Async` values in direct style: each `.await` inside
 * `Async.async { ... }` is rewritten into a `flatMap` at compile time.
 */
object Concept3DirectStyleExample {
  def main(args: Array[String]): Unit = {
    val summary: String = Async.async {
      val user  = Async.succeed("Ada").await
      val order = Async.succeed(9001).await
      s"${user}'s order ${order}"
    }.block
    println(s"Summary: $summary")
  }
}
