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
 * A block-level Markdown element: one of the top-level pieces a [[Doc]] is made
 * of.
 *
 * The cases are [[Paragraph]], [[Heading]], [[CodeBlock]], [[ThematicBreak]],
 * [[BlockQuote]], [[BulletList]], [[OrderedList]], [[ListItem]], [[HtmlBlock]]
 * and [[Table]]. Blocks that hold other blocks ([[BlockQuote]], [[ListItem]])
 * or inline content ([[Paragraph]], [[Heading]], table cells) nest the matching
 * [[Inline]] and `Block` values.
 *
 * @example
 *   {{{
 * import zio.blocks.chunk.Chunk
 *
 * val release = Doc(
 *   Chunk(
 *     Heading(HeadingLevel.H2, Chunk(Text("Release 1.4"))),
 *     BulletList(
 *       Chunk(
 *         ListItem(Chunk(Paragraph(Chunk(Text("Faster startup")))), None),
 *         ListItem(Chunk(Paragraph(Chunk(Text("Fix the --dry-run flag")))), Some(true))
 *       ),
 *       tight = true
 *     )
 *   )
 * )
 *
 * val markdown = release.toString
 * // ## Release 1.4
 * // - Faster startup
 * // - [x] Fix the --dry-run flag
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
sealed trait Block extends Product with Serializable

/**
 * A paragraph: a run of inline content, ended by a blank line or by the start
 * of another block.
 *
 * @param content
 *   The inline elements within the paragraph
 */
final case class Paragraph(content: Chunk[Inline]) extends Block

/**
 * An ATX heading: one to six `#` characters, a space and the heading text.
 *
 * @param level
 *   The heading level, [[HeadingLevel.H1]] to [[HeadingLevel.H6]]
 * @param content
 *   The inline elements within the heading
 */
final case class Heading(level: HeadingLevel, content: Chunk[Inline]) extends Block

/**
 * A fenced code block, opened and closed by a fence of at least three backticks
 * or tildes. [[Parser]] closes the block at a line that repeats the opening
 * fence exactly, and [[Renderer]] always writes three backticks.
 *
 * @param info
 *   The text after the opening fence, such as `scala` or `json`, or `None` when
 *   the fence has none
 * @param code
 *   The code between the fences, without the fence lines
 */
final case class CodeBlock(info: Option[String], code: String) extends Block

/**
 * A thematic break (horizontal rule). [[Parser]] reads three or more `-`, `*`
 * or `_` characters of one kind, with optional spaces between them, and
 * [[Renderer]] writes `---`.
 */
case object ThematicBreak extends Block

/**
 * A block quote: blocks written after a `>` prefix on each line.
 *
 * @param content
 *   The blocks within the quote
 */
final case class BlockQuote(content: Chunk[Block]) extends Block

/**
 * An unordered list. [[Parser]] accepts `-`, `*` and `+` as markers.
 *
 * @param items
 *   The list items
 * @param tight
 *   Whether the list is tight, with no blank lines between its items.
 *   [[Parser]] always sets it to `true`, and [[Renderer]] is the only renderer
 *   that reads it (it writes a blank line after each item of a loose list)
 */
final case class BulletList(items: Chunk[ListItem], tight: Boolean) extends Block

/**
 * An ordered list: items numbered `1.`, `2.` and so on.
 *
 * @param start
 *   The number of the first item. [[Renderer]] numbers the following items
 *   `start + 1`, `start + 2` and so on, whatever numbers the source used
 * @param items
 *   The list items
 * @param tight
 *   Whether the list is tight, with no blank lines between its items.
 *   [[Parser]] always sets it to `true`, and [[Renderer]] is the only renderer
 *   that reads it
 */
final case class OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean) extends Block

/**
 * An item of a [[BulletList]] or an [[OrderedList]], which can also be a task
 * item.
 *
 * `ListItem` is a [[Block]], but it only has a rendering inside a list:
 * [[Renderer]] and [[TerminalRenderer]] write nothing for a `ListItem` outside
 * one, and [[HtmlRenderer]] writes an `li` element.
 *
 * @param content
 *   The blocks within the item
 * @param checked
 *   For a task item, `Some(true)` for `[x]` and `Some(false)` for `[ ]`; `None`
 *   for a plain item. [[Parser]] reads the task marker only in a bullet list
 */
final case class ListItem(content: Chunk[Block], checked: Option[Boolean]) extends Block

/**
 * A block of raw HTML. The renderers write it out unchanged, without escaping
 * or sanitizing it.
 *
 * @param content
 *   The HTML text
 */
final case class HtmlBlock(content: String) extends Block

/**
 * A GFM table.
 *
 * @param header
 *   The header row
 * @param alignments
 *   One alignment per column
 * @param rows
 *   The data rows
 */
final case class Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow]) extends Block
