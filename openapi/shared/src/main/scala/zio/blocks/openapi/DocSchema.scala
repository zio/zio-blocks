package zio.blocks.openapi

import zio.blocks.chunk.Chunk
import zio.blocks.docs.{Doc, Paragraph, Parser, Renderer, Text}
import zio.blocks.schema.Schema

/**
 * Schema[Doc] for use in OpenAPI types. Serializes Doc as a plain markdown
 * string: renders to markdown on encode, parses from markdown on decode.
 *
 * Defined here (rather than in the markdown module) because the markdown module
 * does not depend on the schema module. Can be moved later if dependency
 * structure changes.
 */
object DocSchema {

  implicit val docSchema: Schema[Doc] =
    Schema[String].transform[Doc](
      string =>
        Parser.parse(string) match {
          case Right(doc) => doc
          case Left(_)    => Doc(Chunk(Paragraph(Chunk(Text(string)))))
        },
      doc => Renderer.render(doc)
    )
}
