package smalljsonbackendfromblocks

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * Build a Small JSON Backend from Blocks — the settings
 *
 * One settings type per service, plus the layered source they are read from:
 * environment variables over a YAML file over built-in defaults.
 */
final case class ServerConfig(port: Int)
object ServerConfig {
  implicit val schema: Schema[ServerConfig] = Schema.derived
}

final case class DbConfig(file: String)
object DbConfig {
  implicit val schema: Schema[DbConfig] = Schema.derived
}

final case class CatalogConfig(pageSize: Int)
object CatalogConfig {
  implicit val schema: Schema[CatalogConfig] = Schema.derived
}

final case class BookstoreConfig(server: ServerConfig, db: DbConfig, catalog: CatalogConfig)
object BookstoreConfig {
  implicit val schema: Schema[BookstoreConfig] = Schema.derived
}

object Settings {
  val bookstoreYaml: String =
    """server:
      |  port: 0
      |db:
      |  file: bookstore.db
      |""".stripMargin

  val yaml: ConfigSource = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
    case Right(source) => source
    case Left(error)   => throw error
  }

  val defaults: ConfigSource =
    ConfigSource.fromMap(Map("server.port" -> "8080", "catalog.pageSize" -> "2"), "defaults")

  val environment: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  def layered(env: ConfigSource): ConfigSource = env.orElse(yaml).orElse(defaults)
}
