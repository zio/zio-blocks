package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Convert Between YAML and JSON
 *
 * Converts a book from JSON to YAML, shows how quoted scalars change type on
 * the way to JSON, and reads a camelCase container spec through JSON.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.Step6YamlAndJsonExample"
 */
object Step6YamlAndJsonExample {

  sealed trait Format

  object Format {
    case object Paperback extends Format
    case object Hardcover extends Format
    case object Ebook     extends Format

    val all: List[Format] = List(Paperback, Hardcover, Ebook)

    implicit val schema: Schema[Format] = Schema[String].transform[Format](
      name => all.find(_.toString == name).getOrElse(throw new IllegalArgumentException(s"Unknown format: $name")),
      _.toString
    )
  }

  case class Book(
    title: String,
    authors: List[String],
    isbn: String,
    price: Double,
    pageCount: Int,
    tags: List[String],
    formats: List[Format]
  )

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(store: String, books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val zionomicon: Book = Book(
    title = "Zionomicon",
    authors = List("John A. De Goes", "Adam Fraser", "Milad Khajavi"),
    isbn = "978-0-9862-0040-6",
    price = 49.99,
    pageCount = 744,
    tags = List("zio", "effects"),
    formats = List(Format.Ebook, Format.Paperback)
  )

  case class Container(name: String, image: String, imagePullPolicy: String, containerPort: Int)

  object Container {
    implicit val schema: Schema[Container] = Schema.derived
  }

  val containerYaml: String =
    """name: bookstore-api
      |image: ghcr.io/scala-books/bookstore-api:1.4.2
      |imagePullPolicy: IfNotPresent
      |containerPort: 8080
      |""".stripMargin

  def main(args: Array[String]): Unit = {
    val bookJson: Json = Json.from(zionomicon)

    println(Yaml.fromJson(bookJson).print)

    println(
      YamlReader.read("isbn: \"9780997148008\"\nversion: \"1.10\"\nimage-tag: 1.4.2\nin-stock: \"true\"\n").toJson.print
    )

    println(YamlReader.read(containerYaml).toJson.as[Container])
    println(
      YamlReader
        .read(containerYaml.replace("name: bookstore-api", "name: \"2024\""))
        .toJson
        .as[Container]
        .left
        .map(_.getMessage)
    )
  }
}
