package example.minimal

import golem.{AgentCompanion, BaseAgent}
import golem.config.{AgentConfig, ConfigBuilder, ConfigBuilderDerived, ConfigSchema, ConfigSchemaDerived, RpcConfig, RpcConfigFieldsDerived, RpcFields, Secret}
import golem.runtime.annotations.{agentDefinition, description}

import scala.concurrent.Future

final case class DbConfig(
  host: String,
  port: Int,
  password: Secret[String]
)

object DbConfig {
  implicit val configSchema: ConfigSchema[DbConfig]    = ConfigSchemaDerived.derived
  implicit val configBuilder: ConfigBuilder[DbConfig]  = ConfigBuilderDerived.derived
  val rpcFields: RpcFields[DbConfig]                   = RpcConfigFieldsDerived.fields[DbConfig]
}

final case class MyAppConfig(
  appName: String,
  apiKey: Secret[String],
  db: DbConfig
)

object MyAppConfig {
  implicit val configSchema: ConfigSchema[MyAppConfig]    = ConfigSchemaDerived.derived
  implicit val configBuilder: ConfigBuilder[MyAppConfig]  = ConfigBuilderDerived.derived
  val rpcFields: RpcFields[MyAppConfig]                   = RpcConfigFieldsDerived.fields[MyAppConfig]
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

/**
 * Demonstrates using the type-safe RPC config override API.
 *
 * Before (untyped):
 * {{{
 * ConfigAgent.getWithConfig("hello", List(
 *   ConfigOverride[String](List("appName"), "overridden"),
 *   ConfigOverride[String](List("db", "host"), "new-host")
 * ))
 * }}}
 *
 * After (typed):
 * {{{
 * val f = MyAppConfig.rpcFields
 * val config = RpcConfig.empty[MyAppConfig]
 *   .set(f[String]("appName"), "overridden")
 *   .set(f.nested("db")[String]("host"), "new-host")
 *   // f[String]("apiKey")     — runtime error: secret field excluded
 *   // f[Int]("appName")       — compile error: wrong type
 * ConfigAgent.getWithConfig("hello", config)
 * }}}
 */
object ConfigRpcUsageExample {
  def example(): Unit = {
    val f = MyAppConfig.rpcFields
    val config = RpcConfig.empty[MyAppConfig]
      .set(f[String]("appName"), "overridden")
      .set(f.nested("db")[String]("host"), "new-host")
      .set(f.nested("db")[Int]("port"), 5433)

    // Type-safe: this connects with config overrides
    ConfigAgent.getWithConfig("hello", config)
  }
}
