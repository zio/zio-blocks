package correlatelogsandtraces

import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._
import zio.http._

/**
 * How to Correlate Logs and Traces Across Services: Switch to B3 Headers.
 *
 * Writes a span context in the single-header and multi-header B3 formats, then
 * shows that a W3C-only receiver finds nothing in a B3 request while an
 * extractor with a B3 fallback finds the caller's trace id.
 */
// Run from telemetry-examples:  sbt "runMain correlatelogsandtraces.Step5B3HeadersExample"
object Step5B3HeadersExample {
  def extractParent(request: Request): Option[SpanContext] =
    W3CTraceContextPropagator
      .extract(request, HttpGlue.getHeader)
      .orElse(B3Propagator.multi.extract(request, HttpGlue.getHeader))

  def main(args: Array[String]): Unit = {
    // Setup from the first section: tracer and logger share one storage
    Telemetry.installLogger()

    Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
      val request = PaymentApi.chargeRequest("order-1042", 5990)
      println(B3Propagator.single.inject(span.spanContext, request, HttpGlue.setHeader).headers)
      println(B3Propagator.multi.inject(span.spanContext, request, HttpGlue.setHeader).headers)
    }

    Telemetry.tracer.span("checkout.callPayment", SpanKind.Client) { span =>
      val request =
        B3Propagator.multi.inject(span.spanContext, PaymentApi.chargeRequest("order-1042", 5990), HttpGlue.setHeader)
      println(s"W3C only: ${W3CTraceContextPropagator.extract(request, HttpGlue.getHeader).map(_.traceIdHex)}")
      println(s"with B3 fallback: ${extractParent(request).map(_.traceIdHex)}")
      println(s"checkout span: ${span.spanContext.traceIdHex}")
    }
  }
}
