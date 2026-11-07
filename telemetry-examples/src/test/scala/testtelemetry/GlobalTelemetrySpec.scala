package testtelemetry

import zio.blocks.telemetry._
import zio.test._

/**
 * How to Test Code That Logs, Traces and Records Metrics: code that uses the
 * global `log`, `trace` and `metric` objects.
 *
 * Every test resets the global span store, installs a recording logger and
 * reads the counter's change. The suite runs its tests one at a time, because
 * they share the global state.
 *
 * Run with: sbt "testOnly testtelemetry.GlobalTelemetrySpec"
 */
object GlobalTelemetrySpec extends ZIOSpecDefault {
  def spec = suite("Checkout (global telemetry)")(
    test("a declined payment is traced, logged and counted") {
      trace.clearSpans()
      val logs = new LogRecorder
      log.install(LoggerProvider.builder.addLogRecordProcessor(logs).build().get("test"))
      val failedBefore = Metrics.total(Checkout.ordersFailed)

      Checkout.placeOrder(
        Order("order-2", "Programming in Scala", 2, 5000L),
        PaymentGateway.declineAll("card declined")
      )

      assertTrue(
        trace.collectedSpans.map(s => s.name -> s.status) == List("checkout" -> SpanStatus.Error("card declined")),
        logs.records.map(r => r.severity -> r.body.value) == List(Severity.Error -> "Payment failed"),
        Metrics.total(Checkout.ordersFailed) - failedBefore == 1L
      )
    },
    test("an approved payment is traced and logged, and not counted as failed") {
      trace.clearSpans()
      val logs = new LogRecorder
      log.install(LoggerProvider.builder.addLogRecordProcessor(logs).build().get("test"))
      val failedBefore = Metrics.total(Checkout.ordersFailed)

      Checkout.placeOrder(Order("order-1", "Zionomicon", 1, 4500L), PaymentGateway.approveAll)

      assertTrue(
        trace.collectedSpans.map(s => s.name -> s.status) == List("checkout" -> SpanStatus.Ok),
        logs.records.map(r => r.severity -> r.body.value) == List(Severity.Info -> "Order placed"),
        Metrics.total(Checkout.ordersFailed) - failedBefore == 0L
      )
    }
  ) @@ TestAspect.sequential
}
