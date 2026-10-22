package buildaschemaregistry

import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — Check Incoming Events
 *
 * Sends an event as Avro bytes and checks it against version 1, then checks
 * four wrong events and one JSON event with a whole-number price.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step4CheckEventsExample"
 */
object Step4CheckEventsExample {
  def main(args: Array[String]): Unit = {
    val root: Path               = Files.createTempDirectory("schema-registry")
    val registry: SchemaRegistry = new SchemaRegistry(root)
    registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema)

    val consumerRegistry: SchemaRegistry = new SchemaRegistry(root)
    val storedV1: DynamicSchema          = consumerRegistry.load("book-published", 1)

    val eventCodec = Schema[DynamicValue].derive(AvroFormat)

    val zionomicon        = v1.BookPublished("978-1-00-000001-1", "Zionomicon", BigDecimal("49.99"))
    val sent: Array[Byte] = eventCodec.encode(Schema[v1.BookPublished].toDynamicValue(zionomicon))

    val received: DynamicValue = eventCodec.decode(sent).fold(error => throw error, identity)

    println(storedV1.check(received))
    println(storedV1.conforms(received))

    val textPrice  = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": "49.99"}""")
    val noTitle    = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "price": 49.99}""")
    val extraField =
      Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 49.99, "pages": 312}""")
    val manyMistakes = Json.parseUnsafe("""{"isbn": 9781000000011, "title": 42}""")

    println(storedV1.check(textPrice.toDynamicValue))
    println(storedV1.check(noTitle.toDynamicValue))
    println(storedV1.check(extraField.toDynamicValue))
    println(storedV1.check(manyMistakes.toDynamicValue))

    val wholePrice = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 50}""")

    println(storedV1.check(wholePrice.toDynamicValue))
    println(v1.BookPublished.schema.jsonCodec.decode(wholePrice.print))
  }
}
