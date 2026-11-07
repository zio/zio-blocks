package typedconfigloader

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 6: See Where Each Value Came
 * From
 *
 * Loads the layered configuration with Config.loadWithProvenance, prints the
 * provenance table with secrets redacted, and reads the real password with
 * Secret.unwrap.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept6ProvenanceExample"
 */
object Concept6ProvenanceExample extends App {

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

  val env = ConfigSource.fromMap(Map("db.password" -> "s3cr3t-from-env", "port" -> "9090"), "env")

  val layered = env.orElse(yaml).orElse(defaults)

  Config.loadWithProvenance[BookstoreConfig](layered) match {
    case Right(provenance) => println(provenance.dump())
    case Left(errors)      => errors.foreach(error => println(error.message))
  }

  val config = Config.loadOrThrow[BookstoreConfig](layered)

  println(config.db.password)
  println(Secret.unwrap(config.db.password).length)
}
