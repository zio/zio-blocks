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
 * Block-level content in a Markdown document.
 *
 * Blocks are the structural elements that make up a document. They include
 * paragraphs, headings, code blocks, lists, tables, etc.
 */
sealed trait Block extends Product with Serializable {

  /**
   * Returns the plain text content of this block, stripping all formatting.
   */
  def plainText: String

  /**
   * Returns true if this block contains no content.
   */
  def isEmpty: Boolean
}

object Block {

  /**
   * A paragraph of inline content.
   */
  final case class Paragraph(children: Chunk[Inline]) extends Block {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  object Paragraph {
    def apply(children: Inline*): Paragraph = new Paragraph(Chunk.from(children))
  }

  /**
   * A heading with level (H1-H6) and inline content.
   */
  final case class Heading(level: HeadingLevel, children: Chunk[Inline]) extends Block {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  object Heading {
    def apply(level: HeadingLevel, children: Inline*): Heading = new Heading(level, Chunk.from(children))

    def h1(children: Inline*): Heading = new Heading(HeadingLevel.H1, Chunk.from(children))
    def h2(children: Inline*): Heading = new Heading(HeadingLevel.H2, Chunk.from(children))
    def h3(children: Inline*): Heading = new Heading(HeadingLevel.H3, Chunk.from(children))
    def h4(children: Inline*): Heading = new Heading(HeadingLevel.H4, Chunk.from(children))
    def h5(children: Inline*): Heading = new Heading(HeadingLevel.H5, Chunk.from(children))
    def h6(children: Inline*): Heading = new Heading(HeadingLevel.H6, Chunk.from(children))
  }

  /**
   * A fenced or indented code block with optional language info.
   */
  final case class CodeBlock(code: String, info: Option[String]) extends Block {
    override def plainText: String = code
    override def isEmpty: Boolean  = code.isEmpty
  }

  object CodeBlock {
    def apply(code: String): CodeBlock                   = new CodeBlock(code, None)
    def apply(code: String, language: String): CodeBlock = new CodeBlock(code, Some(language))
  }

  /**
   * A thematic break (horizontal rule).
   */
  case object ThematicBreak extends Block {
    override def plainText: String = ""
    override def isEmpty: Boolean  = false
  }

  /**
   * A block quote containing other blocks.
   */
  final case class BlockQuote(children: Chunk[Block]) extends Block {
    override def plainText: String = children.map(_.plainText).mkString("\n")
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  object BlockQuote {
    def apply(children: Block*): BlockQuote = new BlockQuote(Chunk.from(children))
  }

  /**
   * An unordered (bullet) list.
   */
  final case class BulletList(items: Chunk[ListItem], tight: Boolean) extends Block {
    override def plainText: String = items.map(_.plainText).mkString("\n")
    override def isEmpty: Boolean  = items.isEmpty || items.forall(_.isEmpty)
  }

  object BulletList {
    def apply(items: ListItem*): BulletList = new BulletList(Chunk.from(items), tight = true)
    def tight(items: ListItem*): BulletList = new BulletList(Chunk.from(items), tight = true)
    def loose(items: ListItem*): BulletList = new BulletList(Chunk.from(items), tight = false)
  }

  /**
   * An ordered (numbered) list.
   */
  final case class OrderedList(items: Chunk[ListItem], start: Int, tight: Boolean) extends Block {
    override def plainText: String = items.map(_.plainText).mkString("\n")
    override def isEmpty: Boolean  = items.isEmpty || items.forall(_.isEmpty)
  }

  object OrderedList {
    def apply(items: ListItem*): OrderedList                = new OrderedList(Chunk.from(items), 1, tight = true)
    def starting(start: Int, items: ListItem*): OrderedList = new OrderedList(Chunk.from(items), start, tight = true)
    def tight(items: ListItem*): OrderedList                = new OrderedList(Chunk.from(items), 1, tight = true)
    def loose(items: ListItem*): OrderedList                = new OrderedList(Chunk.from(items), 1, tight = false)
    def tight(start: Int, items: ListItem*): OrderedList    = new OrderedList(Chunk.from(items), start, tight = true)
    def loose(start: Int, items: ListItem*): OrderedList    = new OrderedList(Chunk.from(items), start, tight = false)
  }

  /**
   * A list item containing blocks.
   */
  final case class ListItem(children: Chunk[Block], checked: Option[Boolean]) extends Block {
    override def plainText: String = children.map(_.plainText).mkString("\n")
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)

    def isTaskItem: Boolean = checked.isDefined
  }

  object ListItem {
    def apply(children: Block*): ListItem                  = new ListItem(Chunk.from(children), None)
    def task(checked: Boolean, children: Block*): ListItem = new ListItem(Chunk.from(children), Some(checked))
    def checked(children: Block*): ListItem                = new ListItem(Chunk.from(children), Some(true))
    def unchecked(children: Block*): ListItem              = new ListItem(Chunk.from(children), Some(false))
  }

  /**
   * Raw HTML block content.
   */
  final case class HtmlBlock(value: String) extends Block {
    override def plainText: String = ""
    override def isEmpty: Boolean  = value.isEmpty
  }

  /**
   * A GFM table.
   */
  final case class Table(
    header: Chunk[TableCell],
    alignments: Chunk[Alignment],
    rows: Chunk[Chunk[TableCell]]
  ) extends Block {
    override def plainText: String = {
      val headerText = header.map(_.plainText).mkString(" | ")
      val rowsText   = rows.map(row => row.map(_.plainText).mkString(" | ")).mkString("\n")
      if (rowsText.isEmpty) headerText else s"$headerText\n$rowsText"
    }
    override def isEmpty: Boolean = header.isEmpty && rows.isEmpty
  }

  object Table {
    def apply(
      header: Chunk[TableCell],
      alignments: Chunk[Alignment],
      rows: Chunk[TableCell]*
    ): Table = new Table(header, alignments, Chunk.from(rows))
  }

  /**
   * A table cell containing inline content.
   */
  final case class TableCell(children: Chunk[Inline]) extends Block {
    override def plainText: String = children.map(_.plainText).mkString
    override def isEmpty: Boolean  = children.isEmpty || children.forall(_.isEmpty)
  }

  object TableCell {
    def apply(children: Inline*): TableCell = new TableCell(Chunk.from(children))
    def text(value: String): TableCell      = new TableCell(Chunk(Inline.Text(value)))
  }

  // Convenience constructors
  def paragraph(children: Inline*): Block                    = Paragraph(children: _*)
  def heading(level: HeadingLevel, children: Inline*): Block = Heading(level, children: _*)
  def h1(children: Inline*): Block                           = Heading.h1(children: _*)
  def h2(children: Inline*): Block                           = Heading.h2(children: _*)
  def h3(children: Inline*): Block                           = Heading.h3(children: _*)
  def h4(children: Inline*): Block                           = Heading.h4(children: _*)
  def h5(children: Inline*): Block                           = Heading.h5(children: _*)
  def h6(children: Inline*): Block                           = Heading.h6(children: _*)
  def codeBlock(code: String): Block                         = CodeBlock(code)
  def codeBlock(code: String, language: String): Block       = CodeBlock(code, language)
  def thematicBreak: Block                                   = ThematicBreak
  def blockQuote(children: Block*): Block                    = BlockQuote(children: _*)
  def bulletList(items: ListItem*): Block                    = BulletList(items: _*)
  def orderedList(items: ListItem*): Block                   = OrderedList(items: _*)
  def listItem(children: Block*): ListItem                   = ListItem(children: _*)
  def html(value: String): Block                             = HtmlBlock(value)
}
