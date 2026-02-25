package example.templates

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "rpc-client")
@description("Demonstrates agent-to-agent RPC by calling Counter remotely.")
trait RpcClient extends BaseAgent[String] {
  @description("Invoke Counter.increment remotely and return the result.")
  def callCounter(counterId: String): Future[Int]
}

object RpcClient extends AgentCompanion[RpcClient, String]
