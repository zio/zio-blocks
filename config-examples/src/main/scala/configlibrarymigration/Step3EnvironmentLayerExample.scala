package configlibrarymigration

import zio.blocks.config._
import zio.blocks.config.hocon._

import Common._

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

  val application: ConfigSource =
    orThrow(ConfigSource.fromHocon(applicationConf, "application.conf")).keyFormat(KeyFormat.KebabCase).prefix("bookstore")

  val env: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  report(Config.load[BookstoreConfig](env.orElse(application)))
}
