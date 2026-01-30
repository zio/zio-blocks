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
 * A Markdown document consisting of a sequence of blocks.
 *
 * This is the root of the Markdown AST. A document is simply a container for
 * block-level elements like paragraphs, headings, lists, etc.
 */
final case class Document(blocks: Chunk[Block]) {

  /**
   * Returns the plain text content of this document, stripping all formatting.
   */
  def plainText: String = blocks.map(_.plainText).mkString("\n\n")

  /**
   * Returns true if this document contains no blocks.
   */
  def isEmpty: Boolean = blocks.isEmpty || blocks.forall(_.isEmpty)

  /**
   * Appends blocks to this document.
   */
  def ++(that: Document): Document = Document(blocks ++ that.blocks)

  /**
   * Appends a block to this document.
   */
  def :+(block: Block): Document = Document(blocks :+ block)

  /**
   * Prepends a block to this document.
   */
  def +:(block: Block): Document = Document(block +: blocks)

  /**
   * Renders this document back to Markdown text.
   */
  def render: String = Renderer.render(this)

  /**
   * Renders this document to Markdown text with custom configuration.
   */
  def render(config: RenderConfig): String = Renderer.render(this, config)
}

object Document {

  /**
   * An empty document.
   */
  val empty: Document = Document(Chunk.empty)

  /**
   * Creates a document from blocks.
   */
  def apply(blocks: Block*): Document = new Document(Chunk.from(blocks))

  /**
   * Creates a document containing a single paragraph with the given text.
   */
  def text(value: String): Document = Document(Block.Paragraph(Chunk(Inline.Text(value))))

  /**
   * Creates a document containing a single heading.
   */
  def heading(level: HeadingLevel, text: String): Document =
    Document(Block.Heading(level, Chunk(Inline.Text(text))))
}
