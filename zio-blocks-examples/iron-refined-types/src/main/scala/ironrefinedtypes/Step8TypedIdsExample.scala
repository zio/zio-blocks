//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run Step8TypedIdsExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.Step8TypedIdsExample"

package ironrefinedtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

import ids._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Keep IDs Apart with
 * a Typed Id
 *
 * Round-trips an order with typed IDs through JSON and passes two kinds of ID
 * to a function that accepts any `Id`.
 */
object Step8TypedIdsExample {
  def audit(id: Id[Any]): String = s"audit entry for id ${id.value}"

  def main(args: Array[String]): Unit = {
    val order = Order(
      id = Id(10L),
      customer = Id(1L),
      lines = List(Line(Sku("KEY-0042"), Quantity.parse(2).toOption.get)),
      watchers = Set(Id(1L), Id(2L))
    )

    val orderJson = Order.schema.jsonCodec.encodeToString(order)

    println(orderJson)
    println(Order.schema.jsonCodec.decode(orderJson))

    // `cancel(order.customer)` with `def cancel(id: Id[Order])` does not compile.
    println(audit(order.id))
    println(audit(order.customer))
  }
}
