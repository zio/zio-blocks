//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept6ForkingAndCancellationExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept6ForkingAndCancellationExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Forking and Cancellation: `start` and
 * `Async.Running`
 *
 * Forks a block onto a background worker with `Async.start` and joins it, shows
 * that `Async#start` on a ready chain settles on the calling thread, and
 * cancels a running handle whose body still runs to completion.
 */
object Concept6ForkingAndCancellationExample {
  def main(args: Array[String]): Unit = {
    val running: Async.Running[Int] = Async.start {
      println(s"Computing on ${Thread.currentThread.getName}")
      42 * 2
    }
    val joined: Int = running.block
    println(s"Joined on ${Thread.currentThread.getName}: $joined")

    val ready: Async.Running[Int] = Async
      .succeed(42)
      .map { x => println(s"map runs on ${Thread.currentThread.getName}"); x * 2 }
      .start
    println(s"Joined: ${ready.block}")

    val running2: Async.Running[Int] = Async.start {
      Thread.sleep(100)
      println("Body finished after cancel")
      99
    }
    running2.cancel()
    println("Cancelled running2")
    Thread.sleep(200) // give the body time to finish
  }
}
