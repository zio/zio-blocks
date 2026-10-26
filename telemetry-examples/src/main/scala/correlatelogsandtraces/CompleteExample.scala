package correlatelogsandtraces

import java.util.concurrent.{CompletableFuture, Executors}
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

/**
 * How to Correlate Logs and Traces Across Services: Complete Example.
 *
 * Places two orders. For each one, the checkout service calls the payment
 * service with a `traceparent` header, the payment service continues the trace,
 * and the confirmation email is logged on a worker thread with an `OtelContext`
 * snapshot. Every line of one order carries that order's trace id.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.CompleteExample"
object CompleteExample {
  object Emails {
    private val worker = Executors.newSingleThreadExecutor()

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

  object CheckoutService {
    def placeOrder(orderId: String, amountCents: Long): Unit =
      log.annotated("service.name" -> "checkout") {
        Telemetry.tracer.span("checkout.placeOrder") { _ =>
          log.info("Order received", "order.id" -> orderId)
          Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
            val request = W3CTraceContextPropagator.inject(
              span.spanContext,
              PaymentApi.chargeRequest(orderId, amountCents),
              HttpGlue.setHeader
            )
            val response = Network.send(request)
            log.info("Payment answered", "status" -> response.status.code)
          }
          Emails.send(orderId, OtelContext.current(Telemetry.storage))
        }
      }
  }

  def main(args: Array[String]): Unit = {
    Telemetry.installLogger()

    CheckoutService.placeOrder("order-1042", 5990)
    CheckoutService.placeOrder("order-1043", 1250)

    Network.shutdown()
    Emails.shutdown()
  }
}
