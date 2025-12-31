package demo

import cloud.golem.runtime.annotations.{agentDefinition, description, prompt}
import cloud.golem.sdk.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition("counter-agent")
trait CounterAgent extends BaseAgent {
  final type AgentInput = String

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object CounterAgent extends AgentCompanion[CounterAgent]
