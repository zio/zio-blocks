package smalljsonbackendfromblocks

import zio.blocks.telemetry.{log, metric, trace, AttributeKey, Counter, MetricData}
import zio.http._

/**
 * Build a Small JSON Backend from Blocks — request telemetry
 *
 * Wraps any `Request => Response` function so that every request runs in its
 * own span, writes one structured log line, and adds one to a counter split by
 * status code.
 */
class RequestTelemetry {
  val requests: Counter = metric.counter("bookstore.http.requests")

  def wrap(app: Request => Response): Request => Response = request =>
    trace.span(s"${request.method} ${request.path}") { _ =>
      val response = app(request)
      requests.add(1L, "status" -> response.status.code)
      log.info(
        "Handled request",
        "method" -> request.method.toString,
        "path"   -> request.path.toString,
        "status" -> response.status.code
      )
      response
    }

  def countsByStatus(): List[(Long, Long)] =
    requests.collect() match {
      case MetricData.SumData(points) =>
        points.map(p => p.attributes.get(RequestTelemetry.status).getOrElse(0L) -> p.value).sorted
      case _ => Nil
    }
}

object RequestTelemetry {
  val status: AttributeKey[Long] = AttributeKey.long("status")
}
