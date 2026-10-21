package typedconfigloader

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 3: Read the Same Settings From
 * YAML
 *
 * Parses YAML text with ConfigSource.fromYaml and loads it alone, which reports
 * the missing db.password key.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept3YamlExample"
 */
object Concept3YamlExample extends App {

  case class DbConfig(host: String, port: Int = 5432, user: String, password: Secret)

  case class BookstoreConfig(
    name: String,
    port: Int = 8080,
    featuredTitle: String,
    db: DbConfig,
    tags: List[String],
    discountPercent: Option[Int]
  )

  object BookstoreConfig {
    implicit val schema: Schema[BookstoreConfig] = Schema.derived
  }

  def report(result: Either[::[ConfigError], BookstoreConfig]): Unit =
    result match {
      case Right(config) => println(config)
      case Left(errors)  => errors.foreach(error => println(error.message))
    }

  val bookstoreYaml =
    """name: Functional Bookshelf
      |featuredTitle: Zionomicon
      |db:
      |  host: db.staging.internal
      |  user: bookshelf_app
      |tags:
      |  - scala
      |  - functional-programming
      |  - zio
      |""".stripMargin

  val yaml: ConfigSource = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
    case Right(source) => source
    case Left(error)   => throw error
  }

  report(Config.load[BookstoreConfig](yaml))
}
