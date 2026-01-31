package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * A block-level markdown element.
 *
 * Blocks are the top-level structural elements in a markdown document, such as
 * paragraphs, headings, code blocks, lists, and tables.
 */
sealed trait Block extends Product with Serializable

/**
 * A paragraph containing inline content.
 *
 * @param content
 *   The inline elements within the paragraph
 */
final case class Paragraph(content: Chunk[Inline]) extends Block

/**
 * An ATX-style heading (# to ######).
 *
 * @param level
 *   The heading level (H1 to H6)
 * @param content
 *   The inline elements within the heading
 */
final case class Heading(level: HeadingLevel, content: Chunk[Inline]) extends Block

/**
 * A fenced code block.
 *
 * @param info
 *   Optional language/info string (e.g., "scala", "json")
 * @param code
 *   The code content
 */
final case class CodeBlock(info: Option[String], code: String) extends Block

/** A thematic break (---, ***, or ___). */
case object ThematicBreak extends Block

/**
 * A block quote (> prefix).
 *
 * @param content
 *   The blocks within the quote
 */
final case class BlockQuote(content: Chunk[Block]) extends Block

/**
 * An unordered list with bullet markers (-, *, or +).
 *
 * @param items
 *   The list items
 * @param tight
 *   Whether the list is tight (no blank lines between items)
 */
final case class BulletList(items: Chunk[ListItem], tight: Boolean) extends Block

/**
 * An ordered list with numeric markers (1., 2., etc.).
 *
 * @param start
 *   The starting number
 * @param items
 *   The list items
 * @param tight
 *   Whether the list is tight (no blank lines between items)
 */
final case class OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean) extends Block

/**
 * A list item, which may be a task list item.
 *
 * @param content
 *   The blocks within the list item
 * @param checked
 *   For task lists: Some(true) for [x], Some(false) for [ ], None for regular
 *   items
 */
final case class ListItem(content: Chunk[Block], checked: Option[Boolean]) extends Block

/**
 * Raw HTML block content.
 *
 * @param content
 *   The HTML content
 */
final case class HtmlBlock(content: String) extends Block

/**
 * A GFM table.
 *
 * @param header
 *   The header row
 * @param alignments
 *   Column alignments
 * @param rows
 *   The data rows
 */
final case class Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow]) extends Block
