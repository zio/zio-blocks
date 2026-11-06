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
 * A GitHub Flavored Markdown document: a sequence of [[Block]] elements.
 *
 * Build one by parsing text with [[Parser.parse]], with the `md` string
 * interpolator, or by constructing the blocks directly. Render it with
 * `Doc#toString` (Markdown), `Doc#toHtml`, `Doc#toHtmlFragment` or
 * `Doc#toTerminal`, or with the matching renderer object.
 *
 * `==` and `hashCode` compare the normalized form of both documents and their
 * metadata, so two documents that differ only in how their text is split into
 * `Text` nodes are equal.
 *
 * @param blocks
 *   The blocks of the document, in order
 * @param metadata
 *   Key-value pairs carried along with the document. [[Parser]] and the `md`
 *   interpolator always leave it empty, and the renderers ignore it; it only
 *   takes part in `==`, `hashCode` and `Doc#++`
 *
 * @example
 *   {{{
 * import zio.blocks.chunk.Chunk
 *
 * val parsed = Parser.parse("# Release 1.4\n\n- Faster startup")
 * val built = Doc(
 *   Chunk(
 *     Heading(HeadingLevel.H1, Chunk(Text("Release 1.4"))),
 *     BulletList(Chunk(ListItem(Chunk(Paragraph(Chunk(Text("Faster startup")))), None)), tight = true)
 *   )
 * )
 *
 * val same = parsed == Right(built)
 * // true
 * val html = built.toHtmlFragment
 * // <h1>Release 1.4</h1><ul><li><p>Faster startup</p></li></ul>
 *   }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
    extends Product
    with Serializable {

  /**
   * Concatenate two documents.
   *
   * Appends the blocks of `other` to the blocks of this document and merges the
   * metadata. When both documents have the same metadata key, the value from
   * `other` wins.
   *
   * @param other
   *   The document to concatenate
   * @return
   *   A new document with combined blocks and merged metadata
   */
  def ++(other: Doc): Doc =
    Doc(blocks ++ other.blocks, metadata ++ other.metadata)

  /**
   * Render this document as GitHub Flavored Markdown, the same as
   * [[Renderer.render]].
   *
   * @return
   *   GFM markdown string representation
   */
  override def toString: String =
    Renderer.render(this)

  /**
   * Normalize this document to canonical form, the same as
   * [[Doc.normalizeBlocks]] on its blocks.
   *
   * Merges adjacent text nodes, removes empty text nodes and the empty
   * paragraphs, block quotes and lists, and does the same inside nested blocks,
   * list items, table cells, emphasis, strong, strikethrough and link text. The
   * metadata is kept as it is. A lone `Inline.Text` that has nothing to merge
   * with stays an `Inline.Text`, and so do the other `Inline.` cases without
   * children.
   *
   * @return
   *   A normalized copy of this document
   */
  def normalize: Doc =
    Doc(Doc.normalizeBlocks(blocks), metadata)

  /**
   * Compare two documents by their normalized form.
   *
   * Two documents are equal when their normalized blocks are equal and their
   * metadata is equal. The package-level inline classes and the `Inline.`
   * classes are different classes, so a document built from `Inline.Text("a")`
   * is not equal to one built from `Text("a")`.
   */
  override def equals(obj: Any): Boolean = obj match {
    case that: Doc =>
      this.normalize.blocks == that.normalize.blocks &&
      this.normalize.metadata == that.normalize.metadata
    case _ => false
  }

  /**
   * The hash code of the normalized blocks and the metadata, consistent with
   * `Doc#equals`.
   */
  override def hashCode(): Int = {
    val normalized = this.normalize
    (normalized.blocks, normalized.metadata).hashCode()
  }

  /**
   * Render this document as a complete HTML page, the same as
   * [[HtmlRenderer.render]].
   */
  def toHtml: String = HtmlRenderer.render(this)

  /**
   * Render this document as HTML without the page wrapper, the same as
   * [[HtmlRenderer.renderFragment]].
   */
  def toHtmlFragment: String = HtmlRenderer.renderFragment(this)

  /**
   * Render this document with ANSI escape codes for a terminal, the same as
   * [[TerminalRenderer.render]].
   */
  def toTerminal: String = TerminalRenderer.render(this)
}

/**
 * The empty document and the normalization functions behind `Doc#normalize`.
 */
object Doc {

  /** A document with no blocks and no metadata. */
  val empty: Doc = Doc(Chunk.empty)

  /**
   * Normalize a chunk of blocks to canonical form.
   *
   * Normalizes each block with [[Doc.normalizeBlock]], then drops the blocks
   * for which [[Doc.isEmpty]] is true.
   *
   * @param blocks
   *   The blocks to normalize
   * @return
   *   Normalized blocks with adjacent text nodes merged and empty blocks
   *   removed
   */
  def normalizeBlocks(blocks: Chunk[Block]): Chunk[Block] =
    blocks
      .map(normalizeBlock)
      .filter(!isEmpty(_))

  /**
   * Normalize a single block to canonical form.
   *
   * Normalizes the inline content of paragraphs, headings and table cells, and
   * the blocks inside block quotes and list items. Code blocks, thematic breaks
   * and HTML blocks are returned unchanged. The block itself is kept even when
   * it ends up empty; [[Doc.normalizeBlocks]] removes empty blocks.
   *
   * @param block
   *   The block to normalize
   * @return
   *   Normalized block
   */
  def normalizeBlock(block: Block): Block = block match {
    case Paragraph(content)               => Paragraph(normalizeInlines(content))
    case Heading(level, content)          => Heading(level, normalizeInlines(content))
    case BlockQuote(content)              => BlockQuote(normalizeBlocks(content))
    case BulletList(items, tight)         => BulletList(items.map(normalizeListItem), tight)
    case OrderedList(start, items, tight) =>
      OrderedList(start, items.map(normalizeListItem), tight)
    case ListItem(content, checked)      => ListItem(normalizeBlocks(content), checked)
    case Table(header, alignments, rows) =>
      Table(normalizeTableRow(header), alignments, rows.map(normalizeTableRow))
    case other => other // CodeBlock, ThematicBreak, HtmlBlock unchanged
  }

  /**
   * Normalize a list item.
   *
   * @param item
   *   The list item to normalize
   * @return
   *   Normalized list item
   */
  private def normalizeListItem(item: ListItem): ListItem =
    ListItem(normalizeBlocks(item.content), item.checked)

  /**
   * Normalize a table row.
   *
   * @param row
   *   The table row to normalize
   * @return
   *   Normalized table row
   */
  private def normalizeTableRow(row: TableRow): TableRow =
    TableRow(row.cells.map(normalizeInlines))

  /**
   * Normalize a chunk of inlines to canonical form.
   *
   * Adjacent text nodes, whether `Text` or `Inline.Text`, are merged into one
   * package-level `Text`, and empty text nodes are removed. Each element is
   * first normalized with [[Doc.normalizeInline]].
   *
   * @param inlines
   *   The inlines to normalize
   * @return
   *   Normalized inlines with adjacent Text nodes merged
   */
  def normalizeInlines(inlines: Chunk[Inline]): Chunk[Inline] =
    inlines
      .foldLeft(Chunk.empty[Inline]) { (acc, inline) =>
        (acc.lastOption, normalizeInline(inline)) match {
          case (Some(Text(a)), Text(b))               => acc.dropRight(1) :+ Text(a + b)
          case (Some(Inline.Text(a)), Text(b))        => acc.dropRight(1) :+ Text(a + b)
          case (Some(Text(a)), Inline.Text(b))        => acc.dropRight(1) :+ Text(a + b)
          case (Some(Inline.Text(a)), Inline.Text(b)) => acc.dropRight(1) :+ Text(a + b)
          case (_, normalized)                        => acc :+ normalized
        }
      }
      .filter {
        case Text("")        => false
        case Inline.Text("") => false
        case _               => true
      }

  /**
   * Normalize a single inline to canonical form.
   *
   * Normalizes the children of emphasis, strong, strikethrough and link text,
   * and turns the `Inline.` versions of those four cases into the package-level
   * ones. Every other inline, including `Inline.Text`, is returned unchanged;
   * text nodes are merged by [[Doc.normalizeInlines]].
   *
   * @param inline
   *   The inline to normalize
   * @return
   *   Normalized inline
   */
  def normalizeInline(inline: Inline): Inline = inline match {
    case Emphasis(content)             => Emphasis(normalizeInlines(content))
    case Inline.Emphasis(content)      => Emphasis(normalizeInlines(content))
    case Strong(content)               => Strong(normalizeInlines(content))
    case Inline.Strong(content)        => Strong(normalizeInlines(content))
    case Strikethrough(content)        => Strikethrough(normalizeInlines(content))
    case Inline.Strikethrough(content) => Strikethrough(normalizeInlines(content))
    case Link(text, url, title)        => Link(normalizeInlines(text), url, title)
    case Inline.Link(text, url, title) => Link(normalizeInlines(text), url, title)
    case other                         => other
  }

  /**
   * Check if a block is empty.
   *
   * A paragraph is empty when it has no content or only empty text nodes. A
   * block quote with no blocks is empty, and so is a bullet or ordered list
   * with no items. No other block is empty: headings, code blocks, tables and
   * list items are never removed.
   *
   * @param block
   *   The block to check
   * @return
   *   true if the block is empty
   */
  def isEmpty(block: Block): Boolean = block match {
    case Paragraph(content) =>
      content.isEmpty || content.forall {
        case Text("")        => true
        case Inline.Text("") => true
        case _               => false
      }
    case BlockQuote(content)      => content.isEmpty
    case BulletList(items, _)     => items.isEmpty
    case OrderedList(_, items, _) => items.isEmpty
    case _                        => false
  }
}
