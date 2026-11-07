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
 * The errors that [[Mux]] and [[MuxStream]] operations return, as the `Left` of
 * an `Either` on Scala 2 and as a member of a union type on Scala 3. The
 * library creates `StreamClosed`, `CapacityExceeded`, `QueueFull`, `MuxClosed`
 * and `ProtocolError`; `Cancelled` is never created by the library and is meant
 * as a `reason` for `Mux#cancel` or `Mux#closeAll`, which then comes back from
 * `MuxStream#receive`.
 *
 * The `id` of [[MuxError.StreamClosed]] and [[MuxError.Cancelled]] is typed
 * `Any`, so that `MuxError` needs no type parameter; match on it to recover the
 * concrete id.
 *
 * {{{
 * val mux = Mux[Int, String, String](1)
 * mux.open(1)
 * println(mux.open(1)) // ProtocolError(Stream 1 already exists)
 * println(mux.open(2)) // CapacityExceeded(1)
 * mux.closeAll(MuxError.MuxClosed)
 * println(mux.open(3)) // MuxClosed
 * // On Scala 2 each error is wrapped in Left(...).
 *
 * def retryLater(error: MuxError): Boolean = error match {
 *   case MuxError.QueueFull(_) | MuxError.CapacityExceeded(_) => true
 *   case _                                                    => false
 * }
 * retryLater(MuxError.QueueFull(256)) // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/mux/ Mux]]
 */
sealed trait MuxError

/**
 * The [[MuxError]] cases.
 */
object MuxError {

  /**
   * The stream `id` is closed in the direction asked for. [[MuxStream]] returns
   * it from `send` once its side is done sending, from `offerInbound` once the
   * peer is done, and from `takeOutbound` (and `receive`, unless a cancel
   * reason was given) once the stream is fully closed and the queue is empty.
   * It is not returned for an id that was never opened: `Mux#get` returns
   * `None` and `Mux#cancel` does nothing for those.
   */
  final case class StreamClosed(id: Any) extends MuxError

  /**
   * Returned by `Mux#open` when `limit` streams, the `capacity` the [[Mux]] was
   * created with, are already registered.
   */
  final case class CapacityExceeded(limit: Int) extends MuxError

  /**
   * Returned by `MuxStream#send` and `MuxStream#offerInbound` when the queue
   * already holds `queueCapacity` messages (256); retry after the other side
   * has taken some.
   */
  final case class QueueFull(queueCapacity: Int) extends MuxError

  /**
   * A cancellation with an explanation, for callers to pass as the `reason` of
   * `Mux#cancel` or `Mux#closeAll`; `MuxStream#receive` then returns it once
   * the inbound queue is empty. The library itself never creates it.
   */
  final case class Cancelled(id: Any, reason: String) extends MuxError

  /**
   * Returned by `Mux#open` after `Mux#closeAll`.
   */
  case object MuxClosed extends MuxError

  /**
   * A misuse of the API: returned by `Mux#open` for an id that is already
   * registered (`Stream <id> already exists`), and by `MuxStream#send` and
   * `MuxStream#offerInbound` for a `null` message (`null message`). Callers may
   * also pass it as a `reason` to `Mux#cancel` or `Mux#closeAll`.
   */
  final case class ProtocolError(message: String) extends MuxError
}
