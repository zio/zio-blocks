package zio.blocks.schema.json

import zio.blocks.schema.{Doc, DynamicOptic, Modifier, Validation}
import zio.blocks.schema.json.validation.{JsonSchemaValidator, SchemaRegistry}

/**
 * Represents a JSON Schema 2020-12 document.
 *
 * JSON Schema is a vocabulary for annotating and validating JSON documents.
 * This implementation supports the core, validation, and applicator
 * vocabularies.
 *
 * @see
 *   https://json-schema.org/draft/2020-12/json-schema-core
 * @see
 *   https://json-schema.org/draft/2020-12/json-schema-validation
 */
sealed trait JsonSchema {

  /**
   * Validates a JSON value against this schema.
   *
   * @param json
   *   The JSON value to validate
   * @return
   *   Right(()) if valid, Left(errors) if invalid
   */
  def validate(json: Json): Either[JsonSchemaError, Unit]

  /**
   * Returns true if the JSON value conforms to this schema.
   */
  def isValid(json: Json): Boolean = validate(json).isRight

  /**
   * Converts this schema to its JSON representation.
   */
  def toJson: Json

  /**
   * Combines this schema with another using allOf.
   */
  def &&(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, other)                                   => other
    case (other, JsonSchema.True)                                   => other
    case (JsonSchema.False, _)                                      => JsonSchema.False
    case (_, JsonSchema.False)                                      => JsonSchema.False
    case (s1: JsonSchema.SchemaObject, s2: JsonSchema.SchemaObject) =>
      val combined = (s1.allOf, s2.allOf) match {
        case (Some(a1), Some(a2)) => Some(a1 ++ a2)
        case (Some(a1), None)     => Some(a1 :+ s2)
        case (None, Some(a2))     => Some(s1 +: a2)
        case (None, None)         => Some(Vector(s1, s2))
      }
      JsonSchema.SchemaObject(allOf = combined)
  }

  /**
   * Combines this schema with another using anyOf.
   */
  def ||(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _)                                       => JsonSchema.True
    case (_, JsonSchema.True)                                       => JsonSchema.True
    case (JsonSchema.False, other)                                  => other
    case (other, JsonSchema.False)                                  => other
    case (s1: JsonSchema.SchemaObject, s2: JsonSchema.SchemaObject) =>
      val combined = (s1.anyOf, s2.anyOf) match {
        case (Some(a1), Some(a2)) => Some(a1 ++ a2)
        case (Some(a1), None)     => Some(a1 :+ s2)
        case (None, Some(a2))     => Some(s1 +: a2)
        case (None, None)         => Some(Vector(s1, s2))
      }
      JsonSchema.SchemaObject(anyOf = combined)
  }

  /**
   * Negates this schema using not.
   */
  def unary_! : JsonSchema = this match {
    case JsonSchema.True  => JsonSchema.False
    case JsonSchema.False => JsonSchema.True
    case schema           => JsonSchema.SchemaObject(not = Some(schema))
  }
}

object JsonSchema {

  import zio.blocks.schema.Schema

  /**
   * Schema instance for JsonSchema, enabling serialization, diffing, and
   * patching.
   *
   * This allows JsonSchema values to be treated as first-class schema values.
   */
  implicit lazy val schema: Schema[JsonSchema] = Schema.derived

  // ─────────────────────────────────────────────────────────────────────────
  // Boolean Schemas
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * The boolean schema `true` - accepts any valid JSON value.
   */
  case object True extends JsonSchema {
    def validate(json: Json): Either[JsonSchemaError, Unit] = Right(())
    def toJson: Json                                        = Json.True
  }

  /**
   * The boolean schema `false` - rejects all JSON values.
   */
  case object False extends JsonSchema {
    def validate(json: Json): Either[JsonSchemaError, Unit] =
      Left(
        JsonSchemaError(
          JsonSchemaError.ConstraintViolation(
            DynamicOptic.root,
            "false schema",
            "any value"
          )
        )
      )
    def toJson: Json = Json.False
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Schema Object
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A JSON Schema object with all supported keywords.
   *
   * All fields are optional - an empty schema is equivalent to `true`.
   */
  final case class SchemaObject(
    // ─── Core vocabulary ─────────────────────────────────────────────────
    `$id`: Option[String] = None,
    `$schema`: Option[String] = None,
    `$ref`: Option[String] = None,
    `$anchor`: Option[String] = None,
    `$defs`: Option[Map[String, JsonSchema]] = None,
    `$comment`: Option[String] = None,
    `$dynamicAnchor`: Option[String] = None,
    `$dynamicRef`: Option[String] = None,

    // ─── Type constraint ─────────────────────────────────────────────────
    `type`: Option[JsonType] = None,

    // ─── String validation ───────────────────────────────────────────────
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    pattern: Option[String] = None,
    format: Option[String] = None,

    // ─── Content vocabulary ────────────────────────────────────────────────
    contentEncoding: Option[String] = None,
    contentMediaType: Option[String] = None,
    contentSchema: Option[JsonSchema] = None,

    // ─── Numeric validation ──────────────────────────────────────────────
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    multipleOf: Option[BigDecimal] = None,

    // ─── Array validation ────────────────────────────────────────────────
    items: Option[JsonSchema] = None,
    prefixItems: Option[Vector[JsonSchema]] = None,
    contains: Option[JsonSchema] = None,
    minContains: Option[Int] = None,
    maxContains: Option[Int] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    uniqueItems: Option[Boolean] = None,
    unevaluatedItems: Option[JsonSchema] = None,

    // ─── Object validation ───────────────────────────────────────────────
    properties: Option[Map[String, JsonSchema]] = None,
    patternProperties: Option[Map[String, JsonSchema]] = None,
    additionalProperties: Option[JsonSchema] = None,
    required: Option[Set[String]] = None,
    minProperties: Option[Int] = None,
    maxProperties: Option[Int] = None,
    propertyNames: Option[JsonSchema] = None,
    unevaluatedProperties: Option[JsonSchema] = None,
    dependentRequired: Option[Map[String, Set[String]]] = None,
    dependentSchemas: Option[Map[String, JsonSchema]] = None,

    // ─── Composition ─────────────────────────────────────────────────────
    allOf: Option[Vector[JsonSchema]] = None,
    anyOf: Option[Vector[JsonSchema]] = None,
    oneOf: Option[Vector[JsonSchema]] = None,
    not: Option[JsonSchema] = None,

    // ─── Conditionals ────────────────────────────────────────────────────
    `if`: Option[JsonSchema] = None,
    `then`: Option[JsonSchema] = None,
    `else`: Option[JsonSchema] = None,

    // ─── Enumeration ─────────────────────────────────────────────────────
    `enum`: Option[Vector[Json]] = None,
    const: Option[Json] = None,

    // ─── Metadata (non-validating) ───────────────────────────────────────
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[Json] = None,
    examples: Option[Vector[Json]] = None,
    deprecated: Option[Boolean] = None,
    readOnly: Option[Boolean] = None,
    writeOnly: Option[Boolean] = None,

    // ─── Extensions (unknown keywords) ─────────────────────────────────────
    extensions: Option[Map[String, Json]] = None
  ) extends JsonSchema {

    def validate(json: Json): Either[JsonSchemaError, Unit] =
      JsonSchemaValidator.validate(this, json, DynamicOptic.root, SchemaRegistry.empty)

    def toJson: Json = JsonSchemaCodec.encode(this)

    // ─── Builder methods ─────────────────────────────────────────────────

    def withTitle(title: String): SchemaObject      = copy(title = Some(title))
    def withDescription(desc: String): SchemaObject = copy(description = Some(desc))
    def withDefault(value: Json): SchemaObject      = copy(default = Some(value))
    def withExamples(values: Json*): SchemaObject   = copy(examples = Some(values.toVector))

    def withMinLength(n: Int): SchemaObject      = copy(minLength = Some(n))
    def withMaxLength(n: Int): SchemaObject      = copy(maxLength = Some(n))
    def withPattern(regex: String): SchemaObject = copy(pattern = Some(regex))
    def withFormat(fmt: String): SchemaObject    = copy(format = Some(fmt))

    def withMinimum(n: BigDecimal): SchemaObject                  = copy(minimum = Some(n))
    def withMaximum(n: BigDecimal): SchemaObject                  = copy(maximum = Some(n))
    def withRange(min: BigDecimal, max: BigDecimal): SchemaObject =
      copy(minimum = Some(min), maximum = Some(max))

    def withItems(schema: JsonSchema): SchemaObject = copy(items = Some(schema))
    def withMinItems(n: Int): SchemaObject          = copy(minItems = Some(n))
    def withMaxItems(n: Int): SchemaObject          = copy(maxItems = Some(n))
    def withUniqueItems: SchemaObject               = copy(uniqueItems = Some(true))

    def withProperty(name: String, schema: JsonSchema): SchemaObject =
      copy(properties = Some(properties.getOrElse(Map.empty) + (name -> schema)))

    def withRequired(fields: String*): SchemaObject =
      copy(required = Some(required.getOrElse(Set.empty) ++ fields))

    def withAdditionalProperties(schema: JsonSchema): SchemaObject =
      copy(additionalProperties = Some(schema))

    def noAdditionalProperties: SchemaObject =
      copy(additionalProperties = Some(False))

    def nullable: SchemaObject = `type` match {
      case Some(t: JsonType.Union) => copy(`type` = Some(t.nullable))
      case Some(t)                 => copy(`type` = Some(JsonType.Union(Set(t, JsonType.Null))))
      case None                    => copy(`type` = Some(JsonType.Null))
    }
  }

  object SchemaObject {
    val empty: SchemaObject = SchemaObject()
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Smart Constructors
  // ─────────────────────────────────────────────────────────────────────────

  /** Empty schema (equivalent to true). */
  val empty: SchemaObject = SchemaObject.empty

  /** Creates a schema for a specific type. */
  def apply(`type`: JsonType): SchemaObject = SchemaObject(`type` = Some(`type`))

  /** String schema. */
  def string: SchemaObject = SchemaObject(`type` = Some(JsonType.String))

  /** Number schema. */
  def number: SchemaObject = SchemaObject(`type` = Some(JsonType.Number))

  /** Integer schema. */
  def integer: SchemaObject = SchemaObject(`type` = Some(JsonType.Integer))

  /** Boolean schema. */
  def boolean: SchemaObject = SchemaObject(`type` = Some(JsonType.Boolean))

  /** Null schema. */
  def `null`: SchemaObject = SchemaObject(`type` = Some(JsonType.Null))

  /** Array schema. */
  def array: SchemaObject = SchemaObject(`type` = Some(JsonType.Array))

  /** Array schema with items constraint. */
  def array(items: JsonSchema): SchemaObject =
    SchemaObject(`type` = Some(JsonType.Array), items = Some(items))

  /** Object schema. */
  def `object`: SchemaObject = SchemaObject(`type` = Some(JsonType.Object))

  /** Object schema with properties. */
  def `object`(props: (String, JsonSchema)*): SchemaObject =
    SchemaObject(`type` = Some(JsonType.Object), properties = Some(props.toMap))

  /** Enum schema. */
  def `enum`(values: Json*): SchemaObject = SchemaObject(`enum` = Some(values.toVector))

  /** Const schema. */
  def const(value: Json): SchemaObject = SchemaObject(const = Some(value))

  /** Schema with allOf. */
  def allOf(schemas: JsonSchema*): SchemaObject = SchemaObject(allOf = Some(schemas.toVector))

  /** Schema with anyOf. */
  def anyOf(schemas: JsonSchema*): SchemaObject = SchemaObject(anyOf = Some(schemas.toVector))

  /** Schema with oneOf. */
  def oneOf(schemas: JsonSchema*): SchemaObject = SchemaObject(oneOf = Some(schemas.toVector))

  /** Schema with not. */
  def not(schema: JsonSchema): SchemaObject = SchemaObject(not = Some(schema))

  // ─────────────────────────────────────────────────────────────────────────
  // Parsing
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Parses a JSON value into a JsonSchema.
   */
  def fromJson(json: Json): Either[JsonSchemaError, JsonSchema] =
    JsonSchemaCodec.decode(json)

  /**
   * Parses a JSON string into a JsonSchema.
   */
  def parse(jsonString: String): Either[JsonSchemaError, JsonSchema] =
    Json.parse(jsonString) match {
      case Right(json)     => fromJson(json)
      case Left(jsonError) =>
        Left(
          JsonSchemaError(
            JsonSchemaError.SchemaParseError(DynamicOptic.root, jsonError.message)
          )
        )
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Validation[A] → JSON Schema Mapping
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Applies Validation[A] constraints to a SchemaObject.
   *
   * Mapping:
   *   - Validation.Numeric.Positive → exclusiveMinimum: 0
   *   - Validation.Numeric.NonNegative → minimum: 0
   *   - Validation.Numeric.Negative → exclusiveMaximum: 0
   *   - Validation.Numeric.NonPositive → maximum: 0
   *   - Validation.Numeric.Range(min, max) → minimum/maximum
   *   - Validation.Numeric.Set(values) → enum
   *   - Validation.String.NonEmpty → minLength: 1
   *   - Validation.String.Empty → maxLength: 0
   *   - Validation.String.Length(min, max) → minLength/maxLength
   *   - Validation.String.Pattern(regex) → pattern
   */
  def applyValidation[A](schema: SchemaObject, validation: Validation[A]): SchemaObject =
    validation match {
      case Validation.None => schema

      // Numeric validations
      case Validation.Numeric.Positive =>
        schema.copy(exclusiveMinimum = Some(BigDecimal(0)))
      case Validation.Numeric.NonNegative =>
        schema.copy(minimum = Some(BigDecimal(0)))
      case Validation.Numeric.Negative =>
        schema.copy(exclusiveMaximum = Some(BigDecimal(0)))
      case Validation.Numeric.NonPositive =>
        schema.copy(maximum = Some(BigDecimal(0)))
      case r: Validation.Numeric.Range[?] =>
        schema.copy(
          minimum = r.min.map(v => BigDecimal(v.toString)),
          maximum = r.max.map(v => BigDecimal(v.toString))
        )
      case s: Validation.Numeric.Set[?] =>
        schema.copy(`enum` = Some(s.values.toSeq.map(v => Json.number(BigDecimal(v.toString))).toVector))

      // String validations
      case Validation.String.NonEmpty =>
        schema.copy(minLength = Some(1))
      case Validation.String.Empty =>
        schema.copy(maxLength = Some(0))
      case Validation.String.Blank =>
        schema.copy(pattern = Some("^\\s*$"))
      case Validation.String.NonBlank =>
        schema.copy(pattern = Some(".*\\S.*"))
      case l: Validation.String.Length =>
        schema.copy(minLength = l.min, maxLength = l.max)
      case p: Validation.String.Pattern =>
        schema.copy(pattern = Some(p.regex))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Modifier.config → JSON Schema Mapping
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Applies Modifier.config entries to a SchemaObject.
   *
   * Supported config keys:
   *   - "json-schema.format" → format keyword
   *   - "json-schema.deprecated" → deprecated: true (if value is "true")
   *   - "json-schema.title" → title keyword
   *   - "json-schema.description" → description keyword (overrides Doc)
   */
  def applyModifiers(schema: SchemaObject, modifiers: Seq[Modifier.Reflect]): SchemaObject =
    modifiers.foldLeft(schema) {
      case (s, Modifier.config(key, value)) =>
        key match {
          case "json-schema.format"     => s.copy(format = Some(value))
          case "json-schema.deprecated" =>
            if (value.toLowerCase == "true") s.copy(deprecated = Some(true)) else s
          case "json-schema.title"       => s.copy(title = Some(value))
          case "json-schema.description" => s.copy(description = Some(value))
          case _                         => s // Ignore unknown keys
        }
      case (s, _) => s // Ignore non-config modifiers
    }

  /**
   * Applies Doc to a SchemaObject's description field.
   */
  def applyDoc(schema: SchemaObject, doc: Doc): SchemaObject = doc match {
    case Doc.Empty      => schema
    case Doc.Text(text) =>
      if (schema.description.isEmpty) schema.copy(description = Some(text)) else schema
    case Doc.Concat(leaves) =>
      val text = leaves.collect { case Doc.Text(t) => t }.mkString(" ")
      if (text.nonEmpty && schema.description.isEmpty) schema.copy(description = Some(text)) else schema
  }
}
