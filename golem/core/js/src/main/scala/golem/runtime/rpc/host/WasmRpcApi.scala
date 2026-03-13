package golem.runtime.rpc.host

import golem.Datetime
import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

private[golem] object WasmRpcApi {
  def newClient(componentId: js.Dynamic, agentId: String): WasmRpcClient = {
    // golem:rpc/types@0.2.2 expects AgentId { componentId: ComponentId, agentId: string }
    val agentRef = js.Dynamic.literal(
      "componentId" -> componentId,
      "agentId"     -> agentId
    )
    new WasmRpcClient(new RawWasmRpc(agentRef))
  }

  private def decodeRpcError(value: js.Dynamic): RpcError =
    value.selectDynamic("tag").asInstanceOf[String] match {
      case "protocol-error"        => RpcError("protocol-error", Some(value.selectDynamic("val").asInstanceOf[String]))
      case "denied"                => RpcError("denied", Some(value.selectDynamic("val").asInstanceOf[String]))
      case "not-found"             => RpcError("not-found", Some(value.selectDynamic("val").asInstanceOf[String]))
      case "remote-internal-error" =>
        RpcError("remote-internal-error", Some(value.selectDynamic("val").asInstanceOf[String]))
      case other => RpcError(other, None)
    }

  final class WasmRpcClient private[host] (private val underlying: js.Object) {
    def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[RpcError, js.Dynamic] =
      try Right(raw.invokeAndAwait(functionName, params))
      catch {
        case js.JavaScriptException(e) =>
          Left(decodeRpcError(e.asInstanceOf[js.Dynamic]))
      }

    def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[RpcError, Unit] =
      try {
        raw.invoke(functionName, params)
        Right(())
      } catch {
        case js.JavaScriptException(e) =>
          Left(decodeRpcError(e.asInstanceOf[js.Dynamic]))
      }

    private def raw: RawWasmRpc =
      underlying.asInstanceOf[RawWasmRpc]

    def scheduleInvocation(
      datetime: Datetime,
      functionName: String,
      params: js.Array[js.Dynamic]
    ): Either[RpcError, Unit] =
      try {
        // Host expects a Datetime value; currently represented as `{ ts: <epochMillis> }`.
        raw.scheduleInvocation(js.Dynamic.literal("ts" -> datetime.epochMillis), functionName, params)
        Right(())
      } catch {
        case js.JavaScriptException(e) =>
          Left(decodeRpcError(e.asInstanceOf[js.Dynamic]))
      }
  }

  final case class RpcError(kind: String, message: Option[String]) {
    override def toString: String =
      message.fold(kind)(text => s"$kind: $text")
  }

  @js.native
  @JSImport("golem:rpc/types@0.2.2", "WasmRpc")
  private final class RawWasmRpc(@unused agentId: js.Dynamic) extends js.Object {
    def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): js.Dynamic                     = js.native
    def invoke(functionName: String, params: js.Array[js.Dynamic]): Unit                                   = js.native
    def scheduleInvocation(datetime: js.Dynamic, functionName: String, params: js.Array[js.Dynamic]): Unit = js.native
  }
}
