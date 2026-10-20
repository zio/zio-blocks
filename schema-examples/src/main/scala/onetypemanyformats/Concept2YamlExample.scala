package onetypemanyformats

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema._
import zio.blocks.schema.yaml.YamlFormat

/**
 * One Type, Many Formats — Concept 2: Swap In YAML
 *
 * Derives the YAML codec from the same schema, prints the YAML and checks the
 * round trip.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.Concept2YamlExample"
 */
object Concept2YamlExample extends App {

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

  val yamlCodec = Catalog.schema.derive(YamlFormat)

  val yamlBytes = yamlCodec.encode(catalog)

  println(new String(yamlBytes, UTF_8))

  println(yamlCodec.decode(yamlBytes) == Right(catalog))
}
