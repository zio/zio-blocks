//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step6ReadWithApacheAvroExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step6ReadWithApacheAvroExample"

package avrorecords

import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import zio.blocks.schema.avro._
import java.time.{Instant, LocalDate}

/**
 * How to Encode and Decode Records with Avro — Read the Bytes with Apache Avro
 *
 * Reads bytes written by zio-blocks with Apache Avro's `GenericDatumReader`,
 * using the schema that zio-blocks emits.
 */
object Step6ReadWithApacheAvroExample {
  def main(args: Array[String]): Unit = {
    // Setup from the earlier sections: the codec, a shipped order and the bytes of a placed order
    val codec                     = OrderEvent.schema.derive(AvroFormat)
    val shipped: List[OrderEvent] = List(OrderShipped(1L, LocalDate.of(2024, 1, 1), "DHL"))
    val event: OrderEvent         = OrderPlaced(
      orderId = 42L,
      customer = "Ada",
      amount = BigDecimal("19.99"),
      placedAt = Instant.ofEpochSecond(1700000000L, 123000000),
      note = Some("gift"),
      items = List(LineItem("sku-1", 2, BigDecimal("9.995")))
    )
    val bytes = codec.encode(event)

    val avroSchema = new Parser().parse(codec.avroSchema.toString)

    val reader = new GenericDatumReader[AnyRef](avroSchema)

    def readWithApacheAvro(bytes: Array[Byte]): AnyRef =
      reader.read(null, DecoderFactory.get.binaryDecoder(bytes, null))

    println(readWithApacheAvro(codec.encode(shipped.head)))

    println(readWithApacheAvro(bytes))
  }
}
