package zio.blocks.schema.json

import zio.blocks.schema.Schema

sealed trait JsonEncoder[A] {
  def encode(value: A): Json
}

object JsonEncoder extends JsonEncoderLowPriority {
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = Json.encodeWith(value, codec)
    }
}

trait JsonEncoderLowPriority {
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      private lazy val codec: JsonBinaryCodec[A] = schema.derive(JsonBinaryCodecDeriver)
      def encode(value: A): Json = Json.encodeWith(value, codec)
    }
}
