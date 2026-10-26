package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Build YAML Values in Code
 *
 * Builds a tree with the Yaml constructors, walks a parsed tree, and builds a
 * test input with the yaml interpolator. Scala 3 only: the yaml"..."
 * interpolator does not compile in user code on Scala 2.13.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.Step5BuildYamlInCodeExample"
 */
object Step5BuildYamlInCodeExample {

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

  val catalogCodec: YamlCodec[Catalog] = Catalog.schema.derive(YamlFormat)

  val catalogYaml: String =
    """# Books on the front table this month
      |store: Scala Books Berlin
      |books:
      |  - title: Zionomicon
      |    authors: [John A. De Goes, Adam Fraser, Milad Khajavi]
      |    isbn: 978-0-9862-0040-6
      |    price: 49.99
      |    page-count: 744
      |    tags: [zio, effects, concurrency]
      |    formats: [Ebook]
      |  - title: Programming in Scala
      |    authors:
      |      - Martin Odersky
      |      - Lex Spoon
      |      - Bill Venners
      |      - Frank Sommers
      |    isbn: "9780997148008"
      |    price: 59.95
      |    page-count: 852
      |    tags: [scala, language]
      |    formats:
      |      - Paperback
      |      - Ebook
      |""".stripMargin

  case class Featured(title: String, price: Double, note: String)

  object Featured {
    implicit val schema: Schema[Featured] = Schema.derived
  }

  def main(args: Array[String]): Unit = {
    val deployment: Yaml = Yaml.Mapping.fromStringKeys(
      "service"  -> Yaml.Scalar("bookstore-api"),
      "image"    -> Yaml.Scalar("ghcr.io/scala-books/bookstore-api:1.4.2"),
      "replicas" -> Yaml.Scalar("3", tag = Some(YamlTag.Int)),
      "public"   -> Yaml.Scalar("true", tag = Some(YamlTag.Bool)),
      "regions"  -> Yaml.Sequence(Yaml.Scalar("eu-central"), Yaml.Scalar("us-east"))
    )

    println(deployment.print)

    YamlReader.read(catalogYaml) match {
      case Yaml.Mapping(entries) => println(entries.map(_._1.print).mkString(", "))
      case other                 => println(s"Not a mapping: ${other.print}")
    }

    val featuredTitle = "Zionomicon"
    val featuredPrice = 49.99

    val featured: Yaml =
      yaml"""title: $featuredTitle
price: $featuredPrice
note: "Our book of the month is $featuredTitle"
"""

    println(featured.print)

    println(Featured.schema.derive(YamlFormat).decodeValue(featured))
  }
}
