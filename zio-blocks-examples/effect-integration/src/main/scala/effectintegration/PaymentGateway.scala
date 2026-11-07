package effectintegration

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import zio.blocks.async.Async
import Cancellable.cancellable

// ZIO Blocks code with an asynchronous API: authorizing a payment takes `latencyMillis`,
// and cancelling the `Async` cancels the pending authorization.
final class PaymentGateway(latencyMillis: Long) {
  private val timer: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
      val thread = new Thread(r, "payment-gateway")
      thread.setDaemon(true)
      thread
    }

  def authorize(order: Order): Async[String] =
    cancellable[String] { completer =>
      val pending = timer.schedule(
        new Runnable { def run(): Unit = completer.succeed(s"auth-${order.id}") },
        latencyMillis,
        TimeUnit.MILLISECONDS
      )
      () => {
        pending.cancel(false)
        println(s"payment gateway: authorization of order ${order.id} cancelled")
      }
    }
}
