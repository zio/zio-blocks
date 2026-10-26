//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run CompleteExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.CompleteExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Putting It Together
 *
 * An order-processing pipeline assembled from the sections: a callback lookup
 * bridged with `Async.promise`, an order ID parsed with `Async.attempt`, a
 * stock check forked with `Async.start`, all sequenced in `Async.async` and
 * recovered with `Async#catchAll`.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {

    // Step 1: Bridge a callback-based user-lookup API with promise + Completer
    val userLookup: Async[String] = Async.promise[String] {
      val c = summon[Completer[String]]
      new Thread {
        override def run(): Unit = {
          Thread.sleep(10)
          c.succeed("Ada")
        }
      }.start()
    }

    // Step 2: Parse an order ID that might be malformed
    val orderId: Async[Int] = Async.attempt("9001".toInt)

    // Step 3: Start a stock-availability check on a background worker
    val stockCheck: Async.Running[Boolean] = Async.start { Thread.sleep(5); true }

    // Step 4: Compose all steps in direct style and recover from any failure
    val report: String = Async.async {
      val user    = userLookup.await
      val id      = orderId.await
      val inStock = stockCheck.await
      s"Order $id for $user: ${if (inStock) "in stock" else "out of stock"}"
    }.catchAll { err =>
      Async.succeed(s"Order failed: ${err.getMessage}")
    }.block

    println(s"Report: $report")
  }
}
