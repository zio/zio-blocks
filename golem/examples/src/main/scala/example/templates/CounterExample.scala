package example.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "counter")
@description("A simple counter agent (Scala equivalent of the Rust/TS default template).")
trait Counter extends BaseAgent[String] {

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object Counter extends AgentCompanion[Counter, String]
