package golem.host.js

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

// ---------------------------------------------------------------------------
// golem:durability/durability@1.5.0  –  JS facade traits
// ---------------------------------------------------------------------------

// --- WrappedFunctionType / DurableFunctionType  –  tagged union ---

@js.native
sealed trait JsWrappedFunctionType extends js.Object {
  def tag: String = js.native
}

@js.native
sealed trait JsWrappedFunctionTypeBatched extends JsWrappedFunctionType {
  @JSName("val") def value: js.UndefOr[js.BigInt] = js.native
}

@js.native
sealed trait JsWrappedFunctionTypeTransaction extends JsWrappedFunctionType {
  @JSName("val") def value: js.UndefOr[js.BigInt] = js.native
}

object JsWrappedFunctionType {
  def readLocal: JsWrappedFunctionType  = JsShape.tagOnly[JsWrappedFunctionType]("read-local")
  def writeLocal: JsWrappedFunctionType = JsShape.tagOnly[JsWrappedFunctionType]("write-local")
  def readRemote: JsWrappedFunctionType = JsShape.tagOnly[JsWrappedFunctionType]("read-remote")
  def writeRemote: JsWrappedFunctionType = JsShape.tagOnly[JsWrappedFunctionType]("write-remote")

  def writeRemoteBatched(beginIndex: js.UndefOr[js.BigInt]): JsWrappedFunctionType =
    JsShape.taggedOptional[JsWrappedFunctionType]("write-remote-batched", beginIndex.map(_.asInstanceOf[js.Any]))

  def writeRemoteTransaction(beginIndex: js.UndefOr[js.BigInt]): JsWrappedFunctionType =
    JsShape.taggedOptional[JsWrappedFunctionType]("write-remote-transaction", beginIndex.map(_.asInstanceOf[js.Any]))
}

type JsDurableFunctionType = JsWrappedFunctionType

// --- DurableExecutionState ---

@js.native
sealed trait JsDurableExecutionState extends js.Object {
  def isLive: Boolean                    = js.native
  def persistenceLevel: JsPersistenceLevel = js.native
}

object JsDurableExecutionState {
  def apply(isLive: Boolean, persistenceLevel: JsPersistenceLevel): JsDurableExecutionState =
    js.Dynamic.literal("isLive" -> isLive, "persistenceLevel" -> persistenceLevel).asInstanceOf[JsDurableExecutionState]
}

// --- OplogEntryVersion (plain string) ---

type JsOplogEntryVersion = String // "v1" | "v2"

// --- PersistedDurableFunctionInvocation ---

@js.native
sealed trait JsPersistedDurableFunctionInvocation extends js.Object {
  def timestamp: JsDatetime              = js.native
  def functionName: String               = js.native
  def response: JsValueAndType          = js.native
  def functionType: JsDurableFunctionType = js.native
  def entryVersion: JsOplogEntryVersion  = js.native
}

object JsPersistedDurableFunctionInvocation {
  def apply(
    timestamp: JsDatetime,
    functionName: String,
    response: JsValueAndType,
    functionType: JsDurableFunctionType,
    entryVersion: JsOplogEntryVersion
  ): JsPersistedDurableFunctionInvocation =
    js.Dynamic
      .literal(
        "timestamp"    -> timestamp,
        "functionName" -> functionName,
        "response"     -> response,
        "functionType" -> functionType,
        "entryVersion" -> entryVersion
      )
      .asInstanceOf[JsPersistedDurableFunctionInvocation]
}
