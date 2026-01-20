package zio.blocks.schema.json

import zio.blocks.schema.Schema

/**
 * Type class for decoding [[Json]] values into Scala types.
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
 * val json: Json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
 * val person: Either[JsonError, Person] = json.as[Person]
 * }}}
 *
 * ==Custom Decoders==
 * {{{
 * implicit val customDecoder: JsonDecoder[Person] = new JsonDecoder[Person] {
 *   def decode(json: Json): Either[JsonError, Person] = json match {
 *     case Json.Object(fields) =>
 *       for {
 *         name <- fields.collectFirst { case ("name", Json.String(n)) => n }
 *                   .toRight(JsonError("Missing 'name' field"))
 *         age <- fields.collectFirst { case ("age", Json.Number(a)) => a.toInt }
 *                  .toRight(JsonError("Missing 'age' field"))
 *       } yield Person(name, age)
 *     case _ => Left(JsonError("Expected JSON object"))
 *   }
 * }
 * }}}
 */
sealed trait JsonDecoder[A] {

  /**
   * Decodes a [[Json]] value into type `A`.
   *
   * @param json
   *   The JSON value to decode
   * @return
   *   Either a [[JsonError]] on failure, or the decoded value
   */
  def decode(json: Json): Either[JsonError, A]
}

object JsonDecoder extends JsonDecoderLowPriority {

  /**
   * Summons an implicit [[JsonDecoder]] for type `A`.
   */
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   *
   * This allows users to provide custom codecs that take precedence over
   * schema-derived decoders.
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      def decode(json: Json): Either[JsonError, A] = json.decodeWith(codec)
    }
}

/**
 * Lower priority implicits for [[JsonDecoder]].
 *
 * These are only used if no explicit [[JsonBinaryCodec]] is available.
 */
trait JsonDecoderLowPriority {

  /**
   * Lower priority: derive a codec from an implicit [[Schema]].
   *
   * This automatically derives a decoder from a Schema definition, enabling
   * zero-boilerplate JSON decoding for case classes.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      private lazy val codec: JsonBinaryCodec[A]   = schema.derive(JsonBinaryCodecDeriver)
      def decode(json: Json): Either[JsonError, A] = json.decodeWith(codec)
    }
}
