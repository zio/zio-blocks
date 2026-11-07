package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 4: Load the File
 * on the JVM, Includes and All
 *
 * Reads src/main/resources/configlibrarymigration/split/application.conf with
 * the JVM-only ConfigSource.fromFile, which resolves its `include "db.conf"`
 * relative to the including file, then loads it with the kebab-case mapping.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step4LoadFileWithIncludesExample"
 */
object Step4LoadFileWithIncludesExample extends App {

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

  val file: ConfigSource = orThrow(
    ConfigSource.fromFile("src/main/resources/configlibrarymigration/split/application.conf")
  )

  println(file.sourceId)

  report(Config.load[BookstoreConfig](file.keyFormat(KeyFormat.KebabCase).prefix("bookstore")))

  // A file that does not exist is a Left, not an exception.
  ConfigSource.fromFile("src/main/resources/configlibrarymigration/missing.conf") match {
    case Right(_)    => println("unexpected: the file exists")
    case Left(error) => println(error.message)
  }
}
