package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * A GitHub Flavored Markdown document.
 *
 * Represents a complete GFM document as a sequence of block elements. Documents
 * can be created by parsing markdown strings or constructed programmatically.
 *
 * @param blocks
 *   The sequence of block elements in the document
 * @param metadata
 *   Optional metadata (reserved for future use)
 *
 * @example
 *   {{{ import zio.blocks.docs._ import zio.blocks.chunk.Chunk
 *
 * // Parse from string val doc = Parser.parse("# Hello World")
 *
 * // Construct programmatically val doc2 = Doc(Chunk( Heading(HeadingLevel.H1,
 * Chunk(Text("Hello World"))) )) }}}
 */
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
    extends Product
    with Serializable {

  /**
   * Concatenate two documents.
   *
   * Combines blocks from both documents and merges metadata. On key conflict,
   * right metadata wins.
   *
   * @param other
   *   The document to concatenate
   * @return
   *   A new document with combined blocks and merged metadata
   */
  def ++(other: Doc): Doc =
    Doc(blocks ++ other.blocks, metadata ++ other.metadata)

  /**
   * Render this document as GitHub Flavored Markdown.
   *
   * @return
   *   GFM markdown string representation
   */
  override def toString: String =
    Renderer.render(this)

  /**
   * Normalize this document to canonical form.
   *
   * Merges adjacent Text nodes, removes empty blocks, and recursively
   * normalizes nested structures.
   *
   * @return
   *   A normalized copy of this document
   */
  def normalize: Doc =
    Doc(Doc.normalizeBlocks(blocks), metadata)

  /**
   * Override equals to use normalized comparison.
   *
   * Two documents are equal if their normalized forms are equal.
   */
  override def equals(obj: Any): Boolean = obj match {
    case that: Doc =>
      this.normalize.blocks == that.normalize.blocks &&
      this.normalize.metadata == that.normalize.metadata
    case _ => false
  }

  /**
   * Override hashCode to be consistent with equals.
   *
   * Hash code is based on normalized form.
   */
  override def hashCode(): Int = {
    val normalized = this.normalize
    (normalized.blocks, normalized.metadata).hashCode()
  }

  def toHtml: String         = HtmlRenderer.render(this)
  def toHtmlFragment: String = HtmlRenderer.renderFragment(this)
  def toTerminal: String     = TerminalRenderer.render(this)
}

object Doc {

  /**
   * Normalize a chunk of blocks to canonical form.
   *
   * @param blocks
   *   The blocks to normalize
   * @return
   *   Normalized blocks with adjacent Text nodes merged and empty blocks
   *   removed
   */
  def normalizeBlocks(blocks: Chunk[Block]): Chunk[Block] =
    blocks
      .map(normalizeBlock)
      .filter(!isEmpty(_))

  /**
   * Normalize a single block to canonical form.
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
   * Adjacent Text nodes are merged, and empty Text nodes are removed.
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
   * Empty paragraphs, block quotes, and lists are considered empty.
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
