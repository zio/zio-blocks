package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Book Quotes: putting it together
 *
 * Combines a `Future`-based price service, a callback-based inventory client
 * (through a cancellable bridge) and a `CompletableFuture`-based shipping SDK
 * in one `Async.async` block, hands the result back to a `Future`-based
 * storefront, and cancels one quote to show its warehouse request is withdrawn.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.CompleteExample"
 * }}}
 */
object CompleteExample {
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

  final case class Quote(title: String, price: BigDecimal, inStock: Int, shippingDays: Int)

  def quote(client: InventoryClient, title: String): Async[Quote] =
    Async.async {
      val price = Async.fromFuture(PriceService.lookup(title)).await
      val stock = cancellableStockOf(client, title).await
      val days  = Async.fromCompletionStage(ShippingQuotes.estimateDays(title)).await
      Quote(title, price, stock, days)
    }

  def storefrontQuote(client: InventoryClient, title: String): Future[String] =
    quote(client, title).map { q =>
      val stock = if (q.inStock == 0) "out of stock" else s"${q.inStock} in stock"
      s"${q.title}: $$${q.price}, $stock, ships in ${q.shippingDays} days"
    }.catchAll(e => Async.succeed(s"$title: unavailable (${e.getMessage})")).toFuture

  def main(args: Array[String]): Unit = {
    val warehouse = new InventoryClient(replyAfterMillis = 10)

    List("Zionomicon", "Programming in Scala", "Functional Programming in Scala", "Scala with Cats").foreach { title =>
      println(Await.result(storefrontQuote(warehouse, title), 5.seconds))
    }

    val slowWarehouse = new InventoryClient(replyAfterMillis = 60000)
    quote(slowWarehouse, "Zionomicon").start.cancel()
    println(s"Open warehouse requests after cancel: ${slowWarehouse.openRequests}")
  }
}
