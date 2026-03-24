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

package zio.blocks.otel

/**
 * Reads and collects metric data from all registered instruments.
 */
trait MetricReader {

  /**
   * Collects metric data from all registered instruments across all meters.
   */
  def collectAllMetrics(): Seq[MetricData]

  /**
   * Shuts down the reader, releasing any resources.
   */
  def shutdown(): Unit

  /**
   * Forces a flush of any buffered metric data.
   */
  def forceFlush(): Unit
}

/**
 * Default MetricReader implementation backed by a MeterProvider's meter
 * registry.
 */
final class MetricReaderImpl private[otel] (
  private val meterRegistry: MeterRegistry
) extends MetricReader {

  override def collectAllMetrics(): Seq[MetricData] =
    meterRegistry.collectAll()

  override def shutdown(): Unit = ()

  override def forceFlush(): Unit = ()
}
