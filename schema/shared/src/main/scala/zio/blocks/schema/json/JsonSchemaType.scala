/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  /** The keyword's name for this type, such as `"integer"`. */
  def toJsonString: String
}

/** The seven type names of JSON Schema. */
object JsonSchemaType {

  /** `"null"`. */
  case object Null extends JsonSchemaType {
    def toJsonString: String = "null"
  }

  /** `"boolean"`. */
  case object Boolean extends JsonSchemaType {
    def toJsonString: String = "boolean"
  }

  /** `"string"`. */
  case object String extends JsonSchemaType {
    def toJsonString: String = "string"
  }

  /** `"number"`: any number, integers included. */
  case object Number extends JsonSchemaType {
    def toJsonString: String = "number"
  }

  /** `"integer"`: a number with no fraction, such as `2021` or `2021.0`. */
  case object Integer extends JsonSchemaType {
    def toJsonString: String = "integer"
  }

  /** `"array"`. */
  case object Array extends JsonSchemaType {
    def toJsonString: String = "array"
  }

  /** `"object"`. */
  case object Object extends JsonSchemaType {
    def toJsonString: String = "object"
  }

  /**
   * Returns the type with this name, or `None` for an unknown name (names are
   * case-sensitive).
   */
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

  /**
   * The seven types, in the order null, boolean, string, number, integer,
   * array, object.
   */
  val all: Seq[JsonSchemaType] = Chunk(Null, Boolean, String, Number, Integer, Array, Object)
}

// =============================================================================
// Type Keyword: Single Type or Array of Types
// =============================================================================

/** The value of the `type` keyword: one type name, or an array of them. */
sealed trait SchemaType extends Product with Serializable {

  /** Returns the keyword's value: a string, or an array of strings. */
  def toJson: Json = this match {
    case s: SchemaType.Single => new Json.String(s.value.toJsonString)
    case u: SchemaType.Union  => new Json.Array(Chunk.from(u.values).map(t => new Json.String(t.toJsonString)))
  }

  /**
   * Returns `true` if `t` is one of the types. `Integer` does not count as
   * contained in `Number`.
   */
  def contains(t: JsonSchemaType): scala.Boolean = this match {
    case s: SchemaType.Single => s.value eq t
    case u: SchemaType.Union  => u.values.contains(t)
  }
}

/** The two forms of the `type` keyword. */
object SchemaType {

  /** One type, such as `"type": "string"`. */
  final case class Single(value: JsonSchemaType) extends SchemaType

  /**
   * Several types, such as `"type": ["string", "null"]`; a value may be of any
   * of them.
   */
  final case class Union(values: NonEmptyChunk[JsonSchemaType]) extends SchemaType

  /**
   * Reads the keyword's value: a string, or a non-empty array of strings.
   * Returns `Left` with every unknown name or non-string element.
   */
  def fromJson(json: Json): Either[String, SchemaType] = json match {
    case s: Json.String =>
      JsonSchemaType.fromString(s.value) match {
        case Some(t) => new Right(new Single(t))
        case _       => new Left(s"Unknown type: ${s.value}")
      }
    case a: Json.Array =>
      val types  = ChunkBuilder.make[JsonSchemaType](a.value.length)
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
