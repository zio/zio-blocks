package cloud.golem.runtime.rpc

import scala.scalajs.js

private[rpc] trait RpcInvoker {
  def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic]

  def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]

  def scheduleInvocation(datetime: js.Dynamic, functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]
}
