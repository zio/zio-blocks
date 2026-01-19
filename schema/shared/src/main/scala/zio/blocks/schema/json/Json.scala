package zio.blocks.schema.json

import scala.collection.immutable.{Map => ScalaMap, Vector}

/**
 * A simple JSON Abstract Data Type (ADT) for representing JSON values.
 *
 * This ADT provides a basic representation of JSON structures and is used as an
 * intermediate format in Schema-based JSON codec derivation. It does not
 * include advanced features like navigation, patching, merging, or parsing from
 * string format—these are considered part of a larger Json system.
 *
 * The primary purpose of this ADT is to serve as a bridge between Scala values
 * (via Schema and DynamicValue) and JSON representations.
 */
sealed trait Json {

  def isObject: Boolean = false
  def isArray: Boolean = false
  def isString: Boolean = false
  def isNumber: Boolean = false
  def isBoolean: Boolean = false
  def isNull: Boolean = false

  def fields: Map[String, Json] = Map.empty
  def elements: Vector[Json] = Vector.empty
  def stringValue: Option[String] = None
  def numberValue: Option[BigDecimal] = None
  def booleanValue: Option[Boolean] = None

  /**
   * Gets a field by name from an object.
   */
  def apply(key: String): Json = this match {
    case Json.Object(flds) => flds.getOrElse(key, Json.Null)
    case _              => Json.Null
  }

  /**
   * Gets an element by index from an array.
   */
  def apply(index: Int): Json = this match {
    case Json.Array(elems) if index >= 0 && index < elems.size => elems(index)
    case _                                                            => Json.Null
  }

  /**
   * Sets a field in an object, returning a new object.
   */
  def set(key: String, value: Json): Json = this match {
    case Json.Object(fields) => Json.Object(fields + (key -> value))
    case _                  => Json.Object(Map(key -> value))
  }

  /**
   * Removes a field from an object, returning a new object.
   */
  def remove(key: String): Json = this match {
    case Json.Object(fields) => Json.Object(fields - key)
    case _                  => this
  }

  /**
   * Adds an element to an array, returning a new array.
   */
  def append(value: Json): Json = this match {
    case Json.Array(elements) => Json.Array(elements :+ value)
    case _                  => Json.Array(Vector(value))
  }

  /**
   * Removes an element at index from an array, returning a new array.
   */
  def removeAt(index: Int): Json = this match {
    case Json.Array(elements) if index >= 0 && index < elements.size => 
      Json.Array(elements.take(index) ++ elements.drop(index + 1))
    case _ => this
  }

final case class Object(fields: ScalaMap[Predef.String, Json]) extends Json {
  override def isObject: Boolean = true
  override def fields: Map[String, Json] = fields

  override def apply(key: String): Json = fields.getOrElse(key, Json.Null)
}

final case class Array(elements: Vector[Json]) extends Json {
  override def isArray: Boolean = true
  override def elements: Vector[Json] = elements

  override def apply(index: Int): Json = 
    if (index >= 0 && index < elements.size) elements(index) else Json.Null
}

final case class String(value: Predef.String) extends Json {
  override def isString: Boolean = true
  override def stringValue: Option[String] = Some(value)

  override def apply(key: String): Json = Json.Null // Strings don't have fields
}

final case class Number(value: BigDecimal) extends Json {
  override def isNumber: Boolean = true
  override def numberValue: Option[BigDecimal] = Some(value)

  override def apply(key: String): Json = Json.Null // Numbers don't have fields
}

final case class Boolean(value: scala.Boolean) extends Json {
  override def isBoolean: Boolean = true
  override def booleanValue: Option[Boolean] = Some(value)

  override def apply(key: String): Json = Json.Null // Booleans don't have fields
}

case object Null extends Json {
  override def isNull: Boolean = true

  override def apply(key: String): Json = Json.Null // Null doesn't have fields
}

object Json {
  def obj(fields: (String, Json)*): Json.Object = Json.Object(ScalaMap(fields: _*))

  def arr(elements: Json*): Json.Array = Json.Array(elements.toVector)

  def str(value: String): Json.String = Json.String(value)

  def num(value: BigDecimal): Json.Number = Json.Number(value)

  def bool(value: Boolean): Json.Boolean = Json.Boolean(value)

  def fromString(value: String): Json.String = Json.String(value)
  def fromInt(value: Int): Json.Number = Json.Number(BigDecimal(value))
  def fromLong(value: Long): Json.Number = Json.Number(BigDecimal(value))
  def fromDouble(value: Double): Json.Number = Json.Number(BigDecimal(value))

  /**
   * Parses a JSON string into a Json value.
   */
  def parse(s: String): Either[JsonDecoderError, Json] = {
    val reader = new JsonReader()
    try {
      Right(reader.decode(s))
    } catch {
      case e: JsonBinaryCodecError => 
        Left(JsonDecoderError(e.getMessage))
      case e: Exception => 
        Left(JsonDecoderError(e.getMessage))
    }
  }

  /**
   * Encodes a Json value to a JSON string.
   */
  def encode(json: Json): String = {
    val writer = new JsonWriter()
    try {
      writer.encode(json)
    } catch {
      case e: JsonBinaryCodecError => 
        throw e
      case e: Exception => 
        throw new RuntimeException(e)
    }
  }
}

/**
 * String interpolator for JSON paths.
 * 
 * Usage:
 * {{{
 * import zio.blocks.schema.json.interpolators._
 * 
 * val path = p"user.profile.name"
 * val json = Json.obj("name" -> Json.str("Alice"))
 * }}}
 */
object JsonPathInterpolator {
  implicit class JsonPathContext(val sc: StringContext) {
    def p(args: Any*): JsonPath = {
      val parts = sc.parts.map(_.s)
      JsonPath(parts: _*)
    }
  }
}

/**
 * String interpolator for JSON literals.
 * 
 * Usage:
 * {{{
 * import zio.blocks.schema.json.interpolators._
 * 
 * val json = j"""{"name": "Alice", "age": 30}"""
 * val json = j"""[1, 2, 3]"""
 * }}}
 */
object JsonLiteralInterpolator {
  implicit class JsonLiteralContext(val sc: StringContext) {
    def j(args: Any*): Json = {
      // Reconstruct the JSON string from parts and arguments
      val jsonString = sc.parts.zip(args).map { case (part, arg) =>
        part + arg.toString
      }.mkString + sc.parts.lastOption.getOrElse("")
      
      // Parse the JSON string
      Json.parse(jsonString) match {
        case Right(json) => json
        case Left(error) => throw new RuntimeException(s"Invalid JSON literal: ${error.getMessage}")
      }
    }
  }
}

/**
 * Simple path representation for string interpolation.
 */
case class JsonPath(parts: String*) {
  override def toString: String = parts.mkString(".")
}
