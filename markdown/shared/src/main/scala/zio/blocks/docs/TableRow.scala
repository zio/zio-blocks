package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * A row in a GFM table.
 *
 * Each cell contains a chunk of inline elements.
 *
 * @param cells
 *   The cells in the row, where each cell is a chunk of inline elements
 */
final case class TableRow(cells: Chunk[Chunk[Inline]]) extends Product with Serializable
