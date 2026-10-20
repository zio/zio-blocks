package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Complete
 * Example
 *
 * Encodes the catalog as pipe-delimited tabular TOON, builds the prompt, and
 * decodes a fixed model reply, stripping its code fence and rejecting extra
 * fields.
 *
 * Run with: cd schema-examples && sbt "runMain llmdatawithtoon.CompleteExample"
 */
object CompleteExample extends App {

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

  case class Recommendation(title: String, reason: String)

  object Recommendation {
    implicit val schema: Schema[Recommendation] = Schema.derived
  }

  val replyCodec: ToonCodec[Recommendation] =
    Recommendation.schema.derive(ToonCodecDeriver.withRejectExtraFields(true))

  def extractToon(reply: String): String = {
    val fence = "```"
    val open  = reply.indexOf(fence)
    if (open < 0) reply.trim
    else {
      val bodyStart = reply.indexOf('\n', open) + 1
      val close     = reply.indexOf(fence, bodyStart)
      reply.substring(bodyStart, if (close < 0) reply.length else close).trim
    }
  }

  val chattyReply =
    """Happy to help! Here is my recommendation:
      |
      |```toon
      |title: Zionomicon
      |reason: It teaches ZIO, an effect system for Scala, from first steps to concurrency.
      |```
      |
      |Enjoy the book!""".stripMargin

  println(prompt)

  replyCodec.decode(extractToon(chattyReply)) match {
    case Left(error)           => println(error.message)
    case Right(recommendation) => println(recommendation)
  }
}
