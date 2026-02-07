package golem

/**
 * Scheduled invocation time.
 *
 * This is a platform-neutral value type. The Scala.js runtime converts it to
 * the host representation internally (no public `js.*` types).
 */
final class Datetime private (val epochMillis: Double) extends AnyVal

object Datetime {

  /** Current time (epoch millis). */
  def now: Datetime =
    fromEpochMillis(System.currentTimeMillis().toDouble)

  /** Epoch-millis constructor (recommended). */
  def fromEpochMillis(ms: Double): Datetime =
    new Datetime(ms)

  /** Epoch-seconds constructor (convenience). */
  def fromEpochSeconds(seconds: Double): Datetime =
    fromEpochMillis(seconds * 1000.0)

  /** A time relative to now (milliseconds). */
  def afterMillis(deltaMs: Double): Datetime =
    fromEpochMillis(System.currentTimeMillis().toDouble + deltaMs)

  /** A time relative to now (seconds). */
  def afterSeconds(deltaSeconds: Double): Datetime =
    afterMillis(deltaSeconds * 1000.0)
}
