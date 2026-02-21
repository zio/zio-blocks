package golem.host

import golem.HostApi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:durability/durability@1.3.0`.
 *
 * WIT interface:
 * {{{
 *   type durable-function-type = wrapped-function-type;
 *   record durable-execution-state { is-live: bool, persistence-level: persistence-level }
 *   enum oplog-entry-version { v1, v2 }
 *   record persisted-durable-function-invocation {
 *     timestamp: datetime, function-name: string, response: value-and-type,
 *     function-type: durable-function-type, entry-version: oplog-entry-version
 *   }
 *   observe-function-call: func(iface: string, function: string)
 *   begin-durable-function: func(function-type: durable-function-type) -> oplog-index
 *   end-durable-function: func(function-type: durable-function-type, begin-index: oplog-index, forced-commit: bool)
 *   current-durable-execution-state: func() -> durable-execution-state
 *   persist-durable-function-invocation: func(function-name: string, request: value-and-type, response: value-and-type, function-type: durable-function-type)
 *   read-persisted-durable-function-invocation: func() -> persisted-durable-function-invocation
 * }}}
 */
object DurabilityApi {

  type OplogIndex = BigInt

  // --- WIT: wrapped-function-type variant (aliased as durable-function-type) ---

  sealed trait DurableFunctionType extends Product with Serializable {
    def tag: String
  }

  object DurableFunctionType {
    case object ReadLocal                                          extends DurableFunctionType { val tag = "read-local"   }
    case object WriteLocal                                         extends DurableFunctionType { val tag = "write-local"  }
    case object ReadRemote                                         extends DurableFunctionType { val tag = "read-remote"  }
    case object WriteRemote                                        extends DurableFunctionType { val tag = "write-remote" }
    final case class WriteRemoteBatched(begin: Option[OplogIndex]) extends DurableFunctionType {
      val tag = "write-remote-batched"
    }
    final case class WriteRemoteTransaction(begin: Option[OplogIndex]) extends DurableFunctionType {
      val tag = "write-remote-transaction"
    }

    def fromDynamic(raw: js.Dynamic): DurableFunctionType = {
      val tag = raw.tag.asInstanceOf[String]
      tag match {
        case "read-local"           => ReadLocal
        case "write-local"          => WriteLocal
        case "read-remote"          => ReadRemote
        case "write-remote"         => WriteRemote
        case "write-remote-batched" =>
          val v   = raw.selectDynamic("val")
          val idx = if (js.isUndefined(v) || v == null) None else Some(BigInt(v.toString))
          WriteRemoteBatched(idx)
        case "write-remote-transaction" =>
          val v   = raw.selectDynamic("val")
          val idx = if (js.isUndefined(v) || v == null) None else Some(BigInt(v.toString))
          WriteRemoteTransaction(idx)
        case other => throw new IllegalArgumentException(s"Unknown DurableFunctionType tag: $other")
      }
    }

    def toDynamic(ft: DurableFunctionType): js.Dynamic = ft match {
      case ReadLocal               => js.Dynamic.literal(tag = "read-local")
      case WriteLocal              => js.Dynamic.literal(tag = "write-local")
      case ReadRemote              => js.Dynamic.literal(tag = "read-remote")
      case WriteRemote             => js.Dynamic.literal(tag = "write-remote")
      case WriteRemoteBatched(idx) =>
        val v: js.Any = idx.map(i => js.BigInt(i.toString): js.Any).getOrElse(js.undefined)
        js.Dynamic.literal(tag = "write-remote-batched", `val` = v)
      case WriteRemoteTransaction(idx) =>
        val v: js.Any = idx.map(i => js.BigInt(i.toString): js.Any).getOrElse(js.undefined)
        js.Dynamic.literal(tag = "write-remote-transaction", `val` = v)
    }
  }

  // --- WIT: durable-execution-state record ---

  final case class DurableExecutionState(
    isLive: Boolean,
    persistenceLevel: HostApi.PersistenceLevel
  )

  // --- WIT: oplog-entry-version enum ---

  sealed trait OplogEntryVersion extends Product with Serializable
  object OplogEntryVersion {
    case object V1 extends OplogEntryVersion
    case object V2 extends OplogEntryVersion

    def fromString(s: String): OplogEntryVersion = s match {
      case "v1" => V1
      case "v2" => V2
      case _    => V1
    }
  }

  // --- WIT: persisted-durable-function-invocation record ---

  final case class PersistedDurableFunctionInvocation(
    timestampSeconds: BigInt,
    timestampNanos: Long,
    functionName: String,
    response: WitValueTypes.ValueAndType,
    functionType: DurableFunctionType,
    entryVersion: OplogEntryVersion
  )

  // --- Native bindings ---

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
    ): Unit                                              = js.native
    def readPersistedDurableFunctionInvocation(): js.Any = js.native
  }

  // --- Typed public API ---

  def observeFunctionCall(iface: String, function: String): Unit =
    DurabilityModule.observeFunctionCall(iface, function)

  def beginDurableFunction(functionType: DurableFunctionType): OplogIndex =
    BigInt(DurabilityModule.beginDurableFunction(DurableFunctionType.toDynamic(functionType)).toString)

  def endDurableFunction(functionType: DurableFunctionType, beginIndex: OplogIndex, forcedCommit: Boolean): Unit =
    DurabilityModule.endDurableFunction(
      DurableFunctionType.toDynamic(functionType),
      js.BigInt(beginIndex.toString),
      forcedCommit
    )

  def currentDurableExecutionState(): DurableExecutionState = {
    val raw   = DurabilityModule.currentDurableExecutionState().asInstanceOf[js.Dynamic]
    val live  = raw.isLive.asInstanceOf[Boolean]
    val plRaw = raw.persistenceLevel.asInstanceOf[js.Dynamic]
    val pl    = HostApi.PersistenceLevel.fromTag(plRaw.tag.asInstanceOf[String])
    DurableExecutionState(live, pl)
  }

  def persistDurableFunctionInvocation(
    functionName: String,
    request: WitValueTypes.ValueAndType,
    response: WitValueTypes.ValueAndType,
    functionType: DurableFunctionType
  ): Unit =
    DurabilityModule.persistDurableFunctionInvocation(
      functionName,
      WitValueTypes.ValueAndType.toDynamic(request),
      WitValueTypes.ValueAndType.toDynamic(response),
      DurableFunctionType.toDynamic(functionType)
    )

  def readPersistedDurableFunctionInvocation(): PersistedDurableFunctionInvocation = {
    val raw      = DurabilityModule.readPersistedDurableFunctionInvocation().asInstanceOf[js.Dynamic]
    val ts       = raw.timestamp.asInstanceOf[js.Dynamic]
    val seconds  = BigInt(ts.seconds.toString)
    val nanos    = ts.nanoseconds.asInstanceOf[Int].toLong
    val funcName = raw.functionName.asInstanceOf[String]
    val response = WitValueTypes.ValueAndType.fromDynamic(raw.response.asInstanceOf[js.Dynamic])
    val funcType = DurableFunctionType.fromDynamic(raw.functionType)
    val entryVer = OplogEntryVersion.fromString(raw.entryVersion.asInstanceOf[String])
    PersistedDurableFunctionInvocation(seconds, nanos, funcName, response, funcType, entryVer)
  }

  def raw: Any = DurabilityModule
}
