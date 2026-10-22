package testtelemetry

import zio.blocks.telemetry._
import zio.test._

/**
 * How to Test Code That Logs, Traces and Records Metrics: assert on log
 * records.
 *
 * Builds a logger that records into a `LogRecorder`, runs `placeOrder` and
 * asserts on the record's severity, message, fields and span.
 *
 * Run with: sbt "testOnly testtelemetry.CaptureLogsSpec"
 */
object CaptureLogsSpec extends ZIOSpecDefault {
  def spec = suite("placeOrder logs")(
    test("a declined payment logs one ERROR with the order id and the reason") {
      val logs    = new LogRecorder
      val logger  = LoggerProvider.builder.addLogRecordProcessor(logs).build().get("test")
      val service = new OrderService(
        PaymentGateway.declineAll("card declined"),
        TracerProvider.builder.build().get("test"),
        logger,
        MeterProvider.builder.build().get("test")
      )

      service.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

      val record = logs.records.head
      assertTrue(
        logs.records.map(r => r.severity -> r.body.value) == List(Severity.Error -> "Payment failed"),
        record.attributes.get(AttributeKey.string("order.id")) == Some("order-2"),
        record.attributes.get(AttributeKey.string("reason")) == Some("card declined")
      )
    },
    test("the log record carries the placeOrder span's ids") {
      val spans   = new SpanRecorder
      val logs    = new LogRecorder
      val service = new OrderService(
        PaymentGateway.approveAll,
        TracerProvider.builder.addSpanProcessor(spans).build().get("test"),
        LoggerProvider.builder.addLogRecordProcessor(logs).build().get("test"),
        MeterProvider.builder.build().get("test")
      )

      service.placeOrder(Order("order-1", "Zionomicon", 1, 4500L))

      val span   = spans.spans.find(_.name == "placeOrder").get.spanContext
      val record = logs.records.head
      assertTrue(
        record.severity == Severity.Info,
        record.spanId == span.spanId.value,
        record.traceIdHi == span.traceIdHi,
        record.traceIdLo == span.traceIdLo
      )
    }
  )
}
