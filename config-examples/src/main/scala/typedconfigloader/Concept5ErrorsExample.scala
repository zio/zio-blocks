package typedconfigloader

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 5: Collect Every Error at Once
 *
 * Loads layers with a non-numeric port and no password, prints every error from
 * Config.load, then prints the report carried by Config.loadOrThrow on the YAML source alone.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept5ErrorsExample"
 */
object Concept5ErrorsExample extends App {

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

  val defaultSettings = Map(
    "name"            -> "Scala Bookstore",
    "featuredTitle"   -> "Programming in Scala",
    "db.host"         -> "localhost",
    "db.user"         -> "bookstore",
    "db.password"     -> "dev-password",
    "tags.0"          -> "scala",
    "tags.1"          -> "books",
    "discountPercent" -> "10"
  )

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

  val brokenEnv = ConfigSource.fromMap(Map("port" -> "ninety"), "env")

  val defaultsWithoutPassword = ConfigSource.fromMap(defaultSettings - "db.password", "defaults")

  val brokenLayers = brokenEnv.orElse(yaml).orElse(defaultsWithoutPassword)

  report(Config.load[BookstoreConfig](brokenLayers))

  try Config.loadOrThrow[BookstoreConfig](yaml)
  catch { case e: ConfigLoadException => println(e.getMessage) }
}
