package example.integrationtests

import golem.BaseAgent
import golem.config.AgentConfig
import golem.runtime.annotations.{agentDefinition, description}

import scala.concurrent.Future

@agentDefinition()
@description("Example agent with configuration")
trait ConfigAgent extends BaseAgent with AgentConfig[MyAppConfig] {
  class Id(val value: String)

  @description("Returns a greeting using config values")
  def greet(): Future[String]
}

@agentDefinition()
@description("Example agent that calls ConfigAgent with config overrides")
trait ConfigCallerAgent extends BaseAgent {
  class Id(val value: String)

  @description("Calls ConfigAgent with overridden config values")
  def callWithOverride(): Future[String]
}
