package zio.blocks.schema.jsonschema

import zio.blocks.schema._
import zio.blocks.schema.jsonschema.JsonSchemaValue._

/**
 * Represents a JSON Schema for a Scala type A.
 *
 * JSON Schema is a vocabulary that allows you to annotate and validate JSON documents.
 * This implementation supports JSON Schema Draft 2020-12.
 *
 * @see https://json-schema.org/
 */
final case class JsonSchema[A](schema: JsonSchemaValue.Obj) {

  /**
   * Returns the JSON Schema as a compact JSON string.
   */
  def toJson: String = schema.toJson

  /**
   * Returns the JSON Schema as a pretty-printed JSON string.
   */
  def toPrettyJson: String = schema.toPrettyJson

  /**
   * Adds a custom property to the schema.
   */
  def withProperty(key: String, value: JsonSchemaValue): JsonSchema[A] =
    JsonSchema(schema + (key -> value))

  /**
   * Sets the title of the schema.
   */
  def withTitle(title: String): JsonSchema[A] =
    withProperty("title", Str(title))

  /**
   * Sets the description of the schema.
   */
  def withDescription(description: String): JsonSchema[A] =
    withProperty("description", Str(description))

  /**
   * Marks the schema as deprecated.
   */
  def deprecated: JsonSchema[A] =
    withProperty("deprecated", Bool(true))

  /**
   * Adds examples to the schema.
   */
  def withExamples(examples: JsonSchemaValue*): JsonSchema[A] =
    withProperty("examples", Arr(examples.toIndexedSeq))

  /**
   * Sets a default value for the schema.
   */
  def withDefault(default: JsonSchemaValue): JsonSchema[A] =
    withProperty("default", default)
}

object JsonSchema {

  /**
   * Creates a JSON Schema that references a definition.
   */
  def ref(name: String): JsonSchemaValue.Obj =
    Obj("$ref" -> Str(s"#/$$defs/$name"))

  /**
   * Creates a JSON Schema for a string type.
   */
  def string: JsonSchemaValue.Obj = Obj("type" -> Str("string"))

  /**
   * Creates a JSON Schema for an integer type.
   */
  def integer: JsonSchemaValue.Obj = Obj("type" -> Str("integer"))

  /**
   * Creates a JSON Schema for a number type.
   */
  def number: JsonSchemaValue.Obj = Obj("type" -> Str("number"))

  /**
   * Creates a JSON Schema for a boolean type.
   */
  def boolean: JsonSchemaValue.Obj = Obj("type" -> Str("boolean"))

  /**
   * Creates a JSON Schema for a null type.
   */
  def `null`: JsonSchemaValue.Obj = Obj("type" -> Str("null"))

  /**
   * Creates a JSON Schema for an array type.
   */
  def array(items: JsonSchemaValue.Obj): JsonSchemaValue.Obj =
    Obj("type" -> Str("array"), "items" -> items)

  /**
   * Creates a JSON Schema for an object type with properties.
   */
  def `object`(
    properties: IndexedSeq[(String, JsonSchemaValue.Obj)],
    required: IndexedSeq[String],
    additionalProperties: Option[JsonSchemaValue] = None
  ): JsonSchemaValue.Obj = {
    val base = Obj(
      "type"       -> Str("object"),
      "properties" -> Obj(properties.map { case (k, v) => k -> (v: JsonSchemaValue) }: _*)
    )
    val withRequired =
      if (required.isEmpty) base
      else base + ("required" -> Arr(required.map(Str(_))))
    additionalProperties match {
      case Some(ap) => withRequired + ("additionalProperties" -> ap)
      case None     => withRequired
    }
  }

  /**
   * Creates a JSON Schema for a map type (object with additionalProperties).
   */
  def map(valueSchema: JsonSchemaValue.Obj): JsonSchemaValue.Obj =
    Obj(
      "type"                 -> Str("object"),
      "additionalProperties" -> valueSchema
    )

  /**
   * Creates a JSON Schema oneOf for variants/unions.
   */
  def oneOf(schemas: IndexedSeq[JsonSchemaValue.Obj]): JsonSchemaValue.Obj =
    Obj("oneOf" -> Arr(schemas.map(s => s: JsonSchemaValue)))

  /**
   * Creates a JSON Schema anyOf.
   */
  def anyOf(schemas: IndexedSeq[JsonSchemaValue.Obj]): JsonSchemaValue.Obj =
    Obj("anyOf" -> Arr(schemas.map(s => s: JsonSchemaValue)))

  /**
   * Creates a JSON Schema for an enum.
   */
  def enum(values: IndexedSeq[JsonSchemaValue]): JsonSchemaValue.Obj =
    Obj("enum" -> Arr(values))

  /**
   * Creates a JSON Schema with a constant value.
   */
  def const(value: JsonSchemaValue): JsonSchemaValue.Obj =
    Obj("const" -> value)

  /**
   * Adds string constraints to a schema.
   */
  def withStringConstraints(
    base: JsonSchemaValue.Obj,
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    pattern: Option[String] = None,
    format: Option[String] = None
  ): JsonSchemaValue.Obj = {
    var result = base
    minLength.foreach(v => result = result + ("minLength" -> Num(v)))
    maxLength.foreach(v => result = result + ("maxLength" -> Num(v)))
    pattern.foreach(v => result = result + ("pattern" -> Str(v)))
    format.foreach(v => result = result + ("format" -> Str(v)))
    result
  }

  /**
   * Adds numeric constraints to a schema.
   */
  def withNumericConstraints(
    base: JsonSchemaValue.Obj,
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None
  ): JsonSchemaValue.Obj = {
    var result = base
    minimum.foreach(v => result = result + ("minimum" -> Num(v)))
    maximum.foreach(v => result = result + ("maximum" -> Num(v)))
    exclusiveMinimum.foreach(v => result = result + ("exclusiveMinimum" -> Num(v)))
    exclusiveMaximum.foreach(v => result = result + ("exclusiveMaximum" -> Num(v)))
    result
  }

  /**
   * Adds a description to a schema.
   */
  def withDescription(base: JsonSchemaValue.Obj, description: String): JsonSchemaValue.Obj =
    base + ("description" -> Str(description))

  /**
   * Adds a title to a schema.
   */
  def withTitle(base: JsonSchemaValue.Obj, title: String): JsonSchemaValue.Obj =
    base + ("title" -> Str(title))
}
