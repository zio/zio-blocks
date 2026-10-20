package deriveyourowntypeclass

import zio.blocks.schema._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
import Bookstore._

/**
 * How to Derive Your Own Type Class from a Schema — Masking Fields Through
 * Modifiers
 *
 * Masks the customer's name with Deriver#withModifier, next to the annotated
 * payment token.
 *
 * Run with: cd schema-examples && sbt "runMain
 * deriveyourowntypeclass.Step2ModifiersExample"
 */
object Step2ModifiersExample {
  def main(args: Array[String]): Unit = {
    println(Order.schema.derive(RedactDeriver).redact(order))

    val strictDeriver: Deriver[Redact] =
      RedactDeriver.withModifier(TypeId.of[Customer], "name", Modifier.config("log.redact", "true"))

    println(Order.schema.derive(strictDeriver).redact(order))
  }
}
