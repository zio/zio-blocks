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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

final class Gauge private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
) {
  private val values = new ConcurrentHashMap[Attributes, AtomicLong]()

  def record(value: Double, attributes: Attributes): Unit = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    ref.set(java.lang.Double.doubleToRawLongBits(value))
  }

  def record(value: Double, attrs: (String, Any)*): Unit =
    if (attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var ref        = values.get(attributes)
      if (ref == null) {
        ref = new AtomicLong(0L)
        val existing = values.putIfAbsent(attributes, ref)
        if (existing != null) ref = existing
      }
      ref.set(java.lang.Double.doubleToRawLongBits(value))
    }

  def bind(attributes: Attributes): BoundGauge = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    new BoundGauge(ref)
  }

  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[GaugeDataPoint]()
    values.forEach { (attrs, ref) =>
      points.add(GaugeDataPoint(attrs, now, java.lang.Double.longBitsToDouble(ref.get())))
    }
    MetricData.GaugeData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object Gauge {
  def apply(name: String, description: String, unit: String): Gauge =
    new Gauge(name, description, unit)
}

final class BoundGauge private[telemetry] (private val ref: AtomicLong) {
  def record(value: Double): Unit = ref.set(java.lang.Double.doubleToRawLongBits(value))
}
