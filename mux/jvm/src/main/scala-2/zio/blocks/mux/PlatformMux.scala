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
  private val StreamQueueCapacity = 256

  def create[Id, In, Out](capacity: Int): Mux[Id, In, Out] =
    new JvmMux[Id, In, Out](capacity)

  private final class JvmMux[Id, In, Out](capacity: Int) extends Mux[Id, In, Out] {
    private val streams                   = new ConcurrentHashMap[Id, JvmMuxStream[Id, In, Out]]()
    private val lock                      = new ReentrantLock()
    @volatile private var closed: Boolean = false

    def open(id: Id): Either[MuxError, MuxStream[Id, In, Out]] = {
      lock.lock()
      try {
        if (closed) return Left(MuxError.MuxClosed)
        if (streams.containsKey(id)) return Left(MuxError.ProtocolError(s"Stream $id already exists"))
        if (streams.size() >= capacity) return Left(MuxError.CapacityExceeded(capacity))
        val stream = new JvmMuxStream[Id, In, Out](id, this)
        streams.put(id, stream)
        Right(stream)
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
    mux: JvmMux[Id, In, Out]
  ) extends JvmMuxStreamFields
      with MuxStream[Id, In, Out] {
    private val inboundQueue  = new MpscRingBuffer[AnyRef](StreamQueueCapacity)
    private val outboundQueue = new MpscRingBuffer[AnyRef](StreamQueueCapacity)

    def id: Id = streamId

    def send(msg: In): Either[MuxError, Unit] = {
      if (msg.asInstanceOf[AnyRef] eq null)
        Left(MuxError.ProtocolError("null message"))
      else {
        val s = state
        if (s == StreamState.Closed || s == StreamState.HalfClosedLocal)
          Left(MuxError.StreamClosed(streamId))
        else if (outboundQueue.offer(msg.asInstanceOf[AnyRef]))
          Right(())
        else Left(MuxError.QueueFull(StreamQueueCapacity))
      }
    }

    def receive(): Either[MuxError, Option[Out]] = {
      val polled = inboundQueue.take()
      if (polled != null) Right(Some(polled.asInstanceOf[Out]))
      else {
        val error = terminalError
        if (error != null) Left(error)
        else Right(None)
      }
    }

    def offerInbound(msg: Out): Either[MuxError, Unit] = {
      if (msg.asInstanceOf[AnyRef] eq null)
        Left(MuxError.ProtocolError("null message"))
      else {
        val s = state
        if (s == StreamState.Closed || s == StreamState.HalfClosedRemote)
          Left(MuxError.StreamClosed(streamId))
        else if (inboundQueue.offer(msg.asInstanceOf[AnyRef]))
          Right(())
        else Left(MuxError.QueueFull(StreamQueueCapacity))
      }
    }

    def takeOutbound(): Either[MuxError, Option[In]] = {
      val polled = outboundQueue.take()
      if (polled != null) Right(Some(polled.asInstanceOf[In]))
      else {
        val s = state
        if (s == StreamState.Closed) Left(MuxError.StreamClosed(streamId))
        else Right(None)
      }
    }

    def halfClose(): Unit =
      if (!JvmMuxStreamHandle.STATE.compareAndSet(this, StreamState.Open, StreamState.HalfClosedLocal)) {
        if (state == StreamState.HalfClosedRemote) closeClosedStream(MuxError.StreamClosed(streamId))
      }

    def signalRemoteClose(): Unit =
      if (!JvmMuxStreamHandle.STATE.compareAndSet(this, StreamState.Open, StreamState.HalfClosedRemote)) {
        if (state == StreamState.HalfClosedLocal) closeClosedStream(MuxError.StreamClosed(streamId))
      }

    def isClosed: Boolean = state == StreamState.Closed

    def isHalfClosed: Boolean = {
      val s = state
      s == StreamState.HalfClosedLocal || s == StreamState.HalfClosedRemote
    }

    def close(): Unit = closeClosedStream(MuxError.StreamClosed(streamId))

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
