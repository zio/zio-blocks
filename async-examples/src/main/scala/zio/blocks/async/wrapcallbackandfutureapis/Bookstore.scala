package zio.blocks.async.wrapcallbackandfutureapis

import java.util.concurrent.{CompletableFuture, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

/**
 * Stand-ins for the third-party clients a bookstore might call. None of them
 * knows about `Async`: one returns a Scala `Future`, one returns a Java
 * `CompletableFuture`, and one reports through a callback and hands back a
 * cancel handle. Prices, stock levels and shipping times are fictional sample
 * data.
 */

/** A Future-based price service. */
object PriceService {
  private val prices = Map(
    "Zionomicon"                      -> BigDecimal("49.99"),
    "Programming in Scala"            -> BigDecimal("59.95"),
    "Functional Programming in Scala" -> BigDecimal("44.99")
  )

  def lookup(title: String)(implicit ec: ExecutionContext): Future[BigDecimal] =
    Future(prices.getOrElse(title, throw new NoSuchElementException(s"No price for $title")))
}

/** A Java-style shipping SDK that returns `CompletableFuture`s. */
object ShippingQuotes {
  def estimateDays(title: String): CompletableFuture[Int] =
    CompletableFuture.supplyAsync { () =>
      if (title.isEmpty) throw new IllegalArgumentException("Title must not be empty")
      if (title.length > 20) 5 else 2
    }
}

/**
 * A callback-based inventory client. `stock` reports through `onDone` once the
 * warehouse replies, after `replyAfterMillis`, and returns a `Request` whose
 * `cancel` withdraws the question before the reply arrives.
 */
final class InventoryClient(replyAfterMillis: Long) {
  private val stockLevels = Map(
    "Zionomicon"                      -> 12,
    "Programming in Scala"            -> 3,
    "Functional Programming in Scala" -> 0
  )
  private val open      = new AtomicInteger(0)
  private val warehouse = Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
    val t = new Thread(r, "warehouse")
    t.setDaemon(true)
    t
  }

  /** How many questions are still waiting for the warehouse. */
  def openRequests: Int = open.get()

  def stock(title: String)(onDone: Either[Throwable, Int] => Unit): InventoryClient.Request = {
    open.incrementAndGet()
    val reply = warehouse.schedule(
      new Runnable {
        def run(): Unit = {
          open.decrementAndGet()
          onDone(stockLevels.get(title).toRight(new NoSuchElementException(s"Unknown title: $title")))
        }
      },
      replyAfterMillis,
      TimeUnit.MILLISECONDS
    )
    new InventoryClient.Request {
      def cancel(): Unit = if (reply.cancel(false)) open.decrementAndGet()
    }
  }
}

object InventoryClient {
  trait Request {
    def cancel(): Unit
  }
}
