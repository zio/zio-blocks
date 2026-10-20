package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

import Common._

/**
 * Coming from PureConfig, Typesafe Config or zio-config — Step 5: Check Which
 * HOCON Features Carry Over
 *
 * Shows the HOCON constructs that behave differently in config-hocon than in
 * Typesafe Config: `${?VAR}` never reads the environment, includes other than
 * `include "file"` are parse errors, `fromHocon` drops includes, array
 * concatenation becomes an empty string, and durations must be ISO-8601.
 *
 * Run with: cd config-examples && sbt "runMain
 * configlibrarymigration.Step5HoconCaveatsExample"
 */
object Step5HoconCaveatsExample extends App {

  def keys(hocon: String): Unit =
    ConfigSource.fromHocon(hocon) match {
      case Right(source) =>
        println(source.all("").map { case (key, value) => s"$key = '${value.value}'" }.toList.sorted)
      case Left(error) => println(error.message)
    }

  // HOME is set in almost every environment, but `${?HOME}` only looks inside the document.
  keys("home = ${?HOME}")

  // Only the quoted `include "file"` form parses.
  keys("""include classpath("reference.conf")""")

  // From a string there is nowhere to read an include from, so it is silently dropped.
  keys("""db { include "db.conf" }, name = "Scala Bookstore"""")

  // Array concatenation is not supported: the value becomes an empty string.
  keys("tags = [scala] [zio]")

  // `+=` appends to an array, and substitutions inside the document work.
  keys("""tags = [scala], tags += zio, base = "/srv", data = ${base}"/data"""")

  case class Timeouts(requestTimeout: java.time.Duration)

  object Timeouts {
    implicit val schema: Schema[Timeouts] = Schema.derived
  }

  // Durations are parsed with java.time.Duration.parse, so `10s` must become `PT10S`.
  report(Config.load[Timeouts](orThrow(ConfigSource.fromHocon("requestTimeout = 10s"))))
  report(Config.load[Timeouts](orThrow(ConfigSource.fromHocon("requestTimeout = PT10S"))))
}
