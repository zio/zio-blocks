//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run Step6CaseClassConversionExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step6CaseClassConversionExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Convert Between a Named Tuple
 * and a Case Class
 *
 * Converts a reading to a case class through the plain tuple with `Into`, and a
 * case class back to a reading.
 */
object Step6CaseClassConversionExample {

  case class WeatherReading(city: String, tempC: Double)

  val toWeatherReading: Into[(String, Double), WeatherReading] =
    Into.derived[(String, Double), WeatherReading]

  val fromWeatherReading: Into[WeatherReading, (String, Double)] =
    Into.derived[WeatherReading, (String, Double)]

  def main(args: Array[String]): Unit = {
    val oslo: Reading = (city = "Oslo", tempC = 3.8)

    println(s"to case class: ${toWeatherReading.into(oslo.toTuple)}")

    val bergen: Either[SchemaError, Reading] = fromWeatherReading.into(WeatherReading("Bergen", 7.2))
    println(s"to named tuple: $bergen")
    println(s"city: ${bergen.map(_.city)}")
  }
}
