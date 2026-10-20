package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._

import Common._

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
