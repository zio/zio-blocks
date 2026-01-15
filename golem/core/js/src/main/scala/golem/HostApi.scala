package golem

import golem.runtime.rpc.host.AgentHostApi

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
