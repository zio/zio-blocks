package zio.blocks.schema.json

import zio.blocks.schema.Schema

trait JsonEncoder[A] {
  def encode(value: A): Json
}

object JsonEncoder extends JsonEncoderLowPriority {

  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = Json.encodeWith(value, codec)
    }
}


trait JsonEncoderLowPriority {
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = {
        val dv = schema.toDynamicValue(value)
        Json.fromDynamicValue(dv)
      }
    }
}
