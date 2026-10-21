package deriveyourowntypeclass

import zio.blocks.schema._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
import Bookstore._

/**
 * How to Derive Your Own Type Class from a Schema — Overriding the Instance for
 * One Type
 *
 * Masks every Email partially, first on the deriver with withInstance, then for
 * one derivation with DerivationBuilder#instance.
 *
 * Run with: cd schema-examples && sbt "runMain
 * deriveyourowntypeclass.Step3OverrideExample"
 */
object Step3OverrideExample {
  def main(args: Array[String]): Unit = {
    val partialEmail: Redact[Email] = (email: Email) => {
      val at = email.value.indexOf('@')
      email.value.take(1) + Redact.Masked + email.value.drop(at)
    }

    val logDeriver: Deriver[Redact] = RedactDeriver.withInstance[Email](partialEmail)

    println(Order.schema.derive(logDeriver).redact(order))

    println(Order.schema.deriving(RedactDeriver).instance(TypeId.of[Email], partialEmail).derive.redact(order))
  }
}
