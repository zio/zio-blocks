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

import zio.test._

object MetricSpec extends ZIOSpecDefault {

  private val regionKey = AttributeKey.string("region")
  private val usEast    = Attributes.of(regionKey, "us-east-1")
  private val euWest    = Attributes.of(regionKey, "eu-west-1")

  private def sumPoints(data: MetricData): List[SumDataPoint] = data match {
    case MetricData.SumData(points)  => points
    case MetricData.HistogramData(_) => Nil
    case MetricData.GaugeData(_)     => Nil
  }

  private def histogramPoints(data: MetricData): List[HistogramDataPoint] = data match {
    case MetricData.SumData(_)            => Nil
    case MetricData.HistogramData(points) => points
    case MetricData.GaugeData(_)          => Nil
  }

  private def gaugePoints(data: MetricData): List[GaugeDataPoint] = data match {
    case MetricData.SumData(_)        => Nil
    case MetricData.HistogramData(_)  => Nil
    case MetricData.GaugeData(points) => points
  }

  def spec = suite("Metrics")(
    suite("Measurement")(
      test("stores value and attributes") {
        val m = Measurement(42.0, usEast)
        assertTrue(m.value == 42.0 && m.attributes.get(regionKey).contains("us-east-1"))
      },
      test("supports empty attributes") {
        val m = Measurement(0.0, Attributes.empty)
        assertTrue(m.value == 0.0 && m.attributes.isEmpty)
      }
    ),
    suite("MetricData")(
      test("SumData holds data points") {
        val dp     = SumDataPoint(usEast, 100L, 200L, 5L)
        val data   = MetricData.SumData(List(dp))
        val points = sumPoints(data)
        assertTrue(
          points.size == 1 &&
            points.head.value == 5L &&
            points.head.startTimeNanos == 100L &&
            points.head.timeNanos == 200L
        )
      },
      test("HistogramData holds data points") {
        val dp = HistogramDataPoint(
          usEast,
          100L,
          200L,
          count = 3L,
          sum = 15.0,
          min = 1.0,
          max = 10.0,
          bucketCounts = Array(1L, 1L, 1L, 0L),
          boundaries = Array(5.0, 10.0, 25.0)
        )
        val data   = MetricData.HistogramData(List(dp))
        val points = histogramPoints(data)
        assertTrue(
          points.size == 1 &&
            points.head.count == 3L &&
            points.head.sum == 15.0 &&
            points.head.min == 1.0 &&
            points.head.max == 10.0
        )
      },
      test("GaugeData holds data points") {
        val dp     = GaugeDataPoint(usEast, 200L, 42.5)
        val data   = MetricData.GaugeData(List(dp))
        val points = gaugePoints(data)
        assertTrue(points.size == 1 && points.head.value == 42.5 && points.head.timeNanos == 200L)
      }
    ),
    suite("Counter")(
      test("add increments for a given attribute set") {
        val counter = Counter("requests", "Total requests", "1")
        counter.add(5L, usEast)
        counter.add(3L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 8L)
      },
      test("tracks multiple attribute sets independently") {
        val counter = Counter("requests", "Total requests", "1")
        counter.add(5L, usEast)
        counter.add(3L, euWest)
        val points = sumPoints(counter.collect())
        val values = points.map(_.value).toSet
        assertTrue(points.size == 2 && values == Set(5L, 3L))
      },
      test("add with zero is a no-op") {
        val counter = Counter("requests", "Total requests", "1")
        counter.add(0L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 0L)
      },
      test("add with negative value is silently ignored") {
        val counter = Counter("requests", "Total requests", "1")
        counter.add(5L, usEast)
        counter.add(-3L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 5L)
      },
      test("concurrent adds are safe") {
        val counter = Counter("concurrent", "Concurrent counter", "1")
        val threads = (1 to 10).map { _ =>
          new Thread(() => {
            var i = 0
            while (i < 1000) {
              counter.add(1L, usEast)
              i += 1
            }
          })
        }
        threads.foreach(_.start())
        threads.foreach(_.join())
        val points = sumPoints(counter.collect())
        assertTrue(points.head.value == 10000L)
      }
    ),
    suite("UpDownCounter")(
      test("allows positive and negative additions") {
        val counter = UpDownCounter("queue.size", "Queue depth", "1")
        counter.add(10L, usEast)
        counter.add(-3L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 7L)
      },
      test("can go negative") {
        val counter = UpDownCounter("balance", "Account balance", "1")
        counter.add(-5L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.head.value == -5L)
      },
      test("tracks multiple attribute sets independently") {
        val counter = UpDownCounter("queue.size", "Queue depth", "1")
        counter.add(10L, usEast)
        counter.add(20L, euWest)
        counter.add(-3L, usEast)
        val points = sumPoints(counter.collect())
        val values = points.map(_.value).toSet
        assertTrue(points.size == 2 && values == Set(7L, 20L))
      }
    ),
    suite("Histogram")(
      test("records values and computes statistics") {
        val histogram = Histogram("latency", "Request latency", "ms")
        histogram.record(5.0, usEast)
        histogram.record(10.0, usEast)
        histogram.record(25.0, usEast)
        val points = histogramPoints(histogram.collect())
        assertTrue(
          points.size == 1 &&
            points.head.count == 3L &&
            points.head.sum == 40.0 &&
            points.head.min == 5.0 &&
            points.head.max == 25.0
        )
      },
      test("distributes values into buckets") {
        val histogram = Histogram("latency", "Request latency", "ms", Array(5.0, 10.0, 25.0))
        histogram.record(3.0, usEast)
        histogram.record(7.0, usEast)
        histogram.record(20.0, usEast)
        histogram.record(100.0, usEast)
        val points = histogramPoints(histogram.collect())
        val bc     = points.head.bucketCounts
        assertTrue(
          points.head.count == 4L &&
            bc(0) == 1L &&
            bc(1) == 1L &&
            bc(2) == 1L &&
            bc(3) == 1L
        )
      },
      test("uses default boundaries when none provided") {
        val histogram = Histogram("latency", "Request latency", "ms")
        histogram.record(5.0, usEast)
        val points = histogramPoints(histogram.collect())
        assertTrue(points.head.boundaries.nonEmpty)
      },
      test("tracks multiple attribute sets independently") {
        val histogram = Histogram("latency", "Request latency", "ms")
        histogram.record(5.0, usEast)
        histogram.record(10.0, euWest)
        val points = histogramPoints(histogram.collect())
        assertTrue(points.size == 2)
      },
      test("concurrent records are safe") {
        val histogram = Histogram("concurrent", "Concurrent histogram", "ms")
        val threads   = (1 to 10).map { _ =>
          new Thread(() => {
            var i = 0
            while (i < 1000) {
              histogram.record(1.0, usEast)
              i += 1
            }
          })
        }
        threads.foreach(_.start())
        threads.foreach(_.join())
        val points = histogramPoints(histogram.collect())
        assertTrue(points.head.count == 10000L)
      }
    ),
    suite("Gauge")(
      test("records the latest value") {
        val gauge = Gauge("temperature", "Current temperature", "celsius")
        gauge.record(20.0, usEast)
        gauge.record(25.0, usEast)
        val points = gaugePoints(gauge.collect())
        assertTrue(points.size == 1 && points.head.value == 25.0)
      },
      test("tracks multiple attribute sets independently") {
        val gauge = Gauge("temperature", "Current temperature", "celsius")
        gauge.record(20.0, usEast)
        gauge.record(30.0, euWest)
        val points = gaugePoints(gauge.collect())
        val values = points.map(_.value).toSet
        assertTrue(points.size == 2 && values == Set(20.0, 30.0))
      },
      test("concurrent records settle on one value") {
        val gauge   = Gauge("concurrent", "Concurrent gauge", "1")
        val threads = (1 to 10).map { i =>
          new Thread(() => gauge.record(i.toDouble, usEast))
        }
        threads.foreach(_.start())
        threads.foreach(_.join())
        val points = gaugePoints(gauge.collect())
        assertTrue(points.size == 1 && points.head.value > 0.0)
      }
    ),
    suite("ObservableCounter")(
      test("collects via callback") {
        val counter = ObservableCounter("system.cpu.time", "CPU time", "s") { cb =>
          cb.record(150.0, usEast)
        }
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 150L)
      },
      test("callback invoked on each collect") {
        var callCount = 0
        val counter   = ObservableCounter("invocations", "Invocation count", "1") { cb =>
          callCount += 1
          cb.record(callCount.toDouble, usEast)
        }
        counter.collect()
        counter.collect()
        val points = sumPoints(counter.collect())
        assertTrue(callCount == 3 && points.head.value == 3L)
      }
    ),
    suite("ObservableUpDownCounter")(
      test("collects via callback with negative values") {
        val counter = ObservableUpDownCounter("pool.active", "Active pool connections", "1") { cb =>
          cb.record(-5.0, usEast)
        }
        val points = sumPoints(counter.collect())
        assertTrue(points.head.value == -5L)
      }
    ),
    suite("ObservableGauge")(
      test("collects via callback") {
        val gauge = ObservableGauge("system.memory.usage", "Memory usage", "By") { cb =>
          cb.record(1024.0, usEast)
          cb.record(2048.0, euWest)
        }
        val points = gaugePoints(gauge.collect())
        val values = points.map(_.value).toSet
        assertTrue(points.size == 2 && values == Set(1024.0, 2048.0))
      },
      test("each collect invokes callback fresh") {
        var temp  = 20.0
        val gauge = ObservableGauge("temperature", "Current temp", "celsius") { cb =>
          cb.record(temp, usEast)
        }
        gauge.collect()
        temp = 25.0
        val points = gaugePoints(gauge.collect())
        assertTrue(points.head.value == 25.0)
      }
    ),
    suite("AttributeValue type coverage in SyncInstruments")(
      test("covers all AttributeValue types in Counter") {
        val counter = Counter("test", "Test counter", "1")
        val attrs   = Attributes.builder
          .put("str", "value")
          .put("bool", true)
          .put("long", 42L)
          .put("double", 3.14)
          .put(AttributeKey.stringSeq("str_seq"), Seq("a", "b"))
          .put(AttributeKey.longSeq("long_seq"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("double_seq"), Seq(1.1, 2.2))
          .put(AttributeKey.booleanSeq("bool_seq"), Seq(true, false))
          .build
        counter.add(1L, attrs)
        val points = sumPoints(counter.collect())
        assertTrue(
          points.size == 1 &&
            points.head.attributes.get(AttributeKey.string("str")).contains("value") &&
            points.head.attributes.get(AttributeKey.string("bool")).isDefined
        )
      },
      test("covers all AttributeValue types in Histogram") {
        val histogram = Histogram("test", "Test histogram", "1")
        val attrs     = Attributes.builder
          .put("str", "value")
          .put("bool", true)
          .put("long", 42L)
          .put("double", 3.14)
          .put(AttributeKey.stringSeq("str_seq"), Seq("a", "b"))
          .put(AttributeKey.longSeq("long_seq"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("double_seq"), Seq(1.1, 2.2))
          .put(AttributeKey.booleanSeq("bool_seq"), Seq(true, false))
          .build
        histogram.record(5.0, attrs)
        val points = histogramPoints(histogram.collect())
        assertTrue(
          points.size == 1 &&
            points.head.count == 1L &&
            points.head.sum == 5.0
        )
      },
      test("covers all AttributeValue types in Gauge") {
        val gauge = Gauge("test", "Test gauge", "1")
        val attrs = Attributes.builder
          .put("str", "value")
          .put("bool", true)
          .put("long", 42L)
          .put("double", 3.14)
          .put(AttributeKey.stringSeq("str_seq"), Seq("a", "b"))
          .put(AttributeKey.longSeq("long_seq"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("double_seq"), Seq(1.1, 2.2))
          .put(AttributeKey.booleanSeq("bool_seq"), Seq(true, false))
          .build
        gauge.record(42.0, attrs)
        val points = gaugePoints(gauge.collect())
        assertTrue(
          points.size == 1 &&
            points.head.value == 42.0
        )
      },
      test("covers all AttributeValue types in UpDownCounter") {
        val counter = UpDownCounter("test", "Test counter", "1")
        val attrs   = Attributes.builder
          .put("str", "value")
          .put("bool", true)
          .put("long", 42L)
          .put("double", 3.14)
          .put(AttributeKey.stringSeq("str_seq"), Seq("a", "b"))
          .put(AttributeKey.longSeq("long_seq"), Seq(1L, 2L))
          .put(AttributeKey.doubleSeq("double_seq"), Seq(1.1, 2.2))
          .put(AttributeKey.booleanSeq("bool_seq"), Seq(true, false))
          .build
        counter.add(-5L, attrs)
        val points = sumPoints(counter.collect())
        assertTrue(
          points.size == 1 &&
            points.head.value == -5L
        )
      }
    )
  )
}
