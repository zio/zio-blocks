package typedconfigloader

import zio.blocks.config._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 2: Load It From a Map, Letting
 * Defaults Fill the Gaps
 *
 * Loads BookstoreConfig from a map of defaults, showing how case-class default
 * values and an Option field fill keys the map does not have.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept2LoadFromMapExample"
 */
object Concept2LoadFromMapExample extends App {

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

  report(Config.load[BookstoreConfig](defaults))

  report(Config.load[BookstoreConfig](ConfigSource.fromMap(defaultSettings - "discountPercent", "defaults")))
}
