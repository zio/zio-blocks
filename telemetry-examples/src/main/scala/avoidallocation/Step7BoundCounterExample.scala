//> using scala 3.9.0
//> using jvm 21
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
//> using options -experimental
//> using file Allocation.scala
// Run from this folder:        scala-cli run Step7BoundCounterExample.scala
// Or from telemetry-examples:  sbt "runMain avoidallocation.Step7BoundCounterExample"

package avoidallocation

import zio.blocks.telemetry._

/**
 * How to Avoid Allocation on Hot Paths — Pre-Bind Metric Labels Outside the
 * Loop
 *
 * Counts ticks with label pairs on every call, with a bound counter, with a
 * labeled counter and with its bound handle, and prints what each allocates per
 * tick.
 */
object Step7BoundCounterExample {

  val tickCount = 10000

  def main(args: Array[String]): Unit = {
    val meter        = MeterProvider.builder.build().get("feed")
    val ticksCounter = meter.counterBuilder("ticks").build()

    val acmeOnXnas: BoundCounter =
      ticksCounter.bind(Attributes.builder.put("symbol", "ACME").put("venue", "XNAS").build)

    ticksCounter.add(1L, "symbol" -> "ACME", "venue" -> "XNAS")
    acmeOnXnas.add(1L)

    val collected = ticksCounter.collect() match {
      case MetricData.SumData(points) => points.map(_.value)
      case _                          => Nil
    }
    println(s"collected: $collected")

    val pairBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) {
        ticksCounter.add(1L, "symbol" -> "ACME", "venue" -> "XNAS")
        i += 1
      }
    }

    val boundBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) {
        acmeOnXnas.add(1L)
        i += 1
      }
    }

    println(s"labels per call: ${pairBytes.toDouble / tickCount} bytes per tick")
    println(s"bound counter:   ${boundBytes.toDouble / tickCount} bytes per tick")

    val labeledTicks = meter.labeledCounter("ticks.labeled", "symbol", "venue")
    val acmeHandle   = labeledTicks.bind("ACME", "XNAS")

    val labeledBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) {
        labeledTicks.add(1L, "ACME", "XNAS")
        i += 1
      }
    }

    val handleBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) {
        acmeHandle.add(1L)
        i += 1
      }
    }

    println(s"LabeledCounter#add: ${labeledBytes.toDouble / tickCount} bytes per tick")
    println(s"bound handle:       ${handleBytes.toDouble / tickCount} bytes per tick")
  }
}
