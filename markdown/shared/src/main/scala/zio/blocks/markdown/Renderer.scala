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
 * A GFM-compliant Markdown renderer with round-trip support.
 */
object Renderer {

  /**
   * Renders a Document to a Markdown string using default configuration.
   */
  def render(doc: Document): String = render(doc, RenderConfig.default)

  /**
   * Renders a Document to a Markdown string using the provided configuration.
   */
  def render(doc: Document, config: RenderConfig): String = {
    val sb = new StringBuilder
    renderDocument(doc, config, sb)
    sb.toString
  }

  private def renderDocument(doc: Document, config: RenderConfig, sb: StringBuilder): Unit = {
    val blocks = doc.blocks
    var i      = 0
    val len    = blocks.length

    while (i < len) {
      if (i > 0) sb.append("\n\n")
      renderBlock(blocks(i), config, sb, prefix = "")
      i += 1
    }

    if (sb.nonEmpty && !sb.endsWith("\n")) sb.append('\n')
  }

  private def renderBlock(block: Block, config: RenderConfig, sb: StringBuilder, prefix: String): Unit =
    block match {
      case Block.Paragraph(children) =>
        sb.append(prefix)
        renderInlines(children, config, sb)

      case Block.Heading(level, children) =>
        sb.append(prefix)
        sb.append("#" * level.level)
        sb.append(' ')
        renderInlines(children, config, sb)

      case Block.CodeBlock(code, info) =>
        sb.append(prefix)
        val fence = config.codeBlockChar.toString * 3
        sb.append(fence)
        info.foreach(sb.append)
        sb.append('\n')
        code.split('\n').foreach { line =>
          sb.append(prefix)
          sb.append(line)
          sb.append('\n')
        }
        sb.append(prefix)
        sb.append(fence)

      case Block.ThematicBreak =>
        sb.append(prefix)
        sb.append(config.thematicBreakChar.toString * 3)

      case Block.BlockQuote(children) =>
        val newPrefix = prefix + "> "
        var j         = 0
        while (j < children.length) {
          if (j > 0) {
            sb.append('\n')
            sb.append(prefix)
            sb.append(">\n")
          }
          renderBlock(children(j), config, sb, newPrefix)
          j += 1
        }

      case Block.BulletList(items, tight) =>
        var j = 0
        while (j < items.length) {
          if (j > 0) {
            if (tight) sb.append('\n')
            else sb.append("\n\n")
          }
          renderListItem(items(j), config, sb, prefix, config.bulletChar.toString + " ", tight)
          j += 1
        }

      case Block.OrderedList(items, start, tight) =>
        var j      = 0
        var number = start
        while (j < items.length) {
          if (j > 0) {
            if (tight) sb.append('\n')
            else sb.append("\n\n")
          }
          val marker = s"$number. "
          renderListItem(items(j), config, sb, prefix, marker, tight)
          j += 1
          number += 1
        }

      case item: Block.ListItem =>
        renderListItem(item, config, sb, prefix, config.bulletChar.toString + " ", tight = true)

      case Block.HtmlBlock(value) =>
        sb.append(prefix)
        value.split('\n').zipWithIndex.foreach { case (line, idx) =>
          if (idx > 0) {
            sb.append('\n')
            sb.append(prefix)
          }
          sb.append(line)
        }

      case Block.Table(header, alignments, rows) =>
        renderTable(header, alignments, rows, config, sb, prefix)

      case Block.TableCell(children) =>
        renderInlines(children, config, sb)
    }

  private def renderListItem(
    item: Block.ListItem,
    config: RenderConfig,
    sb: StringBuilder,
    prefix: String,
    marker: String,
    tight: Boolean
  ): Unit = {
    sb.append(prefix)
    sb.append(marker)

    // Render task list marker if present
    item.checked.foreach { checked =>
      if (checked) sb.append("[x] ")
      else sb.append("[ ] ")
    }

    val contentPrefix = prefix + " " * marker.length
    val children      = item.children

    if (children.isEmpty) return

    // First block inline with marker
    children.head match {
      case Block.Paragraph(inlines) if tight =>
        renderInlines(inlines, config, sb)
      case _ =>
        renderBlock(children.head, config, sb, prefix = "")
    }

    // Remaining blocks with indentation
    var j = 1
    while (j < children.length) {
      if (tight) sb.append('\n')
      else sb.append("\n\n")
      renderBlock(children(j), config, sb, contentPrefix)
      j += 1
    }
  }

  private def renderTable(
    header: Chunk[Block.TableCell],
    alignments: Chunk[Alignment],
    rows: Chunk[Chunk[Block.TableCell]],
    config: RenderConfig,
    sb: StringBuilder,
    prefix: String
  ): Unit = {
    // Render header
    sb.append(prefix)
    sb.append("| ")
    header.zipWithIndex.foreach { case (cell, i) =>
      if (i > 0) sb.append(" | ")
      renderInlines(cell.children, config, sb)
    }
    sb.append(" |")

    // Render delimiter row
    sb.append('\n')
    sb.append(prefix)
    sb.append("|")
    alignments.foreach { align =>
      sb.append(' ')
      align match {
        case Alignment.Left   => sb.append(":---")
        case Alignment.Right  => sb.append("---:")
        case Alignment.Center => sb.append(":---:")
        case Alignment.None   => sb.append("---")
      }
      sb.append(" |")
    }

    // Render body rows
    rows.foreach { row =>
      sb.append('\n')
      sb.append(prefix)
      sb.append("| ")
      row.zipWithIndex.foreach { case (cell, i) =>
        if (i > 0) sb.append(" | ")
        renderInlines(cell.children, config, sb)
      }
      sb.append(" |")
    }
  }

  private def renderInlines(inlines: Chunk[Inline], config: RenderConfig, sb: StringBuilder): Unit = {
    var i = 0
    while (i < inlines.length) {
      renderInline(inlines(i), config, sb)
      i += 1
    }
  }

  private def renderInline(inline: Inline, config: RenderConfig, sb: StringBuilder): Unit =
    inline match {
      case Inline.Text(value) =>
        sb.append(escapeText(value))

      case Inline.Code(value) =>
        val ticks  = if (value.contains("`")) "``" else "`"
        val padded = if (value.startsWith("`") || value.endsWith("`")) s" $value " else value
        sb.append(ticks)
        sb.append(padded)
        sb.append(ticks)

      case Inline.Emphasis(children) =>
        sb.append(config.emphasisChar)
        renderInlines(children, config, sb)
        sb.append(config.emphasisChar)

      case Inline.Strong(children) =>
        val marker = config.strongChar.toString * 2
        sb.append(marker)
        renderInlines(children, config, sb)
        sb.append(marker)

      case Inline.Strikethrough(children) =>
        sb.append("~~")
        renderInlines(children, config, sb)
        sb.append("~~")

      case Inline.Link(children, url, title) =>
        sb.append('[')
        renderInlines(children, config, sb)
        sb.append("](")
        sb.append(url)
        title.foreach { t =>
          sb.append(" \"")
          sb.append(t.replace("\"", "\\\""))
          sb.append('"')
        }
        sb.append(')')

      case Inline.Image(alt, url, title) =>
        sb.append("![")
        sb.append(alt)
        sb.append("](")
        sb.append(url)
        title.foreach { t =>
          sb.append(" \"")
          sb.append(t.replace("\"", "\\\""))
          sb.append('"')
        }
        sb.append(')')

      case Inline.HtmlInline(value) =>
        sb.append(value)

      case Inline.SoftBreak =>
        sb.append(config.softBreak)

      case Inline.HardBreak =>
        sb.append("  \n")

      case Inline.Autolink(url, isEmail) =>
        sb.append('<')
        if (isEmail && !url.startsWith("mailto:")) {
          sb.append(url)
        } else {
          sb.append(url)
        }
        sb.append('>')
    }

  private def escapeText(text: String): String = {
    val sb  = new StringBuilder
    var i   = 0
    val len = text.length

    while (i < len) {
      val c = text.charAt(i)
      if (needsEscape(c, text, i)) {
        sb.append('\\')
      }
      sb.append(c)
      i += 1
    }

    sb.toString
  }

  private def needsEscape(c: Char, text: String, pos: Int): Boolean =
    c match {
      case '\\' | '`' | '*' | '_' | '{' | '}' | '[' | ']' | '(' | ')' | '#' | '+' | '-' | '.' | '!' | '|' =>
        true
      case '<' | '>' =>
        // Only escape if it looks like an autolink or HTML
        pos + 1 < text.length && (text.charAt(pos + 1).isLetter || text.charAt(pos + 1) == '/')
      case _ =>
        false
    }
}
