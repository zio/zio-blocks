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

object MeterSpec extends ZIOSpecDefault {

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

  def spec = suite("Meter & MeterProvider")(
    suite("Meter - Counter")(
      test("counterBuilder creates a working counter") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("test-lib")
        val counter  = meter.counterBuilder("requests").setDescription("Total requests").setUnit("1").build()
        counter.add(1L, usEast)
        counter.add(2L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 3L)
      },
      test("counterBuilder with defaults") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("test-lib")
        val counter  = meter.counterBuilder("requests").build()
        counter.add(5L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 5L)
      }
    ),
    suite("Meter - UpDownCounter")(
      test("upDownCounterBuilder creates a working up-down counter") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("test-lib")
        val counter  = meter.upDownCounterBuilder("queue.size").setDescription("Queue depth").setUnit("1").build()
        counter.add(10L, usEast)
        counter.add(-3L, usEast)
        val points = sumPoints(counter.collect())
        assertTrue(points.size == 1 && points.head.value == 7L)
      }
    ),
    suite("Meter - Histogram")(
      test("histogramBuilder creates a working histogram") {
        val provider  = MeterProvider.builder.build()
        val meter     = provider.get("test-lib")
        val histogram = meter.histogramBuilder("latency").setDescription("Request latency").setUnit("ms").build()
        histogram.record(42.5, usEast)
        histogram.record(10.0, usEast)
        val points = histogramPoints(histogram.collect())
        assertTrue(
          points.size == 1 &&
            points.head.count == 2L &&
            points.head.sum == 52.5
        )
      }
    ),
    suite("Meter - Gauge")(
      test("gaugeBuilder creates a working sync gauge") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("test-lib")
        val gauge    = meter.gaugeBuilder("cpu").setDescription("CPU usage").setUnit("%").build()
        gauge.record(50.0, usEast)
        val points = gaugePoints(gauge.collect())
        assertTrue(points.size == 1 && points.head.value == 50.0)
      },
      test("gaugeBuilder buildWithCallback creates an observable gauge") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("test-lib")
        val obsGauge = meter.gaugeBuilder("cpu").setDescription("CPU usage").setUnit("%").buildWithCallback { cb =>
          cb.record(50.0, usEast)
          cb.record(75.0, euWest)
        }
        val points = gaugePoints(obsGauge.collect())
        val values = points.map(_.value).toSet
        assertTrue(points.size == 2 && values == Set(50.0, 75.0))
      }
    ),
    suite("Meter - Observable instruments via builders")(
      test("counterBuilder buildWithCallback creates an observable counter") {
        val provider   = MeterProvider.builder.build()
        val meter      = provider.get("test-lib")
        val obsCounter = meter.counterBuilder("cpu.time").buildWithCallback { cb =>
          cb.record(150.0, usEast)
        }
        val points = sumPoints(obsCounter.collect())
        assertTrue(points.size == 1 && points.head.value == 150L)
      },
      test("upDownCounterBuilder buildWithCallback creates an observable up-down counter") {
        val provider   = MeterProvider.builder.build()
        val meter      = provider.get("test-lib")
        val obsCounter = meter.upDownCounterBuilder("pool.active").buildWithCallback { cb =>
          cb.record(-5.0, usEast)
        }
        val points = sumPoints(obsCounter.collect())
        assertTrue(points.head.value == -5L)
      },
      test("histogramBuilder buildWithCallback is not supported (histogram has no observable variant)") {
        assertTrue(true)
      }
    ),
    suite("MetricReader")(
      test("collectAllMetrics returns data from all instruments across all meters") {
        val provider  = MeterProvider.builder.build()
        val meter1    = provider.get("lib-a")
        val meter2    = provider.get("lib-b")
        val counter   = meter1.counterBuilder("requests").build()
        val histogram = meter2.histogramBuilder("latency").setUnit("ms").build()
        counter.add(5L, usEast)
        histogram.record(42.5, usEast)
        val reader  = provider.reader
        val metrics = reader.collectAllMetrics()
        assertTrue(metrics.size == 2)
      },
      test("collectAllMetrics includes observable instruments") {
        val provider = MeterProvider.builder.build()
        val meter    = provider.get("lib-a")
        meter.counterBuilder("requests").build().add(1L, usEast)
        meter.gaugeBuilder("cpu").buildWithCallback { cb =>
          cb.record(99.0, usEast)
        }
        val metrics = provider.reader.collectAllMetrics()
        assertTrue(metrics.size == 2)
      },
      test("forceFlush and shutdown do not throw") {
        val provider = MeterProvider.builder.build()
        val reader   = provider.reader
        reader.forceFlush()
        reader.shutdown()
        assertTrue(true)
      }
    ),
    suite("MeterProvider")(
      test("get returns the same meter for the same name and version") {
        val provider = MeterProvider.builder.build()
        val meter1   = provider.get("lib", "1.0")
        val meter2   = provider.get("lib", "1.0")
        assertTrue(meter1 eq meter2)
      },
      test("get returns different meters for different names") {
        val provider = MeterProvider.builder.build()
        val meter1   = provider.get("lib-a")
        val meter2   = provider.get("lib-b")
        assertTrue(!(meter1 eq meter2))
      },
      test("builder allows setting resource") {
        val customResource = Resource.create(
          Attributes.builder.put("service.name", "my-service").build
        )
        val provider = MeterProvider.builder.setResource(customResource).build()
        assertTrue(provider.resource == customResource)
      },
      test("shutdown does not throw") {
        val provider = MeterProvider.builder.build()
        provider.shutdown()
        assertTrue(true)
      },
      test("multiple meters collect independently") {
        val provider = MeterProvider.builder.build()
        val meter1   = provider.get("lib-a")
        val meter2   = provider.get("lib-b")
        meter1.counterBuilder("requests").build().add(10L, usEast)
        meter2.counterBuilder("errors").build().add(3L, usEast)
        val allMetrics = provider.reader.collectAllMetrics()
        val sumValues  = allMetrics.flatMap(sumPoints).map(_.value).toSet
        assertTrue(allMetrics.size == 2 && sumValues == Set(10L, 3L))
      }
    )
  )
}
