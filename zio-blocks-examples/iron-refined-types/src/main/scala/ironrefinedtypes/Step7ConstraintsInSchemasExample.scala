//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step7ConstraintsInSchemasExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step7ConstraintsInSchemasExample"

package ironrefinedtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — See Where the
 * Constraints Go
 *
 * Prints the JSON Schema of `Customer`, which carries no constraints, and
 * checks a blank name against its `DynamicSchema`, which does.
 */
object Step7ConstraintsInSchemasExample {
  def main(args: Array[String]): Unit = {
    println(Customer.schema.toJsonSchema.toJson)

    val blankName = DynamicValue.Record(
      "name"  -> DynamicValue.string(""),
      "email" -> DynamicValue.string("ada@example.com")
    )

    println(Customer.schema.toDynamicSchema.check(blankName))
  }
}
