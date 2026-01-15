package golem.runtime.rpc

import golem.runtime.annotations.{DurabilityMode, agentDefinition}

/**
 * Top-level agent trait used to regression-test Scala.js AgentClient.bind
 * behavior for collection parameter types.
 */
@agentDefinition("bind-list-double", mode = DurabilityMode.Durable)
trait BindListDoubleWorkflow {
  type AgentInput = Unit
  def finished(results: List[Double]): Unit
}

