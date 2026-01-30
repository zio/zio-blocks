/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.markdown

import zio.blocks.chunk.Chunk

/**
 * Inline content in a Markdown document.
 *
 * Inline elements are the leaf-level content that appears within blocks. They
 * include text, formatted text (emphasis, strong, strikethrough), code spans,
 * links, images, and line breaks.
 */
sealed trait Inline extends Product with Serializable {

  /**
   * Returns the plain text content of this inline element, stripping all
   * formatting.
   */
  def plainText: String

  /**
   * Returns true if this inline element contains no content.
   */
  def isEmpty: Boolean
}

object Inline {

  /**
   * Plain text content.
   */
  final case class Text(value: String) extends Inline {
    override def plainText: String = value
    override def isEmpty: Boolean  = value.isEmpty
  }

  /**
   * Inline code span (backticks).
   */
  final case class Code(value: String) extends Inline {
    override def plainText: String = value
    override def isEmpty: Boolean  = value.isEmpty
  }

  /**
   * Emphasized text (typically rendered as italic).
   */
  final case class Emphasis(children: Chunk[Inline]) extends Inline {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  /**
   * Strong text (typically rendered as bold).
   */
  final case class Strong(children: Chunk[Inline]) extends Inline {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  /**
   * Strikethrough text (GFM extension).
   */
  final case class Strikethrough(children: Chunk[Inline]) extends Inline {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  /**
   * Hyperlink with optional title.
   */
  final case class Link(children: Chunk[Inline], url: String, title: Option[String]) extends Inline {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  object Link {
    def apply(children: Chunk[Inline], url: String): Link = new Link(children, url, None)
  }

  /**
   * Image with alt text and optional title.
   */
  final case class Image(alt: String, url: String, title: Option[String]) extends Inline {
    override def plainText: String = alt
    override def isEmpty: Boolean  = alt.isEmpty && url.isEmpty
  }

  object Image {
    def apply(alt: String, url: String): Image = new Image(alt, url, None)
  }

  /**
   * Raw HTML inline content.
   */
  final case class HtmlInline(value: String) extends Inline {
    override def plainText: String = ""
    override def isEmpty: Boolean  = value.isEmpty
  }

  /**
   * Soft line break (typically rendered as a space).
   */
  case object SoftBreak extends Inline {
    override def plainText: String = " "
    override def isEmpty: Boolean  = false
  }

  /**
   * Hard line break (typically rendered as <br>).
   */
  case object HardBreak extends Inline {
    override def plainText: String = "\n"
    override def isEmpty: Boolean  = false
  }

  /**
   * Autolink (URL or email automatically turned into a link).
   */
  final case class Autolink(url: String, isEmail: Boolean) extends Inline {
    override def plainText: String = url
    override def isEmpty: Boolean  = url.isEmpty
  }

  // Convenience constructors
  def text(value: String): Inline                                       = Text(value)
  def code(value: String): Inline                                       = Code(value)
  def emphasis(children: Inline*): Inline                               = Emphasis(Chunk.from(children))
  def strong(children: Inline*): Inline                                 = Strong(Chunk.from(children))
  def strikethrough(children: Inline*): Inline                          = Strikethrough(Chunk.from(children))
  def link(text: String, url: String): Inline                           = Link(Chunk(Text(text)), url, None)
  def link(text: String, url: String, title: String): Inline            = Link(Chunk(Text(text)), url, Some(title))
  def link(children: Chunk[Inline], url: String): Inline                = Link(children, url, None)
  def link(children: Chunk[Inline], url: String, title: String): Inline = Link(children, url, Some(title))
  def image(alt: String, url: String): Inline                           = Image(alt, url, None)
  def image(alt: String, url: String, title: String): Inline            = Image(alt, url, Some(title))
  def html(value: String): Inline                                       = HtmlInline(value)
  def softBreak: Inline                                                 = SoftBreak
  def hardBreak: Inline                                                 = HardBreak
  def autolink(url: String): Inline                                     = Autolink(url, isEmail = false)
  def emailAutolink(email: String): Inline                              = Autolink(email, isEmail = true)
}
