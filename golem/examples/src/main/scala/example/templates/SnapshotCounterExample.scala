package example.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "snapshot-counter")
@description(
  "A counter agent that installs custom snapshot save/load hooks (Scala equivalent of the Rust/TS snapshotting template)."
)
trait SnapshotCounter extends BaseAgent[String] {

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object SnapshotCounter extends AgentCompanion[SnapshotCounter, String]
