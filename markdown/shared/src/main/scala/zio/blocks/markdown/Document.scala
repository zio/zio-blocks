package zio.blocks.markdown

import zio.blocks.chunk.Chunk

final case class Document(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
    extends Product
    with Serializable
