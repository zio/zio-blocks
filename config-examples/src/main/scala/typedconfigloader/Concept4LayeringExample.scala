package typedconfigloader

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 4: Layer Environment Overrides,
 * YAML and Defaults
 *
 * Stacks an environment stand-in, the YAML source and the defaults with orElse,
 * then loads the layered source key by key.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept4LayeringExample"
 */
object Concept4LayeringExample extends App {

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

  val defaults = ConfigSource.fromMap(defaultSettings, "defaults")

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

  val env = ConfigSource.fromMap(Map("db.password" -> "s3cr3t-from-env", "port" -> "9090"), "env")

  val layered = env.orElse(yaml).orElse(defaults)

  report(Config.load[BookstoreConfig](layered))
}
