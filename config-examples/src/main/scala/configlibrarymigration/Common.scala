package configlibrarymigration

import zio.blocks.config._
import zio.blocks.schema.Schema

/**
 * The bookstore configuration shared by every example in "Coming from
 * PureConfig, Typesafe Config or zio-config". The case classes are the same as
 * in the "Build a Typed Configuration Loader" tutorial.
 */
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

object Common {

  /** The application.conf a PureConfig application already ships, unchanged. */
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

  /** Prints the loaded configuration, or the message of every error. */
  def report[A](result: Either[::[ConfigError], A]): Unit =
    result match {
      case Right(config) => println(config)
      case Left(errors)  => errors.foreach(error => println(error.message))
    }

  /** Unwraps a parsed source, stopping the program if the text is not valid. */
  def orThrow(parsed: Either[ConfigError, ConfigSource]): ConfigSource =
    parsed match {
      case Right(source) => source
      case Left(error)   => throw error
    }
}
