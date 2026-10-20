package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 2: Keep Your
 * Kebab-Case Keys
 *
 * Wraps the HOCON source with ConfigSource#keyFormat(KeyFormat.KebabCase), so
 * the camelCase field `featuredTitle` reads the `featured-title` key, the way
 * PureConfig maps field names by default.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step2KebabCaseKeysExample"
 */
object Step2KebabCaseKeysExample extends App {

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

  val application: ConfigSource = hocon.keyFormat(KeyFormat.KebabCase).prefix("bookstore")

  // Only the password is still missing: `${?DB_PASSWORD}` never reads the environment.
  report(Config.load[BookstoreConfig](application))
}
