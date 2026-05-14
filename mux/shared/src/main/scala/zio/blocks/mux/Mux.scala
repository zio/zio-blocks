package zio.blocks.mux

/**
 * Multiplexer for ID-multiplexed protocols (HTTP/2, QUIC, etc.).
 *
 * Manages multiple concurrent streams, each identified by a unique ID. Streams are
 * independent and can be opened, closed, or cancelled independently.
 *
 * @tparam Id Stream identifier type (e.g., Int for HTTP/2)
 * @tparam In Message type sent on streams
 * @tparam Out Message type received on streams
 */
object Mux {
  def apply[Id, In, Out](capacity: Int): Mux[Id, In, Out] =
    PlatformMux.create[Id, In, Out](capacity)
}

trait Mux[Id, In, Out] {

  /**
   * Open a new stream with the given ID.
   *
   * Transitions: IDLE → OPEN
   *
   * @param id Stream identifier
   * @return Either a new MuxStream or an error (e.g., capacity exceeded, mux closed)
   */
  def open(id: Id): Either[MuxError, MuxStream[In, Out]]

  /**
   * Get an existing stream by ID.
   *
   * @param id Stream identifier
   * @return Some(stream) if open, None otherwise
   */
  def get(id: Id): Option[MuxStream[In, Out]]

  /**
   * Cancel a stream by ID with an error reason.
   *
   * Transitions: Any → CLOSED (via RST_STREAM equivalent)
   *
   * @param id Stream identifier
   * @param reason Error reason
   */
  def cancel(id: Id, reason: MuxError): Unit

  /**
   * Close all active streams with an error reason.
   *
   * @param reason Error reason
   */
  def closeAll(reason: MuxError): Unit

  /**
   * Number of currently active streams.
   */
  def activeCount: Int
}

/**
 * Individual stream within a Mux.
 *
 * Encapsulates the state machine for a single stream (IDLE, OPEN, HALF_CLOSED_LOCAL,
 * HALF_CLOSED_REMOTE, CLOSED). Provides send/receive operations and state queries.
 *
 * @tparam In Message type sent on this stream
 * @tparam Out Message type received on this stream
 */
trait MuxStream[In, Out] {

  /**
   * Stream identifier (opaque to Mux trait, but available for debugging/logging).
   */
  def id: Any

  /**
   * Send a message on this stream.
   *
   * Fails if stream is closed or in HALF_CLOSED_LOCAL state.
   *
   * @param msg Message to send
   * @return Either success or an error
   */
  def send(msg: In): Either[MuxError, Unit]

  /**
   * Receive a message from this stream.
   *
   * Blocks until a message is available or stream is closed.
   * Returns Left(StreamClosed) if stream is closed.
   *
   * @return Either a message or an error
   */
  def receive(): Either[MuxError, Out]

  /**
   * Signal that this side is done sending (END_STREAM equivalent).
   *
   * Transitions: OPEN → HALF_CLOSED_LOCAL or HALF_CLOSED_REMOTE → CLOSED
   *
   * After this, send() will fail. receive() still works.
   */
  def halfClose(): Unit

  /**
   * Check if stream is fully closed.
   *
   * @return true if state is CLOSED
   */
  def isClosed: Boolean

  /**
   * Check if stream is half-closed (one side done).
   *
   * @return true if state is HALF_CLOSED_LOCAL or HALF_CLOSED_REMOTE
   */
  def isHalfClosed: Boolean

  /**
   * Fully close this stream (both sides done).
   *
   * Equivalent to halfClose() if not already half-closed.
   */
  def close(): Unit
}

/**
 * Error type for Mux operations.
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
