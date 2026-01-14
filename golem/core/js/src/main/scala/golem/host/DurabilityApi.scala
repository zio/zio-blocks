package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:durability/durability@1.3.0`.
 */
object DurabilityApi {

  type OplogIndex                 = js.Any
  type DurableExecutionState      = js.Any
  type PersistedInvocation        = js.Any
  type LazyInitializedPollable    = js.Any

  @js.native
  @JSImport("golem:durability/durability@1.3.0", JSImport.Namespace)
  private object DurabilityModule extends js.Object {
    def observeFunctionCall(iface: String, function: String): Unit                                   = js.native
    def beginDurableFunction(functionType: js.Any): OplogIndex                                       = js.native
    def endDurableFunction(functionType: js.Any, beginIndex: OplogIndex, forcedCommit: Boolean): Unit = js.native
    def currentDurableExecutionState(): DurableExecutionState                                        = js.native
    def persistDurableFunctionInvocation(
      functionName: String,
      request: js.Any,
      response: js.Any,
      functionType: js.Any
    ): Unit = js.native
    def readPersistedDurableFunctionInvocation(): PersistedInvocation = js.native
  }

  def observeFunctionCall(iface: String, function: String): Unit =
    DurabilityModule.observeFunctionCall(iface, function)

  def beginDurableFunction(functionType: js.Any): OplogIndex =
    DurabilityModule.beginDurableFunction(functionType)

  def endDurableFunction(functionType: js.Any, beginIndex: OplogIndex, forcedCommit: Boolean): Unit =
    DurabilityModule.endDurableFunction(functionType, beginIndex, forcedCommit)

  def currentDurableExecutionState(): DurableExecutionState =
    DurabilityModule.currentDurableExecutionState()

  def persistDurableFunctionInvocation(
    functionName: String,
    request: js.Any,
    response: js.Any,
    functionType: js.Any
  ): Unit =
    DurabilityModule.persistDurableFunctionInvocation(functionName, request, response, functionType)

  def readPersistedDurableFunctionInvocation(): PersistedInvocation =
    DurabilityModule.readPersistedDurableFunctionInvocation()

  /** Low-level access to the imported host module. */
  def raw: js.Dynamic =
    DurabilityModule.asInstanceOf[js.Dynamic]
}

