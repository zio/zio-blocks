//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run Step1DeriveSchemaExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step1DeriveSchemaExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Derive a Schema for a Named
 * Tuple
 *
 * Reads the field names of the derived `Reading` schema and prints a value with
 * its labels.
 */
object Step1DeriveSchemaExample {
  def main(args: Array[String]): Unit = {
    println(s"fields: ${Schema[Reading].reflect.asRecord.map(_.fields.map(_.name))}")

    val oslo: Reading = (city = "Oslo", tempC = 3.8)

    println(oslo.show)
  }
}
