package golem.runtime.rpc

import golem.runtime.annotations.{DurabilityMode, agentDefinition}
import golem.BaseAgent

/**
 * Top-level agent trait used to regression-test Scala.js AgentClient.bind
 * behavior for collection parameter types.
 */
@agentDefinition("bind-list-double", mode = DurabilityMode.Durable)
trait BindListDoubleWorkflow extends BaseAgent[Unit] {
  def finished(results: List[Double]): Unit
}
