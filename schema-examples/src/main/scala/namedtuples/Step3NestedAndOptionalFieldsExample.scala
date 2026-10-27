//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run Step3NestedAndOptionalFieldsExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step3NestedAndOptionalFieldsExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Nest Named Tuples and Add
 * Optional Fields
 *
 * Encodes stations with a nested reading, an optional humidity and a list of
 * tags, decodes one with missing optional fields, and encodes a case class that
 * holds named tuples.
 */
object Step3NestedAndOptionalFieldsExample {

  case class DailyReport(day: String, readings: List[(city: String, tempC: Double)])

  object DailyReport {
    implicit val schema: Schema[DailyReport] = Schema.derived
  }

  def main(args: Array[String]): Unit = {
    val oslo: Reading = (city = "Oslo", tempC = 3.8)

    val osloStation: Station   = (id = "OSL-1", reading = oslo, humidity = Some(81), tags = List("coast"))
    val bergenStation: Station =
      (id = "BGO-2", reading = (city = "Bergen", tempC = 7.2), humidity = None, tags = Nil)

    println(osloStation.toJsonString)
    println(bergenStation.toJsonString)

    println(s"decoded: ${"""{"id":"BGO-2","reading":{"city":"Bergen","tempC":7.2}}""".fromJson[Station]}")

    println(DailyReport("2026-10-27", List(oslo, (city = "Bergen", tempC = 7.2))).toJsonString)
  }
}
