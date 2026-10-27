//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step3StreamEventsExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step3StreamEventsExample"

package avrorecords

import zio.blocks.schema.avro._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.LocalDate

/**
 * How to Encode and Decode Records with Avro — Write Several Events to One
 * Stream
 *
 * Writes three `OrderShipped` events to one stream, back to back, and reads
 * them back one record at a time.
 */
object Step3StreamEventsExample {
  def main(args: Array[String]): Unit = {
    // Setup from "Write the .avsc Schema File": the codec
    val codec = OrderEvent.schema.derive(AvroFormat)

    val shipped: List[OrderEvent] = List(
      OrderShipped(1L, LocalDate.of(2024, 1, 1), "DHL"),
      OrderShipped(2L, LocalDate.of(2024, 1, 2), "UPS"),
      OrderShipped(3L, LocalDate.of(2024, 1, 3), "FedEx")
    )

    val buffer = new ByteArrayOutputStream()

    shipped.foreach(e => codec.encode(e, buffer))

    val input = new ByteArrayInputStream(buffer.toByteArray)

    println(s"stream holds ${buffer.size()} bytes")

    shipped.foreach(_ => println(codec.decode(input)))

    println(s"bytes left in the stream: ${input.available()}")
  }
}
