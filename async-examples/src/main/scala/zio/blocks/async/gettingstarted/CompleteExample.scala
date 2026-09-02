package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Complete Example: Order Processing Pipeline
 *
 * Combines all core `Async` concepts in a single order-processing scenario:
 *   - `Async.promise` + `Completer` to bridge a callback-based user-lookup API
 *   - `Async.attempt` to safely parse a potentially malformed order ID
 *   - `.start` + `Async.Running` to check stock availability in the background
 *   - `Async.async { .await }` for direct-style sequential composition
 *   - `.catchAll` to recover from any failure in the pipeline
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.CompleteExample"
 * }}}
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

    // Step 3: Start a stock-availability check in the background
    val stockCheck: Async.Running[Boolean] = Async.succeed(true).map { v => Thread.sleep(5); v }.start

    // Step 4: Compose all steps in direct style and recover from any failure
    val report: String = Async.async {
      val user    = userLookup.await
      val id      = orderId.await
      val inStock = stockCheck.await
      s"Order $id for $user: ${if (inStock) "in stock" else "out of stock"}"
    }.catchAll { err =>
      Async.succeed(s"Order failed: ${err.getMessage}")
    }.block

    println(report)
  }
}
