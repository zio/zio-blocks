package golem

import golem.host.js.JsDatetime

/**
 * Scala.js constructors for [[Datetime]].
 *
 * This is the only place where the JS representation is constructed; public
 * scheduling APIs accept [[Datetime]] instead of `js.*`.
 */
object DatetimeJs {

  /**
   * Construct a Datetime from a JS value understood by the host
   * (router/runtime).
   *
   * This is intentionally "unsafe": it trusts the shape expected by the host.
   */
  def unsafeFromHost(datetime: JsDatetime): Datetime = {
    val secs  = datetime.seconds.toString.toDouble
    val nanos = datetime.nanoseconds.toDouble
    Datetime.fromEpochMillis(secs * 1000.0 + nanos / 1000000.0)
  }

  /**
   * Convenience constructor matching existing tests (`{ ts: <number> }`).
   *
   * Note: the exact shape is host-defined; this helper exists to avoid leaking
   * `js.Dynamic` into user code.
   */
  def fromTs(ts: Double): Datetime =
    Datetime.fromEpochMillis(ts)
}
