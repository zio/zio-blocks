package cloud.golem.runtime.rpc

import cloud.golem.sdk.Uuid
import cloud.golem.runtime.rpc.host.AgentHostApi.RegisteredAgentType
import cloud.golem.runtime.rpc.host.WasmRpcApi.WasmRpcClient
import cloud.golem.runtime.rpc.host.{AgentHostApi, WasmRpcApi}

import scala.scalajs.js

final case class RemoteAgentClient(
  agentTypeName: String,
  agentId: String,
  metadata: RegisteredAgentType,
  rpc: RpcInvoker
)

object RemoteAgentClient {
  def resolve(agentTypeName: String, constructorPayload: js.Dynamic): Either[String, RemoteAgentClient] =
    resolve(agentTypeName, constructorPayload, phantom = None)

  def resolve(
    agentTypeName: String,
    constructorPayload: js.Dynamic,
    phantom: Option[Uuid]
  ): Either[String, RemoteAgentClient] =
    AgentHostApi
      .registeredAgentType(agentTypeName)
      .toRight(s"Agent type '$agentTypeName' is not registered on this host")
      .flatMap { agentType =>
        AgentHostApi.makeAgentId(agentTypeName, constructorPayload, phantom).map { id =>
          val rpcClient = WasmRpcApi.newClient(agentType.implementedBy.asInstanceOf[js.Dynamic], id)
          RemoteAgentClient(agentTypeName, id, agentType, new WasmRpcInvoker(rpcClient))
        }
      }

  private final class WasmRpcInvoker(client: WasmRpcClient) extends RpcInvoker {
    override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] =
      client.invokeAndAwait(functionName, params).left.map(_.toString)

    override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] =
      client.trigger(functionName, params).left.map(_.toString)

    override def scheduleInvocation(
      datetime: js.Dynamic,
      functionName: String,
      params: js.Array[js.Dynamic]
    ): Either[String, Unit] =
      client.scheduleInvocation(datetime, functionName, params).left.map(_.toString)
  }
}
