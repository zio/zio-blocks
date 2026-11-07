package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Building
 * the Prompt
 *
 * Builds the prompt string around the pipe-delimited catalog and prints it.
 *
 * Run with: cd schema-examples && sbt "runMain
 * llmdatawithtoon.Step4BuildPromptExample"
 */
object Step4BuildPromptExample extends App {

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

  val pipeCodec = Catalog.schema.derive(tabular.withDelimiter(Delimiter.Pipe))

  val catalogPipeToon = pipeCodec.encodeToString(catalog)

  def buildPrompt(catalogToon: String, request: String): String =
    List(
      "You recommend books from the catalog below. The catalog is in TOON format.",
      "",
      catalogToon,
      "",
      s"Request: $request",
      "",
      "Reply in TOON with exactly two fields and no other text:",
      "title: <a title from the catalog>",
      "reason: <one sentence>"
    ).mkString("\n")

  val prompt = buildPrompt(catalogPipeToon, "I know Scala and want to learn effect systems.")

  println(prompt)
}
