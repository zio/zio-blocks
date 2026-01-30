package zio.blocks.markdown

import zio.blocks.chunk.Chunk

sealed trait Inline extends Product with Serializable

object Inline {
  final case class Text(value: String)                                           extends Inline
  final case class Code(value: String)                                           extends Inline
  final case class Emphasis(content: Chunk[Inline])                              extends Inline
  final case class Strong(content: Chunk[Inline])                                extends Inline
  final case class Strikethrough(content: Chunk[Inline])                         extends Inline
  final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline
  final case class Image(alt: String, url: String, title: Option[String])        extends Inline
  final case class HtmlInline(content: String)                                   extends Inline
  case object SoftBreak                                                          extends Inline
  case object HardBreak                                                          extends Inline
  final case class Autolink(url: String, isEmail: Boolean)                       extends Inline
}

final case class Text(value: String)                                           extends Inline
final case class Code(value: String)                                           extends Inline
final case class Emphasis(content: Chunk[Inline])                              extends Inline
final case class Strong(content: Chunk[Inline])                                extends Inline
final case class Strikethrough(content: Chunk[Inline])                         extends Inline
final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline
final case class Image(alt: String, url: String, title: Option[String])        extends Inline
final case class HtmlInline(content: String)                                   extends Inline
case object SoftBreak                                                          extends Inline
case object HardBreak                                                          extends Inline
final case class Autolink(url: String, isEmail: Boolean)                       extends Inline
