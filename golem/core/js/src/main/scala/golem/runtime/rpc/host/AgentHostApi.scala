package golem.runtime.rpc.host

import golem.Uuid

import scala.scalajs.js
import scala.scalajs.js.BigInt
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSImport, JSName}
import scala.scalajs.js.typedarray.Uint8Array

object AgentHostApi {
  type OplogIndex       = BigInt
  type ComponentVersion = BigInt
  private lazy val getAgentsConstructor: js.Dynamic =
    HostModule.asInstanceOf[js.Dynamic].selectDynamic("GetAgents")

  def registeredAgentType(typeName: String): Option[RegisteredAgentType] = {
    val v = AgentRegistryModule.getAgentType(typeName)
    if (v == null || js.isUndefined(v)) None else Some(v)
  }

  def getAllAgentTypes(): List[RegisteredAgentType] =
    AgentRegistryModule.getAllAgentTypes().toList

  def makeAgentId(agentTypeName: String, payload: js.Dynamic, phantom: Option[Uuid]): Either[String, String] = {
    val phantomArg = phantom.fold[js.Any](js.undefined)(uuid => toUuidLiteral(uuid))
    try Right(AgentRegistryModule.makeAgentId(agentTypeName, payload, phantomArg))
    catch {
      case js.JavaScriptException(err) => Left(err.toString)
    }
  }

  def parseAgentId(agentId: String): Either[String, AgentIdParts] =
    try {
      val tuple                 = AgentRegistryModule.parseAgentId(agentId)
      val agentType             = tuple(0).asInstanceOf[String]
      val dataValue             = tuple(1).asInstanceOf[js.Dynamic]
      val phantomValue          = tuple(2)
      val phantom: Option[Uuid] =
        if (phantomValue == null || js.isUndefined(phantomValue)) None
        else Some(fromUuidLiteral(phantomValue.asInstanceOf[UuidLiteral]))
      Right(AgentIdParts(agentType, dataValue, phantom))
    } catch {
      case js.JavaScriptException(err) => Left(err.toString)
    }

  def resolveComponentId(componentReference: String): Option[ComponentIdLiteral] =
    toOption(HostModule.resolveComponentId(componentReference))

  def resolveAgentId(componentReference: String, agentName: String): Option[AgentIdLiteral] =
    toOption(HostModule.resolveAgentId(componentReference, agentName))

  def resolveAgentIdStrict(componentReference: String, agentName: String): Option[AgentIdLiteral] =
    toOption(HostModule.resolveAgentIdStrict(componentReference, agentName))

  def getSelfMetadata(): AgentMetadata =
    HostModule.getSelfMetadata().asInstanceOf[AgentMetadata]

  def getAgentMetadata(agentId: AgentIdLiteral): Option[AgentMetadata] =
    toOption(HostModule.getAgentMetadata(agentId))

  private def toOption[A](value: js.Any): Option[A] =
    if (value == null || js.isUndefined(value)) None else Some(value.asInstanceOf[A])

  def getAgents(componentId: ComponentIdLiteral, filter: Option[AgentAnyFilter], precise: Boolean): GetAgentsHandle = {
    val filterArg = filter.fold[js.Any](js.undefined)(identity)
    try
      js.Dynamic
        .newInstance(getAgentsConstructor)(componentId, filterArg, precise)
        .asInstanceOf[GetAgentsHandle]
    catch {
      case js.JavaScriptException(err) =>
        val exports = js.Object.keys(HostModule.asInstanceOf[js.Object]).toList.mkString(",")
        val detail  = s"type=${js.typeOf(err)}, value=${err.toString}"
        throw new IllegalStateException(s"golem host get-agents unavailable (exports=[$exports]): $detail")
    }
  }

  def nextAgentBatch(handle: GetAgentsHandle): Option[List[AgentMetadata]] =
    invokeGetAgentsNext(handle).toOption.map(_.toList)

  private def invokeGetAgentsNext(handle: GetAgentsHandle): js.UndefOr[js.Array[AgentMetadata]] = {
    val dynamicHandle = handle.asInstanceOf[js.Dynamic]
    val camelCase     = dynamicHandle.selectDynamic("getNext")
    val dashedCase    = dynamicHandle.selectDynamic("get-next")
    val fn            =
      if (!js.isUndefined(camelCase) && camelCase != null) camelCase
      else if (!js.isUndefined(dashedCase) && dashedCase != null) dashedCase
      else {
        val protoObj   = js.Object.getPrototypeOf(dynamicHandle.asInstanceOf[js.Object]).asInstanceOf[js.Object]
        val protoKeys  = js.Object.keys(protoObj).toList.mkString(",")
        val ownKeys    = js.Object.keys(dynamicHandle.asInstanceOf[js.Object]).toList.mkString(",")
        val detailInfo = s"own=[$ownKeys], proto=[$protoKeys]"
        throw new IllegalStateException(s"get-agents handle missing getNext/get-next functions ($detailInfo)")
      }
    fn.call(handle).asInstanceOf[js.UndefOr[js.Array[AgentMetadata]]]
  }

  def createPromise(): PromiseIdLiteral =
    HostModule.createPromise().asInstanceOf[PromiseIdLiteral]

  def getPromise(promiseId: PromiseIdLiteral): GetPromiseResultHandle =
    HostModule.getPromise(promiseId).asInstanceOf[GetPromiseResultHandle]

  def completePromise(promiseId: PromiseIdLiteral, data: Uint8Array): Boolean =
    HostModule.completePromise(promiseId, data)

  def getOplogIndex(): OplogIndex =
    HostModule.getOplogIndex()

  def setOplogIndex(index: OplogIndex): Unit =
    HostModule.setOplogIndex(index)

  def markBeginOperation(): OplogIndex =
    HostModule.markBeginOperation()

  def markEndOperation(begin: OplogIndex): Unit =
    HostModule.markEndOperation(begin)

  def oplogCommit(replicas: Int): Unit =
    HostModule.oplogCommit(replicas)

  def getRetryPolicy(): RetryPolicy =
    HostModule.getRetryPolicy().asInstanceOf[RetryPolicy]

  def setRetryPolicy(policy: RetryPolicy): Unit =
    HostModule.setRetryPolicy(policy)

  def getOplogPersistenceLevel(): PersistenceLevel =
    HostModule.getOplogPersistenceLevel().asInstanceOf[PersistenceLevel]

  def setOplogPersistenceLevel(level: PersistenceLevel): Unit =
    HostModule.setOplogPersistenceLevel(level)

  def getIdempotenceMode(): Boolean =
    HostModule.getIdempotenceMode()

  def setIdempotenceMode(flag: Boolean): Unit =
    HostModule.setIdempotenceMode(flag)

  def generateIdempotencyKey(): UuidLiteral =
    HostModule.generateIdempotencyKey()

  def updateAgent(agentId: AgentIdLiteral, targetVersion: ComponentVersion, mode: UpdateMode): Unit =
    HostModule.updateAgent(agentId, targetVersion, mode)

  def forkAgent(sourceAgentId: AgentIdLiteral, targetAgentId: AgentIdLiteral, cutOff: OplogIndex): Unit =
    HostModule.forkAgent(sourceAgentId, targetAgentId, cutOff)

  def revertAgent(agentId: AgentIdLiteral, target: RevertAgentTarget): Unit =
    HostModule.revertAgent(agentId, target)

  def fork(): (String, UuidLiteral) = {
    val raw       = HostModule.fork().asInstanceOf[js.Dynamic]
    val tag       = raw.tag.asInstanceOf[String]
    val details   = raw.selectDynamic("val").asInstanceOf[js.Dynamic]
    val phantomId = details.forkedPhantomId.asInstanceOf[UuidLiteral]
    (tag, phantomId)
  }

  private def tagOnly(tag: String): js.Dynamic =
    js.Dynamic.literal("tag" -> tag)

  private def tagWithValue(tag: String, value: js.Any): js.Dynamic = {
    val literal = js.Dynamic.literal("tag" -> tag)
    literal.updateDynamic("val")(value)
    literal
  }

  @js.native
  sealed trait AgentMetadata extends js.Object {

    // WIT-defined fields (golem:api/host@1.3.0 agent-metadata record)
    def agentId: AgentIdLiteral = js.native

    def args: js.Array[String] = js.native

    def env: js.Array[js.Tuple2[String, String]] = js.native

    def configVars: js.Array[js.Tuple2[String, String]] = js.native

    def status: AgentStatus = js.native

    def componentRevision: BigInt = js.native

    def retryCount: BigInt = js.native

    // Runtime-provided fields (beyond WIT minimum)
    def agentType: String = js.native

    def agentName: String = js.native

    def componentId: ComponentIdLiteral = js.native
  }

  @js.native
  sealed trait RetryPolicy extends js.Object {
    // WIT: retry-policy { max-attempts: u32, min-delay: duration(u64 nanos), max-delay: duration(u64 nanos),
    //                     multiplier: f64, max-jitter-factor: option<f64> }
    // jco guest-types: camelCase field names.
    def maxAttempts: Int = js.native

    def minDelay: BigInt = js.native

    def maxDelay: BigInt = js.native

    def multiplier: Double = js.native

    def maxJitterFactor: js.UndefOr[Double] = js.native
  }

  @js.native
  sealed trait PersistenceLevel extends js.Any

  @js.native
  sealed trait AgentStatus extends js.Any

  @js.native
  sealed trait UpdateMode extends js.Any

  @js.native
  sealed trait FilterComparator extends js.Any

  @js.native
  sealed trait StringFilterComparator extends js.Any

  @js.native
  sealed trait AgentPropertyFilter extends js.Object

  @js.native
  sealed trait RevertAgentTarget extends js.Any

  @js.native
  trait RegisteredAgentType extends js.Object {
    def agentType: AgentTypeDescriptor = js.native

    def implementedBy: ComponentIdLiteral = js.native
  }

  @js.native
  trait AgentTypeDescriptor extends js.Object {
    def typeName: String = js.native
  }

  @js.native
  trait ComponentIdLiteral extends js.Object {
    def uuid: UuidLiteral = js.native
  }

  @js.native
  trait AgentIdLiteral extends js.Object {
    def componentId: ComponentIdLiteral = js.native

    def agentId: String = js.native
  }

  @js.native
  trait PromiseIdLiteral extends js.Object {
    def agentId: AgentIdLiteral = js.native

    def oplogIdx: OplogIndex = js.native
  }

  @js.native
  trait GetAgentsHandle extends js.Object

  @js.native
  trait GetPromiseResultHandle extends js.Object {
    def subscribe(): Pollable = js.native

    def get(): PromiseResult = js.native
  }

  @js.native
  trait PromiseResult extends js.Object {
    @JSName("is_some")
    def isSome: Boolean = js.native

    def get(): Uint8Array = js.native
  }

  @js.native
  trait Pollable extends js.Object {
    def ready(): Boolean = js.native

    def block(): Unit = js.native
  }

  @js.native
  trait UuidLiteral extends js.Object {
    def highBits: BigInt = js.native

    def lowBits: BigInt = js.native
  }

  @js.native
  trait AgentNameFilter extends js.Object {
    def comparator: StringFilterComparator = js.native

    def value: String = js.native
  }

  @js.native
  trait AgentStatusFilter extends js.Object {
    def comparator: FilterComparator = js.native

    def value: AgentStatus = js.native
  }

  @js.native
  trait AgentVersionFilter extends js.Object {
    def comparator: FilterComparator = js.native

    def value: BigInt = js.native
  }

  @js.native
  trait AgentCreatedAtFilter extends js.Object {
    def comparator: FilterComparator = js.native

    def value: BigInt = js.native
  }

  @js.native
  trait AgentEnvFilter extends js.Object {
    def name: String = js.native

    def comparator: StringFilterComparator = js.native

    def value: String = js.native
  }

  @js.native
  trait AgentConfigVarsFilter extends js.Object {
    def name: String = js.native

    def comparator: StringFilterComparator = js.native

    def value: String = js.native
  }

  @js.native
  trait AgentAllFilter extends js.Object {
    def filters: js.Array[AgentPropertyFilter] = js.native
  }

  @js.native
  trait AgentAnyFilter extends js.Object {
    def filters: js.Array[AgentAllFilter] = js.native
  }

  final case class AgentIdParts(agentTypeName: String, payload: js.Dynamic, phantom: Option[Uuid])

  object ComponentIdLiteral {
    def apply(uuid: UuidLiteral): ComponentIdLiteral =
      js.Dynamic.literal("uuid" -> uuid).asInstanceOf[ComponentIdLiteral]
  }

  object AgentIdLiteral {
    def apply(componentId: ComponentIdLiteral, agentId: String): AgentIdLiteral =
      js.Dynamic
        .literal(
          "component-id" -> componentId,
          "agent-id"     -> agentId
        )
        .asInstanceOf[AgentIdLiteral]
  }

  object PromiseIdLiteral {
    def apply(agentId: AgentIdLiteral, oplogIndex: OplogIndex): PromiseIdLiteral =
      js.Dynamic
        .literal(
          "agentId"  -> agentId,
          "oplogIdx" -> oplogIndex
        )
        .asInstanceOf[PromiseIdLiteral]
  }

  object UuidLiteral {
    def apply(highBits: BigInt, lowBits: BigInt): UuidLiteral =
      js.Dynamic
        .literal(
          "highBits" -> highBits,
          "lowBits"  -> lowBits
        )
        .asInstanceOf[UuidLiteral]
  }

  private def toUuidLiteral(uuid: Uuid): UuidLiteral =
    UuidLiteral(
      highBits = js.BigInt(uuid.highBits.toString),
      lowBits = js.BigInt(uuid.lowBits.toString)
    )

  private def fromUuidLiteral(uuid: UuidLiteral): Uuid =
    Uuid(
      highBits = _root_.scala.BigInt(uuid.highBits.toString),
      lowBits = _root_.scala.BigInt(uuid.lowBits.toString)
    )

  object RetryPolicy {
    def apply(maxAttempts: BigInt, initialDelayMs: BigInt, maxDelayMs: BigInt, factor: Double): RetryPolicy =
      js.Dynamic
        .literal(
          "maxAttempts"    -> maxAttempts,
          "initialDelayMs" -> initialDelayMs,
          "maxDelayMs"     -> maxDelayMs,
          "factor"         -> factor
        )
        .asInstanceOf[RetryPolicy]
  }

  object PersistenceLevel {
    def PersistNothing: PersistenceLevel =
      tagOnly("persist-nothing").asInstanceOf[PersistenceLevel]

    def PersistRemoteSideEffects: PersistenceLevel =
      tagOnly("persist-remote-side-effects").asInstanceOf[PersistenceLevel]

    def Smart: PersistenceLevel =
      tagOnly("smart").asInstanceOf[PersistenceLevel]
  }

  object AgentStatus {
    def Running: AgentStatus = tagOnly("running").asInstanceOf[AgentStatus]

    def Idle: AgentStatus = tagOnly("idle").asInstanceOf[AgentStatus]

    def Suspended: AgentStatus = tagOnly("suspended").asInstanceOf[AgentStatus]

    def Interrupted: AgentStatus = tagOnly("interrupted").asInstanceOf[AgentStatus]

    def Retrying: AgentStatus = tagOnly("retrying").asInstanceOf[AgentStatus]

    def Failed: AgentStatus = tagOnly("failed").asInstanceOf[AgentStatus]

    def Exited: AgentStatus = tagOnly("exited").asInstanceOf[AgentStatus]
  }

  object UpdateMode {
    def Automatic: UpdateMode =
      tagOnly("automatic").asInstanceOf[UpdateMode]

    def SnapshotBased: UpdateMode =
      tagOnly("snapshot-based").asInstanceOf[UpdateMode]
  }

  object FilterComparator {
    def Equal: FilterComparator = "equal".asInstanceOf[FilterComparator]

    def NotEqual: FilterComparator = "not-equal".asInstanceOf[FilterComparator]

    def GreaterEqual: FilterComparator = "greater-equal".asInstanceOf[FilterComparator]

    def Greater: FilterComparator = "greater".asInstanceOf[FilterComparator]

    def LessEqual: FilterComparator = "less-equal".asInstanceOf[FilterComparator]

    def Less: FilterComparator = "less".asInstanceOf[FilterComparator]
  }

  object StringFilterComparator {
    def Equal: StringFilterComparator = "equal".asInstanceOf[StringFilterComparator]

    def NotEqual: StringFilterComparator = "not-equal".asInstanceOf[StringFilterComparator]

    def Like: StringFilterComparator = "like".asInstanceOf[StringFilterComparator]

    def NotLike: StringFilterComparator = "not-like".asInstanceOf[StringFilterComparator]

    def StartsWith: StringFilterComparator = "starts-with".asInstanceOf[StringFilterComparator]
  }

  object AgentNameFilter {
    def apply(comparator: StringFilterComparator, value: String): AgentNameFilter =
      js.Dynamic
        .literal(
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentNameFilter]
  }

  object AgentStatusFilter {
    def apply(comparator: FilterComparator, value: AgentStatus): AgentStatusFilter =
      js.Dynamic
        .literal(
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentStatusFilter]
  }

  object AgentVersionFilter {
    def apply(comparator: FilterComparator, value: BigInt): AgentVersionFilter =
      js.Dynamic
        .literal(
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentVersionFilter]
  }

  object AgentCreatedAtFilter {
    def apply(comparator: FilterComparator, value: BigInt): AgentCreatedAtFilter =
      js.Dynamic
        .literal(
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentCreatedAtFilter]
  }

  object AgentEnvFilter {
    def apply(name: String, comparator: StringFilterComparator, value: String): AgentEnvFilter =
      js.Dynamic
        .literal(
          "name"       -> name,
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentEnvFilter]
  }

  object AgentConfigVarsFilter {
    def apply(name: String, comparator: StringFilterComparator, value: String): AgentConfigVarsFilter =
      js.Dynamic
        .literal(
          "name"       -> name,
          "comparator" -> comparator,
          "value"      -> value
        )
        .asInstanceOf[AgentConfigVarsFilter]
  }

  object AgentPropertyFilter {
    def name(filter: AgentNameFilter): AgentPropertyFilter =
      tagWithValue("name", filter).asInstanceOf[AgentPropertyFilter]

    def status(filter: AgentStatusFilter): AgentPropertyFilter =
      tagWithValue("status", filter).asInstanceOf[AgentPropertyFilter]

    def version(filter: AgentVersionFilter): AgentPropertyFilter =
      tagWithValue("version", filter).asInstanceOf[AgentPropertyFilter]

    def createdAt(filter: AgentCreatedAtFilter): AgentPropertyFilter =
      tagWithValue("created-at", filter).asInstanceOf[AgentPropertyFilter]

    def env(filter: AgentEnvFilter): AgentPropertyFilter =
      tagWithValue("env", filter).asInstanceOf[AgentPropertyFilter]

    def wasiConfigVars(filter: AgentConfigVarsFilter): AgentPropertyFilter =
      tagWithValue("wasi-config-vars", filter).asInstanceOf[AgentPropertyFilter]
  }

  object AgentAllFilter {
    def apply(filters: List[AgentPropertyFilter]): AgentAllFilter =
      js.Dynamic
        .literal(
          "filters" -> filters.toJSArray
        )
        .asInstanceOf[AgentAllFilter]
  }

  object AgentAnyFilter {
    def apply(filters: List[AgentAllFilter]): AgentAnyFilter =
      js.Dynamic
        .literal(
          "filters" -> filters.toJSArray
        )
        .asInstanceOf[AgentAnyFilter]
  }

  object RevertAgentTarget {
    def RevertToOplogIndex(index: OplogIndex): RevertAgentTarget =
      tagWithValue("revert-to-oplog-index", index).asInstanceOf[RevertAgentTarget]

    def RevertLastInvocations(count: BigInt): RevertAgentTarget =
      tagWithValue("revert-last-invocations", count).asInstanceOf[RevertAgentTarget]
  }

  @js.native
  @JSImport("golem:api/host@1.3.0", JSImport.Namespace)
  private object HostModule extends js.Object {
    def resolveComponentId(componentReference: String): js.Any = js.native

    def resolveAgentId(componentReference: String, agentName: String): js.Any = js.native

    def resolveAgentIdStrict(componentReference: String, agentName: String): js.Any = js.native

    def getSelfMetadata(): js.Any = js.native

    def getAgentMetadata(agentId: AgentIdLiteral): js.Any = js.native

    def createPromise(): js.Any = js.native

    def getPromise(promiseId: PromiseIdLiteral): js.Any = js.native

    def completePromise(promiseId: PromiseIdLiteral, data: Uint8Array): Boolean = js.native

    def getOplogIndex(): OplogIndex = js.native

    def setOplogIndex(index: OplogIndex): Unit = js.native

    def markBeginOperation(): OplogIndex = js.native

    def markEndOperation(begin: OplogIndex): Unit = js.native

    def oplogCommit(replicas: Int): Unit = js.native

    def getRetryPolicy(): js.Any = js.native

    def setRetryPolicy(policy: RetryPolicy): Unit = js.native

    def getOplogPersistenceLevel(): js.Any = js.native

    def setOplogPersistenceLevel(level: PersistenceLevel): Unit = js.native

    def getIdempotenceMode(): Boolean = js.native

    def setIdempotenceMode(flag: Boolean): Unit = js.native

    def generateIdempotencyKey(): UuidLiteral = js.native

    def updateAgent(agentId: AgentIdLiteral, targetVersion: ComponentVersion, mode: UpdateMode): Unit =
      js.native

    def forkAgent(sourceAgentId: AgentIdLiteral, targetAgentId: AgentIdLiteral, oplogIdxCutOff: OplogIndex): Unit =
      js.native

    def revertAgent(agentId: AgentIdLiteral, target: RevertAgentTarget): Unit = js.native

    def fork(): js.Any = js.native
  }

  @js.native
  @JSImport("golem:agent/host", JSImport.Namespace)
  private object AgentRegistryModule extends js.Object {
    def getAgentType(typeName: String): RegisteredAgentType = js.native

    def getAllAgentTypes(): js.Array[RegisteredAgentType] = js.native

    def makeAgentId(agentTypeName: String, input: js.Dynamic, phantom: js.Any): String = js.native

    def parseAgentId(agentId: String): js.Array[js.Any] = js.native
  }
}
