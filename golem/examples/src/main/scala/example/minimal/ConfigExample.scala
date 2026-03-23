package example.minimal

import golem.{AgentCompanion, BaseAgent}
import golem.config.{AgentConfig, ConfigBuilder, ConfigBuilderDerived, ConfigSchema, ConfigSchemaDerived, Secret}
import golem.runtime.annotations.{agentDefinition, description}

import scala.concurrent.Future

final case class DbConfig(
  host: String,
  port: Int,
  password: Secret[String]
)

object DbConfig {
  implicit val configSchema: ConfigSchema[DbConfig]   = ConfigSchemaDerived.derived
  implicit val configBuilder: ConfigBuilder[DbConfig]  = ConfigBuilderDerived.derived
}

final case class MyAppConfig(
  appName: String,
  apiKey: Secret[String],
  db: DbConfig
)

object MyAppConfig {
  implicit val configSchema: ConfigSchema[MyAppConfig]   = ConfigSchemaDerived.derived
  implicit val configBuilder: ConfigBuilder[MyAppConfig]  = ConfigBuilderDerived.derived
}

@agentDefinition()
@description("Example agent with configuration")
trait ConfigAgent extends BaseAgent[String] with AgentConfig[MyAppConfig] {
  @description("Returns a greeting using config values")
  def greet(): Future[String]
}

object ConfigAgent extends AgentCompanion[ConfigAgent, String]

@agentDefinition()
@description("Example agent that calls ConfigAgent with config overrides")
trait ConfigCallerAgent extends BaseAgent[String] {
  @description("Calls ConfigAgent with overridden config values")
  def callWithOverride(): Future[String]
}

object ConfigCallerAgent extends AgentCompanion[ConfigCallerAgent, String]
