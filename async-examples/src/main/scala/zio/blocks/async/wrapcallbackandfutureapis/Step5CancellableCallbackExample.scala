package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

/**
 * Cancel the Underlying Call When the `Async` Is Cancelled
 *
 * Shows that a promise-based `stockOf` leaves the warehouse request open after
 * cancellation, then fixes it with a `cancellable` helper: a custom `Pollable`
 * whose `cancel` forwards to the callback API's `Request` handle.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step5CancellableCallbackExample"
 * }}}
 */
object Step5CancellableCallbackExample {
  def stockOf(client: InventoryClient, title: String): Async[Int] =
    Async.promise[Int] { c ?=>
      client.stock(title) {
        case Right(level) => c.succeed(level)
        case Left(error)  => c.fail(error)
      }
      () // the Request handle is dropped here
    }

  def cancellable[A](register: Completer[A] => (() => Unit)): Async[A] =
    new Pollable[A] {
      private val result     = new Completer[A]
      private val cancelCall = register(result)

      def poll(onComplete: Runnable): Async[A] = {
        val polled = result.poll(onComplete)
        // While pending, answer with this Pollable rather than the Completer, so
        // the driver keeps waiting on us and our cancel() is the one it calls.
        if (polled.asInstanceOf[AnyRef] eq result) this else polled
      }

      override def cancel(): Unit = cancelCall()
    }

  def cancellableStockOf(client: InventoryClient, title: String): Async[Int] =
    cancellable[Int] { c =>
      val request = client.stock(title) {
        case Right(level) => c.succeed(level)
        case Left(error)  => c.fail(error)
      }
      () => request.cancel()
    }

  def main(args: Array[String]): Unit = {
    val inventory = new InventoryClient(replyAfterMillis = 10)

    val slowWarehouse = new InventoryClient(replyAfterMillis = 60000)
    stockOf(slowWarehouse, "Zionomicon").start.cancel()
    println(s"Open warehouse requests after cancel: ${slowWarehouse.openRequests}")

    val anotherSlowWarehouse = new InventoryClient(replyAfterMillis = 60000)
    cancellableStockOf(anotherSlowWarehouse, "Zionomicon").start.cancel()
    println(s"Open warehouse requests after cancel: ${anotherSlowWarehouse.openRequests}")

    println(cancellableStockOf(inventory, "Programming in Scala").block)
  }
}
