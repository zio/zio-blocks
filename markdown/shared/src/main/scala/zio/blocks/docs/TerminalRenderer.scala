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
 * Writes a [[Doc]], or any of its [[Block]] and [[Inline]] elements, as text
 * with ANSI escape codes for a terminal.
 *
 * Headings are bold and colored by level (level 1 red, 2 yellow, 3 green, 4
 * cyan, 5 blue, 6 magenta). Emphasis is italic, strong is bold, strikethrough
 * is struck through, and code spans and code blocks have a gray background.
 * Links are blue and underlined, followed by the address in parentheses (a link
 * title is not shown); an autolink is the blue, underlined address alone.
 * Images become `[Image: alt] (url)`. Quotes get a `│ ` prefix, bullet items
 * start with `• `, task items with `[x] ` or `[ ] `, and a thematic break is a
 * line of 40 `─` characters. Text, raw HTML and code are written without
 * escaping.
 *
 * @example
 *   {{{
 * val doc = Parser.parse("# Deploy\n\nRun `make deploy` for **staging**.").toOption.get
 *
 * val ansi = TerminalRenderer.render(doc)
 * val shown = ansi.replace("\u001b", "ESC").replace("\n", "\\n")
 * // ESC[1mESC[31mDeployESC[0m\n\nRun ESC[48;5;236mmake deployESC[0m for ESC[1mstagingESC[0m.\n\n
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
object TerminalRenderer {

  private val Reset       = "\u001b[0m"
  private val Bold        = "\u001b[1m"
  private val Italic      = "\u001b[3m"
  private val Underline   = "\u001b[4m"
  private val StrikeStyle = "\u001b[9m"
  private val Red         = "\u001b[31m"
  private val Green       = "\u001b[32m"
  private val Yellow      = "\u001b[33m"
  private val Blue        = "\u001b[34m"
  private val Magenta     = "\u001b[35m"
  private val Cyan        = "\u001b[36m"
  private val GrayBg      = "\u001b[48;5;236m"

  private def headingColor(level: HeadingLevel): String = level match {
    case HeadingLevel.H1 => Red
    case HeadingLevel.H2 => Yellow
    case HeadingLevel.H3 => Green
    case HeadingLevel.H4 => Cyan
    case HeadingLevel.H5 => Blue
    case HeadingLevel.H6 => Magenta
  }

  /**
   * Render a Doc to an ANSI-colored terminal string.
   *
   * Paragraphs, headings, code blocks, thematic breaks and HTML blocks end with
   * a blank line; lists, block quotes and tables end with a single newline.
   *
   * @param doc
   *   The document to render
   * @return
   *   ANSI-colored string for terminal display
   */
  def render(doc: Doc): String =
    doc.blocks.map(renderBlock).mkString

  /**
   * Render a Block to its terminal representation.
   *
   * A table is drawn with `│` around and between cells and a separator row of
   * `─` segments joined by `├`, `┼` and `┤`; the separator has one segment per
   * alignment and the alignment itself has no effect. A block quote drops its
   * blank lines. A `ListItem` on its own renders as the empty string; it is
   * only written inside a list.
   *
   * @param block
   *   The block element to render
   * @return
   *   ANSI-colored string for this block
   */
  def renderBlock(block: Block): String = block match {
    case Paragraph(content) =>
      s"${renderInlines(content)}\n\n"

    case Heading(level, content) =>
      s"$Bold${headingColor(level)}${renderInlines(content)}$Reset\n\n"

    case CodeBlock(_, code) =>
      s"$GrayBg$code$Reset\n\n"

    case ThematicBreak =>
      s"${"─" * 40}\n\n"

    case BlockQuote(content) =>
      val rendered = content.map(renderBlock).mkString
      val lines    = rendered.split("\n", -1).toList.dropRight(1).filterNot(_.isEmpty)
      lines.map { line =>
        "│ " + line
      }.mkString("", "\n", "\n")

    case BulletList(items, _) =>
      items.map { item =>
        renderListItem(item, None)
      }.mkString

    case OrderedList(start, items, _) =>
      items.zipWithIndex.map { case (item, idx) =>
        renderListItem(item, Some(start + idx))
      }.mkString

    case ListItem(_, _) =>
      ""

    case HtmlBlock(content) =>
      s"$content\n\n"

    case Table(header, alignments, rows) =>
      val headerStr = renderTableRow(header)
      val alignStr  = renderAlignmentRow(alignments)
      val rowsStr   = rows.map(renderTableRow).mkString
      headerStr + alignStr + rowsStr
  }

  /**
   * Render a single list item.
   *
   * @param item
   *   The list item to render
   * @param numberOpt
   *   If Some(n), render as ordered list item; if None, render as bullet
   * @return
   *   Terminal string for this list item
   */
  private def renderListItem(item: ListItem, numberOpt: Option[Int]): String = {
    val prefix = numberOpt match {
      case Some(n) => s"$n. "
      case None    =>
        item.checked match {
          case Some(true)  => "[x] "
          case Some(false) => "[ ] "
          case None        => "• "
        }
    }

    val contentStr = renderBlocks(item.content).stripTrailing
    val lines      = contentStr.split("\n").toList
    lines match {
      case Nil           => prefix + "\n"
      case first :: rest =>
        val firstLine    = prefix + first + "\n"
        val indentedRest = rest.map { line =>
          if (line.isEmpty) "\n"
          else "  " + line + "\n"
        }.mkString
        firstLine + indentedRest
    }
  }

  /**
   * Render a table row.
   *
   * @param row
   *   The table row to render
   * @return
   *   Terminal string for this row
   */
  private def renderTableRow(row: TableRow): String = {
    val cells = row.cells.map(renderInlines).mkString(" │ ")
    s"│ $cells │\n"
  }

  /**
   * Render the alignment row of a table.
   *
   * @param alignments
   *   The column alignments
   * @return
   *   Terminal string for the alignment row
   */
  private def renderAlignmentRow(alignments: Chunk[Alignment]): String = {
    val cells = alignments.map { alignment =>
      alignment match {
        case Alignment.Left   => "─────"
        case Alignment.Right  => "─────"
        case Alignment.Center => "─────"
        case Alignment.None   => "─────"
      }
    }.mkString("├─", "─┼─", "─┤")
    s"$cells\n"
  }

  /**
   * Render a chunk of blocks as a single string.
   *
   * @param blocks
   *   The block elements to render
   * @return
   *   Concatenated terminal string
   */
  private def renderBlocks(blocks: Chunk[Block]): String =
    blocks.map(renderBlock).mkString

  /**
   * Render a chunk of inlines as a single string.
   *
   * @param inlines
   *   The inline elements to render
   * @return
   *   The rendered elements, one after another
   */
  def renderInlines(inlines: Chunk[Inline]): String =
    inlines.map(renderInline).mkString

  /**
   * Render a single inline element.
   *
   * A soft break becomes a space and a hard break becomes a newline.
   *
   * @param inline
   *   The inline element to render
   * @return
   *   ANSI-colored string for this inline
   */
  def renderInline(inline: Inline): String = inline match {
    case Text(value) =>
      value
    case Inline.Text(value) =>
      value

    case Code(value) =>
      s"$GrayBg$value$Reset"
    case Inline.Code(value) =>
      s"$GrayBg$value$Reset"

    case Emphasis(content) =>
      s"$Italic${renderInlines(content)}$Reset"
    case Inline.Emphasis(content) =>
      s"$Italic${renderInlines(content)}$Reset"

    case Strong(content) =>
      s"$Bold${renderInlines(content)}$Reset"
    case Inline.Strong(content) =>
      s"$Bold${renderInlines(content)}$Reset"

    case Strikethrough(content) =>
      s"$StrikeStyle${renderInlines(content)}$Reset"
    case Inline.Strikethrough(content) =>
      s"$StrikeStyle${renderInlines(content)}$Reset"

    case Link(text, url, _) =>
      s"$Blue$Underline${renderInlines(text)}$Reset ($url)"
    case Inline.Link(text, url, _) =>
      s"$Blue$Underline${renderInlines(text)}$Reset ($url)"

    case Image(alt, url, _) =>
      s"[Image: $alt] ($url)"
    case Inline.Image(alt, url, _) =>
      s"[Image: $alt] ($url)"

    case HtmlInline(html) =>
      html
    case Inline.HtmlInline(html) =>
      html

    case SoftBreak =>
      " "
    case Inline.SoftBreak =>
      " "

    case HardBreak =>
      "\n"
    case Inline.HardBreak =>
      "\n"

    case Autolink(url, _) =>
      s"$Blue$Underline$url$Reset"
    case Inline.Autolink(url, _) =>
      s"$Blue$Underline$url$Reset"
  }
}
