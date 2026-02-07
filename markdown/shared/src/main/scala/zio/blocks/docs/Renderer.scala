package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * Renderer for GitHub Flavored Markdown.
 *
 * Converts markdown AST elements back to GFM-compliant markdown strings. The
 * rendered output can be re-parsed to produce an equivalent AST.
 */
object Renderer {

  /**
   * Render a Doc to a GitHub Flavored Markdown string.
   *
   * @param doc
   *   The document to render
   * @return
   *   GFM markdown string
   *
   * @example
   *   {{{ val doc = Doc(Chunk( Heading(HeadingLevel.H1, Chunk(Text("Hello")))
   *   )) val markdown = Renderer.render(doc) // markdown == "# Hello\n" }}}
   */
  def render(doc: Doc): String =
    doc.blocks.map(renderBlock).mkString

  /**
   * Render a Block to its GFM representation.
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
   *   Concatenated GFM markdown string
   */
  def renderInlines(inlines: Chunk[Inline]): String =
    inlines.map(renderInline).mkString

  /**
   * Render a single inline element.
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
