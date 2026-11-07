package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Why
 * ArrayFormat.Tabular Must Be Set
 *
 * Prints the catalog with ArrayFormat.Auto and with ArrayFormat.Tabular, side
 * by side.
 *
 * Run with: cd schema-examples && sbt "runMain
 * llmdatawithtoon.Step2AutoVsTabularExample"
 */
object Step2AutoVsTabularExample extends App {

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

  val autoToon = Catalog.schema.derive(ToonFormat).encodeToString(catalog)

  println("ArrayFormat.Auto:")
  println(autoToon)
  println()
  println("ArrayFormat.Tabular:")
  println(catalogToon)
  println()
  println(s"Auto: ${autoToon.length} characters, Tabular: ${catalogToon.length} characters")
}
