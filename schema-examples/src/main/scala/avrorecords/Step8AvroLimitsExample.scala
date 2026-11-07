//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step8AvroLimitsExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step8AvroLimitsExample"

package avrorecords

import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.time.LocalDate

/**
 * How to Encode and Decode Records with Avro — What Avro Support Does Not Cover
 *
 * Reads old bytes with a new version of a type and new bytes with the old
 * version, then decodes the bytes of one type as another type.
 */
object Step8AvroLimitsExample {

  case class OrderV1(orderId: Long, customer: String)

  object OrderV1 {
    implicit val schema: Schema[OrderV1] = Schema.derived
  }

  case class OrderV2(orderId: Long, customer: String, priority: Int = 0)

  object OrderV2 {
    implicit val schema: Schema[OrderV2] = Schema.derived
  }

  def main(args: Array[String]): Unit = {
    // Setup from the earlier sections: the codec and a shipped order
    val codec                     = OrderEvent.schema.derive(AvroFormat)
    val shipped: List[OrderEvent] = List(OrderShipped(1L, LocalDate.of(2024, 1, 1), "DHL"))

    val v1Bytes = OrderV1.schema.derive(AvroFormat).encode(OrderV1(9L, "Bob"))
    val v2Bytes = OrderV2.schema.derive(AvroFormat).encode(OrderV2(9L, "Bob", 3))

    println(OrderV2.schema.derive(AvroFormat).decode(v1Bytes))

    println(OrderV1.schema.derive(AvroFormat).decode(v2Bytes))

    println(OrderV1.schema.derive(AvroFormat).decode(codec.encode(shipped.head)))
  }
}
