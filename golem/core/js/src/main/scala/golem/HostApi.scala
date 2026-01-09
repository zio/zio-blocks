package golem

import golem.runtime.rpc.host.AgentHostApi

import scala.scalajs.js

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

  // ----- Helpers ---------------------------------------------------------------------------

  private def toJsBigInt(value: BigInt): js.BigInt =
    js.Dynamic.global.BigInt(value.toString).asInstanceOf[js.BigInt]

  private def fromJsBigInt(value: js.BigInt): BigInt =
    BigInt(value.toString)

  private def fromHostRetryPolicy(policy: AgentHostApi.RetryPolicy): RetryPolicy = {
    val p = policy.asInstanceOf[js.Dynamic]
    // jco guest-types represent u32 as number and u64 as bigint (BigInt).
    val maxAttempts = p.selectDynamic("maxAttempts").asInstanceOf[Int]
    val minDelay    = fromJsBigInt(p.selectDynamic("minDelay").asInstanceOf[js.BigInt])
    val maxDelay    = fromJsBigInt(p.selectDynamic("maxDelay").asInstanceOf[js.BigInt])
    val multiplier  = p.selectDynamic("multiplier").asInstanceOf[Double]
    val maxJitter   =
      if (js.isUndefined(p.selectDynamic("maxJitterFactor")) || p.selectDynamic("maxJitterFactor") == null) None
      else Some(p.selectDynamic("maxJitterFactor").asInstanceOf[Double])
    RetryPolicy(maxAttempts, minDelay, maxDelay, multiplier, maxJitter)
  }

  private def toHostRetryPolicy(policy: RetryPolicy): AgentHostApi.RetryPolicy =
    js.Dynamic
      .literal(
        "maxAttempts"     -> policy.maxAttempts,
        "minDelay"        -> toJsBigInt(policy.minDelayNanos),
        "maxDelay"        -> toJsBigInt(policy.maxDelayNanos),
        "multiplier"      -> policy.multiplier,
        "maxJitterFactor" -> policy.maxJitterFactor.fold[js.Any](null)(identity)
      )
      .asInstanceOf[AgentHostApi.RetryPolicy]

  private def fromHostPersistenceLevel(level: AgentHostApi.PersistenceLevel): PersistenceLevel = {
    val raw = level.asInstanceOf[js.Dynamic]
    val tag =
      if (!js.isUndefined(raw.selectDynamic("tag")) && raw.selectDynamic("tag") != null)
        raw.selectDynamic("tag").asInstanceOf[String]
      else level.toString
    PersistenceLevel.fromTag(tag)
  }

  private def toHostPersistenceLevel(level: PersistenceLevel): AgentHostApi.PersistenceLevel =
    js.Dynamic.literal("tag" -> level.tag).asInstanceOf[AgentHostApi.PersistenceLevel]
}
