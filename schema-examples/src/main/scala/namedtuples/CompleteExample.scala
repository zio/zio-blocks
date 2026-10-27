//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run CompleteExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.CompleteExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Complete Example
 *
 * Decodes a feed of station reports, reports a damaged one, corrects every
 * temperature with a lens, converts the readings to a case class with `Into`
 * and encodes a daily report.
 */
object CompleteExample {

  case class WeatherReading(city: String, tempC: Double)

  case class DailyReport(day: String, readings: List[(city: String, tempC: Double)])

  object DailyReport {
    implicit val schema: Schema[DailyReport] = Schema.derived
  }

  object Stations extends CompanionOptics[Station] {
    val tempC: Lens[Station, Double] = $(_.reading.tempC)
  }

  val toWeatherReading: Into[(String, Double), WeatherReading] =
    Into.derived[(String, Double), WeatherReading]

  val feed: String =
    """[
      |  {"id":"OSL-1","reading":{"city":"Oslo","tempC":3.8},"humidity":81,"tags":["coast"]},
      |  {"id":"BGO-2","reading":{"city":"Bergen","tempC":7.2}},
      |  {"id":"TOS-1","reading":{"city":"Tromsø","tempC":-1.5},"humidity":74}
      |]""".stripMargin

  def main(args: Array[String]): Unit = {
    val stations: List[Station] = feed.fromJson[List[Station]].fold(e => sys.error(e.message), identity)
    println(s"Decoded ${stations.size} stations")

    val damaged = """{"id":"BGO-2","reading":{"city":"Bergen"}}""".fromJson[Station]
    damaged.left.foreach(error => println(s"Damaged report: ${error.message}"))

    // The stations' sensors read 0.5 degrees low
    val corrected = stations.map(Stations.tempC.modify(_, _ + 0.5))
    println(s"Corrected: ${corrected.map(s => s"${s.id} ${s.reading.tempC}").mkString(", ")}")

    val readings: List[Reading] = corrected.map(_.reading)
    val weather                 = readings.map(r => toWeatherReading.into(r.toTuple))
    println(s"Case classes: ${weather.collect { case Right(w) => w }}")

    println(s"Report: ${DailyReport("2026-10-27", readings).toJsonString}")
  }
}
