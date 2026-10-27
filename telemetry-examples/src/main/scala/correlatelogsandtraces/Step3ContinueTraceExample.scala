package correlatelogsandtraces

import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

/**
 * How to Correlate Logs and Traces Across Services: Continue the Trace in the
 * Payment Service.
 *
 * The checkout service sends the charge request through the network stand-in;
 * the payment service extracts the `traceparent` header and continues the same
 * trace, so both services' log lines carry the same trace id.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step3ContinueTraceExample"
object Step3ContinueTraceExample {
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
        }
      }
  }

  def main(args: Array[String]): Unit = {
    // Setup from the first section: tracer and logger share one storage
    Telemetry.installLogger()

    CheckoutService.placeOrder("order-1042", 5990)
  }
}
