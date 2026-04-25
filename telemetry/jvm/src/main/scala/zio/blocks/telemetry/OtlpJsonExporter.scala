/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.telemetry

import java.time.Duration

final case class ExporterConfig(
  endpoint: String = "http://localhost:4318",
  headers: Map[String, String] = Map.empty,
  timeout: Duration = Duration.ofSeconds(30),
  maxQueueSize: Int = 2048,
  maxBatchSize: Int = 512,
  flushIntervalMillis: Long = 5000
)

object OtlpJsonExporter {

  private val retryableStatusCodes: Set[Int] = Set(429, 502, 503, 504)

  def mapResponse(response: HttpResponse): ExportResult =
    if (response.statusCode >= 200 && response.statusCode < 300) ExportResult.Success
    else if (retryableStatusCodes.contains(response.statusCode))
      ExportResult.Failure(retryable = true, message = "HTTP " + response.statusCode)
    else ExportResult.Failure(retryable = false, message = "HTTP " + response.statusCode)

  private[telemetry] def mergeHeaders(config: ExporterConfig): Map[String, String] =
    config.headers + ("Content-Type" -> "application/json")
}

final class OtlpJsonTraceExporter(
  config: ExporterConfig,
  resource: Resource,
  scope: InstrumentationScope,
  httpSender: HttpSender,
  platformExecutor: PlatformExecutor
) extends SpanProcessor {

  private val url     = config.endpoint + "/v1/traces"
  private val headers = OtlpJsonExporter.mergeHeaders(config)

  private val batchProcessor: BatchProcessor[SpanData] = new BatchProcessor[SpanData](
    exportFn = { batch =>
      val body     = OtlpJsonEncoder.encodeTraces(batch, resource, scope)
      val response = httpSender.send(url, headers, body)
      OtlpJsonExporter.mapResponse(response)
    },
    executor = platformExecutor.executor,
    maxQueueSize = config.maxQueueSize,
    maxBatchSize = config.maxBatchSize,
    flushIntervalMillis = config.flushIntervalMillis
  )

  def onStart(span: Span): Unit = ()

  def onEnd(spanData: SpanData): Unit =
    batchProcessor.enqueue(spanData)

  def shutdown(): Unit = {
    batchProcessor.shutdown()
    httpSender.shutdown()
  }

  def forceFlush(): Unit =
    batchProcessor.forceFlush()
}

final class OtlpJsonLogExporter(
  config: ExporterConfig,
  resource: Resource,
  scope: InstrumentationScope,
  httpSender: HttpSender,
  platformExecutor: PlatformExecutor
) extends LogRecordProcessor {

  private val url     = config.endpoint + "/v1/logs"
  private val headers = OtlpJsonExporter.mergeHeaders(config)

  private val batchProcessor: BatchProcessor[LogRecord] = new BatchProcessor[LogRecord](
    exportFn = { batch =>
      val body     = OtlpJsonEncoder.encodeLogs(batch, resource, scope)
      val response = httpSender.send(url, headers, body)
      OtlpJsonExporter.mapResponse(response)
    },
    executor = platformExecutor.executor,
    maxQueueSize = config.maxQueueSize,
    maxBatchSize = config.maxBatchSize,
    flushIntervalMillis = config.flushIntervalMillis
  )

  def onEmit(logRecord: LogRecord): Unit =
    batchProcessor.enqueue(logRecord)

  def shutdown(): Unit = {
    batchProcessor.shutdown()
    httpSender.shutdown()
  }

  def forceFlush(): Unit =
    batchProcessor.forceFlush()
}

final class OtlpJsonMetricExporter(
  config: ExporterConfig,
  resource: Resource,
  scope: InstrumentationScope,
  httpSender: HttpSender,
  collectFn: () => Seq[NamedMetric]
) {

  private val url     = config.endpoint + "/v1/metrics"
  private val headers = OtlpJsonExporter.mergeHeaders(config)

  def exportMetrics(): ExportResult = {
    val metrics = collectFn()
    if (metrics.isEmpty) ExportResult.Success
    else {
      val body     = OtlpJsonEncoder.encodeMetrics(metrics, resource, scope)
      val response = httpSender.send(url, headers, body)
      OtlpJsonExporter.mapResponse(response)
    }
  }

  def shutdown(): Unit =
    httpSender.shutdown()

  def forceFlush(): Unit = ()
}
