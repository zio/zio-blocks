package example.minimal

import golem.config.{ConfigBuilder, ConfigBuilderDerived, Secret}
import zio.blocks.schema.Schema

final case class DbConfig(
  host: String,
  port: Int,
  password: Secret[String]
)

object DbConfig {
  implicit val schema: Schema[DbConfig]               = Schema.derived
  implicit val configBuilder: ConfigBuilder[DbConfig] = ConfigBuilderDerived.derived
}

final case class MyAppConfig(
  appName: String,
  apiKey: Secret[String],
  db: DbConfig
)

object MyAppConfig {
  implicit val schema: Schema[MyAppConfig]               = Schema.derived
  implicit val configBuilder: ConfigBuilder[MyAppConfig] = ConfigBuilderDerived.derived
}
