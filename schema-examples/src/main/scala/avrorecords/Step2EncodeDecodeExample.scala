//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step2EncodeDecodeExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step2EncodeDecodeExample"

package avrorecords

import zio.blocks.schema.avro._
import java.time.Instant

/**
 * How to Encode and Decode Records with Avro — Encode and Decode One Event
 *
 * Encodes one `OrderPlaced` event, prints the bytes in hexadecimal and decodes
 * them back.
 */
object Step2EncodeDecodeExample {
  def main(args: Array[String]): Unit = {
    // Setup from "Write the .avsc Schema File": the codec
    val codec = OrderEvent.schema.derive(AvroFormat)

    val event: OrderEvent = OrderPlaced(
      orderId = 42L,
      customer = "Ada",
      amount = BigDecimal("19.99"),
      placedAt = Instant.ofEpochSecond(1700000000L, 123000000),
      note = Some("gift"),
      items = List(LineItem("sku-1", 2, BigDecimal("9.995")))
    )

    val bytes = codec.encode(event)

    println(s"${bytes.length} bytes: ${bytes.map(b => f"$b%02x").mkString(" ")}")

    println(codec.decode(bytes))
  }
}
