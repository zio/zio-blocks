package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 3: Replace
 * `${?VAR}` Substitutions With an Environment Layer
 *
 * Layers the real environment over the HOCON file with ConfigSource#orElse.
 * EnvSource is scoped to BOOKSTORE_* variables with ConfigSource#prefix, and
 * KeyFormat.UpperSnakeCase splits camelCase field names, so `featuredTitle` is
 * read from BOOKSTORE_FEATURED_TITLE and `db.password` from
 * BOOKSTORE_DB_PASSWORD.
 *
 * Run with: cd config-examples && BOOKSTORE_DB_PASSWORD=s3cr3t sbt "runMain
 * configlibrarymigration.Step3EnvironmentLayerExample"
 */
object Step3EnvironmentLayerExample extends App {

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

  val application: ConfigSource =
    orThrow(ConfigSource.fromHocon(applicationConf, "application.conf"))
      .keyFormat(KeyFormat.KebabCase)
      .prefix("bookstore")

  val env: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  report(Config.load[BookstoreConfig](env.orElse(application)))
}
