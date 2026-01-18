package zio.schema.codec

import zio.blocks.schema.bson.BsonFormat
import zio.blocks.schema.Schema
import zio.blocks.schema.codec.BinaryCodec

object BsonSchemaCodec {
  def codec[A](schema: Schema[A]): BinaryCodec[A] =
    schema.derive(BsonFormat.deriver)
}
