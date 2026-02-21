package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

@agentDefinition(typeName = "sync-return")
@description("Demonstrates agent methods with synchronous return types.")
trait SyncReturnAgent extends BaseAgent[Unit] {
  @description("Returns a greeting synchronously.")
  def greet(name: String): String

  @description("Adds two numbers synchronously.")
  def add(a: Int, b: Int): Int

  @description("Stores a tag without returning a value.")
  def touch(tag: String): Unit

  @description("Returns the last stored tag.")
  def lastTag(): String
}

object SyncReturnAgent extends AgentCompanion[SyncReturnAgent, Unit]
