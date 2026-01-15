package zio.blocks.schema.json

import zio.blocks.schema.Schema

/**
 * Type class for encoding Scala types into [[Json]] values.
 *
 * Implicit resolution prefers explicitly provided [[JsonBinaryCodec]] instances
 * over schema-derived instances, allowing users to override derived behavior.
 */
sealed trait JsonEncoder[A] {

  /**
   * Encodes a value of type `A` into [[Json]].
   *
   * @param value
   *   The value to encode
   * @return
   *   The encoded JSON value
   */
  def encode(value: A): Json
}

object JsonEncoder extends JsonEncoderLowPriority {

  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  /**
   * Creates a JsonEncoder from a function.
   */
  def instance[A](f: A => Json): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = f(value)
    }

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = JsonBridge.encodeJsonWith(value, codec)
    }
}

/**
 * Lower priority implicits for [[JsonEncoder]].
 */
trait JsonEncoderLowPriority {

  /**
   * Lower priority: derive a codec from an implicit [[Schema]].
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      private lazy val codec: JsonBinaryCodec[A] = schema.derive(JsonBinaryCodecDeriver)

      def encode(value: A): Json = JsonBridge.encodeJsonWith(value, codec)
    }
}
