package testtelemetry

import zio.blocks.telemetry._
import zio.test._

/**
 * How to Test Code That Logs, Traces and Records Metrics: assert on spans.
 *
 * Builds a tracer that records into a `SpanRecorder`, runs `placeOrder` and
 * asserts on the span's name, attributes, status and parent.
 *
 * Run with: sbt "testOnly testtelemetry.RecordSpansSpec"
 */
object RecordSpansSpec extends ZIOSpecDefault {
  def spec = suite("placeOrder spans")(
    test("a declined payment ends the placeOrder span with an error status") {
      val recorder = new SpanRecorder
      val tracer   = TracerProvider.builder.addSpanProcessor(recorder).build().get("test")
      val service  = new OrderService(
        PaymentGateway.declineAll("card declined"),
        tracer,
        LoggerProvider.builder.build().get("test"),
        MeterProvider.builder.build().get("test")
      )

      service.placeOrder(Order("order-2", "Programming in Scala", 2, 5000L))

      val placeOrder = recorder.spans.find(_.name == "placeOrder")
      assertTrue(
        placeOrder.map(_.status) == Some(SpanStatus.Error("card declined")),
        placeOrder.flatMap(_.attributes.get(AttributeKey.string("order.id"))) == Some("order-2"),
        placeOrder.flatMap(_.attributes.get(AttributeKey.string("book.title"))) == Some("Programming in Scala")
      )
    },
    test("charge is a child of placeOrder") {
      val recorder = new SpanRecorder
      val tracer   = TracerProvider.builder.addSpanProcessor(recorder).build().get("test")
      val service  = new OrderService(
        PaymentGateway.approveAll,
        tracer,
        LoggerProvider.builder.build().get("test"),
        MeterProvider.builder.build().get("test")
      )

      service.placeOrder(Order("order-1", "Zionomicon", 1, 4500L))

      val charge = recorder.spans.find(_.name == "charge")
      assertTrue(
        recorder.tree == List("charge" -> Some("placeOrder"), "placeOrder" -> None),
        charge.flatMap(_.attributes.get(AttributeKey.long("amount.cents"))) == Some(4500L)
      )
    }
  )
}
