package readandwritejsonlines

import java.nio.file.{Files, Path}

import zio.blocks.schema._

/** One reading from a sensor, one JSON object per line. */
final case class SensorReading(sensor: String, celsius: Double, sample: Long)

object SensorReading {
  implicit val schema: Schema[SensorReading] = Schema.derived
}

/**
 * A line that could not be decoded: its number (from 1), its text and the
 * reason.
 */
final case class BadLine(line: Long, text: String, reason: String)

/**
 * Sample data: six lines, two of them bad (line 3 has a string for `celsius`;
 * line 5 is cut off).
 */
object Sample {
  val text: String =
    """{"sensor":"boiler-7","celsius":21.5,"sample":1}
      |{"sensor":"boiler-7","celsius":22.0,"sample":2}
      |{"sensor":"boiler-7","celsius":"hot","sample":3}
      |{"sensor":"attic-2","celsius":14.25,"sample":4}
      |{"sensor":"attic-2","celsius":14.5,"sample":
      |{"sensor":"cellar-1","celsius":9.0,"sample":6}
      |""".stripMargin

  def writeTo(dir: Path): Path = {
    val file = dir.resolve("sensors.jsonl")
    Files.writeString(file, text)
    file
  }
}
