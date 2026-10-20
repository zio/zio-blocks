package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 6: Read Every
 * Error at Once
 *
 * Loads a broken application.conf, layered under an environment stand-in, and
 * prints every error the load collected: a missing key and two invalid values,
 * each named with the whole layered source.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step6ErrorsExample"
 */
object Step6ErrorsExample extends App {

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

  val brokenConf =
    """bookstore {
      |  name = "Scala Bookstore"
      |  port = eighty
      |  discount-percent = "ten"
      |  db { host = "localhost", user = "bookstore" }
      |  tags = ["scala"]
      |}
      |""".stripMargin

  val broken: ConfigSource =
    orThrow(ConfigSource.fromHocon(brokenConf, "application.conf")).keyFormat(KeyFormat.KebabCase).prefix("bookstore")

  val env: ConfigSource = ConfigSource.fromMap(Map.empty, "env")

  report(Config.load[BookstoreConfig](env.orElse(broken)))
}
