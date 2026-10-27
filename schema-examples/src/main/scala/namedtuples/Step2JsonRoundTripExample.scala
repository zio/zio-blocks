//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run Step2JsonRoundTripExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step2JsonRoundTripExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Round-Trip a Named Tuple
 * Through JSON
 *
 * Encodes a reading, decodes it back, and prints the decode errors for a
 * missing field and a value of the wrong type.
 */
object Step2JsonRoundTripExample {
  def main(args: Array[String]): Unit = {
    val oslo: Reading = (city = "Oslo", tempC = 3.8)

    val osloJson = oslo.toJsonString
    println(osloJson)

    println(s"decoded: ${osloJson.fromJson[Reading]}")
    println(s"round trip equal: ${osloJson.fromJson[Reading] == Right(oslo)}")

    println("""{"city":"Oslo"}""".fromJson[Reading].left.map(_.message))
    println("""{"city":"Oslo","tempC":"mild"}""".fromJson[Reading].left.map(_.message))

    println(s"unknown field: ${"""{"city":"Oslo","tempC":3.8,"wind":4}""".fromJson[Reading]}")
  }
}
