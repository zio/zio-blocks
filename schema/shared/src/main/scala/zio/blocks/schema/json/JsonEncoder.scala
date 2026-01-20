package zio.blocks.schema.json

import zio.blocks.schema.Schema

/**
 * Type class for encoding Scala types into [[Json]] values.
 *
 * Implicit resolution prefers explicitly provided [[JsonBinaryCodec]] instances
 * over schema-derived instances, allowing users to override derived behavior.
 *
 * ==Usage==
 * {{{
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * val person = Person("Alice", 30)
 * val json: Json = Json.from(person)
 * }}}
 *
 * ==Custom Encoders==
 * {{{
 * implicit val customEncoder: JsonEncoder[Person] = new JsonEncoder[Person] {
 *   def encode(p: Person): Json = Json.Object(
 *     "name" -> Json.String(p.name),
 *     "age" -> Json.number(p.age)
 *   )
 * }
 * }}}
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

  /**
   * Summons an implicit [[JsonEncoder]] for type `A`.
   */
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   *
   * This allows users to provide custom codecs that take precedence over
   * schema-derived encoders.
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = Json.encodeWith(value, codec)
    }
}

/**
 * Lower priority implicits for [[JsonEncoder]].
 *
 * These are only used if no explicit [[JsonBinaryCodec]] is available.
 */
trait JsonEncoderLowPriority {

  /**
   * Lower priority: derive a codec from an implicit [[Schema]].
   *
   * This automatically derives an encoder from a Schema definition, enabling
   * zero-boilerplate JSON encoding for case classes.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      private lazy val codec: JsonBinaryCodec[A] = schema.derive(JsonBinaryCodecDeriver)
      def encode(value: A): Json                 = Json.encodeWith(value, codec)
    }
}
