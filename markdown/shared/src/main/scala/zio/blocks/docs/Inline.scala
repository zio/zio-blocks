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

package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * An inline Markdown element: the content inside a block such as a paragraph, a
 * heading or a table cell.
 *
 * The cases are plain text ([[Text]]), code spans ([[Code]]), emphasis
 * ([[Emphasis]], [[Strong]], [[Strikethrough]]), links and images ([[Link]],
 * [[Image]], [[Autolink]]), raw HTML ([[HtmlInline]]) and line breaks
 * ([[SoftBreak]], [[HardBreak]]). Every case exists twice: as a member of the
 * package (`Text`) and as a member of the [[Inline]] companion (`Inline.Text`).
 * [[Parser]] builds the package-level ones, and the renderers accept both. A
 * [[Doc]] compares normalized documents. Normalizing turns the `Inline.`
 * emphasis, strong, strikethrough and link cases, and any text node merged with
 * a neighbour, into the package-level ones, but keeps a lone `Inline.Text`,
 * `Inline.Code` and the other `Inline.` cases without children. So a document
 * holding `Inline.Code("a")` is not equal to one holding `Code("a")`.
 *
 * @example
 *   {{{
 * import zio.blocks.chunk.Chunk
 *
 * val inlines: Chunk[Inline] = Chunk(
 *   Text("Read the "),
 *   Link(Chunk(Text("changelog")), "https://example.com/changelog", None),
 *   Text(" for "),
 *   Strong(Chunk(Text("breaking"))),
 *   Text(" changes")
 * )
 *
 * val markdown = Renderer.renderInlines(inlines)
 * // Read the [changelog](https://example.com/changelog) for **breaking** changes
 * val html = HtmlRenderer.renderInlines(inlines)
 * // Read the <a href="https://example.com/changelog">changelog</a> for <strong>breaking</strong> changes
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
sealed trait Inline extends Product with Serializable

/**
 * The inline cases in the `Inline` namespace. They are separate classes from
 * the package-level cases of the same names, and carry the same fields.
 */
object Inline {

  /**
   * Plain text.
   *
   * @param value
   *   The text, with no Markdown syntax interpreted
   */
  final case class Text(value: String) extends Inline

  /**
   * A code span, written with backticks.
   *
   * @param value
   *   The code, shown literally
   */
  final case class Code(value: String) extends Inline

  /**
   * Emphasized text, written `*text*` or `_text_`.
   *
   * @param content
   *   The inline elements to emphasize
   */
  final case class Emphasis(content: Chunk[Inline]) extends Inline

  /**
   * Strong (bold) text, written `**text**` or `__text__`.
   *
   * @param content
   *   The inline elements to make strong
   */
  final case class Strong(content: Chunk[Inline]) extends Inline

  /**
   * Struck-through text, written `~~text~~`.
   *
   * @param content
   *   The inline elements to strike through
   */
  final case class Strikethrough(content: Chunk[Inline]) extends Inline

  /**
   * A hyperlink, written `[text](url "title")`.
   *
   * @param text
   *   The link text
   * @param url
   *   The link target
   * @param title
   *   The optional title in double quotes
   */
  final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline

  /**
   * An image, written `![alt](url "title")`.
   *
   * @param alt
   *   The alternative text, a plain string (Markdown inside it is not parsed)
   * @param url
   *   The image location
   * @param title
   *   The optional title in double quotes
   */
  final case class Image(alt: String, url: String, title: Option[String]) extends Inline

  /**
   * Raw HTML inside a paragraph or heading, such as the `<br>` in `x <br> y`.
   * The renderers write it out unchanged.
   *
   * @param content
   *   The HTML text, including the angle brackets
   */
  final case class HtmlInline(content: String) extends Inline

  /**
   * A soft line break: a single newline inside a paragraph. [[Renderer]] writes
   * a newline, and [[HtmlRenderer]] and [[TerminalRenderer]] write a space.
   */
  case object SoftBreak extends Inline

  /**
   * A hard line break: a line that ends with two spaces or a backslash.
   * [[Renderer]] writes two spaces and a newline, [[HtmlRenderer]] writes
   * `<br>` and [[TerminalRenderer]] writes a newline.
   */
  case object HardBreak extends Inline

  /**
   * A link written as `<url>` or `<email>`, or as a bare `http://` or
   * `https://` address.
   *
   * @param url
   *   The address, without angle brackets
   * @param isEmail
   *   Whether the address is an email address, which [[HtmlRenderer]] turns
   *   into a `mailto:` link
   */
  final case class Autolink(url: String, isEmail: Boolean) extends Inline
}

/**
 * Plain text.
 *
 * @param value
 *   The text, with no Markdown syntax interpreted
 */
final case class Text(value: String) extends Inline

/**
 * A code span, written with backticks.
 *
 * @param value
 *   The code, shown literally
 */
final case class Code(value: String) extends Inline

/**
 * Emphasized text, written `*text*` or `_text_`.
 *
 * @param content
 *   The inline elements to emphasize
 */
final case class Emphasis(content: Chunk[Inline]) extends Inline

/**
 * Strong (bold) text, written `**text**` or `__text__`.
 *
 * @param content
 *   The inline elements to make strong
 */
final case class Strong(content: Chunk[Inline]) extends Inline

/**
 * Struck-through text, written `~~text~~`.
 *
 * @param content
 *   The inline elements to strike through
 */
final case class Strikethrough(content: Chunk[Inline]) extends Inline

/**
 * A hyperlink, written `[text](url "title")`.
 *
 * @param text
 *   The link text
 * @param url
 *   The link target
 * @param title
 *   The optional title in double quotes
 */
final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline

/**
 * An image, written `![alt](url "title")`.
 *
 * @param alt
 *   The alternative text, a plain string (Markdown inside it is not parsed)
 * @param url
 *   The image location
 * @param title
 *   The optional title in double quotes
 */
final case class Image(alt: String, url: String, title: Option[String]) extends Inline

/**
 * Raw HTML inside a paragraph or heading, such as the `<br>` in `x <br> y`. The
 * renderers write it out unchanged.
 *
 * @param content
 *   The HTML text, including the angle brackets
 */
final case class HtmlInline(content: String) extends Inline

/**
 * A soft line break: a single newline inside a paragraph. [[Renderer]] writes a
 * newline, and [[HtmlRenderer]] and [[TerminalRenderer]] write a space.
 */
case object SoftBreak extends Inline

/**
 * A hard line break: a line that ends with two spaces or a backslash.
 * [[Renderer]] writes two spaces and a newline, [[HtmlRenderer]] writes `<br>`
 * and [[TerminalRenderer]] writes a newline.
 */
case object HardBreak extends Inline

/**
 * A link written as `<url>` or `<email>`, or as a bare `http://` or `https://`
 * address.
 *
 * @param url
 *   The address, without angle brackets
 * @param isEmail
 *   Whether the address is an email address, which [[HtmlRenderer]] turns into
 *   a `mailto:` link
 */
final case class Autolink(url: String, isEmail: Boolean) extends Inline
