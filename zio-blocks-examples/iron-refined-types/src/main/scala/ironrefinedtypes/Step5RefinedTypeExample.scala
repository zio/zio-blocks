//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step5RefinedTypeExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step5RefinedTypeExample"

package ironrefinedtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Name a Refined Type
 * with RefinedType
 *
 * Refines SKUs through the `Sku` companion and decodes products whose SKU,
 * price or tags break their constraints.
 */
object Step5RefinedTypeExample {
  def main(args: Array[String]): Unit = {
    println(Sku("KEY-0042"))
    println(Sku.either("key-42"))

    println(
      Product.schema.jsonCodec.decode(
        """{"sku":"KEY-0042","title":"Mechanical keyboard","price":89.90,"tags":["usb","wired"]}"""
      )
    )
    println(
      Product.schema.jsonCodec
        .decode("""{"sku":"key-42","title":"Mechanical keyboard","price":89.90,"tags":[]}""")
        .left
        .map(_.message)
    )
    println(
      Product.schema.jsonCodec
        .decode("""{"sku":"KEY-0042","title":"Mechanical keyboard","price":-1,"tags":[]}""")
        .left
        .map(_.message)
    )
    println(
      Product.schema.jsonCodec
        .decode("""{"sku":"KEY-0042","title":"Mechanical keyboard","price":89.90,"tags":[""]}""")
        .left
        .map(_.message)
    )
  }
}
