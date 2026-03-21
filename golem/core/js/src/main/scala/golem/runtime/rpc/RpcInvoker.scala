package golem.runtime.rpc

import golem.Datetime
import golem.host.js._

private[rpc] trait RpcInvoker {
  def invokeAndAwait(functionName: String, input: JsDataValue): Either[String, JsDataValue]

  def invoke(functionName: String, input: JsDataValue): Either[String, Unit]

  def scheduleInvocation(datetime: Datetime, functionName: String, input: JsDataValue): Either[String, Unit]
}
