package zio.blocks.mux

import scala.collection.mutable

private[mux] object PlatformMux {

  def create[Id, In, Out](capacity: Int): Mux[Id, In, Out] =
    new JsMux[Id, In, Out](capacity)

  private final class JsMux[Id, In, Out](capacity: Int) extends Mux[Id, In, Out] {
    private val streams = mutable.HashMap.empty[Id, JsMuxStream[Id, In, Out]]
    private var closed: Boolean = false

    def open(id: Id): Either[MuxError, MuxStream[Id, In, Out]] = {
      if (closed) return Left(MuxError.MuxClosed)
      if (streams.contains(id)) return Left(MuxError.ProtocolError(s"Stream $id already exists"))
      if (streams.size >= capacity) return Left(MuxError.CapacityExceeded(capacity))
      val stream = new JsMuxStream[Id, In, Out](id, this)
      streams.put(id, stream)
      Right(stream)
    }

    def get(id: Id): Option[MuxStream[Id, In, Out]] =
      streams.get(id)

    def cancel(id: Id, reason: MuxError): Unit = {
      val stream = streams.remove(id)
      stream.foreach(_.cancelWith(reason))
    }

    def closeAll(reason: MuxError): Unit = {
      closed = true
      streams.values.foreach(_.cancelWith(reason))
      streams.clear()
    }

    def activeCount: Int = streams.size

    private[mux] def removeStream(id: Id): Unit =
      streams.remove(id)
  }

  private sealed trait StreamState
  private object StreamState {
    case object Open             extends StreamState
    case object HalfClosedLocal  extends StreamState
    case object HalfClosedRemote extends StreamState
    case object Closed           extends StreamState
  }

  private final class JsMuxStream[Id, In, Out](
    streamId: Id,
    mux: JsMux[Id, In, Out]
  ) extends MuxStream[Id, In, Out] {
    private var state: StreamState                                      = StreamState.Open
    private val inboundQueue: mutable.ArrayDeque[Either[MuxError, Out]] = mutable.ArrayDeque.empty
    private val outboundQueue: mutable.ArrayDeque[In]                   = mutable.ArrayDeque.empty
    private var cancelError: Option[MuxError]                           = None

    def id: Id = streamId

    def send(msg: In): Either[MuxError, Unit] =
      if (state == StreamState.Closed || state == StreamState.HalfClosedLocal)
        Left(MuxError.StreamClosed(streamId))
      else {
        outboundQueue.append(msg)
        Right(())
      }

    def receive(): Either[MuxError, Option[Out]] =
      if (inboundQueue.nonEmpty) inboundQueue.removeHead().map(Some(_))
      else if (state == StreamState.Closed)
        cancelError.map(Left(_)).getOrElse(Left(MuxError.StreamClosed(streamId)))
      else Right(None)

    def offerInbound(msg: Out): Either[MuxError, Unit] =
      if (state == StreamState.Closed || state == StreamState.HalfClosedRemote)
        Left(MuxError.StreamClosed(streamId))
      else {
        inboundQueue.append(Right(msg))
        Right(())
      }

    def takeOutbound(): Either[MuxError, Option[In]] =
      if (outboundQueue.nonEmpty) Right(Some(outboundQueue.removeHead()))
      else if (state == StreamState.Closed) Left(MuxError.StreamClosed(streamId))
      else Right(None)

    def halfClose(): Unit =
      state match {
        case StreamState.Open =>
          state = StreamState.HalfClosedLocal
        case StreamState.HalfClosedRemote =>
          state = StreamState.Closed
          mux.removeStream(streamId)
        case StreamState.HalfClosedLocal | StreamState.Closed =>
          ()
      }

    def signalRemoteClose(): Unit =
      state match {
        case StreamState.Open =>
          state = StreamState.HalfClosedRemote
        case StreamState.HalfClosedLocal =>
          state = StreamState.Closed
          mux.removeStream(streamId)
        case StreamState.HalfClosedRemote | StreamState.Closed =>
          ()
      }

    def isClosed: Boolean = state == StreamState.Closed

    def isHalfClosed: Boolean =
      state == StreamState.HalfClosedLocal || state == StreamState.HalfClosedRemote

    def close(): Unit = {
      state = StreamState.Closed
      mux.removeStream(streamId)
    }

    private[mux] def cancelWith(reason: MuxError): Unit = {
      state = StreamState.Closed
      cancelError = Some(reason)
    }
  }
}
