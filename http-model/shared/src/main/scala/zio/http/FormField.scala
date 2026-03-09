package zio.http

import zio.blocks.chunk.Chunk

sealed trait FormField {
  def name: String
}

object FormField {

  final case class Simple(name: String, value: String) extends FormField

  final case class Text(
    name: String,
    value: String,
    contentType: Option[ContentType] = None,
    filename: Option[String] = None
  ) extends FormField

  final case class Binary(
    name: String,
    data: Chunk[Byte],
    contentType: ContentType,
    filename: Option[String] = None
  ) extends FormField
}
