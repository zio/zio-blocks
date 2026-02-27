package zio.blocks.http

import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

final class Body private (val data: Array[Byte], val contentType: Option[ContentType]) {

  override def equals(that: Any): Boolean = that match {
    case b: Body => java.util.Arrays.equals(data, b.data) && contentType == b.contentType
    case _       => false
  }

  override def hashCode: Int = java.util.Arrays.hashCode(data) * 31 + contentType.hashCode

  def asString(charset: Charset = Charset.UTF8): String = new String(data, charset.name)

  def asChunk: Chunk[Byte] = Chunk.fromArray(data)

  def length: Int = data.length

  def isEmpty: Boolean = data.length == 0

  def nonEmpty: Boolean = data.length > 0

  override def toString: String = s"Body(length=${data.length}, contentType=$contentType)"
}

object Body {

  val empty: Body = new Body(Array.emptyByteArray, None)

  def fromArray(bytes: Array[Byte], contentType: Option[ContentType] = None): Body = {
    val copy = new Array[Byte](bytes.length)
    System.arraycopy(bytes, 0, copy, 0, bytes.length)
    new Body(copy, contentType)
  }

  def fromChunk(chunk: Chunk[Byte], contentType: Option[ContentType] = None): Body =
    new Body(chunk.toArray, contentType)

  def fromString(s: String, charset: Charset = Charset.UTF8): Body = {
    val bytes = s.getBytes(charset.name)
    val ct    = ContentType(MediaTypes.text.`plain`, charset = Some(charset))
    new Body(bytes, Some(ct))
  }
}
