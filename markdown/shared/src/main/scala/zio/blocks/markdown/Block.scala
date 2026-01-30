package zio.blocks.markdown

import zio.blocks.chunk.Chunk

sealed trait Block extends Product with Serializable

final case class Paragraph(content: Chunk[Inline])                                            extends Block
final case class Heading(level: HeadingLevel, content: Chunk[Inline])                         extends Block
final case class CodeBlock(info: Option[String], code: String)                                extends Block
case object ThematicBreak                                                                     extends Block
final case class BlockQuote(content: Chunk[Block])                                            extends Block
final case class BulletList(items: Chunk[ListItem], tight: Boolean)                           extends Block
final case class OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean)              extends Block
final case class ListItem(content: Chunk[Block], checked: Option[Boolean])                    extends Block
final case class HtmlBlock(content: String)                                                   extends Block
final case class Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow]) extends Block
