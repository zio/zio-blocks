package zio.http

import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

/**
 * Materialized, immutable HTTP message body backed by a `Chunk[Byte]`.
 *
 * The underlying `data` chunk provides structural equality and efficient
 * slicing without defensive copies.
 */
final class Body private (val data: Chunk[Byte], val contentType: Option[ContentType]) {

  override def equals(that: Any): Boolean = that match {
    case b: Body => data == b.data && contentType == b.contentType
    case _       => false
  }

  override def hashCode: Int = data.hashCode * 31 + contentType.hashCode

  def asString(charset: Charset = Charset.UTF8): String = new String(data.toArray, charset.name)

  def toArray: Array[Byte] = data.toArray

  def length: Int = data.length

  def isEmpty: Boolean = data.isEmpty

  def nonEmpty: Boolean = data.nonEmpty

  override def toString: String = s"Body(length=${data.length}, contentType=$contentType)"
}

object Body {

  val empty: Body = new Body(Chunk.empty, None)

  def fromArray(bytes: Array[Byte], contentType: Option[ContentType] = None): Body =
    new Body(Chunk.fromArray(bytes), contentType)

  def fromChunk(chunk: Chunk[Byte], contentType: Option[ContentType] = None): Body =
    new Body(chunk, contentType)

  def fromString(s: String, charset: Charset = Charset.UTF8): Body = {
    val bytes = s.getBytes(charset.name)
    val ct    = ContentType(MediaTypes.text.`plain`, charset = Some(charset))
    new Body(Chunk.fromArray(bytes), Some(ct))
  }
}
