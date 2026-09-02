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
   * Maximum number of concurrent streams reached (mux-level capacity).
   */
  final case class CapacityExceeded(limit: Int) extends MuxError

  /**
   * Per-stream message queue is full (backpressure).
   */
  final case class QueueFull(queueCapacity: Int) extends MuxError

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
