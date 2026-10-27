//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
// Run from this folder:      scala-cli run Step4MoreThan22FieldsExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step4MoreThan22FieldsExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Go Past 22 Fields
 *
 * Derives a schema for a 24-field named tuple and round-trips it through JSON.
 */
object Step4MoreThan22FieldsExample {

  type HourlyTemps = (
    h00: Double,
    h01: Double,
    h02: Double,
    h03: Double,
    h04: Double,
    h05: Double,
    h06: Double,
    h07: Double,
    h08: Double,
    h09: Double,
    h10: Double,
    h11: Double,
    h12: Double,
    h13: Double,
    h14: Double,
    h15: Double,
    h16: Double,
    h17: Double,
    h18: Double,
    h19: Double,
    h20: Double,
    h21: Double,
    h22: Double,
    h23: Double
  )

  implicit val hourlySchema: Schema[HourlyTemps] = Schema.derived

  def main(args: Array[String]): Unit = {
    val osloDay: HourlyTemps = (
      h00 = 1.0,
      h01 = 0.8,
      h02 = 0.5,
      h03 = 0.3,
      h04 = 0.2,
      h05 = 0.4,
      h06 = 1.1,
      h07 = 2.0,
      h08 = 3.2,
      h09 = 4.5,
      h10 = 5.6,
      h11 = 6.3,
      h12 = 6.9,
      h13 = 7.1,
      h14 = 7.0,
      h15 = 6.4,
      h16 = 5.5,
      h17 = 4.6,
      h18 = 3.8,
      h19 = 3.1,
      h20 = 2.6,
      h21 = 2.2,
      h22 = 1.9,
      h23 = 1.5
    )

    println(s"fields: ${Schema[HourlyTemps].reflect.asRecord.map(_.fields.size)}")

    val osloDayJson = osloDay.toJsonString
    println(s"round trip equal: ${osloDayJson.fromJson[HourlyTemps] == Right(osloDay)}")
    println(osloDayJson)
  }
}
