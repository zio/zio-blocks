package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "fork-demo")
@description("Demonstrates fork() with the promise-based join pattern from the Golem docs.")
trait ForkDemo extends BaseAgent[String] {

  @description("Forks the agent, joins via a promise, and returns info about both branches.")
  def runFork(): Future[String]
}

object ForkDemo extends AgentCompanion[ForkDemo, String]
