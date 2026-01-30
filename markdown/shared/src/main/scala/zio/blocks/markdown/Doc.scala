package zio.blocks.markdown

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
 *   {{{ import zio.blocks.markdown._ import zio.blocks.chunk.Chunk
 *
 * // Parse from string val doc = Parser.parse("# Hello World")
 *
 * // Construct programmatically val doc2 = Doc(Chunk( Heading(HeadingLevel.H1,
 * Chunk(Text("Hello World"))) )) }}}
 */
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty) extends Product with Serializable
