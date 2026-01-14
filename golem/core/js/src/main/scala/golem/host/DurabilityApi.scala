package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:durability/durability@1.3.0`.
 */
object DurabilityApi {

  type OplogIndex = BigInt
  type DurableExecutionState = Any
  type PersistedInvocation   = Any

  @js.native
  @JSImport("golem:durability/durability@1.3.0", JSImport.Namespace)
  private object DurabilityModule extends js.Object {
    def observeFunctionCall(iface: String, function: String): Unit                                   = js.native
    def beginDurableFunction(functionType: js.Any): js.BigInt                                        = js.native
    def endDurableFunction(functionType: js.Any, beginIndex: js.BigInt, forcedCommit: Boolean): Unit = js.native
    def currentDurableExecutionState(): js.Any                                                       = js.native
    def persistDurableFunctionInvocation(
      functionName: String,
      request: js.Any,
      response: js.Any,
      functionType: js.Any
    ): Unit = js.native
    def readPersistedDurableFunctionInvocation(): js.Any = js.native
  }

  def observeFunctionCall(iface: String, function: String): Unit =
    DurabilityModule.observeFunctionCall(iface, function)

  def beginDurableFunction(functionType: Any): OplogIndex =
    BigInt(DurabilityModule.beginDurableFunction(functionType.asInstanceOf[js.Any]).toString)

  def endDurableFunction(functionType: Any, beginIndex: OplogIndex, forcedCommit: Boolean): Unit =
    DurabilityModule.endDurableFunction(functionType.asInstanceOf[js.Any], js.BigInt(beginIndex.toString), forcedCommit)

  def currentDurableExecutionState(): DurableExecutionState =
    DurabilityModule.currentDurableExecutionState()

  def persistDurableFunctionInvocation(
    functionName: String,
    request: Any,
    response: Any,
    functionType: Any
  ): Unit =
    DurabilityModule.persistDurableFunctionInvocation(
      functionName,
      request.asInstanceOf[js.Any],
      response.asInstanceOf[js.Any],
      functionType.asInstanceOf[js.Any]
    )

  def readPersistedDurableFunctionInvocation(): PersistedInvocation =
    DurabilityModule.readPersistedDurableFunctionInvocation()

  /** Low-level access to the imported host module. */
  def raw: Any =
    DurabilityModule
}

