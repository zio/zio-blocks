---
id: metric-data
title: "MetricData"
description: "Sealed trait for aggregated metric snapshots in the ZIO Blocks Telemetry metrics pillar — three variants covering sum, histogram, and gauge instruments."
keywords:
  - "MetricData"
  - "SumData"
  - "HistogramData"
  - "GaugeData"
  - "Metrics Export"
  - "MetricReader"
---

`MetricData` is the sealed trait that represents an aggregated metric snapshot collected from a single instrument. Each call to `Counter#collect`, `Histogram#collect`, `Gauge#collect`, or `MetricReader#collectAllMetrics` returns `MetricData` values. There are three variants, one per instrument family: `SumData` for counters and up-down counters, `HistogramData` for histograms, and `GaugeData` for gauges.

```scala
sealed trait MetricData

object MetricData {
  final case class SumData(points: List[SumDataPoint])             extends MetricData
  final case class HistogramData(points: List[HistogramDataPoint]) extends MetricData
  final case class GaugeData(points: List[GaugeDataPoint])         extends MetricData
}
```

Each variant holds a list of data points, one per unique `Attributes` combination observed on the instrument since it was created.

## Usage

The following example collects all metrics from the global `MetricReader` and dispatches on each variant:

```scala
import zio.blocks.telemetry._

val c = metric.counter("requests")
c.add(3L, "method" -> "GET")

val h = metric.histogram("latency")
h.record(42.5, "endpoint" -> "/api")

val g = metric.gauge("queue.depth")
g.record(7.0)

metric.reader.collectAllMetrics().foreach {
  case MetricData.SumData(pts) =>
    pts.foreach(p => println(s"sum ${p.value} attrs=${p.attributes}"))

  case MetricData.HistogramData(pts) =>
    pts.foreach(p => println(s"hist count=${p.count} sum=${p.sum}"))

  case MetricData.GaugeData(pts) =>
    pts.foreach(p => println(s"gauge ${p.value} at ${p.timeNanos}"))
}
```
