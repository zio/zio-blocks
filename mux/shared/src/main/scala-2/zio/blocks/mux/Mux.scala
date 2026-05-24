/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.mux

/**
 * Multiplexer for ID-multiplexed protocols (HTTP/2, QUIC, etc.).
 *
 * Manages multiple concurrent streams, each identified by a unique ID. Streams
 * are independent and can be opened, closed, or cancelled independently.
 *
 * @tparam Id
 *   Stream identifier type (e.g., Int for HTTP/2)
 * @tparam In
 *   Message type sent on streams
 * @tparam Out
 *   Message type received on streams
 */
object Mux {
  def apply[Id, In, Out](capacity: Int): Mux[Id, In, Out] = {
    require(capacity > 0, s"Mux capacity must be positive, got $capacity")
    PlatformMux.create[Id, In, Out](capacity)
  }
}

/**
 * A multiplexer that manages multiple independent streams over a shared
 * transport.
 *
 * Each stream is identified by a unique `Id` and has independent
 * inbound/outbound message queues. The multiplexer enforces a maximum number of
  * concurrent streams (capacity). Attempting to open a stream beyond that limit
  * returns [[MuxError.CapacityExceeded]].
 *
 * '''Invariants:'''
 *   - Stream IDs must be unique among active streams; opening a duplicate
 *     returns [[MuxError.ProtocolError]].
 *   - `capacity` is fixed at construction and never changes.
 *   - After [[closeAll]], no new streams can be opened ([[MuxError.MuxClosed]]
 *     is returned) and all previously active streams transition to CLOSED with
 *     the supplied error enqueued for any pending receive.
 *
 * '''Thread safety:''' `open`, `get`, `cancel`, `closeAll`, and `activeCount`
 * are safe to call from multiple threads concurrently. Per-stream operations
 * on [[MuxStream]]: `send` and `offerInbound` are multi-thread safe.
 * `receive` and `takeOutbound` must each be called from a single consumer
 * thread at a time (single-consumer contract of the underlying ring buffer).
 *
 * @tparam Id
 *   Stream identifier type (e.g., `Int` for HTTP/2 stream IDs)
 * @tparam In
 *   Message type sent on streams (outbound from user code)
 * @tparam Out
 *   Message type received on streams (inbound to user code)
 */
trait Mux[Id, In, Out] {

  /**
   * Open a new stream with the given ID.
   *
   * Transitions: IDLE -> OPEN
   *
   * @param id
   *   Stream identifier
   * @return
   *   Either a new MuxStream or an error (e.g., capacity exceeded, mux closed)
   */
  def open(id: Id): Either[MuxError, MuxStream[Id, In, Out]]

  /**
   * Get an existing stream by ID.
   *
   * @param id
   *   Stream identifier
   * @return
   *   Some(stream) if open, None otherwise
   */
  def get(id: Id): Option[MuxStream[Id, In, Out]]

  /**
   * Cancel a stream by ID with an error reason.
   *
   * Transitions: Any -> CLOSED (via RST_STREAM equivalent)
   *
   * @param id
   *   Stream identifier
   * @param reason
   *   Error reason
   */
  def cancel(id: Id, reason: MuxError): Unit

  /**
   * Close all active streams with an error reason.
   *
   * @param reason
   *   Error reason
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
 * Encapsulates the state machine for a single stream (IDLE, OPEN,
 * HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED). Provides send/receive
 * operations and state queries.
 *
 * This stream has two separate message queues:
 *   - '''Outbound queue''': messages sent by user code via `send()`, drained by
 *     the mux/protocol via `takeOutbound()`
 *   - '''Inbound queue''': messages delivered by the mux/protocol via
 *     `offerInbound()`, read by user code via `receive()`
 *
 * @tparam Id
 *   Stream identifier type
 * @tparam In
 *   Message type sent on this stream
 * @tparam Out
 *   Message type received on this stream
 */
trait MuxStream[Id, In, Out] {

  /**
   * Stream identifier.
   */
  def id: Id

  /**
   * Send a message on this stream.
   *
   * Places the message into the outbound queue for the mux/protocol to drain.
   * Fails if stream is closed or in HALF_CLOSED_LOCAL state.
   *
   * @param msg
   *   Message to send
   * @return
   *   Either success or an error
   */
  def send(msg: In): Either[MuxError, Unit]

  /**
   * Receive a message from the inbound queue.
   *
   * Returns the next available inbound message, or None if no message is
   * available yet. Returns Left(error) if the stream is closed.
   *
   * @return
   *   Right(Some(msg)) if a message is available, Right(None) if no message
   *   yet, Left(error) if closed
   */
  def receive(): Either[MuxError, Option[Out]]

  /**
   * Deliver a message to this stream's inbound queue.
   *
   * Called by the mux/protocol layer to deliver messages TO this stream.
   *
   * @param msg
   *   Message to deliver
   * @return
   *   Either success or an error if the stream is closed
   */
  def offerInbound(msg: Out): Either[MuxError, Unit]

  /**
   * Take the next message from the outbound queue.
   *
   * Called by the mux/protocol layer to drain messages FROM this stream.
   * Returns None if no outbound message is available.
   *
   * @return
   *   Right(Some(msg)) if a message is available, Right(None) if empty,
   *   Left(error) if closed
   */
  def takeOutbound(): Either[MuxError, Option[In]]

  /**
   * Signal that this side is done sending (END_STREAM equivalent).
   *
   * Transitions: OPEN -> HALF_CLOSED_LOCAL or HALF_CLOSED_REMOTE -> CLOSED
   *
   * After this, send() will fail. receive() still works.
   */
  def halfClose(): Unit

  /**
   * Signal that the remote side is done sending.
   *
   * Transitions: OPEN -> HALF_CLOSED_REMOTE or HALF_CLOSED_LOCAL -> CLOSED
   *
   * Called by the mux/protocol layer when the peer signals END_STREAM.
   */
  def signalRemoteClose(): Unit

  /**
   * Check if stream is fully closed.
   *
   * @return
   *   true if state is CLOSED
   */
  def isClosed: Boolean

  /**
   * Check if stream is half-closed (one side done).
   *
   * @return
   *   true if state is HALF_CLOSED_LOCAL or HALF_CLOSED_REMOTE
   */
  def isHalfClosed: Boolean

  /**
   * Fully close this stream immediately.
   *
   * Transitions the stream to CLOSED state and enqueues a terminal error.
   * After this call:
   *   - `send()` fails with [[MuxError.StreamClosed]].
   *   - `receive()` drains any already-buffered inbound messages, then
   *     returns the terminal error once the buffer is empty.
   *   - `offerInbound()` fails with [[MuxError.StreamClosed]].
   */
  def close(): Unit
}
