package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Choosing a
 * Delimiter
 *
 * Prints the catalog with the comma, pipe and tab delimiters and decodes the
 * pipe version back.
 *
 * Run with: cd schema-examples && sbt "runMain
 * llmdatawithtoon.Step3DelimitersExample"
 */
object Step3DelimitersExample extends App {

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

  for (delimiter <- List(Delimiter.Comma, Delimiter.Pipe, Delimiter.Tab)) {
    val codec = Catalog.schema.derive(tabular.withDelimiter(delimiter))
    println(s"$delimiter:")
    println(codec.encodeToString(catalog))
    println()
  }

  val pipeCodec = Catalog.schema.derive(tabular.withDelimiter(Delimiter.Pipe))

  println(pipeCodec.decode(pipeCodec.encodeToString(catalog)) == Right(catalog))
}
