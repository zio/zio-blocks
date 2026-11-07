package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 7: Keep Secrets
 * Out of Logs
 *
 * Loads the layered configuration with Config.loadWithProvenance and prints the
 * configuration and its provenance table. The password is a Secret, so the
 * configuration prints it as <secret>, and ProvenanceMap#dump hides it because
 * its key name contains "password". Secret.unwrap reads the real value.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step7SecretsExample"
 */
object Step7SecretsExample extends App {

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

  def orThrow(parsed: Either[ConfigError, ConfigSource]): ConfigSource =
    parsed match {
      case Right(source) => source
      case Left(error)   => throw error
    }

  val application: ConfigSource =
    orThrow(ConfigSource.fromHocon(applicationConf, "application.conf"))
      .keyFormat(KeyFormat.KebabCase)
      .prefix("bookstore")

  // Stands in for EnvSource so the output does not depend on the machine.
  val env: ConfigSource = ConfigSource.fromMap(Map("db.password" -> "s3cr3t-from-env", "port" -> "9090"), "env")

  Config.loadWithProvenance[BookstoreConfig](env.orElse(application)) match {
    case Right(provenance) =>
      println(provenance.value)
      println(provenance.dump())
      println(Secret.unwrap(provenance.value.db.password).length)
    case Left(errors) =>
      errors.foreach(error => println(error.message))
  }
}
