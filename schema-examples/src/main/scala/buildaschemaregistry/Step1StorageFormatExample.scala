package buildaschemaregistry

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — Choose a Storage Format
 *
 * Round-trips version 1's schema through JSON and through Avro, and checks a
 * wrong event against each copy. The JSON copy reads back as an accept-anything
 * schema; the Avro copy is equal to the original.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step1StorageFormatExample"
 */
object Step1StorageFormatExample {
  def main(args: Array[String]): Unit = {
    val v1Schema: DynamicSchema = Schema[v1.BookPublished].toDynamicSchema

    val wrongEvent: DynamicValue =
      Json.parseUnsafe("""{"isbn": 9781000000011, "price": "free", "pages": 300}""").toDynamicValue

    val schemaAsJson = Schema[DynamicSchema].derive(JsonFormat)
    val schemaAsAvro = Schema[DynamicSchema].derive(AvroFormat)

    val fromJson = schemaAsJson.decode(schemaAsJson.encode(v1Schema))
    val fromAvro = schemaAsAvro.decode(schemaAsAvro.encode(v1Schema))

    println(v1Schema.check(wrongEvent))
    println(fromJson.map(_ == v1Schema))
    println(fromJson.map(_.reflect.isDynamic))
    println(fromJson.map(_.check(wrongEvent)))

    println(fromAvro.map(_ == v1Schema))
    println(fromAvro.map(_.check(wrongEvent)))
  }
}
