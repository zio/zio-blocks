package typedconfigloader

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Putting It Together
 *
 * Combines a typed configuration with defaults, an optional field and a secret,
 * a YAML source layered between the real environment (EnvSource) and the
 * defaults, error reporting, and the provenance table.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.CompleteExample"
 *
 * Override settings with: BOOKSTORE_DB_PASSWORD=s3cr3t BOOKSTORE_PORT=9090 sbt
 * "runMain typedconfigloader.CompleteExample"
 */
object CompleteExample extends App {

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

  // `prefix("bookstore")` scopes EnvSource to BOOKSTORE_* variables: `db.password` is read
  // from BOOKSTORE_DB_PASSWORD and `port` from BOOKSTORE_PORT.
  val layered = EnvSource.prefix("bookstore").orElse(yaml).orElse(defaults)

  Config.loadWithProvenance[BookstoreConfig](layered) match {
    case Right(provenance) =>
      println(provenance.value)
      println(provenance.dump())
    case Left(errors) =>
      errors.foreach(error => println(error.message))
  }

  // A broken load: the port is not a number and no layer has a password.
  val brokenEnv = ConfigSource.fromMap(Map("port" -> "ninety"), "env")

  val defaultsWithoutPassword = ConfigSource.fromMap(defaultSettings - "db.password", "defaults")

  val brokenLayers = brokenEnv.orElse(yaml).orElse(defaultsWithoutPassword)

  Config.load[BookstoreConfig](brokenLayers) match {
    case Right(config) => println(config)
    case Left(errors)  => errors.foreach(error => println(error.message))
  }
}
