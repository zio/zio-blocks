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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

private[otel] final class OtlpJsonLogExporter(
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
      ExportResult.fromHttpResponse(response)
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
