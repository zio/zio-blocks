package correlatelogsandtraces

import zio.blocks.telemetry._

/**
 * How to Correlate Logs and Traces Across Services: the one `ContextStorage`
 * that the tracer and the logger share, the tracer built on it, and the setup
 * that installs a logger on the same storage with JSON output on standard
 * output.
 */
object Telemetry {
  val storage: ContextStorage[Option[SpanContext]] =
    ContextStorage.create[Option[SpanContext]](None)

  val tracer: Tracer =
    TracerProvider.builder.setContextStorage(storage).build().get("shop")

  def installLogger(): Unit = {
    log.install(LoggerProvider.builder.setContextStorage(storage).build().get("shop"))
    log.writer(JsonLogFormatter, StdoutWriter)
  }
}
