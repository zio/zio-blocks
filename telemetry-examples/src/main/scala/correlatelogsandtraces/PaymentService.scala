package correlatelogsandtraces

import java.util.concurrent.{CompletableFuture, Executors}
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._
import zio.http._

/**
 * How to Correlate Logs and Traces Across Services: the payment service, which
 * continues the caller's trace, and the stand-in for the network between the
 * two services.
 */
object PaymentService {
  def handle(request: Request): Response = {
    val parent = W3CTraceContextPropagator.extract(request, HttpGlue.getHeader)
    Telemetry.storage.scoped(parent) {
      Telemetry.tracer.span("payment.authorize", SpanKind.Server) { _ =>
        log.annotated("service.name" -> "payment") {
          log.info("Card charged")
        }
        Response.text("charged")
      }
    }
  }
}

// Stand-in for the network: the payment service runs on its own thread,
// so the only thing it receives from checkout is the request.
object Network {
  private val paymentThread = Executors.newSingleThreadExecutor()

  def send(request: Request): Response =
    CompletableFuture.supplyAsync(() => PaymentService.handle(request), paymentThread).join()

  def shutdown(): Unit = paymentThread.shutdown()
}
