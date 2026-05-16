package zio.blocks.mux

/**
 * Error type for Mux operations.
 *
 * The `id` field in [[MuxError.StreamClosed]] and [[MuxError.Cancelled]] is
 * typed as `Any` to keep `MuxError` non-generic. Parameterizing the sealed
 * hierarchy on the stream ID type would make pattern matching and error
 * handling unwieldy across different Mux instances. Callers can recover the
 * concrete ID type via pattern matching at the use site.
 */
sealed trait MuxError

object MuxError {

  /**
   * Stream is closed or does not exist.
   */
  final case class StreamClosed(id: Any) extends MuxError

  /**
   * Mux capacity exceeded (max concurrent streams).
   */
  final case class CapacityExceeded(limit: Int) extends MuxError

  /**
   * Stream was cancelled by peer or local error.
   */
  final case class Cancelled(id: Any, reason: String) extends MuxError

  /**
   * Mux itself is closed.
   */
  case object MuxClosed extends MuxError

  /**
   * Protocol error (e.g., invalid state transition).
   */
  final case class ProtocolError(message: String) extends MuxError
}
