//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep io.github.iltotore::iron-zio-blocks-schema:3.4.1,exclude=dev.zio%zio-blocks-schema_3
//> using options -experimental
//> using file Model.scala
//> using file Ids.scala
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from zio-blocks-examples:  sbt "ironRefinedTypes/runMain ironrefinedtypes.CompleteExample"

package ironrefinedtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

import ids._

/**
 * How to Use Iron Refined Types and Typed IDs with Schema — Putting It Together
 *
 * Decodes two incoming orders: a valid one, and one whose first line has a
 * lowercase SKU.
 */
object CompleteExample {
  def receive(json: String): String =
    Order.schema.jsonCodec.decode(json) match {
      case Right(order) => s"accepted order ${order.id.value} for customer ${order.customer.value}: ${order.lines}"
      case Left(error)  => s"rejected order: ${error.message}"
    }

  def main(args: Array[String]): Unit = {
    println(receive("""{"id":10,"customer":1,"lines":[{"sku":"KEY-0042","quantity":2}],"watchers":[1,2]}"""))
    println(receive("""{"id":11,"customer":1,"lines":[{"sku":"key-42","quantity":2}],"watchers":[]}"""))
  }
}
