package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "guards-demo")
@description("Demonstrates Guards API, HostApi config get/set, and oplog management.")
trait GuardsDemo extends BaseAgent[String] {

  @description("Exercises block-scoped guards: withPersistenceLevel, withRetryPolicy, withIdempotenceMode, atomically.")
  def guardsBlockDemo(): Future[String]

  @description(
    "Exercises resource-style guards: usePersistenceLevel, useRetryPolicy, useIdempotenceMode, markAtomicOperation."
  )
  def guardsResourceDemo(): Future[String]

  @description("Exercises oplog management: getOplogIndex, markBeginOperation, markEndOperation, oplogCommit.")
  def oplogDemo(): Future[String]
}

object GuardsDemo extends AgentCompanion[GuardsDemo, String]
