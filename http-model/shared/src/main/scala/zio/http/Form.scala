package zio.http

import zio.blocks.chunk.Chunk

final case class Form(entries: Chunk[(String, String)]) {

  def get(key: String): Option[String] = {
    var i = 0
    while (i < entries.length) {
      val entry = entries(i)
      if (entry._1 == key) return Some(entry._2)
      i += 1
    }
    None
  }

  def getAll(key: String): Chunk[String] = {
    val builder = Chunk.newBuilder[String]
    var i       = 0
    while (i < entries.length) {
      val entry = entries(i)
      if (entry._1 == key) builder += entry._2
      i += 1
    }
    builder.result()
  }

  def add(key: String, value: String): Form =
    Form(entries :+ (key -> value))

  def isEmpty: Boolean  = entries.isEmpty
  def nonEmpty: Boolean = entries.nonEmpty

  def encode: String = {
    if (entries.isEmpty) return ""
    val sb = new StringBuilder
    var i  = 0
    while (i < entries.length) {
      if (i > 0) sb.append('&')
      val entry = entries(i)
      sb.append(PercentEncoder.encode(entry._1, PercentEncoder.ComponentType.QueryKey))
      sb.append('=')
      sb.append(PercentEncoder.encode(entry._2, PercentEncoder.ComponentType.QueryValue))
      i += 1
    }
    sb.toString
  }
}

object Form {
  val empty: Form = Form(Chunk.empty)

  def apply(pairs: (String, String)*): Form =
    Form(Chunk.fromArray(pairs.toArray))

  def fromString(s: String): Form = {
    if (s.isEmpty) return empty
    val parts   = s.split('&')
    val builder = Chunk.newBuilder[(String, String)]
    var i       = 0
    while (i < parts.length) {
      val part  = parts(i)
      val eqIdx = part.indexOf('=')
      if (eqIdx >= 0) {
        val key   = PercentEncoder.decode(part.substring(0, eqIdx))
        val value = PercentEncoder.decode(part.substring(eqIdx + 1))
        builder += (key -> value)
      } else {
        builder += (PercentEncoder.decode(part) -> "")
      }
      i += 1
    }
    Form(builder.result())
  }
}
