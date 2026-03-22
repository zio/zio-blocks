/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package golem.runtime.rpc

import golem.Datetime
import golem.Uuid
import golem.runtime.rpc.host.AgentHostApi.RegisteredAgentType
import golem.runtime.rpc.host.WasmRpcApi.WasmRpcClient
import golem.runtime.rpc.host.{AgentHostApi, WasmRpcApi}

import scala.scalajs.js
import scala.scalajs.js.JavaScriptException

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
        val displayTypeName = agentType.agentType.typeName
        AgentHostApi.makeAgentId(displayTypeName, constructorPayload, phantom).map { id =>
          val rpcClient = WasmRpcApi.newClient(agentType.implementedBy.asInstanceOf[js.Dynamic], id)
          RemoteAgentClient(displayTypeName, id, agentType, new WasmRpcInvoker(rpcClient))
        }
      }

  private final class WasmRpcInvoker(client: WasmRpcClient) extends RpcInvoker {
    override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] =
      invokeWithFallback(functionName)(fn => client.invokeAndAwait(fn, params).left.map(_.toString))

    override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] =
      invokeWithFallback(functionName)(fn => client.trigger(fn, params).left.map(_.toString))

    override def scheduleInvocation(
      datetime: Datetime,
      functionName: String,
      params: js.Array[js.Dynamic]
    ): Either[String, Unit] =
      invokeWithFallback(functionName)(fn => client.scheduleInvocation(datetime, fn, params).left.map(_.toString))
  }

  private def invokeWithFallback[A](functionName: String)(call: String => Either[String, A]): Either[String, A] =
    safeCall(call(functionName))

  private def safeCall[A](thunk: => Either[String, A]): Either[String, A] =
    try thunk
    catch {
      case JavaScriptException(err) => Left(err.toString)
    }

}
