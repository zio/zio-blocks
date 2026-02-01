package golem.runtime.rpc

import golem.Datetime
import scala.scalajs.js

private[rpc] trait RpcInvoker {
  def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic]

  def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]

  def scheduleInvocation(datetime: Datetime, functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit]
}
