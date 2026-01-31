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
    resolveWithFallback(agentTypeName, constructorPayload, phantom)

  private def resolveWithFallback(
    agentTypeName: String,
    constructorPayload: js.Dynamic,
    phantom: Option[Uuid]
  ): Either[String, RemoteAgentClient] = {
    def resolveOnce(name: String): Either[String, RemoteAgentClient] =
      AgentHostApi
        .registeredAgentType(name)
        .toRight(s"Agent type '$name' is not registered on this host")
        .flatMap { agentType =>
          val resolvedTypeName = agentType.agentType.typeName
          AgentHostApi.makeAgentId(resolvedTypeName, constructorPayload, phantom).map { id =>
            val rpcClient = WasmRpcApi.newClient(agentType.implementedBy.asInstanceOf[js.Dynamic], id)
            RemoteAgentClient(resolvedTypeName, id, agentType, new WasmRpcInvoker(rpcClient))
          }
        }

    resolveOnce(agentTypeName) match {
      case ok @ Right(_) => ok
      case Left(err) =>
        kebabCase(agentTypeName) match {
          case fallbackName if fallbackName != agentTypeName =>
            resolveOnce(fallbackName) match {
              case ok @ Right(_) => ok
              case Left(fallbackErr) =>
                Left(s"$err (fallback: $fallbackErr)")
            }
          case _ => Left(err)
        }
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
    safeCall(call(functionName)) match {
      case ok @ Right(_) => ok
      case Left(err) =>
        if (isMissingInterface(err)) {
          fallbackFunctionName(functionName) match {
            case Some(fallback) =>
              safeCall(call(fallback)).left.map(fallbackErr => s"$err (fallback: $fallbackErr)")
            case None => Left(err)
          }
        } else Left(err)
    }

  private def safeCall[A](thunk: => Either[String, A]): Either[String, A] =
    try thunk
    catch {
      case JavaScriptException(err) => Left(err.toString)
    }

  private def isMissingInterface(message: String): Boolean =
    message.contains("could not load exports for interface")

  private def fallbackFunctionName(functionName: String): Option[String] = {
    val methodIdx = functionName.indexOf(".{")
    if (methodIdx <= 0) return None

    val prefix  = functionName.substring(0, methodIdx)
    val suffix  = functionName.substring(methodIdx)
    val slashAt = prefix.lastIndexOf('/')
    val (base, agentName) =
      if (slashAt >= 0) (prefix.substring(0, slashAt + 1), prefix.substring(slashAt + 1))
      else ("", prefix)

    val kebab = kebabCase(agentName)
    if (kebab == agentName) None else Some(base + kebab + suffix)
  }

  private def kebabCase(value: String): String = {
    val builder = new StringBuilder(value.length * 2)
    var i       = 0
    var prevWasDash = false
    while (i < value.length) {
      val ch = value.charAt(i)
      if (ch == '_' || ch == ' ') {
        if (!prevWasDash && builder.nonEmpty) {
          builder.append('-')
          prevWasDash = true
        }
      } else if (ch.isUpper) {
        if (builder.nonEmpty && !prevWasDash) builder.append('-')
        builder.append(ch.toLower)
        prevWasDash = false
      } else {
        builder.append(ch)
        prevWasDash = ch == '-'
      }
      i += 1
    }
    builder.toString
  }
}
