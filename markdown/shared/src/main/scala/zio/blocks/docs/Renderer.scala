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
 * Writes a [[Doc]], or any of its [[Block]] and [[Inline]] elements, as GitHub
 * Flavored Markdown text.
 *
 * The output is meant to be read by [[Parser]], but the round trip does not
 * always give back an equal document. [[Renderer]] writes text, code spans,
 * URLs and titles as they are, without escaping Markdown syntax, so a `Text`
 * node that contains `*` or a `Code` node that contains a backtick comes back
 * as something else. It also writes the alignment row of a centered table
 * column with two dashes (`:--:`), which [[Parser]] does not accept as a
 * delimiter row (it needs at least three dashes per column), so such a table is
 * read back as a paragraph.
 *
 * @example
 *   {{{
 * import zio.blocks.chunk.Chunk
 *
 * val doc = Doc(
 *   Chunk(
 *     Heading(HeadingLevel.H1, Chunk(Text("Release notes"))),
 *     Paragraph(Chunk(Text("Fixed "), Strong(Chunk(Text("two"))), Text(" bugs."))),
 *     OrderedList(
 *       3,
 *       Chunk(
 *         ListItem(Chunk(Paragraph(Chunk(Text("Upgrade")))), None),
 *         ListItem(Chunk(Paragraph(Chunk(Text("Restart")))), None)
 *       ),
 *       tight = true
 *     )
 *   )
 * )
 *
 * val markdown = Renderer.render(doc)
 * // # Release notes
 * // Fixed **two** bugs.
 * //
 * // 3. Upgrade
 * // 4. Restart
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
object Renderer {

  /**
   * Render a Doc to a GitHub Flavored Markdown string.
   *
   * Writes the blocks one after another with [[Renderer.renderBlock]]. Each
   * block ends with a newline, and a paragraph ends with a blank line. No blank
   * line is written after a heading, a list, a code block or a thematic break.
   *
   * @param doc
   *   The document to render
   * @return
   *   GFM markdown string
   *
   * @example
   *   {{{
   * import zio.blocks.chunk.Chunk
   *
   * val doc = Doc(Chunk(Heading(HeadingLevel.H1, Chunk(Text("Hello")))))
   * val markdown = Renderer.render(doc)
   * // "# Hello\n"
   *   }}}
   */
  def render(doc: Doc): String =
    doc.blocks.map(renderBlock).mkString

  /**
   * Render a Block to its GFM representation.
   *
   * A code block is written with a fence of three backticks, a thematic break
   * as `---`, a block quote with a `> ` prefix on each line (`>` on a blank
   * line), a bullet list with `- ` markers and an ordered list numbered from
   * its `start`. A `ListItem` on its own renders as the empty string; it is
   * only written inside a list.
   *
   * @param block
   *   The block element to render
   * @return
   *   GFM markdown string for this block
   */
  def renderBlock(block: Block): String = block match {
    case Heading(level, content) =>
      val hashes = "#" * level.value
      s"$hashes ${renderInlines(content)}\n"

    case Paragraph(content) =>
      s"${renderInlines(content)}\n\n"

    case CodeBlock(info, code) =>
      val infoStr = info.getOrElse("")
      s"```$infoStr\n$code\n```\n"

    case ThematicBreak =>
      "---\n"

    case BlockQuote(content) =>
      val rendered = content.map(renderBlock).mkString
      val lines    = rendered.split("\n", -1).toList.dropRight(1)
      lines.map { line =>
        if (line.isEmpty) ">" else "> " + line
      }.mkString("", "\n", "\n")

    case BulletList(items, tight) =>
      val rendered = items.map { item =>
        renderListItemForList(item, None, tight)
      }
      rendered.mkString

    case OrderedList(start, items, tight) =>
      val rendered = items.zipWithIndex.map { case (item, idx) =>
        renderListItemForList(item, Some(start + idx), tight)
      }
      rendered.mkString

    case ListItem(_, _) =>
      ""

    case HtmlBlock(content) =>
      s"$content\n"

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
   * @param tight
   *   Whether to use tight list rendering (no blank lines)
   * @return
   *   GFM markdown string for this list item
   */
  private def renderListItemForList(
    item: ListItem,
    numberOpt: Option[Int],
    tight: Boolean
  ): String = {
    val prefix = numberOpt match {
      case Some(n) => s"$n. "
      case None    =>
        item.checked match {
          case Some(true)  => "- [x] "
          case Some(false) => "- [ ] "
          case None        => "- "
        }
    }

    val contentStr = item.content.map(renderBlock).mkString.stripTrailing
    if (tight) {
      val content = contentStr.replaceAll("\n\n+", "\n")
      val lines   = content.split("\n").toList
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
    } else {
      val lines = contentStr.split("\n").toList
      lines match {
        case Nil           => prefix + "\n"
        case first :: rest =>
          val firstLine    = prefix + first + "\n"
          val indentedRest = rest.map { line =>
            if (line.isEmpty) "\n"
            else "  " + line + "\n"
          }.mkString
          firstLine + indentedRest + "\n"
      }
    }
  }

  /**
   * Render a table row.
   *
   * @param row
   *   The table row to render
   * @return
   *   GFM markdown string for this row
   */
  private def renderTableRow(row: TableRow): String = {
    val cells = row.cells.map(renderInlines).mkString(" | ")
    s"| $cells |\n"
  }

  /**
   * Render the alignment row of a table.
   *
   * @param alignments
   *   The column alignments
   * @return
   *   GFM markdown string for the alignment row
   */
  private def renderAlignmentRow(alignments: Chunk[Alignment]): String = {
    val cells = alignments.map { alignment =>
      alignment match {
        case Alignment.Left   => ":---"
        case Alignment.Right  => "---:"
        case Alignment.Center => ":--:"
        case Alignment.None   => "---"
      }
    }.mkString("|", "|", "|")
    s"$cells\n"
  }

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
   * Text, URLs and titles are written as they are, and a title is always
   * wrapped in double quotes. A soft break is a newline, a hard break is two
   * spaces and a newline, and an autolink is written as `<url>` whether or not
   * it is an email address.
   *
   * @param inline
   *   The inline element to render
   * @return
   *   GFM markdown string for this inline
   */
  def renderInline(inline: Inline): String = inline match {
    case Text(value) =>
      value
    case Inline.Text(value) =>
      value

    case Code(value) =>
      s"`$value`"
    case Inline.Code(value) =>
      s"`$value`"

    case Emphasis(content) =>
      s"*${renderInlines(content)}*"
    case Inline.Emphasis(content) =>
      s"*${renderInlines(content)}*"

    case Strong(content) =>
      s"**${renderInlines(content)}**"
    case Inline.Strong(content) =>
      s"**${renderInlines(content)}**"

    case Strikethrough(content) =>
      s"~~${renderInlines(content)}~~"
    case Inline.Strikethrough(content) =>
      s"~~${renderInlines(content)}~~"

    case Link(text, url, titleOpt) =>
      val titleStr = titleOpt.map(t => s""" "$t"""").getOrElse("")
      s"[${renderInlines(text)}]($url$titleStr)"
    case Inline.Link(text, url, titleOpt) =>
      val titleStr = titleOpt.map(t => s""" "$t"""").getOrElse("")
      s"[${renderInlines(text)}]($url$titleStr)"

    case Image(alt, url, titleOpt) =>
      val titleStr = titleOpt.map(t => s""" "$t"""").getOrElse("")
      s"![${alt}]($url$titleStr)"
    case Inline.Image(alt, url, titleOpt) =>
      val titleStr = titleOpt.map(t => s""" "$t"""").getOrElse("")
      s"![${alt}]($url$titleStr)"

    case HtmlInline(content) =>
      content
    case Inline.HtmlInline(content) =>
      content

    case SoftBreak =>
      "\n"
    case Inline.SoftBreak =>
      "\n"

    case HardBreak =>
      "  \n"
    case Inline.HardBreak =>
      "  \n"

    case Autolink(url, _) =>
      s"<$url>"
    case Inline.Autolink(url, _) =>
      s"<$url>"
  }
}
