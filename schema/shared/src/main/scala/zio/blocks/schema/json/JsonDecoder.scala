package zio.blocks.schema.json

import zio.blocks.schema.Schema

trait JsonDecoder[A] {
  def decode(json: Json): Either[JsonError, A]
}

object JsonDecoder extends JsonDecoderLowPriority {

  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      def decode(json: Json): Either[JsonError, A] = json.decodeWith(codec)
    }
}



trait JsonDecoderLowPriority {
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
       def decode(json: Json): Either[JsonError, A] = {
         val dv = json.toDynamicValue
         schema.fromDynamicValue(dv).left.map(e => JsonError.fromSchemaError(e))
       }
    }
}
