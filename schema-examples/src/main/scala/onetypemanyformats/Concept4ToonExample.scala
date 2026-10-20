package onetypemanyformats

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema._
import zio.blocks.schema.toon.ToonFormat

/**
 * One Type, Many Formats — Concept 4: Swap In TOON
 *
 * Derives the TOON codec, prints the TOON text and checks the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept4ToonExample"
 */
object Concept4ToonExample extends App {

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

  val toonCodec = Catalog.schema.derive(ToonFormat)

  val toonBytes = toonCodec.encode(catalog)

  println(new String(toonBytes, UTF_8))

  println(toonCodec.decode(toonBytes) == Right(catalog))
}
