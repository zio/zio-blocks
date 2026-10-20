package onetypemanyformats

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema._
import zio.blocks.schema.xml.XmlFormat

/**
 * One Type, Many Formats — Concept 3: Swap In XML
 *
 * Derives the XML codec, prints the XML with its <Catalog> root element and
 * checks the round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept3XmlExample"
 */
object Concept3XmlExample extends App {

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

  val xmlCodec = Catalog.schema.derive(XmlFormat)

  val xmlBytes = xmlCodec.encode(catalog)

  println(new String(xmlBytes, UTF_8))

  println(xmlCodec.decode(xmlBytes) == Right(catalog))
}
