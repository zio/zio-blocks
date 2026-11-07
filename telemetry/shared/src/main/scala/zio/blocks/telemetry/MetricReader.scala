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

/**
 * Reads the current data of a set of instruments. Every [[MeterProvider]] has
 * one, its `reader`, which covers the instruments its meters registered;
 * `metric.reader` is the reader of the installed provider. Nothing is pushed:
 * an exporter calls `collectAllMetrics()` on its own schedule.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/metric-data/ MetricData]]
 */
trait MetricReader extends AutoCloseable {

  /**
   * Returns the current data of every instrument the reader covers, one
   * [[MetricData]] per instrument. The data carry no instrument name, so to
   * know where an entry comes from, call `collect()` on the instrument itself.
   */
  def collectAllMetrics(): Seq[MetricData]

  /**
   * Releases the reader's resources; `MeterProvider#shutdown` calls it.
   */
  def shutdown(): Unit

  /** Exports buffered data now, for a reader that buffers. */
  def forceFlush(): Unit

  override def close(): Unit = shutdown()
}

/**
 * The reader that every [[MeterProvider]] creates. Each `collectAllMetrics()`
 * call runs `collect()` on every registered instrument, including the callbacks
 * of observable instruments, on the calling thread; an exception from a
 * callback propagates and no data is returned. Instruments with no data yet
 * appear with an empty point list. `shutdown` and `forceFlush` do nothing.
 */
final class MetricReaderImpl private[telemetry] (
  private val meterRegistry: MeterRegistry
) extends MetricReader {

  override def collectAllMetrics(): Seq[MetricData] =
    meterRegistry.collectAll()

  override def shutdown(): Unit = ()

  override def forceFlush(): Unit = ()
}
