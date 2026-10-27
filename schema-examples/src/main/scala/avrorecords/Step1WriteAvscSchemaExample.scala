//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step1WriteAvscSchemaExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step1WriteAvscSchemaExample"

package avrorecords

import org.apache.avro.SchemaFormatter
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.nio.file.{Files, Paths}

/**
 * How to Encode and Decode Records with Avro — Write the .avsc Schema File
 *
 * Derives the codec, prints the schema of `OrderCancelled`, and writes the
 * schema of the whole sealed trait to `order-event.avsc`.
 */
object Step1WriteAvscSchemaExample {
  def main(args: Array[String]): Unit = {
    val codec = OrderEvent.schema.derive(AvroFormat)

    println(SchemaFormatter.format("json/pretty", Schema.derived[OrderCancelled].derive(AvroFormat).avroSchema))

    // The whole sealed trait, written to order-event.avsc in the current directory
    val avscText = SchemaFormatter.format("json/pretty", codec.avroSchema)
    val avscFile = Paths.get("order-event.avsc")
    Files.writeString(avscFile, avscText)
    println(
      s"wrote $avscFile: ${Files.readAllLines(avscFile).size} lines, starting with ${Files.readAllLines(avscFile).get(0)}"
    )
  }
}
