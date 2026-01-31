package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * Renderer for HTML output.
 *
 * Converts markdown AST elements to HTML5-compliant HTML strings. Provides both
 * full document rendering with DOCTYPE and head/body tags, and fragment
 * rendering for embedding in existing HTML documents.
 */
object HtmlRenderer {

  /**
   * Render a Doc to a complete HTML5 document.
   *
   * @param doc
   *   The document to render
   * @return
   *   Complete HTML5 document with DOCTYPE, html, head, and body tags
   */
  def render(doc: Doc): String = {
    val content = renderFragment(doc)
    s"<!DOCTYPE html><html><head></head><body>$content</body></html>"
  }

  /**
   * Render a Doc to an HTML fragment (content only).
   *
   * @param doc
   *   The document to render
   * @return
   *   HTML content without DOCTYPE or wrapper tags
   */
  def renderFragment(doc: Doc): String =
    doc.blocks.map(renderBlock).mkString

  /**
   * Render a Block to its HTML representation.
   *
   * @param block
   *   The block element to render
   * @return
   *   HTML string for this block
   */
  def renderBlock(block: Block): String = block match {
    case Paragraph(content) =>
      s"<p>${renderInlines(content)}</p>"

    case Heading(level, content) =>
      val tag = s"h${level.value}"
      s"<$tag>${renderInlines(content)}</$tag>"

    case CodeBlock(info, code) =>
      info match {
        case Some(lang) => s"""<pre><code class="language-$lang">${escape(code)}</code></pre>"""
        case None       => s"<pre><code>${escape(code)}</code></pre>"
      }

    case ThematicBreak =>
      "<hr>"

    case BlockQuote(content) =>
      s"<blockquote>${content.map(renderBlock).mkString}</blockquote>"

    case BulletList(items, _) =>
      s"<ul>${items.map(renderBlock).mkString}</ul>"

    case OrderedList(start, items, _) =>
      if (start == 1) {
        s"<ol>${items.map(renderBlock).mkString}</ol>"
      } else {
        s"""<ol start="$start">${items.map(renderBlock).mkString}</ol>"""
      }

    case ListItem(content, checked) =>
      checked match {
        case Some(true)  => s"<li><input type=\"checkbox\" checked disabled>${renderBlocks(content)}</li>"
        case Some(false) => s"<li><input type=\"checkbox\" disabled>${renderBlocks(content)}</li>"
        case None        => s"<li>${renderBlocks(content)}</li>"
      }

    case HtmlBlock(html) =>
      html

    case Table(header, alignments, rows) =>
      val headerHtml = s"<thead><tr>${renderTableHeader(header, alignments)}</tr></thead>"
      val bodyHtml   = s"<tbody>${rows.map(row => s"<tr>${renderTableRow(row, alignments)}</tr>").mkString}</tbody>"
      s"<table>$headerHtml$bodyHtml</table>"
  }

  /**
   * Render a chunk of blocks as a single string.
   *
   * @param blocks
   *   The block elements to render
   * @return
   *   Concatenated HTML string
   */
  private def renderBlocks(blocks: Chunk[Block]): String =
    blocks.map(renderBlock).mkString

  /**
   * Render a table header row.
   *
   * @param row
   *   The header row
   * @param alignments
   *   Column alignments
   * @return
   *   HTML string for header cells
   */
  private def renderTableHeader(row: TableRow, alignments: Chunk[Alignment]): String =
    row.cells
      .zip(alignments)
      .map { case (cell, alignment) =>
        alignment match {
          case Alignment.Left   => s"""<th style="text-align:left">${renderInlines(cell)}</th>"""
          case Alignment.Right  => s"""<th style="text-align:right">${renderInlines(cell)}</th>"""
          case Alignment.Center => s"""<th style="text-align:center">${renderInlines(cell)}</th>"""
          case Alignment.None   => s"<th>${renderInlines(cell)}</th>"
        }
      }
      .mkString

  /**
   * Render a table row.
   *
   * @param row
   *   The table row
   * @param alignments
   *   Column alignments
   * @return
   *   HTML string for data cells
   */
  private def renderTableRow(row: TableRow, alignments: Chunk[Alignment]): String =
    row.cells
      .zip(alignments)
      .map { case (cell, alignment) =>
        alignment match {
          case Alignment.Left   => s"""<td style="text-align:left">${renderInlines(cell)}</td>"""
          case Alignment.Right  => s"""<td style="text-align:right">${renderInlines(cell)}</td>"""
          case Alignment.Center => s"""<td style="text-align:center">${renderInlines(cell)}</td>"""
          case Alignment.None   => s"<td>${renderInlines(cell)}</td>"
        }
      }
      .mkString

  /**
   * Render a chunk of inlines as a single string.
   *
   * @param inlines
   *   The inline elements to render
   * @return
   *   Concatenated HTML string
   */
  def renderInlines(inlines: Chunk[Inline]): String =
    inlines.map(renderInline).mkString

  /**
   * Render a single inline element.
   *
   * @param inline
   *   The inline element to render
   * @return
   *   HTML string for this inline
   */
  def renderInline(inline: Inline): String = inline match {
    case Text(value) =>
      escape(value)
    case Inline.Text(value) =>
      escape(value)

    case Code(value) =>
      s"<code>${escape(value)}</code>"
    case Inline.Code(value) =>
      s"<code>${escape(value)}</code>"

    case Emphasis(content) =>
      s"<em>${renderInlines(content)}</em>"
    case Inline.Emphasis(content) =>
      s"<em>${renderInlines(content)}</em>"

    case Strong(content) =>
      s"<strong>${renderInlines(content)}</strong>"
    case Inline.Strong(content) =>
      s"<strong>${renderInlines(content)}</strong>"

    case Strikethrough(content) =>
      s"<del>${renderInlines(content)}</del>"
    case Inline.Strikethrough(content) =>
      s"<del>${renderInlines(content)}</del>"

    case Link(text, url, titleOpt) =>
      titleOpt match {
        case Some(title) => s"""<a href="${escape(url)}" title="${escape(title)}">${renderInlines(text)}</a>"""
        case None        => s"""<a href="${escape(url)}">${renderInlines(text)}</a>"""
      }
    case Inline.Link(text, url, titleOpt) =>
      titleOpt match {
        case Some(title) => s"""<a href="${escape(url)}" title="${escape(title)}">${renderInlines(text)}</a>"""
        case None        => s"""<a href="${escape(url)}">${renderInlines(text)}</a>"""
      }

    case Image(alt, url, titleOpt) =>
      titleOpt match {
        case Some(title) => s"""<img src="${escape(url)}" alt="${escape(alt)}" title="${escape(title)}">"""
        case None        => s"""<img src="${escape(url)}" alt="${escape(alt)}">"""
      }
    case Inline.Image(alt, url, titleOpt) =>
      titleOpt match {
        case Some(title) => s"""<img src="${escape(url)}" alt="${escape(alt)}" title="${escape(title)}">"""
        case None        => s"""<img src="${escape(url)}" alt="${escape(alt)}">"""
      }

    case HtmlInline(html) =>
      html
    case Inline.HtmlInline(html) =>
      html

    case SoftBreak =>
      " "
    case Inline.SoftBreak =>
      " "

    case HardBreak =>
      "<br>"
    case Inline.HardBreak =>
      "<br>"

    case Autolink(url, isEmail) =>
      if (isEmail) {
        s"""<a href="mailto:${escape(url)}">${escape(url)}</a>"""
      } else {
        s"""<a href="${escape(url)}">${escape(url)}</a>"""
      }
    case Inline.Autolink(url, isEmail) =>
      if (isEmail) {
        s"""<a href="mailto:${escape(url)}">${escape(url)}</a>"""
      } else {
        s"""<a href="${escape(url)}">${escape(url)}</a>"""
      }
  }

  /**
   * Escape HTML entities.
   *
   * @param s
   *   The string to escape
   * @return
   *   String with HTML entities escaped
   */
  def escape(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
}
