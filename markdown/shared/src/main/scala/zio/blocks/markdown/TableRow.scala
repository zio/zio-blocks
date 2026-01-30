package zio.blocks.markdown

import zio.blocks.chunk.Chunk

final case class TableRow(cells: Chunk[Chunk[Inline]]) extends Product with Serializable
