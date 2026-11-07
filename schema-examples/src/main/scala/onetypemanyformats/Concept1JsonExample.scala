package onetypemanyformats

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema._
import zio.blocks.schema.json.JsonFormat

/**
 * One Type, Many Formats — Concept 1: Model the Catalog and Encode to JSON
 *
 * Models the catalog, derives its JSON codec with Schema#derive, prints the
 * JSON and checks the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept1JsonExample"
 */
object Concept1JsonExample extends App {

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
      Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, List("zio", "effects")),
      Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, List("scala", "fundamentals")),
      Book("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, List("scala", "fp"))
    )
  )

  val jsonCodec = Catalog.schema.derive(JsonFormat)

  val jsonBytes = jsonCodec.encode(catalog)

  println(new String(jsonBytes, UTF_8))

  println(jsonCodec.decode(jsonBytes) == Right(catalog))
}
