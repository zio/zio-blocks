package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._

import Common._

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
