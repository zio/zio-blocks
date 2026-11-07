package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 1: Read Your
 * Existing application.conf
 *
 * Parses the unchanged PureConfig application.conf with config-hocon, scopes it
 * to the `bookstore` block with ConfigSource#prefix, and loads it. The load
 * reports the kebab-case `featured-title` key and the `${?DB_PASSWORD}`
 * substitution, which the next steps fix.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step1ReadApplicationConfExample"
 */
object Step1ReadApplicationConfExample extends App {

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

  // The application.conf a PureConfig application already ships, unchanged.
  val applicationConf: String =
    """bookstore {
      |  name = "Scala Bookstore"
      |  port = 8080
      |  featured-title = "Zionomicon"
      |  discount-percent = 10
      |
      |  db {
      |    host = "localhost"
      |    port = 5432
      |    user = "bookstore"
      |    password = ${?DB_PASSWORD}
      |  }
      |
      |  tags = ["scala", "functional-programming", "zio"]
      |}
      |""".stripMargin

  def report[A](result: Either[::[ConfigError], A]): Unit =
    result match {
      case Right(config) => println(config)
      case Left(errors)  => errors.foreach(error => println(error.message))
    }

  def orThrow(parsed: Either[ConfigError, ConfigSource]): ConfigSource =
    parsed match {
      case Right(source) => source
      case Left(error)   => throw error
    }

  val hocon: ConfigSource = orThrow(ConfigSource.fromHocon(applicationConf, "application.conf"))

  // `bookstore.featured-title` is looked up as `bookstore.featuredTitle`, so it is missing.
  // `discount-percent` is missing too, but `discountPercent` is an Option, so it silently
  // becomes None instead of an error.
  report(Config.load[BookstoreConfig](hocon.prefix("bookstore")))
}
