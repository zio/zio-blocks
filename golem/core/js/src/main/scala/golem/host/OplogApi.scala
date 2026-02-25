package golem.host

import golem.HostApi
import golem.runtime.rpc.host.AgentHostApi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:api/oplog@1.3.0`.
 *
 * Provides typed access to the oplog via `GetOplog` and `SearchOplog`
 * resources. Each `OplogEntry` variant is a full Scala sealed trait case,
 * matching the WIT definition.
 */
object OplogApi {

  type OplogIndex = BigInt

  // ---------------------------------------------------------------------------
  // Supporting record types
  // ---------------------------------------------------------------------------

  final case class PluginInstallationDescription(
    name: String,
    version: String,
    parameters: Map[String, String]
  )

  final case class CreateParameters(
    timestamp: ContextApi.DateTime,
    agentId: AgentHostApi.AgentIdLiteral,
    componentRevision: BigInt,
    args: List[String],
    env: Map[String, String],
    createdBy: String,
    environmentId: String,
    parent: Option[AgentHostApi.AgentIdLiteral],
    componentSize: BigInt,
    initialTotalLinearMemorySize: BigInt,
    initialActivePlugins: List[PluginInstallationDescription],
    configVars: Map[String, String]
  )

  final case class ImportedFunctionInvokedParameters(
    timestamp: ContextApi.DateTime,
    functionName: String,
    request: WitValueTypes.ValueAndType,
    response: WitValueTypes.ValueAndType,
    wrappedFunctionType: DurabilityApi.DurableFunctionType
  )

  final case class LocalSpanData(
    spanId: String,
    start: ContextApi.DateTime,
    parent: Option[String],
    linkedContext: Option[BigInt],
    attributes: List[ContextApi.Attribute],
    inherited: Boolean
  )

  final case class ExternalSpanData(spanId: String)

  sealed trait SpanData extends Product with Serializable
  object SpanData {
    final case class LocalSpan(data: LocalSpanData)       extends SpanData
    final case class ExternalSpan(data: ExternalSpanData) extends SpanData
  }

  final case class ExportedFunctionInvokedParameters(
    timestamp: ContextApi.DateTime,
    functionName: String,
    request: List[WitValueTypes.ValueAndType],
    idempotencyKey: String,
    traceId: String,
    traceStates: List[String],
    invocationContext: List[List[SpanData]]
  )

  final case class ExportedFunctionCompletedParameters(
    timestamp: ContextApi.DateTime,
    response: Option[WitValueTypes.ValueAndType],
    consumedFuel: Long
  )

  final case class ErrorParameters(
    timestamp: ContextApi.DateTime,
    error: String,
    retryFrom: OplogIndex
  )

  final case class OplogRegion(start: OplogIndex, end: OplogIndex)

  final case class JumpParameters(
    timestamp: ContextApi.DateTime,
    jump: OplogRegion
  )

  final case class ChangeRetryPolicyParameters(
    timestamp: ContextApi.DateTime,
    newPolicy: HostApi.RetryPolicy
  )

  final case class EndAtomicRegionParameters(
    timestamp: ContextApi.DateTime,
    beginIndex: OplogIndex
  )

  final case class EndRemoteWriteParameters(
    timestamp: ContextApi.DateTime,
    beginIndex: OplogIndex
  )

  final case class ExportedFunctionInvocationParameters(
    idempotencyKey: String,
    functionName: String,
    input: Option[List[WitValueTypes.ValueAndType]],
    traceId: String,
    traceStates: List[String],
    invocationContext: List[List[SpanData]]
  )

  sealed trait AgentInvocation extends Product with Serializable
  object AgentInvocation {
    final case class ExportedFunction(params: ExportedFunctionInvocationParameters) extends AgentInvocation
    final case class ManualUpdate(componentRevision: BigInt)                        extends AgentInvocation
  }

  final case class PendingAgentInvocationParameters(
    timestamp: ContextApi.DateTime,
    invocation: AgentInvocation
  )

  sealed trait UpdateDescription extends Product with Serializable
  object UpdateDescription {
    case object AutoUpdate                            extends UpdateDescription
    final case class SnapshotBased(data: Array[Byte]) extends UpdateDescription
  }

  final case class PendingUpdateParameters(
    timestamp: ContextApi.DateTime,
    targetRevision: BigInt,
    updateDescription: UpdateDescription
  )

  final case class SuccessfulUpdateParameters(
    timestamp: ContextApi.DateTime,
    targetRevision: BigInt,
    newComponentSize: BigInt,
    newActivePlugins: List[PluginInstallationDescription]
  )

  final case class FailedUpdateParameters(
    timestamp: ContextApi.DateTime,
    targetRevision: BigInt,
    details: Option[String]
  )

  final case class GrowMemoryParameters(
    timestamp: ContextApi.DateTime,
    delta: BigInt
  )

  final case class CreateResourceParameters(
    timestamp: ContextApi.DateTime,
    resourceId: BigInt,
    name: String,
    owner: String
  )

  final case class DropResourceParameters(
    timestamp: ContextApi.DateTime,
    resourceId: BigInt,
    name: String,
    owner: String
  )

  sealed trait LogLevel extends Product with Serializable
  object LogLevel {
    case object Stdout   extends LogLevel
    case object Stderr   extends LogLevel
    case object Trace    extends LogLevel
    case object Debug    extends LogLevel
    case object Info     extends LogLevel
    case object Warn     extends LogLevel
    case object Error    extends LogLevel
    case object Critical extends LogLevel

    def fromString(s: String): LogLevel = s match {
      case "stdout"   => Stdout
      case "stderr"   => Stderr
      case "trace"    => Trace
      case "debug"    => Debug
      case "info"     => Info
      case "warn"     => Warn
      case "error"    => Error
      case "critical" => Critical
      case _          => Info
    }
  }

  final case class LogParameters(
    timestamp: ContextApi.DateTime,
    level: LogLevel,
    context: String,
    message: String
  )

  final case class ActivatePluginParameters(
    timestamp: ContextApi.DateTime,
    plugin: PluginInstallationDescription
  )

  final case class DeactivatePluginParameters(
    timestamp: ContextApi.DateTime,
    plugin: PluginInstallationDescription
  )

  final case class RevertParameters(
    timestamp: ContextApi.DateTime,
    start: OplogIndex,
    end: OplogIndex
  )

  final case class CancelInvocationParameters(
    timestamp: ContextApi.DateTime,
    idempotencyKey: String
  )

  final case class StartSpanParameters(
    timestamp: ContextApi.DateTime,
    spanId: String,
    parent: Option[String],
    linkedContext: Option[String],
    attributes: List[ContextApi.Attribute]
  )

  final case class FinishSpanParameters(
    timestamp: ContextApi.DateTime,
    spanId: String
  )

  final case class SetSpanAttributeParameters(
    timestamp: ContextApi.DateTime,
    spanId: String,
    key: String,
    value: ContextApi.AttributeValue
  )

  final case class ChangePersistenceLevelParameters(
    timestamp: ContextApi.DateTime,
    persistenceLevel: HostApi.PersistenceLevel
  )

  final case class BeginRemoteTransactionParameters(
    timestamp: ContextApi.DateTime,
    transactionId: String
  )

  final case class RemoteTransactionParameters(
    timestamp: ContextApi.DateTime,
    beginIndex: OplogIndex
  )

  // ---------------------------------------------------------------------------
  // OplogEntry sealed trait — 37 variants
  // ---------------------------------------------------------------------------

  sealed trait OplogEntry extends Product with Serializable {
    def timestamp: ContextApi.DateTime
  }

  object OplogEntry {
    final case class Create(params: CreateParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class ImportedFunctionInvoked(params: ImportedFunctionInvokedParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class ExportedFunctionInvoked(params: ExportedFunctionInvokedParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class ExportedFunctionCompleted(params: ExportedFunctionCompletedParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class Suspend(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class Error(params: ErrorParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class NoOp(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class Jump(params: JumpParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class Interrupted(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class Exited(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class ChangeRetryPolicy(params: ChangeRetryPolicyParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class BeginAtomicRegion(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class EndAtomicRegion(params: EndAtomicRegionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class BeginRemoteWrite(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class EndRemoteWrite(params: EndRemoteWriteParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class PendingAgentInvocation(params: PendingAgentInvocationParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class PendingUpdate(params: PendingUpdateParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class SuccessfulUpdate(params: SuccessfulUpdateParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class FailedUpdate(params: FailedUpdateParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class GrowMemory(params: GrowMemoryParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class CreateResource(params: CreateResourceParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class DropResource(params: DropResourceParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class Log(params: LogParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class Restart(ts: ContextApi.DateTime) extends OplogEntry {
      def timestamp: ContextApi.DateTime = ts
    }
    final case class ActivatePlugin(params: ActivatePluginParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class DeactivatePlugin(params: DeactivatePluginParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class Revert(params: RevertParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class CancelInvocation(params: CancelInvocationParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class StartSpan(params: StartSpanParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class FinishSpan(params: FinishSpanParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class SetSpanAttribute(params: SetSpanAttributeParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class ChangePersistenceLevel(params: ChangePersistenceLevelParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class BeginRemoteTransaction(params: BeginRemoteTransactionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class PreCommitRemoteTransaction(params: RemoteTransactionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class PreRollbackRemoteTransaction(params: RemoteTransactionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class CommittedRemoteTransaction(params: RemoteTransactionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }
    final case class RolledBackRemoteTransaction(params: RemoteTransactionParameters) extends OplogEntry {
      def timestamp: ContextApi.DateTime = params.timestamp
    }

    // --- Parsing ---

    def fromDynamic(raw: js.Dynamic): OplogEntry = {
      val tag = raw.tag.asInstanceOf[String]
      val v   = raw.selectDynamic("val").asInstanceOf[js.Dynamic]
      tag match {
        case "create"                          => Create(parseCreateParameters(v))
        case "imported-function-invoked"       => ImportedFunctionInvoked(parseImportedFunctionInvokedParameters(v))
        case "exported-function-invoked"       => ExportedFunctionInvoked(parseExportedFunctionInvokedParameters(v))
        case "exported-function-completed"     => ExportedFunctionCompleted(parseExportedFunctionCompletedParameters(v))
        case "suspend"                         => Suspend(parseTimestamp(v))
        case "error"                           => Error(parseErrorParameters(v))
        case "no-op"                           => NoOp(parseTimestamp(v))
        case "jump"                            => Jump(parseJumpParameters(v))
        case "interrupted"                     => Interrupted(parseTimestamp(v))
        case "exited"                          => Exited(parseTimestamp(v))
        case "change-retry-policy"             => ChangeRetryPolicy(parseChangeRetryPolicyParameters(v))
        case "begin-atomic-region"             => BeginAtomicRegion(parseTimestamp(v))
        case "end-atomic-region"               => EndAtomicRegion(parseEndAtomicRegionParameters(v))
        case "begin-remote-write"              => BeginRemoteWrite(parseTimestamp(v))
        case "end-remote-write"                => EndRemoteWrite(parseEndRemoteWriteParameters(v))
        case "pending-agent-invocation"        => PendingAgentInvocation(parsePendingAgentInvocationParameters(v))
        case "pending-update"                  => PendingUpdate(parsePendingUpdateParameters(v))
        case "successful-update"               => SuccessfulUpdate(parseSuccessfulUpdateParameters(v))
        case "failed-update"                   => FailedUpdate(parseFailedUpdateParameters(v))
        case "grow-memory"                     => GrowMemory(parseGrowMemoryParameters(v))
        case "create-resource"                 => CreateResource(parseCreateResourceParameters(v))
        case "drop-resource"                   => DropResource(parseDropResourceParameters(v))
        case "log"                             => Log(parseLogParameters(v))
        case "restart"                         => Restart(parseTimestamp(v))
        case "activate-plugin"                 => ActivatePlugin(parseActivatePluginParameters(v))
        case "deactivate-plugin"               => DeactivatePlugin(parseDeactivatePluginParameters(v))
        case "revert"                          => Revert(parseRevertParameters(v))
        case "cancel-invocation"               => CancelInvocation(parseCancelInvocationParameters(v))
        case "start-span"                      => StartSpan(parseStartSpanParameters(v))
        case "finish-span"                     => FinishSpan(parseFinishSpanParameters(v))
        case "set-span-attribute"              => SetSpanAttribute(parseSetSpanAttributeParameters(v))
        case "change-persistence-level"        => ChangePersistenceLevel(parseChangePersistenceLevelParameters(v))
        case "begin-remote-transaction"        => BeginRemoteTransaction(parseBeginRemoteTransactionParameters(v))
        case "pre-commit-remote-transaction"   => PreCommitRemoteTransaction(parseRemoteTransactionParameters(v))
        case "pre-rollback-remote-transaction" => PreRollbackRemoteTransaction(parseRemoteTransactionParameters(v))
        case "committed-remote-transaction"    => CommittedRemoteTransaction(parseRemoteTransactionParameters(v))
        case "rolled-back-remote-transaction"  => RolledBackRemoteTransaction(parseRemoteTransactionParameters(v))
        case other                             =>
          throw new IllegalArgumentException(s"Unknown oplog entry tag: $other")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Parsing helpers
  // ---------------------------------------------------------------------------

  private def parseDateTime(raw: js.Dynamic): ContextApi.DateTime = {
    val secs  = BigInt(raw.seconds.toString)
    val nanos = BigInt(raw.nanoseconds.toString).toLong
    ContextApi.DateTime(secs, nanos)
  }

  private def parseTimestamp(raw: js.Dynamic): ContextApi.DateTime =
    parseDateTime(raw.timestamp.asInstanceOf[js.Dynamic])

  private def optString(raw: js.Dynamic, field: String): Option[String] = {
    val v = raw.selectDynamic(field)
    if (js.isUndefined(v) || v == null) None else Some(v.asInstanceOf[String])
  }

  private def optBigInt(raw: js.Dynamic, field: String): Option[BigInt] = {
    val v = raw.selectDynamic(field)
    if (js.isUndefined(v) || v == null) None else Some(BigInt(v.toString))
  }

  private def parseTupleListToMap(raw: js.Dynamic): Map[String, String] =
    if (js.isUndefined(raw) || raw == null) Map.empty
    else {
      val arr = raw.asInstanceOf[js.Array[js.Tuple2[String, String]]]
      arr.toSeq.map(t => t._1 -> t._2).toMap
    }

  private def parsePluginInstallationDescription(raw: js.Dynamic): PluginInstallationDescription =
    PluginInstallationDescription(
      name = raw.name.asInstanceOf[String],
      version = raw.version.asInstanceOf[String],
      parameters = parseTupleListToMap(raw.parameters)
    )

  private def parseAgentId(raw: js.Dynamic): AgentHostApi.AgentIdLiteral =
    raw.asInstanceOf[AgentHostApi.AgentIdLiteral]

  private def parseCreateParameters(raw: js.Dynamic): CreateParameters = {
    val plugins = raw.initialActivePlugins.asInstanceOf[js.Array[js.Dynamic]]
    val parent  = raw.parent
    CreateParameters(
      timestamp = parseTimestamp(raw),
      agentId = parseAgentId(raw.agentId),
      componentRevision = BigInt(raw.componentRevision.toString),
      args = raw.args.asInstanceOf[js.Array[String]].toList,
      env = parseTupleListToMap(raw.env),
      createdBy = raw.createdBy.toString,
      environmentId = raw.environmentId.toString,
      parent =
        if (js.isUndefined(parent) || parent == null) None else Some(parseAgentId(parent.asInstanceOf[js.Dynamic])),
      componentSize = BigInt(raw.componentSize.toString),
      initialTotalLinearMemorySize = BigInt(raw.initialTotalLinearMemorySize.toString),
      initialActivePlugins = plugins.toList.map(parsePluginInstallationDescription),
      configVars = parseTupleListToMap(raw.configVars)
    )
  }

  private def parseImportedFunctionInvokedParameters(raw: js.Dynamic): ImportedFunctionInvokedParameters =
    ImportedFunctionInvokedParameters(
      timestamp = parseTimestamp(raw),
      functionName = raw.functionName.asInstanceOf[String],
      request = WitValueTypes.ValueAndType.fromDynamic(raw.request.asInstanceOf[js.Dynamic]),
      response = WitValueTypes.ValueAndType.fromDynamic(raw.response.asInstanceOf[js.Dynamic]),
      wrappedFunctionType =
        DurabilityApi.DurableFunctionType.fromDynamic(raw.wrappedFunctionType.asInstanceOf[js.Dynamic])
    )

  private def parseSpanData(raw: js.Dynamic): SpanData = {
    val tag = raw.tag.asInstanceOf[String]
    val v   = raw.selectDynamic("val").asInstanceOf[js.Dynamic]
    tag match {
      case "local-span" =>
        val attrs =
          if (js.isUndefined(v.attributes) || v.attributes == null) Nil
          else
            v.attributes.asInstanceOf[js.Array[js.Dynamic]].toList.map { a =>
              ContextApi.Attribute(
                a.key.asInstanceOf[String],
                ContextApi.AttributeValue.fromDynamic(a.value.asInstanceOf[js.Dynamic])
              )
            }
        SpanData.LocalSpan(
          LocalSpanData(
            spanId = v.spanId.asInstanceOf[String],
            start = parseDateTime(v.start.asInstanceOf[js.Dynamic]),
            parent = optString(v, "parent"),
            linkedContext = optBigInt(v, "linkedContext"),
            attributes = attrs,
            inherited = v.inherited.asInstanceOf[Boolean]
          )
        )
      case "external-span" =>
        SpanData.ExternalSpan(ExternalSpanData(v.spanId.asInstanceOf[String]))
      case other =>
        throw new IllegalArgumentException(s"Unknown SpanData tag: $other")
    }
  }

  private def parseSpanDataLists(raw: js.Dynamic): List[List[SpanData]] =
    if (js.isUndefined(raw) || raw == null) Nil
    else {
      raw.asInstanceOf[js.Array[js.Dynamic]].toList.map { inner =>
        inner.asInstanceOf[js.Array[js.Dynamic]].toList.map(parseSpanData)
      }
    }

  private def parseExportedFunctionInvokedParameters(raw: js.Dynamic): ExportedFunctionInvokedParameters =
    ExportedFunctionInvokedParameters(
      timestamp = parseTimestamp(raw),
      functionName = raw.functionName.asInstanceOf[String],
      request =
        raw.request.asInstanceOf[js.Array[js.Dynamic]].toList.map(v => WitValueTypes.ValueAndType.fromDynamic(v)),
      idempotencyKey = raw.idempotencyKey.asInstanceOf[String],
      traceId = raw.traceId.asInstanceOf[String],
      traceStates = raw.traceStates.asInstanceOf[js.Array[String]].toList,
      invocationContext = parseSpanDataLists(raw.invocationContext)
    )

  private def parseExportedFunctionCompletedParameters(raw: js.Dynamic): ExportedFunctionCompletedParameters = {
    val resp = raw.response
    ExportedFunctionCompletedParameters(
      timestamp = parseTimestamp(raw),
      response =
        if (js.isUndefined(resp) || resp == null) None
        else Some(WitValueTypes.ValueAndType.fromDynamic(resp.asInstanceOf[js.Dynamic])),
      consumedFuel = BigInt(raw.consumedFuel.toString).toLong
    )
  }

  private def parseErrorParameters(raw: js.Dynamic): ErrorParameters =
    ErrorParameters(
      timestamp = parseTimestamp(raw),
      error = raw.error.asInstanceOf[String],
      retryFrom = BigInt(raw.retryFrom.toString)
    )

  private def parseJumpParameters(raw: js.Dynamic): JumpParameters =
    JumpParameters(
      timestamp = parseTimestamp(raw),
      jump = OplogRegion(BigInt(raw.jump.start.toString), BigInt(raw.jump.end.toString))
    )

  private def parseChangeRetryPolicyParameters(raw: js.Dynamic): ChangeRetryPolicyParameters = {
    val p         = raw.newPolicy.asInstanceOf[js.Dynamic]
    val maxJitter = {
      val mj = p.maxJitterFactor
      if (js.isUndefined(mj) || mj == null) None else Some(mj.asInstanceOf[Double])
    }
    ChangeRetryPolicyParameters(
      timestamp = parseTimestamp(raw),
      newPolicy = HostApi.RetryPolicy(
        maxAttempts = p.maxAttempts.asInstanceOf[Int],
        minDelayNanos = BigInt(p.minDelay.toString),
        maxDelayNanos = BigInt(p.maxDelay.toString),
        multiplier = p.multiplier.asInstanceOf[Double],
        maxJitterFactor = maxJitter
      )
    )
  }

  private def parseEndAtomicRegionParameters(raw: js.Dynamic): EndAtomicRegionParameters =
    EndAtomicRegionParameters(
      timestamp = parseTimestamp(raw),
      beginIndex = BigInt(raw.beginIndex.toString)
    )

  private def parseEndRemoteWriteParameters(raw: js.Dynamic): EndRemoteWriteParameters =
    EndRemoteWriteParameters(
      timestamp = parseTimestamp(raw),
      beginIndex = BigInt(raw.beginIndex.toString)
    )

  private def parsePendingAgentInvocationParameters(raw: js.Dynamic): PendingAgentInvocationParameters = {
    val inv      = raw.invocation.asInstanceOf[js.Dynamic]
    val invTag   = inv.tag.asInstanceOf[String]
    val invVal   = inv.selectDynamic("val").asInstanceOf[js.Dynamic]
    val agentInv = invTag match {
      case "exported-function" =>
        val efInput  = invVal.input
        val inputOpt =
          if (js.isUndefined(efInput) || efInput == null) None
          else Some(efInput.asInstanceOf[js.Array[js.Dynamic]].toList.map(WitValueTypes.ValueAndType.fromDynamic))
        AgentInvocation.ExportedFunction(
          ExportedFunctionInvocationParameters(
            idempotencyKey = invVal.idempotencyKey.asInstanceOf[String],
            functionName = invVal.functionName.asInstanceOf[String],
            input = inputOpt,
            traceId = invVal.traceId.asInstanceOf[String],
            traceStates = invVal.traceStates.asInstanceOf[js.Array[String]].toList,
            invocationContext = parseSpanDataLists(invVal.invocationContext)
          )
        )
      case "manual-update" =>
        AgentInvocation.ManualUpdate(BigInt(invVal.toString))
      case other =>
        throw new IllegalArgumentException(s"Unknown AgentInvocation tag: $other")
    }
    PendingAgentInvocationParameters(timestamp = parseTimestamp(raw), invocation = agentInv)
  }

  private def parsePendingUpdateParameters(raw: js.Dynamic): PendingUpdateParameters = {
    val desc    = raw.updateDescription.asInstanceOf[js.Dynamic]
    val descTag = desc.tag.asInstanceOf[String]
    val ud      = descTag match {
      case "auto-update"    => UpdateDescription.AutoUpdate
      case "snapshot-based" =>
        val arr = desc.selectDynamic("val").asInstanceOf[js.Array[Int]]
        UpdateDescription.SnapshotBased(arr.toArray.map(_.toByte))
      case other =>
        throw new IllegalArgumentException(s"Unknown UpdateDescription tag: $other")
    }
    PendingUpdateParameters(
      timestamp = parseTimestamp(raw),
      targetRevision = BigInt(raw.targetRevision.toString),
      updateDescription = ud
    )
  }

  private def parseSuccessfulUpdateParameters(raw: js.Dynamic): SuccessfulUpdateParameters =
    SuccessfulUpdateParameters(
      timestamp = parseTimestamp(raw),
      targetRevision = BigInt(raw.targetRevision.toString),
      newComponentSize = BigInt(raw.newComponentSize.toString),
      newActivePlugins =
        raw.newActivePlugins.asInstanceOf[js.Array[js.Dynamic]].toList.map(parsePluginInstallationDescription)
    )

  private def parseFailedUpdateParameters(raw: js.Dynamic): FailedUpdateParameters =
    FailedUpdateParameters(
      timestamp = parseTimestamp(raw),
      targetRevision = BigInt(raw.targetRevision.toString),
      details = optString(raw, "details")
    )

  private def parseGrowMemoryParameters(raw: js.Dynamic): GrowMemoryParameters =
    GrowMemoryParameters(
      timestamp = parseTimestamp(raw),
      delta = BigInt(raw.delta.toString)
    )

  private def parseCreateResourceParameters(raw: js.Dynamic): CreateResourceParameters =
    CreateResourceParameters(
      timestamp = parseTimestamp(raw),
      resourceId = BigInt(raw.resourceId.toString),
      name = raw.name.asInstanceOf[String],
      owner = raw.owner.asInstanceOf[String]
    )

  private def parseDropResourceParameters(raw: js.Dynamic): DropResourceParameters =
    DropResourceParameters(
      timestamp = parseTimestamp(raw),
      resourceId = BigInt(raw.resourceId.toString),
      name = raw.name.asInstanceOf[String],
      owner = raw.owner.asInstanceOf[String]
    )

  private def parseLogParameters(raw: js.Dynamic): LogParameters =
    LogParameters(
      timestamp = parseTimestamp(raw),
      level = LogLevel.fromString(raw.level.tag.asInstanceOf[String]),
      context = raw.context.asInstanceOf[String],
      message = raw.message.asInstanceOf[String]
    )

  private def parseActivatePluginParameters(raw: js.Dynamic): ActivatePluginParameters =
    ActivatePluginParameters(
      timestamp = parseTimestamp(raw),
      plugin = parsePluginInstallationDescription(raw.plugin.asInstanceOf[js.Dynamic])
    )

  private def parseDeactivatePluginParameters(raw: js.Dynamic): DeactivatePluginParameters =
    DeactivatePluginParameters(
      timestamp = parseTimestamp(raw),
      plugin = parsePluginInstallationDescription(raw.plugin.asInstanceOf[js.Dynamic])
    )

  private def parseRevertParameters(raw: js.Dynamic): RevertParameters =
    RevertParameters(
      timestamp = parseTimestamp(raw),
      start = BigInt(raw.start.toString),
      end = BigInt(raw.end.toString)
    )

  private def parseCancelInvocationParameters(raw: js.Dynamic): CancelInvocationParameters =
    CancelInvocationParameters(
      timestamp = parseTimestamp(raw),
      idempotencyKey = raw.idempotencyKey.asInstanceOf[String]
    )

  private def parseStartSpanParameters(raw: js.Dynamic): StartSpanParameters = {
    val attrs =
      if (js.isUndefined(raw.attributes) || raw.attributes == null) Nil
      else
        raw.attributes.asInstanceOf[js.Array[js.Dynamic]].toList.map { a =>
          ContextApi.Attribute(
            a.key.asInstanceOf[String],
            ContextApi.AttributeValue.fromDynamic(a.value.asInstanceOf[js.Dynamic])
          )
        }
    StartSpanParameters(
      timestamp = parseTimestamp(raw),
      spanId = raw.spanId.asInstanceOf[String],
      parent = optString(raw, "parent"),
      linkedContext = optString(raw, "linkedContext"),
      attributes = attrs
    )
  }

  private def parseFinishSpanParameters(raw: js.Dynamic): FinishSpanParameters =
    FinishSpanParameters(
      timestamp = parseTimestamp(raw),
      spanId = raw.spanId.asInstanceOf[String]
    )

  private def parseSetSpanAttributeParameters(raw: js.Dynamic): SetSpanAttributeParameters =
    SetSpanAttributeParameters(
      timestamp = parseTimestamp(raw),
      spanId = raw.spanId.asInstanceOf[String],
      key = raw.key.asInstanceOf[String],
      value = ContextApi.AttributeValue.fromDynamic(raw.value.asInstanceOf[js.Dynamic])
    )

  private def parseChangePersistenceLevelParameters(raw: js.Dynamic): ChangePersistenceLevelParameters =
    ChangePersistenceLevelParameters(
      timestamp = parseTimestamp(raw),
      persistenceLevel = HostApi.PersistenceLevel.fromTag(raw.persistenceLevel.tag.asInstanceOf[String])
    )

  private def parseBeginRemoteTransactionParameters(raw: js.Dynamic): BeginRemoteTransactionParameters =
    BeginRemoteTransactionParameters(
      timestamp = parseTimestamp(raw),
      transactionId = raw.transactionId.asInstanceOf[String]
    )

  private def parseRemoteTransactionParameters(raw: js.Dynamic): RemoteTransactionParameters =
    RemoteTransactionParameters(
      timestamp = parseTimestamp(raw),
      beginIndex = BigInt(raw.beginIndex.toString)
    )

  // ---------------------------------------------------------------------------
  // GetOplog resource
  // ---------------------------------------------------------------------------

  final class GetOplog private (private val handle: js.Dynamic) {

    def getNext(): Option[List[OplogEntry]] = {
      val batch = handle.getNext()
      if (js.isUndefined(batch) || batch == null) None
      else {
        val arr = batch.asInstanceOf[js.Array[js.Dynamic]]
        Some(arr.toList.map(OplogEntry.fromDynamic))
      }
    }
  }

  object GetOplog {
    def apply(agentId: AgentHostApi.AgentIdLiteral, start: OplogIndex): GetOplog = {
      val ctor   = OplogModule.asInstanceOf[js.Dynamic].selectDynamic("GetOplog")
      val handle = js.Dynamic.newInstance(ctor)(agentId, js.BigInt(start.toString))
      new GetOplog(handle)
    }
  }

  // ---------------------------------------------------------------------------
  // SearchOplog resource
  // ---------------------------------------------------------------------------

  final class SearchOplog private (private val handle: js.Dynamic) {

    def getNext(): Option[List[(OplogIndex, OplogEntry)]] = {
      val batch = handle.getNext()
      if (js.isUndefined(batch) || batch == null) None
      else {
        val arr = batch.asInstanceOf[js.Array[js.Tuple2[js.Any, js.Dynamic]]]
        Some(arr.toList.map { t =>
          val idx   = BigInt(t._1.toString)
          val entry = OplogEntry.fromDynamic(t._2)
          (idx, entry)
        })
      }
    }
  }

  object SearchOplog {
    def apply(agentId: AgentHostApi.AgentIdLiteral, text: String): SearchOplog = {
      val ctor   = OplogModule.asInstanceOf[js.Dynamic].selectDynamic("SearchOplog")
      val handle = js.Dynamic.newInstance(ctor)(agentId, text)
      new SearchOplog(handle)
    }
  }

  // ---------------------------------------------------------------------------
  // Native bindings
  // ---------------------------------------------------------------------------

  @js.native
  @JSImport("golem:api/oplog@1.3.0", JSImport.Namespace)
  private object OplogModule extends js.Object

  def raw: Any = OplogModule
}
