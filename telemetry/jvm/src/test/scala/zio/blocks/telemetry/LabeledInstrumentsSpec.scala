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

import zio.test._

import java.util.UUID

object LabeledInstrumentsSpec extends ZIOSpecDefault {

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

  private def throwsIllegalArgument(body: => Unit): Boolean =
    try {
      body
      false
    } catch {
      case _: IllegalArgumentException => true
    }

  def spec = suite("LabeledInstruments")(
    suite("LabeledCounter")(
      test("add records labeled values and collect returns sum data") {
        val meter   = MeterProvider.builder.build().get("labeled-counter-add")
        val counter = meter.labeledCounter("requests", "region", "status")

        counter.add(2L, "us-east-1", 200)
        counter.add(3L, "us-east-1", 200)

        val points = sumPoints(counter.collect())

        assertTrue(
          points.size == 1,
          points.head.value == 5L,
          points.head.attributes.get(AttributeKey.string("region")).contains("us-east-1"),
          points.head.attributes.get(AttributeKey.long("status")).contains(200L)
        )
      },
      test("bind returns a BoundCounter and updates collected data") {
        val meter      = MeterProvider.builder.build().get("labeled-counter-bind")
        val counter    = meter.labeledCounter("requests", "region", "status")
        val bound      = counter.bind("eu-west-1", 201)
        val boundValue = bound.asInstanceOf[Any]

        bound.add(4L)
        bound.add(1L)

        val points = sumPoints(counter.collect())

        assertTrue(
          boundValue.isInstanceOf[BoundCounter],
          points.size == 1,
          points.head.value == 5L,
          points.head.attributes.get(AttributeKey.string("region")).contains("eu-west-1"),
          points.head.attributes.get(AttributeKey.long("status")).contains(201L)
        )
      },
      test("add throws IllegalArgumentException on arity mismatch") {
        val meter   = MeterProvider.builder.build().get("labeled-counter-arity-add")
        val counter = meter.labeledCounter("requests", "region", "status")

        assertTrue(throwsIllegalArgument(counter.add(1L, "only-one-label")))
      },
      test("bind throws IllegalArgumentException on arity mismatch") {
        val meter   = MeterProvider.builder.build().get("labeled-counter-arity-bind")
        val counter = meter.labeledCounter("requests", "region", "status")

        assertTrue(throwsIllegalArgument(counter.bind("only-one-label")))
      }
    ),
    suite("LabeledHistogram")(
      test("record stores labeled values and collect returns histogram data") {
        val meter     = MeterProvider.builder.build().get("labeled-histogram-record")
        val histogram = meter.labeledHistogram("latency", "region", "status")

        histogram.record(5.0, "us-east-1", 200)
        histogram.record(10.0, "us-east-1", 200)

        val points = histogramPoints(histogram.collect())

        assertTrue(
          points.size == 1,
          points.head.count == 2L,
          points.head.sum == 15.0,
          points.head.min == 5.0,
          points.head.max == 10.0,
          points.head.attributes.get(AttributeKey.string("region")).contains("us-east-1"),
          points.head.attributes.get(AttributeKey.long("status")).contains(200L)
        )
      },
      test("bind returns a BoundHistogram and updates collected data") {
        val meter          = MeterProvider.builder.build().get("labeled-histogram-bind")
        val histogram      = meter.labeledHistogram("latency", "region", "status")
        val bound          = histogram.bind("eu-west-1", 201)
        val boundHistogram = bound.asInstanceOf[Any]

        bound.record(7.5)
        bound.record(12.5)

        val points = histogramPoints(histogram.collect())

        assertTrue(
          boundHistogram.isInstanceOf[BoundHistogram],
          points.size == 1,
          points.head.count == 2L,
          points.head.sum == 20.0,
          points.head.min == 7.5,
          points.head.max == 12.5,
          points.head.attributes.get(AttributeKey.string("region")).contains("eu-west-1"),
          points.head.attributes.get(AttributeKey.long("status")).contains(201L)
        )
      },
      test("record throws IllegalArgumentException on arity mismatch") {
        val meter     = MeterProvider.builder.build().get("labeled-histogram-arity-record")
        val histogram = meter.labeledHistogram("latency", "region", "status")

        assertTrue(throwsIllegalArgument(histogram.record(1.0, "only-one-label")))
      },
      test("bind throws IllegalArgumentException on arity mismatch") {
        val meter     = MeterProvider.builder.build().get("labeled-histogram-arity-bind")
        val histogram = meter.labeledHistogram("latency", "region", "status")

        assertTrue(throwsIllegalArgument(histogram.bind("only-one-label")))
      }
    ),
    suite("LabeledGauge")(
      test("record stores labeled values and collect returns gauge data") {
        val meter = MeterProvider.builder.build().get("labeled-gauge-record")
        val gauge = meter.labeledGauge("temperature", "region", "active")

        gauge.record(20.5, "us-east-1", true)
        gauge.record(21.5, "us-east-1", true)

        val points = gaugePoints(gauge.collect())

        assertTrue(
          points.size == 1,
          points.head.value == 21.5,
          points.head.attributes.get(AttributeKey.string("region")).contains("us-east-1"),
          points.head.attributes.get(AttributeKey.boolean("active")).contains(true)
        )
      },
      test("bind returns a BoundGauge and updates collected data") {
        val meter      = MeterProvider.builder.build().get("labeled-gauge-bind")
        val gauge      = meter.labeledGauge("temperature", "region", "active")
        val bound      = gauge.bind("eu-west-1", false)
        val boundGauge = bound.asInstanceOf[Any]

        bound.record(18.0)
        bound.record(19.5)

        val points = gaugePoints(gauge.collect())

        assertTrue(
          boundGauge.isInstanceOf[BoundGauge],
          points.size == 1,
          points.head.value == 19.5,
          points.head.attributes.get(AttributeKey.string("region")).contains("eu-west-1"),
          points.head.attributes.get(AttributeKey.boolean("active")).contains(false)
        )
      },
      test("record throws IllegalArgumentException on arity mismatch") {
        val meter = MeterProvider.builder.build().get("labeled-gauge-arity-record")
        val gauge = meter.labeledGauge("temperature", "region", "active")

        assertTrue(throwsIllegalArgument(gauge.record(1.0, "only-one-label")))
      },
      test("bind throws IllegalArgumentException on arity mismatch") {
        val meter = MeterProvider.builder.build().get("labeled-gauge-arity-bind")
        val gauge = meter.labeledGauge("temperature", "region", "active")

        assertTrue(throwsIllegalArgument(gauge.bind("only-one-label")))
      }
    ),
    suite("LabeledInstrumentsHelper")(
      test("buildAttributes handles all supported value types") {
        val uuid       = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val attributes = LabeledInstrumentsHelper.buildAttributes(
          Array("string", "long", "int", "double", "boolean", "other"),
          Seq[Any]("value", 42L, 7, 3.14, true, uuid)
        )

        assertTrue(
          attributes.get(AttributeKey.string("string")).contains("value"),
          attributes.get(AttributeKey.long("long")).contains(42L),
          attributes.get(AttributeKey.long("int")).contains(7L),
          attributes.get(AttributeKey.double("double")).contains(3.14),
          attributes.get(AttributeKey.boolean("boolean")).contains(true),
          attributes.get(AttributeKey.string("other")).contains(uuid.toString)
        )
      }
    ),
    suite("Meter factory methods")(
      test("labeledCounter creates a labeled counter with declared labels") {
        val meter   = MeterProvider.builder.build().get("meter-factory-counter")
        val counter = meter.labeledCounter("requests", "region", "status")

        assertTrue(
          counter.isInstanceOf[LabeledCounter],
          counter.labelNames.sameElements(Array("region", "status"))
        )
      },
      test("labeledHistogram creates a labeled histogram with declared labels") {
        val meter     = MeterProvider.builder.build().get("meter-factory-histogram")
        val histogram = meter.labeledHistogram("latency", "region", "status")

        assertTrue(
          histogram.isInstanceOf[LabeledHistogram],
          histogram.labelNames.sameElements(Array("region", "status"))
        )
      },
      test("labeledGauge creates a labeled gauge with declared labels") {
        val meter = MeterProvider.builder.build().get("meter-factory-gauge")
        val gauge = meter.labeledGauge("temperature", "region", "active")

        assertTrue(
          gauge.isInstanceOf[LabeledGauge],
          gauge.labelNames.sameElements(Array("region", "active"))
        )
      }
    )
  ) @@ TestAspect.sequential
}
