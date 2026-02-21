package golem

import golem.runtime.rpc.host.AgentHostApi
import golem.Uuid

import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonBinaryCodec, JsonBinaryCodecDeriver}

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.Dictionary
import scala.scalajs.js.timers
import scala.scalajs.js.typedarray.Uint8Array

/**
 * Public Scala.js SDK access to Golem's runtime host API.
 *
 * Scala.js-only API (delegates to `golem:api/host@1.3.0`).
 */
object HostApi {
  // ----- Core oplog / atomic region ---------------------------------------------------------

  type OplogIndex = BigInt

  def getOplogIndex(): OplogIndex =
    fromJsBigInt(AgentHostApi.getOplogIndex())

  def setOplogIndex(index: OplogIndex): Unit =
    AgentHostApi.setOplogIndex(toJsBigInt(index))

  def markBeginOperation(): OplogIndex =
    fromJsBigInt(AgentHostApi.markBeginOperation())

  def markEndOperation(begin: OplogIndex): Unit =
    AgentHostApi.markEndOperation(toJsBigInt(begin))

  def oplogCommit(replicas: Int): Unit =
    AgentHostApi.oplogCommit(replicas)

  // ----- Retry policy ----------------------------------------------------------------------

  /**
   * Retry policy as defined by `golem:api/host@1.3.0`.
   *
   *   - `maxAttempts`: u32
   *   - `minDelayNanos`: duration (u64, in nanoseconds)
   *   - `maxDelayNanos`: duration (u64, in nanoseconds)
   *   - `multiplier`: f64
   *   - `maxJitterFactor`: option<f64>
   */
  final case class RetryPolicy(
    maxAttempts: Int,
    minDelayNanos: BigInt,
    maxDelayNanos: BigInt,
    multiplier: Double,
    maxJitterFactor: Option[Double]
  )

  def getRetryPolicy(): RetryPolicy =
    fromHostRetryPolicy(AgentHostApi.getRetryPolicy())

  def setRetryPolicy(policy: RetryPolicy): Unit =
    AgentHostApi.setRetryPolicy(toHostRetryPolicy(policy))

  // ----- Persistence level -----------------------------------------------------------------

  sealed trait PersistenceLevel extends Product with Serializable {
    def tag: String
  }

  object PersistenceLevel {
    case object PersistNothing           extends PersistenceLevel { override val tag: String = "persist-nothing" }
    case object PersistRemoteSideEffects extends PersistenceLevel {
      override val tag: String = "persist-remote-side-effects"
    }
    case object Smart extends PersistenceLevel { override val tag: String = "smart" }

    /** Forward-compatible wrapper for unknown host values. */
    final case class Unknown(tag: String) extends PersistenceLevel

    def fromTag(tag: String): PersistenceLevel =
      tag match {
        case "persist-nothing"             => PersistNothing
        case "persist-remote-side-effects" => PersistRemoteSideEffects
        case "smart"                       => Smart
        case other                         => Unknown(other)
      }
  }

  def getOplogPersistenceLevel(): PersistenceLevel =
    fromHostPersistenceLevel(AgentHostApi.getOplogPersistenceLevel())

  def setOplogPersistenceLevel(level: PersistenceLevel): Unit =
    AgentHostApi.setOplogPersistenceLevel(toHostPersistenceLevel(level))

  // ----- Idempotence -----------------------------------------------------------------------

  def getIdempotenceMode(): Boolean =
    AgentHostApi.getIdempotenceMode()

  def setIdempotenceMode(flag: Boolean): Unit =
    AgentHostApi.setIdempotenceMode(flag)

  // ----- Agent management / registry ------------------------------------------------------

  type ComponentVersion   = AgentHostApi.ComponentVersion
  type ComponentIdLiteral = AgentHostApi.ComponentIdLiteral
  type AgentIdLiteral     = AgentHostApi.AgentIdLiteral
  type AgentStatus        = AgentHostApi.AgentStatus

  final case class AgentMetadata(
    agentId: AgentIdLiteral,
    args: List[String],
    env: Map[String, String],
    configVars: Map[String, String],
    status: AgentStatus,
    componentRevision: BigInt,
    retryCount: BigInt,
    agentType: String,
    agentName: String,
    componentId: ComponentIdLiteral
  )
  type UpdateMode             = AgentHostApi.UpdateMode
  type RevertAgentTarget      = AgentHostApi.RevertAgentTarget
  type RegisteredAgentType    = AgentHostApi.RegisteredAgentType
  type AgentTypeDescriptor    = AgentHostApi.AgentTypeDescriptor
  type FilterComparator       = AgentHostApi.FilterComparator
  type StringFilterComparator = AgentHostApi.StringFilterComparator
  type AgentPropertyFilter    = AgentHostApi.AgentPropertyFilter
  type AgentNameFilter        = AgentHostApi.AgentNameFilter
  type AgentStatusFilter      = AgentHostApi.AgentStatusFilter
  type AgentVersionFilter     = AgentHostApi.AgentVersionFilter
  type AgentCreatedAtFilter   = AgentHostApi.AgentCreatedAtFilter
  type AgentEnvFilter         = AgentHostApi.AgentEnvFilter
  type AgentConfigVarsFilter  = AgentHostApi.AgentConfigVarsFilter
  type AgentAllFilter         = AgentHostApi.AgentAllFilter
  type AgentAnyFilter         = AgentHostApi.AgentAnyFilter
  final case class ForkDetails(
    forkedPhantomId: Uuid,
    agentId: AgentIdLiteral,
    oplogIndex: BigInt,
    componentRevision: BigInt
  )

  sealed trait ForkResult {
    def details: ForkDetails
  }

  object ForkResult {
    final case class Original(details: ForkDetails) extends ForkResult
    final case class Forked(details: ForkDetails)   extends ForkResult
  }
  type GetAgentsHandle        = AgentHostApi.GetAgentsHandle
  type GetPromiseResultHandle = AgentHostApi.GetPromiseResultHandle
  type PromiseResult          = AgentHostApi.PromiseResult
  type Pollable               = AgentHostApi.Pollable
  type UuidLiteral            = AgentHostApi.UuidLiteral
  val UuidLiteral: AgentHostApi.UuidLiteral.type               = AgentHostApi.UuidLiteral
  val ComponentIdLiteral: AgentHostApi.ComponentIdLiteral.type = AgentHostApi.ComponentIdLiteral
  type AgentIdParts = AgentHostApi.AgentIdParts
  val AgentIdLiteral: AgentHostApi.AgentIdLiteral.type     = AgentHostApi.AgentIdLiteral
  val PromiseIdLiteral: AgentHostApi.PromiseIdLiteral.type = AgentHostApi.PromiseIdLiteral

  def registeredAgentType(typeName: String): Option[RegisteredAgentType] =
    AgentHostApi.registeredAgentType(typeName)

  def getAllAgentTypes(): List[RegisteredAgentType] =
    AgentHostApi.getAllAgentTypes()

  def makeAgentId(agentTypeName: String, payload: js.Dynamic, phantom: Option[Uuid]): Either[String, String] =
    AgentHostApi.makeAgentId(agentTypeName, payload, phantom)

  def parseAgentId(agentId: String): Either[String, AgentIdParts] =
    AgentHostApi.parseAgentId(agentId)

  def resolveComponentId(componentReference: String): Option[ComponentIdLiteral] =
    AgentHostApi.resolveComponentId(componentReference)

  def resolveAgentId(componentReference: String, agentName: String): Option[AgentIdLiteral] =
    AgentHostApi.resolveAgentId(componentReference, agentName)

  def resolveAgentIdStrict(componentReference: String, agentName: String): Option[AgentIdLiteral] =
    AgentHostApi.resolveAgentIdStrict(componentReference, agentName)

  def getSelfMetadata(): AgentMetadata =
    fromHostMetadata(AgentHostApi.getSelfMetadata())

  def getAgentMetadata(agentId: AgentIdLiteral): Option[AgentMetadata] =
    AgentHostApi.getAgentMetadata(agentId).map(fromHostMetadata)

  def getAgents(componentId: ComponentIdLiteral, filter: Option[AgentAnyFilter], precise: Boolean): GetAgentsHandle =
    AgentHostApi.getAgents(componentId, filter, precise)

  def nextAgentBatch(handle: GetAgentsHandle): Option[List[AgentMetadata]] =
    AgentHostApi.nextAgentBatch(handle).map(_.map(fromHostMetadata))

  def generateIdempotencyKey(): Uuid =
    fromUuidLiteral(AgentHostApi.generateIdempotencyKey())

  def updateAgent(agentId: AgentIdLiteral, targetVersion: BigInt, mode: UpdateMode): Unit =
    AgentHostApi.updateAgent(agentId, toJsBigInt(targetVersion), mode)

  def updateAgentRaw(agentId: AgentIdLiteral, targetVersion: ComponentVersion, mode: UpdateMode): Unit =
    AgentHostApi.updateAgent(agentId, targetVersion, mode)

  def forkAgent(sourceAgentId: AgentIdLiteral, targetAgentId: AgentIdLiteral, cutOff: OplogIndex): Unit =
    AgentHostApi.forkAgent(sourceAgentId, targetAgentId, toJsBigInt(cutOff))

  def revertAgent(agentId: AgentIdLiteral, target: RevertAgentTarget): Unit =
    AgentHostApi.revertAgent(agentId, target)

  def fork(): ForkResult = {
    val (tag, phantomIdLit) = AgentHostApi.fork()
    val selfMeta            = AgentHostApi.getSelfMetadata()
    val details             = ForkDetails(
      forkedPhantomId = fromUuidLiteral(phantomIdLit),
      agentId = selfMeta.agentId,
      oplogIndex = fromJsBigInt(AgentHostApi.getOplogIndex()),
      componentRevision = fromJsBigInt(selfMeta.componentRevision)
    )
    tag match {
      case "original" => ForkResult.Original(details)
      case "forked"   => ForkResult.Forked(details)
      case other      => throw new IllegalStateException(s"Unknown fork result tag: $other")
    }
  }

  object AgentStatus {
    val Running: AgentStatus     = AgentHostApi.AgentStatus.Running
    val Idle: AgentStatus        = AgentHostApi.AgentStatus.Idle
    val Suspended: AgentStatus   = AgentHostApi.AgentStatus.Suspended
    val Interrupted: AgentStatus = AgentHostApi.AgentStatus.Interrupted
    val Retrying: AgentStatus    = AgentHostApi.AgentStatus.Retrying
    val Failed: AgentStatus      = AgentHostApi.AgentStatus.Failed
    val Exited: AgentStatus      = AgentHostApi.AgentStatus.Exited
  }

  object UpdateMode {
    val Automatic: UpdateMode     = AgentHostApi.UpdateMode.Automatic
    val SnapshotBased: UpdateMode = AgentHostApi.UpdateMode.SnapshotBased
  }

  object FilterComparator {
    val Equal: FilterComparator        = AgentHostApi.FilterComparator.Equal
    val NotEqual: FilterComparator     = AgentHostApi.FilterComparator.NotEqual
    val GreaterEqual: FilterComparator = AgentHostApi.FilterComparator.GreaterEqual
    val Greater: FilterComparator      = AgentHostApi.FilterComparator.Greater
    val LessEqual: FilterComparator    = AgentHostApi.FilterComparator.LessEqual
    val Less: FilterComparator         = AgentHostApi.FilterComparator.Less
  }

  object StringFilterComparator {
    val Equal: StringFilterComparator      = AgentHostApi.StringFilterComparator.Equal
    val NotEqual: StringFilterComparator   = AgentHostApi.StringFilterComparator.NotEqual
    val Like: StringFilterComparator       = AgentHostApi.StringFilterComparator.Like
    val NotLike: StringFilterComparator    = AgentHostApi.StringFilterComparator.NotLike
    val StartsWith: StringFilterComparator = AgentHostApi.StringFilterComparator.StartsWith
  }

  object AgentNameFilter {
    def apply(comparator: StringFilterComparator, value: String): AgentNameFilter =
      AgentHostApi.AgentNameFilter(comparator, value)
  }

  object AgentStatusFilter {
    def apply(comparator: FilterComparator, value: AgentStatus): AgentStatusFilter =
      AgentHostApi.AgentStatusFilter(comparator, value)
  }

  object AgentVersionFilter {
    def apply(comparator: FilterComparator, value: BigInt): AgentVersionFilter =
      AgentHostApi.AgentVersionFilter(comparator, toJsBigInt(value))
  }

  object AgentCreatedAtFilter {
    def apply(comparator: FilterComparator, value: BigInt): AgentCreatedAtFilter =
      AgentHostApi.AgentCreatedAtFilter(comparator, toJsBigInt(value))
  }

  object AgentEnvFilter {
    def apply(name: String, comparator: StringFilterComparator, value: String): AgentEnvFilter =
      AgentHostApi.AgentEnvFilter(name, comparator, value)
  }

  object AgentConfigVarsFilter {
    def apply(name: String, comparator: StringFilterComparator, value: String): AgentConfigVarsFilter =
      AgentHostApi.AgentConfigVarsFilter(name, comparator, value)
  }

  object AgentPropertyFilter {
    def name(filter: AgentNameFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.name(filter)
    def status(filter: AgentStatusFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.status(filter)
    def version(filter: AgentVersionFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.version(filter)
    def createdAt(filter: AgentCreatedAtFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.createdAt(filter)
    def env(filter: AgentEnvFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.env(filter)
    def wasiConfigVars(filter: AgentConfigVarsFilter): AgentPropertyFilter =
      AgentHostApi.AgentPropertyFilter.wasiConfigVars(filter)
  }

  object AgentAllFilter {
    def apply(filters: List[AgentPropertyFilter]): AgentAllFilter =
      AgentHostApi.AgentAllFilter(filters)
  }

  object AgentAnyFilter {
    def apply(filters: List[AgentAllFilter]): AgentAnyFilter =
      AgentHostApi.AgentAnyFilter(filters)
  }

  object RevertAgentTarget {
    def RevertToOplogIndex(index: OplogIndex): RevertAgentTarget =
      AgentHostApi.RevertAgentTarget.RevertToOplogIndex(toJsBigInt(index))
    def RevertLastInvocations(count: BigInt): RevertAgentTarget =
      AgentHostApi.RevertAgentTarget.RevertLastInvocations(toJsBigInt(count))
  }

  // ----- Promises --------------------------------------------------------------------------

  type PromiseId = AgentHostApi.PromiseIdLiteral

  def createPromise(): PromiseId =
    AgentHostApi.createPromise()

  /** Completes a promise with a binary payload. */
  def completePromise(promiseId: PromiseId, data: Array[Byte]): Boolean =
    AgentHostApi.completePromise(promiseId, toUint8Array(data))

  /**
   * Low-level completion using `Uint8Array` (internal; prefer `Array[Byte]`).
   */
  private[golem] def completePromiseRaw(promiseId: PromiseId, data: Uint8Array): Boolean =
    AgentHostApi.completePromise(promiseId, data)

  /**
   * Awaits a promise completion and returns the payload bytes.
   *
   * This is implemented in a non-blocking way (polling `pollable.ready()`), so
   * it can be safely composed with other async work using `Future`.
   *
   * If you want the explicit blocking behavior, use `awaitPromiseBlocking`.
   */
  def awaitPromise(promiseId: PromiseId): Future[Array[Byte]] =
    awaitPromiseRaw(promiseId).map(fromUint8Array)(scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /**
   * Blocks until a promise is completed, then returns the payload bytes.
   *
   * Under the hood this calls WIT `subscribe` / `pollable.block`.
   */
  def awaitPromiseBlocking(promiseId: PromiseId): Array[Byte] =
    fromUint8Array(awaitPromiseBlockingRaw(promiseId))

  /** Low-level await using `Uint8Array` (internal; prefer `Array[Byte]`). */
  private[golem] def awaitPromiseRaw(promiseId: PromiseId): Future[Uint8Array] =
    awaitPromiseImpl(promiseId, initialDelayMs = 0)

  /**
   * Low-level blocking await using `Uint8Array` (internal; prefer
   * `Array[Byte]`).
   */
  private[golem] def awaitPromiseBlockingRaw(promiseId: PromiseId): Uint8Array = {
    val handle   = AgentHostApi.getPromise(promiseId)
    val pollable = handle.subscribe()
    pollable.block()
    val result = handle.get()
    if (!result.isSome) throw new IllegalStateException("Promise completed but result is empty (unexpected)")
    result.get()
  }

  /**
   * Await a promise and decode the payload as JSON using
   * `zio.blocks.schema.json`.
   *
   * By default, decoding is lenient (extra JSON fields are ignored). If you
   * want strict decoding, set `rejectExtraFields = true`.
   */
  def awaitPromiseJson[A](promiseId: PromiseId, rejectExtraFields: Boolean = false)(implicit
    schema: Schema[A]
  ): Future[A] =
    awaitPromise(promiseId).map { bytes =>
      val codec = jsonCodec[A](rejectExtraFields)
      codec.decode(bytes) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalArgumentException(err.toString)
      }
    }(scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /**
   * Encode a value as JSON and complete the promise with the encoded bytes.
   *
   * Encoding uses `zio.blocks.schema.json` and is deterministic w.r.t. the
   * derived schema.
   */
  def completePromiseJson[A](
    promiseId: PromiseId,
    value: A
  )(implicit schema: Schema[A]): Boolean = {
    val codec = jsonCodec[A](rejectExtraFields = false)
    completePromise(promiseId, codec.encode(value))
  }

  // ----- Helpers ---------------------------------------------------------------------------

  private def toJsBigInt(value: BigInt): js.BigInt =
    js.BigInt(value.toString)

  private def fromJsBigInt(value: js.BigInt): BigInt =
    BigInt(value.toString)

  private def fromUuidLiteral(uuid: UuidLiteral): Uuid =
    Uuid(
      highBits = BigInt(uuid.highBits.toString),
      lowBits = BigInt(uuid.lowBits.toString)
    )

  private def awaitPromiseImpl(promiseId: PromiseId, initialDelayMs: Int): Future[Uint8Array] = {
    val p        = Promise[Uint8Array]()
    val handle   = AgentHostApi.getPromise(promiseId)
    val pollable = handle.subscribe()

    def finishOrFail(): Unit = {
      val result = handle.get()
      if (!result.isSome) p.failure(new IllegalStateException("Promise completed but result is empty (unexpected)"))
      else p.success(result.get())
    }

    def loop(delayMs: Int): Unit =
      timers.setTimeout(delayMs.toDouble) {
        if (pollable.ready()) finishOrFail()
        else {
          // Small backoff to avoid hot-spinning; keep latency low for typical quick completions.
          val next = if (delayMs <= 0) 1 else Math.min(delayMs * 2, 50)
          loop(next)
        }
      }

    loop(initialDelayMs)
    p.future
  }

  private def jsonCodec[A](rejectExtraFields: Boolean)(implicit schema: Schema[A]): JsonBinaryCodec[A] = {
    val deriver =
      if (rejectExtraFields) JsonBinaryCodecDeriver.withRejectExtraFields(true)
      else JsonBinaryCodecDeriver
    schema.derive(deriver)
  }

  private def toUint8Array(bytes: Array[Byte]): Uint8Array = {
    val array = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      array(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    array
  }

  private def fromUint8Array(bytes: Uint8Array): Array[Byte] = {
    val out = new Array[Byte](bytes.length)
    var i   = 0
    while (i < bytes.length) {
      out(i) = bytes(i).toByte
      i += 1
    }
    out
  }

  private def fromHostMetadata(m: AgentHostApi.AgentMetadata): AgentMetadata = {
    val args       = if (m.args == null || js.isUndefined(m.args)) Nil else m.args.toList
    val env        = tuplesToMap(m.env)
    val configVars = tuplesToMap(m.configVars)
    val compRev    =
      if (js.isUndefined(m.componentRevision.asInstanceOf[js.Any])) BigInt(0) else fromJsBigInt(m.componentRevision)
    val retry = if (js.isUndefined(m.retryCount.asInstanceOf[js.Any])) BigInt(0) else fromJsBigInt(m.retryCount)
    AgentMetadata(
      agentId = m.agentId,
      args = args,
      env = env,
      configVars = configVars,
      status = m.status,
      componentRevision = compRev,
      retryCount = retry,
      agentType = m.agentType,
      agentName = m.agentName,
      componentId = m.componentId
    )
  }

  private def tuplesToMap(arr: js.Array[js.Tuple2[String, String]]): Map[String, String] =
    if (arr == null || js.isUndefined(arr)) Map.empty
    else arr.map(t => (t._1, t._2)).toMap

  private def fromHostRetryPolicy(policy: AgentHostApi.RetryPolicy): RetryPolicy = {
    // jco guest-types represent u32 as number and u64 as bigint (BigInt).
    val maxAttempts = policy.maxAttempts
    val minDelay    = fromJsBigInt(policy.minDelay)
    val maxDelay    = fromJsBigInt(policy.maxDelay)
    val multiplier  = policy.multiplier
    val maxJitter   = policy.maxJitterFactor.toOption
    RetryPolicy(maxAttempts, minDelay, maxDelay, multiplier, maxJitter)
  }

  private def toHostRetryPolicy(policy: RetryPolicy): AgentHostApi.RetryPolicy =
    Dictionary[js.Any](
      "maxAttempts"     -> policy.maxAttempts,
      "minDelay"        -> toJsBigInt(policy.minDelayNanos),
      "maxDelay"        -> toJsBigInt(policy.maxDelayNanos),
      "multiplier"      -> policy.multiplier,
      "maxJitterFactor" -> policy.maxJitterFactor.fold[js.Any](null)(identity)
    ).asInstanceOf[AgentHostApi.RetryPolicy]

  private def fromHostPersistenceLevel(level: AgentHostApi.PersistenceLevel): PersistenceLevel = {
    val tag = level.asInstanceOf[HasTag].tag.toOption.getOrElse(level.toString)
    PersistenceLevel.fromTag(tag)
  }

  private def toHostPersistenceLevel(level: PersistenceLevel): AgentHostApi.PersistenceLevel =
    Dictionary[js.Any]("tag" -> level.tag).asInstanceOf[AgentHostApi.PersistenceLevel]

  @js.native
  private trait HasTag extends js.Object {
    def tag: js.UndefOr[String] = js.native
  }
}
