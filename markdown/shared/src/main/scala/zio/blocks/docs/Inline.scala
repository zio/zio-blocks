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
 * An inline Markdown element.
 *
 * Inline elements are the content within block elements, such as text,
 * emphasis, links, and code spans.
 */
sealed trait Inline extends Product with Serializable

object Inline {

  /**
   * Plain text content.
   *
   * @param value
   *   The text string
   */
  final case class Text(value: String) extends Inline

  /**
   * Inline code span (`code`).
   *
   * @param value
   *   The code content
   */
  final case class Code(value: String) extends Inline

  /**
   * Emphasized text (*text* or _text_).
   *
   * @param content
   *   The inline elements to emphasize
   */
  final case class Emphasis(content: Chunk[Inline]) extends Inline

  /**
   * Strong/bold text (**text** or __text__).
   *
   * @param content
   *   The inline elements to make strong
   */
  final case class Strong(content: Chunk[Inline]) extends Inline

  /**
   * Strikethrough text (~~text~~).
   *
   * @param content
   *   The inline elements to strike through
   */
  final case class Strikethrough(content: Chunk[Inline]) extends Inline

  /**
   * A hyperlink.
   *
   * @param text
   *   The link text
   * @param url
   *   The link URL
   * @param title
   *   Optional link title
   */
  final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline

  /**
   * A wiki link.
   *
   * @param url
   *   Wiki link URL
   *
   * @param text
   *   Wiki link text
   */
  final case class WikiLink(url: String, text: Option[String]) extends Inline

  /**
   * An image.
   *
   * @param alt
   *   The alt text
   * @param url
   *   The image URL
   * @param title
   *   Optional image title
   */
  final case class Image(alt: String, url: String, title: Option[String]) extends Inline

  /**
   * Raw HTML inline content.
   *
   * @param content
   *   The HTML content
   */
  final case class HtmlInline(content: String) extends Inline

  /** A soft line break (single newline). */
  case object SoftBreak extends Inline

  /** A hard line break (two spaces or backslash before newline). */
  case object HardBreak extends Inline

  /**
   * An autolink (<url> or <email>).
   *
   * @param url
   *   The URL or email address
   * @param isEmail
   *   Whether this is an email autolink
   */
  final case class Autolink(url: String, isEmail: Boolean) extends Inline
}

/**
 * Plain text content.
 *
 * @param value
 *   The text string
 */
final case class Text(value: String) extends Inline

/**
 * Inline code span (`code`).
 *
 * @param value
 *   The code content
 */
final case class Code(value: String) extends Inline

/**
 * Emphasized text (*text* or _text_).
 *
 * @param content
 *   The inline elements to emphasize
 */
final case class Emphasis(content: Chunk[Inline]) extends Inline

/**
 * Strong/bold text (**text** or __text__).
 *
 * @param content
 *   The inline elements to make strong
 */
final case class Strong(content: Chunk[Inline]) extends Inline

/**
 * Strikethrough text (~~text~~).
 *
 * @param content
 *   The inline elements to strike through
 */
final case class Strikethrough(content: Chunk[Inline]) extends Inline

/**
 * A hyperlink.
 *
 * @param text
 *   The link text
 * @param url
 *   The link URL
 * @param title
 *   Optional link title
 */
final case class Link(text: Chunk[Inline], url: String, title: Option[String]) extends Inline

/**
 * A wiki link.
 *
 * @param url
 *   Wiki link URL
 *
 * @param text
 *   Wiki link text
 */
final case class WikiLink(url: String, text: Option[String]) extends Inline

/**
 * An image.
 *
 * @param alt
 *   The alt text
 * @param url
 *   The image URL
 * @param title
 *   Optional image title
 */
final case class Image(alt: String, url: String, title: Option[String]) extends Inline

/**
 * Raw HTML inline content.
 *
 * @param content
 *   The HTML content
 */
final case class HtmlInline(content: String) extends Inline

/** A soft line break (single newline). */
case object SoftBreak extends Inline

/** A hard line break (two spaces or backslash before newline). */
case object HardBreak extends Inline

/**
 * An autolink (<url> or <email>).
 *
 * @param url
 *   The URL or email address
 * @param isEmail
 *   Whether this is an email autolink
 */
final case class Autolink(url: String, isEmail: Boolean) extends Inline
