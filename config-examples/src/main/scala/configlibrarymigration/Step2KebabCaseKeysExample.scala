package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._

import Common._

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

  val hocon: ConfigSource = orThrow(ConfigSource.fromHocon(applicationConf, "application.conf"))

  val application: ConfigSource = hocon.keyFormat(KeyFormat.KebabCase).prefix("bookstore")

  // Only the password is still missing: `${?DB_PASSWORD}` never reads the environment.
  report(Config.load[BookstoreConfig](application))
}
