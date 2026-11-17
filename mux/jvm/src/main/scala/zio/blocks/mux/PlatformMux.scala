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

import java.lang.invoke.{MethodHandles, VarHandle}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

import zio.blocks.ringbuffer.MpscRingBuffer

private[mux] object PlatformMux {
  def create[Id, In, Out](capacity: Int, streamQueueCapacity: Int = Mux.DefaultStreamQueueCapacity): Mux[Id, In, Out] =
    new JvmMux[Id, In, Out](capacity, streamQueueCapacity)

  private final class JvmMux[Id, In, Out](capacity: Int, streamQueueCapacity: Int) extends Mux[Id, In, Out] {
    private val streams                   = new ConcurrentHashMap[Id, JvmMuxStream[Id, In, Out]]()
    private val lock                      = new ReentrantLock()
    @volatile private var closed: Boolean = false

    /**
     * Single shared instance: the mux capacity never changes, so every
     * over-capacity `open` returns this same error. One eager allocation per
     * mux; `open` takes a lock already, and a final val needs no further
     * synchronization.
     */
    private val capacityExceededError: MuxError = MuxError.CapacityExceeded(capacity)

    def open(id: Id): MuxStream[Id, In, Out] | MuxError = {
      lock.lock()
      try {
        if (closed) return MuxError.MuxClosed
        if (streams.containsKey(id)) return MuxError.ProtocolError(s"Stream $id already exists")
        if (streams.size() >= capacity) return capacityExceededError
        val stream = new JvmMuxStream[Id, In, Out](id, this, streamQueueCapacity)
        streams.put(id, stream)
        stream
      } finally lock.unlock()
    }

    def get(id: Id): Option[MuxStream[Id, In, Out]] =
      Option(streams.get(id))

    def cancel(id: Id, reason: MuxError): Unit = {
      lock.lock()
      val stream =
        try streams.remove(id)
        finally lock.unlock()
      if (stream != null) stream.cancelWith(reason)
    }

    def closeAll(reason: MuxError): Unit = {
      lock.lock()
      try {
        closed = true
        streams.values().forEach(_.cancelWith(reason))
        streams.clear()
      } finally lock.unlock()
    }

    def activeCount: Int = streams.size()

    private[mux] def removeStream(id: Id): Unit = {
      lock.lock()
      try streams.remove(id)
      finally lock.unlock()
    }
  }

  private sealed trait StreamState
  private object StreamState {
    case object Open             extends StreamState
    case object HalfClosedLocal  extends StreamState
    case object HalfClosedRemote extends StreamState
    case object Closed           extends StreamState
  }

  private class JvmMuxStreamFields {
    @volatile var state: StreamState      = StreamState.Open
    @volatile var terminalError: MuxError = null
  }

  private object JvmMuxStreamHandle {
    private val lookup = MethodHandles.privateLookupIn(classOf[JvmMuxStreamFields], MethodHandles.lookup())

    val STATE: VarHandle =
      lookup.findVarHandle(classOf[JvmMuxStreamFields], "state", classOf[StreamState])

    val TERMINAL_ERROR: VarHandle =
      lookup.findVarHandle(classOf[JvmMuxStreamFields], "terminalError", classOf[MuxError])
  }

  private final class JvmMuxStream[Id, In, Out](
    streamId: Id,
    mux: JvmMux[Id, In, Out],
    streamQueueCapacity: Int
  ) extends JvmMuxStreamFields
      with MuxStream[Id, In, Out] {
    private val inboundQueue  = new MpscRingBuffer[AnyRef](streamQueueCapacity)
    private val outboundQueue = new MpscRingBuffer[AnyRef](streamQueueCapacity)

    /**
     * Lazily cached immutable errors. Each instance is created at most once per
     * stream, on the first cold path that needs it; streams that never hit a
     * full/closed signal allocate nothing. Scala `lazy val` initialization is
     * thread-safe, so concurrent `send`/`offerInbound` failures share the same
     * instance.
     */
    private lazy val queueFullError: MuxError    = MuxError.QueueFull(streamQueueCapacity)
    private lazy val streamClosedError: MuxError = MuxError.StreamClosed(streamId)

    def id: Id = streamId

    def send(msg: In): Unit | MuxError =
      if (msg.asInstanceOf[AnyRef] eq null)
        MuxError.nullMessage
      else {
        val s = state
        if (s == StreamState.Closed || s == StreamState.HalfClosedLocal)
          streamClosedError
        else if (outboundQueue.offer(msg.asInstanceOf[AnyRef]))
          ()
        else queueFullError
      }

    def receive(): Out | MuxEmpty | MuxError = {
      val polled = inboundQueue.take()
      if (polled != null) polled.asInstanceOf[Out]
      else {
        val error = terminalError
        if (error != null) error
        else MuxEmpty
      }
    }

    def offerInbound(msg: Out): Unit | MuxError =
      if (msg.asInstanceOf[AnyRef] eq null)
        MuxError.nullMessage
      else {
        val s = state
        if (s == StreamState.Closed || s == StreamState.HalfClosedRemote)
          streamClosedError
        else if (inboundQueue.offer(msg.asInstanceOf[AnyRef]))
          ()
        else queueFullError
      }

    def takeOutbound(): In | MuxEmpty | MuxError = {
      val polled = outboundQueue.take()
      if (polled != null) polled.asInstanceOf[In]
      else {
        val s = state
        if (s == StreamState.Closed) streamClosedError
        else MuxEmpty
      }
    }

    def halfClose(): Unit =
      if (!JvmMuxStreamHandle.STATE.compareAndSet(this, StreamState.Open, StreamState.HalfClosedLocal)) {
        if (state == StreamState.HalfClosedRemote) closeClosedStream(streamClosedError)
      }

    def signalRemoteClose(): Unit =
      if (!JvmMuxStreamHandle.STATE.compareAndSet(this, StreamState.Open, StreamState.HalfClosedRemote)) {
        if (state == StreamState.HalfClosedLocal) closeClosedStream(streamClosedError)
      }

    def isClosed: Boolean = state == StreamState.Closed

    def isHalfClosed: Boolean = {
      val s = state
      s == StreamState.HalfClosedLocal || s == StreamState.HalfClosedRemote
    }

    def close(): Unit = closeClosedStream(streamClosedError)

    private[mux] def cancelWith(reason: MuxError): Unit = {
      state = StreamState.Closed
      setTerminalErrorIfAbsent(reason)
    }

    private def closeClosedStream(reason: MuxError): Unit = {
      state = StreamState.Closed
      setTerminalErrorIfAbsent(reason)
      mux.removeStream(streamId)
    }

    private def setTerminalErrorIfAbsent(reason: MuxError): Unit = {
      JvmMuxStreamHandle.TERMINAL_ERROR.compareAndSet(this, null, reason)
      ()
    }
  }
}
