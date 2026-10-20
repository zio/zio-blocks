package onetypemanyformats

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.json.JsonFormat
import zio.blocks.schema.msgpack.MessagePackFormat

/**
 * One Type, Many Formats — Concept 6: Swap In Avro
 *
 * Derives the Avro codec, compares its size with MessagePack and JSON, prints
 * the first bytes in hexadecimal and checks the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept6AvroExample"
 */
object Concept6AvroExample extends App {

  case class Book(title: String, author: String, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalog = Catalog(
    List(
      Book("Zionomicon", "John A. De Goes, Adam Fraser", 2024, List("zio", "effects")),
      Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, List("scala", "fundamentals")),
      Book("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, List("scala", "fp"))
    )
  )

  val jsonCodec = Catalog.schema.derive(JsonFormat)

  val jsonBytes = jsonCodec.encode(catalog)

  val msgpackCodec = Catalog.schema.derive(MessagePackFormat)

  val msgpackBytes = msgpackCodec.encode(catalog)

  def hexPrefix(bytes: Array[Byte]): String =
    bytes.take(16).map(b => f"$b%02x").mkString(" ")

  val avroCodec = Catalog.schema.derive(AvroFormat)

  val avroBytes = avroCodec.encode(catalog)

  println(
    s"Avro: ${avroBytes.length} bytes, MessagePack: ${msgpackBytes.length} bytes, JSON: ${jsonBytes.length} bytes"
  )

  println(hexPrefix(avroBytes))

  println(avroCodec.decode(avroBytes) == Right(catalog))
}
