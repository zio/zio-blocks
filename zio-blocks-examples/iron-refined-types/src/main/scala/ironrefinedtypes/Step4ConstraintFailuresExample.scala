//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step4ConstraintFailuresExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step4ConstraintFailuresExample"

package ironrefinedtypes

import io.github.iltotore.iron._
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Read Constraint
 * Failures from the Decoder
 *
 * Decodes a valid and two invalid customers, then refines a runtime string with
 * the same email constraint.
 */
object Step4ConstraintFailuresExample {
  def main(args: Array[String]): Unit = {
    println(Customer.schema.jsonCodec.decode("""{"name":"Ada","email":"ada@example.com"}"""))
    println(Customer.schema.jsonCodec.decode("""{"name":"","email":"ada@example.com"}""").left.map(_.message))
    println(Customer.schema.jsonCodec.decode("""{"name":"Ada","email":"ada-at-example"}""").left.map(_.message))

    val fromForm: String = "grace@example.com"

    println(fromForm.refineEither[EmailFormat])
    println("grace-at-example".refineEither[EmailFormat])
  }
}
