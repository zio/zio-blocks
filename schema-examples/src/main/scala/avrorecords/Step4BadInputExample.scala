//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step4BadInputExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step4BadInputExample"

package avrorecords

import org.apache.avro.io.EncoderFactory
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * How to Encode and Decode Records with Avro — Handle Bad Input
 *
 * Decodes truncated bytes, bytes with extra data at the end, and a list whose
 * length prefix is above `AvroCodec.maxCollectionSize`.
 */
object Step4BadInputExample {
  def main(args: Array[String]): Unit = {
    // Setup from "Write the .avsc Schema File" and "Encode and Decode One Event": the codec and the bytes of one event
    val codec             = OrderEvent.schema.derive(AvroFormat)
    val event: OrderEvent = OrderPlaced(
      orderId = 42L,
      customer = "Ada",
      amount = BigDecimal("19.99"),
      placedAt = Instant.ofEpochSecond(1700000000L, 123000000),
      note = Some("gift"),
      items = List(LineItem("sku-1", 2, BigDecimal("9.995")))
    )
    val bytes = codec.encode(event)

    println(codec.decode(bytes.take(10)))

    println(codec.decode(bytes ++ Array[Byte](1, 2, 3)))

    val hugeList      = new ByteArrayOutputStream()
    val prefixEncoder = EncoderFactory.get.binaryEncoder(hugeList, null)
    prefixEncoder.writeLong(AvroCodec.maxCollectionSize.toLong + 1L)
    prefixEncoder.writeLong(0L)
    prefixEncoder.flush()

    println(Schema[List[Int]].derive(AvroFormat).decode(hugeList.toByteArray))
  }
}
