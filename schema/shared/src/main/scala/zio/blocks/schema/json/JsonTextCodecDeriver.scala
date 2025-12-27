package zio.blocks.schema.json

object JsonTextCodecDeriver {
  def derive[A](schema: zio.blocks.schema.Schema[A]): JsonTextCodec[A] = JsonTextCodec(schema)
}
