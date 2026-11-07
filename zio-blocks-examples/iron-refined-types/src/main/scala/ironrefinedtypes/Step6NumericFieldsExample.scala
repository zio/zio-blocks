//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step6NumericFieldsExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step6NumericFieldsExample"

package ironrefinedtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Refine Numeric
 * Fields with a Validated Newtype
 *
 * Decodes order lines that combine an Iron `Sku` with a validated `Quantity`
 * newtype.
 */
object Step6NumericFieldsExample {
  def main(args: Array[String]): Unit = {
    println(Line.schema.jsonCodec.decode("""{"sku":"KEY-0042","quantity":2}"""))
    println(Line.schema.jsonCodec.decode("""{"sku":"KEY-0042","quantity":0}""").left.map(_.message))
  }
}
