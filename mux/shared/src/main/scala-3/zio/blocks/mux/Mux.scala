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
 * Creates [[Mux]] instances.
 */
object Mux {

  /**
   * Creates an empty multiplexer that holds at most `capacity` streams at once.
   *
   * @tparam Id
   *   the stream id type, such as `Int` for HTTP/2 stream ids
   * @tparam In
   *   the type of the messages the application sends on a stream
   * @tparam Out
   *   the type of the messages the application receives on a stream
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is 0 or negative
   */
  def apply[Id, In, Out](capacity: Int): Mux[Id, In, Out] = {
    require(capacity > 0, s"Mux capacity must be positive, got $capacity")
    PlatformMux.create[Id, In, Out](capacity)
  }
}

/**
 * A registry of independent streams that share one transport, each identified
 * by an `Id`, as in HTTP/2 or QUIC. A protocol layer opens a stream per id,
 * moves messages between the transport and each stream's two queues, and closes
 * or cancels streams; the application sends and receives on the [[MuxStream]].
 *
 * At most `capacity` streams are registered at once: [[open]] returns
 * [[MuxError.CapacityExceeded]] beyond that, and [[MuxError.ProtocolError]] for
 * an id that is already registered. A stream leaves the registry when it is
 * fully closed, cancelled with [[cancel]], or closed by [[closeAll]]; its id
 * can then be opened again. After `closeAll`, `open` returns
 * [[MuxError.MuxClosed]] for good.
 *
 * Methods that can fail return `Either[MuxError, X]` on Scala 2 and
 * `X | MuxError` on Scala 3, where `X` is the success value.
 *
 * On the JVM `open`, `get`, `cancel`, `closeAll` and `activeCount` may be
 * called from any thread: `open`, `cancel` and `closeAll` take one lock, and
 * `get` and `activeCount` read a concurrent map. On Scala.js, which runs one
 * thread, the registry is a plain map.
 *
 * {{{
 * // scala-2-only
 * val mux = Mux[Int, String, String](16)
 *
 * mux.open(1) match {
 *   case Left(err) => println("cannot open: " + err)
 *   case Right(stream) =>
 *     stream.send("GET /health")     // the application sends
 *     println(stream.takeOutbound()) // Right(Some(GET /health)): the protocol takes it
 *     stream.offerInbound("200 OK")  // the protocol delivers the reply
 *     println(stream.receive())      // Right(Some(200 OK))
 * }
 * println(mux.open(1))     // Left(ProtocolError(Stream 1 already exists))
 * mux.closeAll(MuxError.MuxClosed)
 * println(mux.activeCount) // 0
 * }}}
 *
 * {{{
 * // scala-3-only
 * val mux = Mux[Int, String, String](16)
 *
 * mux.open(1) match {
 *   case err: MuxError => println("cannot open: " + err)
 *   case stream: MuxStream[Int, String, String] =>
 *     stream.send("GET /health")     // the application sends
 *     println(stream.takeOutbound()) // Some(GET /health): the protocol takes it
 *     stream.offerInbound("200 OK")  // the protocol delivers the reply
 *     println(stream.receive())      // Some(200 OK)
 * }
 * println(mux.open(1))     // ProtocolError(Stream 1 already exists)
 * mux.closeAll(MuxError.MuxClosed)
 * println(mux.activeCount) // 0
 * }}}
 *
 * @tparam Id
 *   the stream id type, such as `Int` for HTTP/2 stream ids
 * @tparam In
 *   the type of the messages the application sends (outbound)
 * @tparam Out
 *   the type of the messages the application receives (inbound)
 * @see
 *   [[https://zioblocks.com/reference/mux/ Mux]]
 */
trait Mux[Id, In, Out] {

  /**
   * Registers a new open stream with the given id and returns it. Returns
   * [[MuxError.MuxClosed]] after [[closeAll]], [[MuxError.ProtocolError]] when
   * `id` is already registered (message `Stream <id> already exists`), and
   * [[MuxError.CapacityExceeded]] when `capacity` streams are registered,
   * checked in that order.
   *
   * Calling `MuxStream#close` on an old handle, after that stream was closed
   * (by `close`, by both sides finishing, or by `Mux#cancel`) and its id was
   * opened again, removes the new stream from the registry, a known bug: `get`
   * then returns `None` for a live stream and `open` accepts the id a second
   * time.
   */
  def open(id: Id): MuxStream[Id, In, Out] | MuxError

  /**
   * Returns the registered stream with this id, or `None` when no stream with
   * this id is registered: it was never opened, or it was fully closed,
   * cancelled or closed by [[closeAll]].
   */
  def get(id: Id): Option[MuxStream[Id, In, Out]]

  /**
   * Closes the stream with this id at once and removes it from the registry;
   * does nothing when no stream with this id is registered. Afterwards the
   * stream's `receive` returns its queued inbound messages and then `reason`,
   * while `takeOutbound` returns its queued outbound messages and then
   * [[MuxError.StreamClosed]]; `send` and `offerInbound` return `StreamClosed`.
   * Like an HTTP/2 `RST_STREAM`.
   */
  def cancel(id: Id, reason: MuxError): Unit

  /**
   * Closes every registered stream the way [[cancel]] does, with the same
   * `reason`, empties the registry, and makes every later [[open]] return
   * [[MuxError.MuxClosed]].
   */
  def closeAll(reason: MuxError): Unit

  /**
   * Returns the number of registered streams: open and half-closed ones, not
   * fully closed ones.
   */
  def activeCount: Int
}

/**
 * One stream of a [[Mux]], with two queues: the outbound queue, which the
 * application fills with [[send]] and the protocol layer drains with
 * [[takeOutbound]], and the inbound queue, which the protocol fills with
 * [[offerInbound]] and the application drains with [[receive]]. Each queue
 * holds at most 256 messages; past that `send` and `offerInbound` return
 * [[MuxError.QueueFull]]. `null` messages are rejected with
 * [[MuxError.ProtocolError]].
 *
 * The stream follows the HTTP/2 states: it starts OPEN; [[halfClose]] (this
 * side is done sending) and [[signalRemoteClose]] (the peer is done sending)
 * each close one direction, and the second of the two closes the stream;
 * [[close]], `Mux#cancel` and `Mux#closeAll` close it at once. A fully closed
 * stream leaves the `Mux` registry at once, while messages already in its
 * queues can still be taken from this handle.
 *
 * Methods that can fail return `Either[MuxError, X]` on Scala 2 and
 * `X | MuxError` on Scala 3, where `X` is the success value.
 *
 * On the JVM any number of threads may call `send` and `offerInbound` at once,
 * but `takeOutbound` and `receive` must each have one caller thread at a time,
 * which nothing checks: two threads draining one queue get some messages twice,
 * lose others, and can leave the queue looking full for good, a known bug. On
 * Scala.js everything runs on one thread.
 *
 * {{{
 * val mux = Mux[Int, String, String](4)
 * mux.open(1)
 * val stream = mux.get(1).get
 *
 * stream.send("upload part 1")
 * stream.halfClose()                   // this side is done sending
 * stream.send("too late")              // StreamClosed(1)
 * stream.takeOutbound()                // Some(upload part 1)
 * stream.offerInbound("upload stored")
 * stream.signalRemoteClose()           // the peer is done too: the stream closes
 * stream.isClosed                      // true
 * mux.get(1)                           // None
 * stream.receive()                     // Some(upload stored)
 * stream.receive()                     // StreamClosed(1)
 * // On Scala 2 the results are wrapped: Left(StreamClosed(1)),
 * // Right(Some(upload part 1)), and so on.
 * }}}
 *
 * @tparam Id
 *   the stream id type
 * @tparam In
 *   the type of the messages the application sends (outbound)
 * @tparam Out
 *   the type of the messages the application receives (inbound)
 * @see
 *   [[https://zioblocks.com/reference/mux/ Mux]]
 */
trait MuxStream[Id, In, Out] {

  /**
   * Returns the id this stream was opened with.
   */
  def id: Id

  /**
   * Appends `msg` to the outbound queue, for the protocol layer to take with
   * [[takeOutbound]]. Returns [[MuxError.StreamClosed]] once this side is done
   * sending (after [[halfClose]], [[close]], `Mux#cancel` or `Mux#closeAll`),
   * [[MuxError.QueueFull]] when 256 messages are waiting, and
   * [[MuxError.ProtocolError]] for a `null` message. It still works after
   * [[signalRemoteClose]].
   */
  def send(msg: In): Unit | MuxError

  /**
   * Removes and returns the next inbound message: `Some(msg)`, or `None` when
   * no message is waiting. Once the stream is fully closed and its inbound
   * queue is empty, it returns the stream's terminal error instead: the
   * `reason` given to `Mux#cancel` or `Mux#closeAll`, or
   * [[MuxError.StreamClosed]] after [[close]] or after both sides finished.
   *
   * After only the peer finished ([[signalRemoteClose]]), it returns `None`
   * forever once the queue is empty, the same as "no message yet", a known bug;
   * the stream offers no way to tell that the peer is done. On the JVM call it
   * from one thread at a time: two threads at once lose and duplicate messages,
   * a known bug.
   */
  def receive(): Option[Out] | MuxError

  /**
   * Appends `msg` to the inbound queue, for the application to take with
   * [[receive]]; the protocol layer calls it. Returns [[MuxError.StreamClosed]]
   * once the peer is done sending (after [[signalRemoteClose]], [[close]],
   * `Mux#cancel` or `Mux#closeAll`), [[MuxError.QueueFull]] when 256 messages
   * are waiting, and [[MuxError.ProtocolError]] for a `null` message. It still
   * works after [[halfClose]].
   */
  def offerInbound(msg: Out): Unit | MuxError

  /**
   * Removes and returns the next outbound message: `Some(msg)`, or `None` when
   * no message is waiting; the protocol layer calls it. Once the stream is
   * fully closed and its outbound queue is empty, it returns
   * [[MuxError.StreamClosed]], also after `Mux#cancel` with another reason.
   *
   * A fully closed stream leaves the `Mux` registry at once, even with messages
   * still in its outbound queue, a known bug: a protocol that finds streams
   * through `Mux#get` never sends them. Keep a reference to each stream and
   * drain it until `takeOutbound` returns an error. On the JVM call it from one
   * thread at a time: two threads at once lose and duplicate messages, a known
   * bug.
   */
  def takeOutbound(): Option[In] | MuxError

  /**
   * Marks this side as done sending, like an HTTP/2 `END_STREAM` it sends. From
   * OPEN the stream becomes half-closed; when the peer has already finished
   * ([[signalRemoteClose]]), the stream closes and leaves the `Mux` registry,
   * with any queued outbound messages still takeable from this handle. Has no
   * effect on a stream that already finished sending or is closed. Afterwards
   * [[send]] returns [[MuxError.StreamClosed]], while [[receive]] and
   * [[offerInbound]] keep working.
   */
  def halfClose(): Unit

  /**
   * Marks the peer as done sending, like an HTTP/2 `END_STREAM` it receives;
   * the protocol layer calls it. From OPEN the stream becomes half-closed; when
   * this side has already finished ([[halfClose]]), the stream closes and
   * leaves the `Mux` registry. Has no effect on a stream whose peer already
   * finished or that is closed. Afterwards [[offerInbound]] returns
   * [[MuxError.StreamClosed]], while [[send]] keeps working. [[receive]] cannot
   * tell the application that the peer finished, a known bug.
   */
  def signalRemoteClose(): Unit

  /**
   * Returns `true` once the stream is fully closed: both sides finished, or
   * [[close]], `Mux#cancel` or `Mux#closeAll` closed it.
   */
  def isClosed: Boolean

  /**
   * Returns `true` while exactly one side has finished sending. It does not say
   * which side, and no other method does.
   */
  def isHalfClosed: Boolean

  /**
   * Closes the stream at once, from any state, and removes its id from the
   * `Mux` registry. Afterwards [[send]] and [[offerInbound]] return
   * [[MuxError.StreamClosed]]; [[receive]] and [[takeOutbound]] return the
   * messages already queued and then the terminal error (`StreamClosed`, or for
   * `receive` the reason of an earlier `Mux#cancel`).
   *
   * It removes whatever stream is registered under its id, a known bug: when
   * this stream was already closed (by `close`, by both sides finishing, or by
   * `Mux#cancel`) and its id was opened again, closing the old handle
   * unregisters the new stream.
   */
  def close(): Unit
}
