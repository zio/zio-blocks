package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "observability-demo")
@description("Demonstrates the full span/context and durability APIs with typed responses.")
trait ObservabilityDemo extends BaseAgent[String] {

  @description("Create nested spans with attributes and read the invocation context.")
  def traceDemo(): Future[String]

  @description("Demonstrate durability state management and function type variants.")
  def durabilityDemo(): Future[String]
}

object ObservabilityDemo extends AgentCompanion[ObservabilityDemo, String]
