package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "oplog-inspector")
@description("Reads and inspects oplog entries with full type-safe pattern matching.")
trait OplogInspector extends BaseAgent[String] {

  @description("Read the last N oplog entries and format a typed summary.")
  def inspectRecent(): Future[String]

  @description("Search the oplog for entries matching the given text.")
  def searchOplog(text: String): Future[String]
}

object OplogInspector extends AgentCompanion[OplogInspector, String]
