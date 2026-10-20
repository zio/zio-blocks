package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Putting It Together
 *
 * Loads the migrated src/main/resources/configlibrarymigration/application.conf
 * with config-hocon, maps its kebab-case keys to camelCase fields, layers
 * BOOKSTORE_* environment variables over it, and prints the configuration and
 * where each value came from, or every error at once.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.CompleteExample"
 *
 * Override settings with: BOOKSTORE_DB_PASSWORD=s3cr3t
 * BOOKSTORE_FEATURED_TITLE="Programming in Scala" sbt "runMain
 * configlibrarymigration.CompleteExample"
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

  // Same file the PureConfig application read, minus the `${?DB_PASSWORD}` line.
  val application: Either[ConfigError, ConfigSource] =
    ConfigSource
      .fromFile("src/main/resources/configlibrarymigration/application.conf")
      .map(_.keyFormat(KeyFormat.KebabCase).prefix("bookstore"))

  // BOOKSTORE_FEATURED_TITLE, BOOKSTORE_DB_PASSWORD, ... override the file.
  val env: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  application match {
    case Left(error) =>
      println(error.message)
    case Right(file) =>
      Config.loadWithProvenance[BookstoreConfig](env.orElse(file)) match {
        case Right(provenance) =>
          println(provenance.value)
          println(provenance.provenanceOf("featuredTitle"))
          println(provenance.dump())
        case Left(errors) =>
          errors.foreach(error => println(error.message))
      }
  }
}
