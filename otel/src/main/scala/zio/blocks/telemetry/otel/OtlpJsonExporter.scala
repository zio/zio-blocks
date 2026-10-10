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

/**
 * Shared helpers for the OTLP JSON exporters (trace, log, metric).
 *
 * Exporters batch through [[BatchProcessor]] and POST OTLP JSON via
 * [[HttpSender]]. Note the flush asymmetry: the trace and log exporters buffer
 * through a batch processor (so `forceFlush` pushes queued items), while the
 * metric exporter is pull-based (`exportMetrics` collects on demand) and its
 * `forceFlush` is a no-op by design.
 */
private[otel] object OtlpJsonExporter {

  private[otel] def mergeHeaders(config: ExporterConfig): Map[String, String] = {
    val filtered = config.headers.filterNot { case (k, _) => k.equalsIgnoreCase("Content-Type") }
    filtered + ("Content-Type" -> "application/json")
  }
}
