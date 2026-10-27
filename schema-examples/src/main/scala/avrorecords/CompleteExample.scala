//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.CompleteExample"

package avrorecords

import org.apache.avro.SchemaFormatter
import zio.blocks.schema.avro._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.{Instant, LocalDate}

/**
 * How to Encode and Decode Records with Avro — Putting It Together
 *
 * Derives the codec, prints the `.avsc` schema of the whole sealed trait,
 * writes three different events to one stream and reads them back.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val codec = OrderEvent.schema.derive(AvroFormat)

    println(SchemaFormatter.format("json/pretty", codec.avroSchema))

    val events: List[OrderEvent] = List(
      OrderPlaced(
        orderId = 42L,
        customer = "Ada",
        amount = BigDecimal("19.99"),
        placedAt = Instant.ofEpochSecond(1700000000L, 123000000),
        note = Some("gift"),
        items = List(LineItem("sku-1", 2, BigDecimal("9.995")))
      ),
      OrderShipped(42L, LocalDate.of(2024, 1, 3), "DHL"),
      OrderCancelled(43L, "customer request")
    )

    val buffer = new ByteArrayOutputStream()

    events.foreach(e => codec.encode(e, buffer))

    val input = new ByteArrayInputStream(buffer.toByteArray)

    println(s"stream holds ${buffer.size()} bytes")

    val decoded = events.map(_ => codec.decode(input))

    decoded.foreach(println)

    println(s"decoded events equal the originals: ${decoded == events.map(Right(_))}")
  }
}
