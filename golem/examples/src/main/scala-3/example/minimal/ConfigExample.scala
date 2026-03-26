package example.minimal

import golem.BaseAgent
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
  implicit val configBuilder: ConfigBuilder[DbConfig] = ConfigBuilderDerived.derived
}

final case class MyAppConfig(
  appName: String,
  apiKey: Secret[String],
  db: DbConfig
)

object MyAppConfig {
  implicit val configSchema: ConfigSchema[MyAppConfig]   = ConfigSchemaDerived.derived
  implicit val configBuilder: ConfigBuilder[MyAppConfig] = ConfigBuilderDerived.derived
}

@agentDefinition()
@description("Example agent with configuration")
trait ConfigAgent extends BaseAgent with AgentConfig[MyAppConfig] {
  class Constructor(val value: String)

  @description("Returns a greeting using config values")
  def greet(): Future[String]
}

@agentDefinition()
@description("Example agent that calls ConfigAgent with config overrides")
trait ConfigCallerAgent extends BaseAgent {
  class Constructor(val value: String)

  @description("Calls ConfigAgent with overridden config values")
  def callWithOverride(): Future[String]
}

/**
 * Demonstrates the generated config override API.
 *
 * The generated `getWithConfig` takes one `Option[T] = None` parameter per
 * non-secret config field, derived from the config case class at codegen time.
 * Secret fields (`apiKey`, `db.password`) are excluded automatically.
 *
 * {{{
 * ConfigAgentClient.getWithConfig("hello",
 *   appName = Some("overridden"),
 *   dbHost  = Some("new-host"),
 *   dbPort  = Some(5433)
 * )
 * }}}
 */
object ConfigRpcUsageExample {
  def example(): Unit =
    ConfigAgentClient.getWithConfig(
      "hello",
      appName = Some("overridden"),
      dbHost = Some("new-host"),
      dbPort = Some(5433)
    )
}
