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

import java.time.Duration

/**
 * The settings of an OTLP/HTTP JSON export: where to send, which headers to
 * add, and how to batch. Every field has a default, so `ExporterConfig()`
 * points at a collector on `localhost:4318`, the standard OTLP/HTTP port.
 *
 * The module's exporters that read it are internal, so it serves as the one
 * place for these settings in an export loop of your own, built from
 * [[OtlpJsonEncoder]], [[HttpSender]] and [[ExportResult]]. `toString` prints
 * the number of headers, not their values, so printing a config with `toString`
 * does not show a token; the `headers` field, and anything that reads the case
 * class's fields, still does.
 *
 * {{{
 * import java.time.Duration
 *
 * val config = ExporterConfig(
 *   endpoint = "https://otlp.example.com:4318",
 *   headers = Map("Authorization" -> "Bearer s3cr3t"),
 *   timeout = Duration.ofSeconds(10),
 *   flushIntervalMillis = 1000
 * )
 * val printed = config.toString
 * // ExporterConfig(endpoint=https://otlp.example.com:4318, headers=<redacted:1>, timeout=PT10S, maxQueueSize=2048, maxBatchSize=512, flushIntervalMillis=1000)
 * val tracesUrl = config.endpoint + "/v1/traces" // https://otlp.example.com:4318/v1/traces
 * }}}
 *
 * @param endpoint
 *   the collector's base URL, without a trailing slash: the exporters append
 *   `/v1/traces`, `/v1/logs` or `/v1/metrics`
 * @param headers
 *   HTTP headers sent with every export, such as an API key; the exporters
 *   replace any `Content-Type` entry with `application/json`
 * @param timeout
 *   not read by this module, since the exporters take an [[HttpSender]] built
 *   separately: pass it to `HttpSender.jdk`
 * @param maxQueueSize
 *   the most records waiting for export; one more drops the oldest, with a line
 *   on standard error
 * @param maxBatchSize
 *   the most records sent in one request; a flush sends as many requests as the
 *   queue needs
 * @param flushIntervalMillis
 *   the time between the periodic flushes of the trace and log exporters; the
 *   metric exporter exports only when asked
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 */
final case class ExporterConfig(
  endpoint: String = "http://localhost:4318",
  headers: Map[String, String] = Map.empty,
  timeout: Duration = Duration.ofSeconds(30),
  maxQueueSize: Int = 2048,
  maxBatchSize: Int = 512,
  flushIntervalMillis: Long = 5000
) {
  override def toString: String =
    s"ExporterConfig(endpoint=$endpoint, headers=<redacted:${headers.size}>, timeout=$timeout, maxQueueSize=$maxQueueSize, maxBatchSize=$maxBatchSize, flushIntervalMillis=$flushIntervalMillis)"
}
