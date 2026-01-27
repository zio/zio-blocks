package zio.blocks.schema.json

/**
 * Represents the "type" keyword values in JSON Schema.
 *
 * Note: This is distinct from [[JsonType]] which represents runtime JSON value
 * types. JSON Schema's type keyword includes "integer" as a distinct type,
 * while runtime JSON only has "number" for all numeric values.
 */
sealed trait JsonSchemaType extends Product with Serializable {
  def toJsonString: String = this match {
    case JsonSchemaType.Null    => "null"
    case JsonSchemaType.Boolean => "boolean"
    case JsonSchemaType.String  => "string"
    case JsonSchemaType.Number  => "number"
    case JsonSchemaType.Integer => "integer"
    case JsonSchemaType.Array   => "array"
    case JsonSchemaType.Object  => "object"
  }
}

object JsonSchemaType {
  case object Null    extends JsonSchemaType
  case object Boolean extends JsonSchemaType
  case object String  extends JsonSchemaType
  case object Number  extends JsonSchemaType
  case object Integer extends JsonSchemaType
  case object Array   extends JsonSchemaType
  case object Object  extends JsonSchemaType

  def fromString(s: String): Option[JsonSchemaType] = s match {
    case "null"    => Some(Null)
    case "boolean" => Some(Boolean)
    case "string"  => Some(String)
    case "number"  => Some(Number)
    case "integer" => Some(Integer)
    case "array"   => Some(Array)
    case "object"  => Some(Object)
    case _         => None
  }

  val all: Seq[JsonSchemaType] = Seq(Null, Boolean, String, Number, Integer, Array, Object)
}

// =============================================================================
// Type Keyword: Single Type or Array of Types
// =============================================================================

sealed trait SchemaType extends Product with Serializable {
  def toJson: Json = this match {
    case SchemaType.Single(t) => Json.String(t.toJsonString)
    case SchemaType.Union(ts) => Json.Array(ts.map(t => Json.String(t.toJsonString)): _*)
  }

  def contains(t: JsonSchemaType): scala.Boolean = this match {
    case SchemaType.Single(st) => st == t
    case SchemaType.Union(ts)  => ts.contains(t)
  }
}

object SchemaType {
  final case class Single(value: JsonSchemaType)     extends SchemaType
  final case class Union(values: ::[JsonSchemaType]) extends SchemaType

  def fromJson(json: Json): Either[String, SchemaType] = json match {
    case s: Json.String =>
      JsonSchemaType.fromString(s.value) match {
        case Some(t) => Right(Single(t))
        case None    => Left(s"Unknown type: ${s.value}")
      }
    case a: Json.Array =>
      val types = a.value.map {
        case s: Json.String =>
          JsonSchemaType.fromString(s.value) match {
            case Some(t) => Right(t)
            case None    => Left(s"Unknown type: ${s.value}")
          }
        case other => Left(s"Expected string in type array, got ${other.getClass.getSimpleName}")
      }
      val errors = types.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString(", "))
      else {
        val ts = types.collect { case Right(t) => t }
        ts.toList match {
          case head :: tail => Right(Union(new ::(head, tail)))
          case Nil          => Left("Empty type array")
        }
      }
    case other => Left(s"Expected string or array for type, got ${other.getClass.getSimpleName}")
  }
}
