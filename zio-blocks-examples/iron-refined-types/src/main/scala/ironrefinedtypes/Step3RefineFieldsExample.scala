//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step3RefineFieldsExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step3RefineFieldsExample"

package ironrefinedtypes

import io.github.iltotore.iron._
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Refine the Fields
 * of a Case Class
 *
 * Builds a `Customer` with Iron-refined fields from literals and encodes it
 * with the JSON codec of its derived schema.
 */
object Step3RefineFieldsExample {
  def main(args: Array[String]): Unit = {
    val ada = Customer("Ada", "ada@example.com")

    println(s"customer: $ada")
    println(Customer.schema.jsonCodec.encodeToString(ada))
  }
}
