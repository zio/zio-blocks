package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._

import Common._

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 1: Read Your
 * Existing application.conf
 *
 * Parses the unchanged PureConfig application.conf with config-hocon, scopes
 * it to the `bookstore` block with ConfigSource#prefix, and loads it. The load
 * reports the kebab-case `featured-title` key and the `${?DB_PASSWORD}`
 * substitution, which the next steps fix.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step1ReadApplicationConfExample"
 */
object Step1ReadApplicationConfExample extends App {

  val hocon: ConfigSource = orThrow(ConfigSource.fromHocon(applicationConf, "application.conf"))

  // `bookstore.featured-title` is looked up as `bookstore.featuredTitle`, so it is missing.
  // `discount-percent` is missing too, but `discountPercent` is an Option, so it silently
  // becomes None instead of an error.
  report(Config.load[BookstoreConfig](hocon.prefix("bookstore")))
}
