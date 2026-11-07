//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step7WriteWithApacheAvroExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step7WriteWithApacheAvroExample"

package avrorecords

import org.apache.avro.Schema.Parser
import org.apache.avro.generic.{GenericData, GenericDatumWriter}
import org.apache.avro.io.EncoderFactory
import zio.blocks.schema.avro._
import java.io.ByteArrayOutputStream

/**
 * How to Encode and Decode Records with Avro — Write Bytes with Apache Avro
 *
 * Writes an `OrderShipped` record with Apache Avro's `GenericDatumWriter` and
 * decodes the bytes with zio-blocks.
 */
object Step7WriteWithApacheAvroExample {
  def main(args: Array[String]): Unit = {
    // Setup from the earlier sections: the codec and its schema parsed by Apache Avro
    val codec      = OrderEvent.schema.derive(AvroFormat)
    val avroSchema = new Parser().parse(codec.avroSchema.toString)

    val shippedSchema = avroSchema.getTypes.get(1)

    val shippedOn = new GenericData.Record(shippedSchema.getField("shippedOn").schema())
    shippedOn.put("year", 2024)
    shippedOn.put("month", 3)
    shippedOn.put("day", 9)

    val fromJava = new GenericData.Record(shippedSchema)
    fromJava.put("orderId", 7L)
    fromJava.put("shippedOn", shippedOn)
    fromJava.put("carrier", "DHL")

    val javaBytes   = new ByteArrayOutputStream()
    val javaEncoder = EncoderFactory.get.binaryEncoder(javaBytes, null)
    new GenericDatumWriter[AnyRef](avroSchema).write(fromJava, javaEncoder)
    javaEncoder.flush()

    println(codec.decode(javaBytes.toByteArray))
  }
}
