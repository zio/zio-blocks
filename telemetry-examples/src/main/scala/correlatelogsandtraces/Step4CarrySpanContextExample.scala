package correlatelogsandtraces

import java.util.concurrent.{CompletableFuture, Executors}
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

/**
 * How to Correlate Logs and Traces Across Services: Carry the Span Context to
 * Another Thread.
 *
 * Hands the confirmation email to a worker thread twice: once without the span
 * context, whose log line has no ids, and once with an `OtelContext` snapshot,
 * whose log line keeps the order's trace and span ids.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step4CarrySpanContextExample"
object Step4CarrySpanContextExample {
  object Emails {
    private val worker = Executors.newSingleThreadExecutor()

    def sendWithoutContext(orderId: String): Unit =
      CompletableFuture.runAsync(() => log.info("Confirmation email sent", "order.id" -> orderId), worker).join()

    def send(orderId: String, context: OtelContext): Unit =
      CompletableFuture
        .runAsync(
          () =>
            Telemetry.storage.scoped(context.spanContext) {
              log.info("Confirmation email sent", "order.id" -> orderId)
            },
          worker
        )
        .join()

    def shutdown(): Unit = worker.shutdown()
  }

  def main(args: Array[String]): Unit = {
    // Setup from the first section: tracer and logger share one storage
    Telemetry.installLogger()

    Telemetry.tracer.span("checkout.placeOrder") { _ =>
      log.info("Order received", "order.id" -> "order-1042")
      Emails.sendWithoutContext("order-1042")
      Emails.send("order-1042", OtelContext.current(Telemetry.storage))
    }

    Emails.shutdown()
  }
}
