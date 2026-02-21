package demo

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "counter-agent")
trait CounterAgent extends BaseAgent[String] {

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object CounterAgent extends AgentCompanion[CounterAgent, String]

