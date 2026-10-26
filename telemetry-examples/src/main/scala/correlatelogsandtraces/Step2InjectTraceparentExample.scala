package correlatelogsandtraces

import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

/**
 * How to Correlate Logs and Traces Across Services: Write the Trace Context
 * into an Outgoing Request.
 *
 * Injects the active span's context into the `traceparent` header of the
 * request the checkout service sends to the payment service.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step2InjectTraceparentExample"
object Step2InjectTraceparentExample {
  def main(args: Array[String]): Unit = {
    // Setup from the first section: tracer and logger share one storage
    Telemetry.installLogger()

    Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
      val request = W3CTraceContextPropagator.inject(
        span.spanContext,
        PaymentApi.chargeRequest("order-1042", 5990),
        HttpGlue.setHeader
      )
      println(request.headers)
      log.info("Calling the payment service")
    }
  }
}
