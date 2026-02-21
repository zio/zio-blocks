package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, NonEmptyChunk}

/**
 * Represents the "type" keyword values in JSON Schema.
 *
 * Note: This is distinct from [[JsonType]] which represents runtime JSON value
 * types. JSON Schema's type keyword includes "integer" as a distinct type,
 * while runtime JSON only has "number" for all numeric values.
 */
sealed trait JsonSchemaType extends Product with Serializable {
  def toJsonString: String
}

object JsonSchemaType {
  case object Null extends JsonSchemaType {
    def toJsonString: String = "null"
  }
  case object Boolean extends JsonSchemaType {
    def toJsonString: String = "boolean"
  }
  case object String extends JsonSchemaType {
    def toJsonString: String = "string"
  }
  case object Number extends JsonSchemaType {
    def toJsonString: String = "number"
  }
  case object Integer extends JsonSchemaType {
    def toJsonString: String = "integer"
  }
  case object Array extends JsonSchemaType {
    def toJsonString: String = "array"
  }
  case object Object extends JsonSchemaType {
    def toJsonString: String = "object"
  }

  def fromString(s: String): Option[JsonSchemaType] = new Some(s match {
    case "null"    => Null
    case "boolean" => Boolean
    case "string"  => String
    case "number"  => Number
    case "integer" => Integer
    case "array"   => Array
    case "object"  => Object
    case _         => return None
  })

  val all: Seq[JsonSchemaType] = Chunk(Null, Boolean, String, Number, Integer, Array, Object)
}

// =============================================================================
// Type Keyword: Single Type or Array of Types
// =============================================================================

sealed trait SchemaType extends Product with Serializable {
  def toJson: Json = this match {
    case s: SchemaType.Single => new Json.String(s.value.toJsonString)
    case u: SchemaType.Union  => new Json.Array(Chunk.from(u.values).map(t => new Json.String(t.toJsonString)))
  }

  def contains(t: JsonSchemaType): scala.Boolean = this match {
    case s: SchemaType.Single => s.value eq t
    case u: SchemaType.Union  => u.values.contains(t)
  }
}

object SchemaType {
  final case class Single(value: JsonSchemaType)                extends SchemaType
  final case class Union(values: NonEmptyChunk[JsonSchemaType]) extends SchemaType

  def fromJson(json: Json): Either[String, SchemaType] = json match {
    case s: Json.String =>
      JsonSchemaType.fromString(s.value) match {
        case Some(t) => new Right(new Single(t))
        case _       => new Left(s"Unknown type: ${s.value}")
      }
    case a: Json.Array =>
      val types  = ChunkBuilder.make[JsonSchemaType]()
      val errors = new java.lang.StringBuilder
      a.value.foreach {
        case s: Json.String =>
          JsonSchemaType.fromString(s.value) match {
            case Some(t) =>
              if (errors.length == 0) types.addOne(t)
            case _ =>
              if (errors.length > 0) errors.append(", ")
              errors.append(s"Unknown type: ${s.value}")
          }
        case other =>
          if (errors.length > 0) errors.append(", ")
          errors.append(s"Expected string in type array, got ${other.getClass.getSimpleName}")
      }
      if (errors.length > 0) new Left(errors.toString)
      else {
        NonEmptyChunk.fromChunk(types.result()) match {
          case Some(ts) => new Right(new Union(ts))
          case _        => new Left("Empty type array")
        }
      }
    case other => new Left(s"Expected string or array for type, got ${other.getClass.getSimpleName}")
  }
}
