package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Encoding
 * the Records for a Prompt
 *
 * Encodes the catalog as tabular TOON and compares its length with the JSON
 * encoding.
 *
 * Run with: cd schema-examples && sbt "runMain
 * llmdatawithtoon.Step1EncodeForPromptExample"
 */
object Step1EncodeForPromptExample extends App {

  case class Book(title: String, firstAuthor: String, year: Int, tags: String)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalog = Catalog(
    List(
      Book("Zionomicon", "John A. De Goes", 2024, "zio, effects, concurrency"),
      Book("Programming in Scala", "Martin Odersky", 2008, "scala, language, fundamentals"),
      Book("Functional Programming in Scala", "Paul Chiusano", 2014, "fp, scala, exercises")
    )
  )

  val tabular = ToonCodecDeriver.withArrayFormat(ArrayFormat.Tabular)

  val catalogCodec: ToonCodec[Catalog] = Catalog.schema.derive(tabular)

  val catalogToon = catalogCodec.encodeToString(catalog)

  println(catalogToon)

  val catalogJson = catalog.toJsonString

  println(catalogJson)
  println(s"JSON: ${catalogJson.length} characters, TOON: ${catalogToon.length} characters")
}
