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
import java.util.concurrent.atomic.LongAdder

final class UpDownCounter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
) {
  private val adders = new ConcurrentHashMap[Attributes, LongAdder]()

  def add(value: Long, attributes: Attributes): Unit = {
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    adder.add(value)
  }

  def add(value: Long, attrs: (String, Any)*): Unit =
    if (attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var adder      = adders.get(attributes)
      if (adder == null) {
        adder = new LongAdder()
        val existing = adders.putIfAbsent(attributes, adder)
        if (existing != null) adder = existing
      }
      adder.add(value)
    }

  def bind(attributes: Attributes): BoundUpDownCounter = {
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    new BoundUpDownCounter(adder)
  }

  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[SumDataPoint]()
    adders.forEach { (attrs, adder) =>
      points.add(SumDataPoint(attrs, 0L, now, adder.sum()))
    }
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object UpDownCounter {
  def apply(name: String, description: String, unit: String): UpDownCounter =
    new UpDownCounter(name, description, unit)
}

final class BoundUpDownCounter private[telemetry] (private val adder: LongAdder) {
  def add(value: Long): Unit = adder.add(value)
}
