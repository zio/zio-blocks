package muxoverasocket

import java.io.{DataInputStream, DataOutputStream, EOFException, IOException}
import java.nio.charset.StandardCharsets.UTF_8

import zio.blocks.mux._

// What the application sends and receives on one stream. `End` is the
// in-band "I am done sending" marker; it travels the same FIFO as the data.
sealed trait Message
object Message {
  final case class Text(text: String) extends Message
  case object End                     extends Message
}

// Every stream in this guide carries `Message` in both directions.
type MessageStream = MuxStream[Int, Message, Message]

// One frame on the wire: stream id (4 bytes), kind (1 byte),
// payload length (4 bytes), payload (UTF-8 text).
sealed trait Frame { def streamId: Int }

object Frame {
  final case class Data(streamId: Int, text: String)    extends Frame
  final case class End(streamId: Int)                   extends Frame
  final case class Reset(streamId: Int, reason: String) extends Frame

  val MaxPayload: Int = 64 * 1024

  def write(out: DataOutputStream, frame: Frame): Unit = {
    val (kind, payload) = frame match {
      case Data(_, text)    => (0, text.getBytes(UTF_8))
      case End(_)           => (1, Array.emptyByteArray)
      case Reset(_, reason) => (2, reason.getBytes(UTF_8))
    }
    if (payload.length > MaxPayload) throw new IOException(s"payload of ${payload.length} bytes is too large")
    out.writeInt(frame.streamId)
    out.writeByte(kind)
    out.writeInt(payload.length)
    out.write(payload)
  }

  // Returns None when the peer closed the connection between two frames.
  def read(in: DataInputStream): Option[Frame] = {
    val streamId =
      try in.readInt()
      catch { case _: EOFException => return None }
    val kind   = in.readByte()
    val length = in.readInt()
    if (length < 0 || length > MaxPayload) throw new IOException(s"frame length $length is out of range")
    val payload = new Array[Byte](length)
    in.readFully(payload)
    kind match {
      case 0     => Some(Data(streamId, new String(payload, UTF_8)))
      case 1     => Some(End(streamId))
      case 2     => Some(Reset(streamId, new String(payload, UTF_8)))
      case other => throw new IOException(s"unknown frame kind $other")
    }
  }
}
