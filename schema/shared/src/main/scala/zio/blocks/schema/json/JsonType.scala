package zio.blocks.schema.json

/**
 * Represents JSON type values for the `type` keyword in JSON Schema 2020-12.
 *
 * JSON Schema defines seven primitive types: null, boolean, object, array,
 * number, string, and integer. The `type` keyword can accept a single type or
 * an array of types (represented by `Union`).
 *
 * @see
 *   https://json-schema.org/draft/2020-12/json-schema-validation#section-6.1.1
 */
sealed trait JsonType {
  def name: String

  /** Combines this type with another to form a union. */
  def |(that: JsonType): JsonType.Union = (this, that) match {
    case (u1: JsonType.Union, u2: JsonType.Union) => JsonType.Union(u1.types ++ u2.types)
    case (u1: JsonType.Union, t2)                 => JsonType.Union(u1.types + t2)
    case (t1, u2: JsonType.Union)                 => JsonType.Union(u2.types + t1)
    case (t1, t2)                                 => JsonType.Union(Set(t1, t2))
  }
}

object JsonType {

  /** JSON null type */
  case object Null extends JsonType {
    val name: String = "null"
  }

  /** JSON boolean type */
  case object Boolean extends JsonType {
    val name: String = "boolean"
  }

  /** JSON string type */
  case object String extends JsonType {
    val name: String = "string"
  }

  /** JSON number type (includes integers) */
  case object Number extends JsonType {
    val name: String = "number"
  }

  /** JSON integer type (subset of number with no fractional part) */
  case object Integer extends JsonType {
    val name: String = "integer"
  }

  /** JSON array type */
  case object Array extends JsonType {
    val name: String = "array"
  }

  /** JSON object type */
  case object Object extends JsonType {
    val name: String = "object"
  }

  /**
   * Union of multiple JSON types.
   *
   * In JSON Schema, `type` can be an array like `["string", "null"]` to
   * indicate the value can be either a string or null.
   */
  final case class Union(types: Set[JsonType]) extends JsonType {
    require(
      types.forall(!_.isInstanceOf[Union]),
      "Union cannot contain nested unions"
    )

    val name: String = types.map(_.name).mkString("[", ", ", "]")

    /** Adds null to this union (convenience for nullable types). */
    def nullable: Union = Union(types + Null)

    /** Returns true if this union includes null. */
    def isNullable: Boolean = types.contains(Null)
  }

  object Union {
    def apply(first: JsonType, second: JsonType, rest: JsonType*): Union =
      new Union((rest.toSet + first + second).filterNot(_.isInstanceOf[Union]))
  }

  /** Parses a type name to a JsonType. */
  def fromString(name: String): Option[JsonType] = name match {
    case "null"    => Some(Null)
    case "boolean" => Some(Boolean)
    case "string"  => Some(String)
    case "number"  => Some(Number)
    case "integer" => Some(Integer)
    case "array"   => Some(Array)
    case "object"  => Some(Object)
    case _         => None
  }

  /** All primitive types (excluding Union). */
  val primitives: Set[JsonType] = Set(Null, Boolean, String, Number, Integer, Array, Object)

  /** Determines the JsonType of a Json value at runtime. */
  def of(json: Json): JsonType = json match {
    case _: Json.Object  => Object
    case _: Json.Array   => Array
    case _: Json.String  => String
    case _: Json.Number  => Number
    case _: Json.Boolean => Boolean
    case Json.Null       => Null
  }
}
