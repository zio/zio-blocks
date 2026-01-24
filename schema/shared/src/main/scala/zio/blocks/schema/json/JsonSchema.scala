package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaError}

import java.net.URI
import java.util.regex.{Pattern, PatternSyntaxException}
import scala.util.control.NonFatal

// =============================================================================
// Helper Types for Precision
// =============================================================================

/**
 * Non-negative integer (>= 0). Used for minLength, maxLength, minItems, etc.
 */
final case class NonNegativeInt private (value: Int) extends AnyVal

object NonNegativeInt {
  def apply(n: Int): Option[NonNegativeInt] =
    if (n >= 0) Some(new NonNegativeInt(n)) else None

  def unsafe(n: Int): NonNegativeInt = {
    require(n >= 0, s"NonNegativeInt requires n >= 0, got $n")
    new NonNegativeInt(n)
  }

  val zero: NonNegativeInt = new NonNegativeInt(0)
  val one: NonNegativeInt  = new NonNegativeInt(1)
}

/**
 * Strictly positive number (> 0). Used for multipleOf.
 */
final case class PositiveNumber private (value: BigDecimal) extends AnyVal

object PositiveNumber {
  def apply(n: BigDecimal): Option[PositiveNumber] =
    if (n > 0) Some(new PositiveNumber(n)) else None

  def unsafe(n: BigDecimal): PositiveNumber = {
    require(n > 0, s"PositiveNumber requires n > 0, got $n")
    new PositiveNumber(n)
  }

  def fromInt(n: Int): Option[PositiveNumber] =
    if (n > 0) Some(new PositiveNumber(BigDecimal(n))) else None
}

/**
 * ECMA-262 regular expression pattern.
 */
final case class RegexPattern(value: String) extends AnyVal {

  /** Compiles the pattern to a Java Pattern for validation. */
  def compiled: Either[String, Pattern] =
    try Right(Pattern.compile(value))
    catch {
      case e: PatternSyntaxException => Left(e.getMessage)
    }
}

object RegexPattern {

  /** Validates and creates a RegexPattern. */
  def apply(value: String): Either[String, RegexPattern] =
    try {
      Pattern.compile(value)
      Right(new RegexPattern(value))
    } catch {
      case e: PatternSyntaxException => Left(e.getMessage)
    }

  /** Creates a RegexPattern without validation (for trusted input). */
  def unsafe(value: String): RegexPattern = new RegexPattern(value)
}

/**
 * URI-Reference per RFC 3986 (may be relative).
 */
final case class UriReference(value: String) extends AnyVal {

  /** Attempts to resolve this reference against a base URI. */
  def resolve(base: URI): Either[String, URI] =
    try Right(base.resolve(value))
    catch {
      case e if NonFatal(e) => Left(e.getMessage)
    }
}

object UriReference {
  def apply(value: String): UriReference = new UriReference(value)
}

/**
 * Anchor name (plain name fragment without #).
 */
final case class Anchor(value: String) extends AnyVal

object Anchor {
  def apply(value: String): Anchor = new Anchor(value)
}

// =============================================================================
// JSON Primitive Type Enumeration
// =============================================================================

sealed trait JsonType extends Product with Serializable {
  def toJsonString: String = this match {
    case JsonType.Null    => "null"
    case JsonType.Boolean => "boolean"
    case JsonType.String  => "string"
    case JsonType.Number  => "number"
    case JsonType.Integer => "integer"
    case JsonType.Array   => "array"
    case JsonType.Object  => "object"
  }
}

object JsonType {
  case object Null    extends JsonType
  case object Boolean extends JsonType
  case object String  extends JsonType
  case object Number  extends JsonType
  case object Integer extends JsonType
  case object Array   extends JsonType
  case object Object  extends JsonType

  def fromString(s: String): Option[JsonType] = s match {
    case "null"    => Some(Null)
    case "boolean" => Some(Boolean)
    case "string"  => Some(String)
    case "number"  => Some(Number)
    case "integer" => Some(Integer)
    case "array"   => Some(Array)
    case "object"  => Some(Object)
    case _         => None
  }

  val all: Seq[JsonType] = Seq(Null, Boolean, String, Number, Integer, Array, Object)
}

// =============================================================================
// Type Keyword: Single Type or Array of Types
// =============================================================================

sealed trait SchemaType extends Product with Serializable {
  def toJson: Json = this match {
    case SchemaType.Single(t) => Json.String(t.toJsonString)
    case SchemaType.Union(ts) => Json.Array(ts.map(t => Json.String(t.toJsonString)).toVector)
  }

  def contains(t: JsonType): scala.Boolean = this match {
    case SchemaType.Single(st) => st == t
    case SchemaType.Union(ts)  => ts.contains(t)
  }
}

object SchemaType {
  final case class Single(value: JsonType)     extends SchemaType
  final case class Union(values: ::[JsonType]) extends SchemaType

  def fromJson(json: Json): Either[String, SchemaType] = json match {
    case s: Json.String =>
      JsonType.fromString(s.value) match {
        case Some(t) => Right(Single(t))
        case None    => Left(s"Unknown type: ${s.value}")
      }
    case a: Json.Array =>
      val types = a.value.map {
        case s: Json.String =>
          JsonType.fromString(s.value) match {
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

// =============================================================================
// JSON Schema 2020-12 ADT
// =============================================================================

/**
 * A JSON Schema 2020-12 representation.
 *
 * This sealed trait provides a complete model of JSON Schema with:
 *   - Boolean schemas (`True` accepts all, `False` rejects all)
 *   - Full schema objects with all standard vocabularies
 *   - Round-trip JSON serialization via `toJson` / `fromJson`
 *   - Validation via `check` method returning accumulated errors
 *   - Combinators (`&&` for allOf, `||` for anyOf, `!` for not)
 */
sealed trait JsonSchema extends Product with Serializable {

  /** Serialize this schema to its canonical JSON representation. */
  def toJson: Json

  /**
   * Validate a JSON value against this schema.
   *
   * @return
   *   None if valid, Some(error) with accumulated failures if invalid.
   */
  def check(json: Json): Option[SchemaError]

  /** Returns true if the JSON value conforms to this schema. */
  def conforms(json: Json): scala.Boolean = check(json).isEmpty

  // ===========================================================================
  // Combinators
  // ===========================================================================

  /** Combine with another schema using allOf. */
  def &&(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.False, _)                                      => JsonSchema.False
    case (_, JsonSchema.False)                                      => JsonSchema.False
    case (JsonSchema.True, s)                                       => s
    case (s, JsonSchema.True)                                       => s
    case (s1: JsonSchema.SchemaObject, s2: JsonSchema.SchemaObject) =>
      (s1.allOf, s2.allOf) match {
        case (Some(a1), Some(a2)) =>
          JsonSchema.SchemaObject(allOf = Some(new ::(a1.head, a1.tail ++ a2.toList)))
        case (Some(a1), None) =>
          JsonSchema.SchemaObject(allOf = Some(new ::(s2, a1.toList)))
        case (None, Some(a2)) =>
          JsonSchema.SchemaObject(allOf = Some(new ::(s1, a2.toList)))
        case (None, None) =>
          JsonSchema.SchemaObject(allOf = Some(new ::(s1, s2 :: Nil)))
      }
  }

  /** Combine with another schema using anyOf. */
  def ||(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _)                                       => JsonSchema.True
    case (_, JsonSchema.True)                                       => JsonSchema.True
    case (JsonSchema.False, s)                                      => s
    case (s, JsonSchema.False)                                      => s
    case (s1: JsonSchema.SchemaObject, s2: JsonSchema.SchemaObject) =>
      (s1.anyOf, s2.anyOf) match {
        case (Some(a1), Some(a2)) =>
          JsonSchema.SchemaObject(anyOf = Some(new ::(a1.head, a1.tail ++ a2.toList)))
        case (Some(a1), None) =>
          JsonSchema.SchemaObject(anyOf = Some(new ::(s2, a1.toList)))
        case (None, Some(a2)) =>
          JsonSchema.SchemaObject(anyOf = Some(new ::(s1, a2.toList)))
        case (None, None) =>
          JsonSchema.SchemaObject(anyOf = Some(new ::(s1, s2 :: Nil)))
      }
  }

  /** Negate this schema. */
  def unary_! : JsonSchema = this match {
    case JsonSchema.True  => JsonSchema.False
    case JsonSchema.False => JsonSchema.True
    case s                => JsonSchema.SchemaObject(not = Some(s))
  }

  /** Make this schema nullable (accepts null in addition to current types). */
  def withNullable: JsonSchema = this match {
    case JsonSchema.True            => JsonSchema.True
    case JsonSchema.False           => JsonSchema.ofType(JsonType.Null)
    case s: JsonSchema.SchemaObject =>
      s.`type` match {
        case Some(SchemaType.Single(t)) if t == JsonType.Null =>
          s
        case Some(SchemaType.Single(t)) =>
          s.copy(`type` = Some(SchemaType.Union(new ::(JsonType.Null, t :: Nil))))
        case Some(SchemaType.Union(ts)) if ts.contains(JsonType.Null) =>
          s
        case Some(SchemaType.Union(ts)) =>
          s.copy(`type` = Some(SchemaType.Union(new ::(JsonType.Null, ts.toList))))
        case None =>
          JsonSchema.SchemaObject(anyOf = Some(new ::(JsonSchema.ofType(JsonType.Null), s :: Nil)))
      }
  }
}

object JsonSchema {

  // ===========================================================================
  // Parsing
  // ===========================================================================

  /** Parse a JsonSchema from its JSON representation. */
  def fromJson(json: Json): Either[SchemaError, JsonSchema] = json match {
    case Json.Boolean(true)  => Right(True)
    case Json.Boolean(false) => Right(False)
    case obj: Json.Object    => parseSchemaObject(obj)
    case other               =>
      Left(
        SchemaError.expectationMismatch(
          Nil,
          s"Expected boolean or object for JSON Schema, got ${other.getClass.getSimpleName}"
        )
      )
  }

  /** Parse a JsonSchema from a JSON string. */
  def parse(jsonString: String): Either[SchemaError, JsonSchema] =
    Json.parse(jsonString) match {
      case Left(err)   => Left(SchemaError.expectationMismatch(Nil, err.message))
      case Right(json) => fromJson(json)
    }

  // ===========================================================================
  // Boolean Schemas
  // ===========================================================================

  /** Schema that accepts all instances. Equivalent to `{}`. */
  case object True extends JsonSchema {
    override def toJson: Json                           = Json.Boolean(true)
    override def check(json: Json): Option[SchemaError] = None
  }

  /** Schema that rejects all instances. Equivalent to `{"not": {}}`. */
  case object False extends JsonSchema {
    override def toJson: Json                           = Json.Boolean(false)
    override def check(json: Json): Option[SchemaError] =
      Some(SchemaError.expectationMismatch(Nil, "Schema rejects all values"))
  }

  // ===========================================================================
  // Schema Object
  // ===========================================================================

  /**
   * A schema object containing keywords from JSON Schema 2020-12. All fields
   * are optional; an empty SchemaObject is equivalent to True.
   */
  final case class SchemaObject(
    // =========================================================================
    // Core Vocabulary
    // =========================================================================
    $id: Option[UriReference] = None,
    $schema: Option[URI] = None,
    $anchor: Option[Anchor] = None,
    $dynamicAnchor: Option[Anchor] = None,
    $ref: Option[UriReference] = None,
    $dynamicRef: Option[UriReference] = None,
    $vocabulary: Option[Map[URI, scala.Boolean]] = None,
    $defs: Option[Map[String, JsonSchema]] = None,
    $comment: Option[String] = None,
    // =========================================================================
    // Applicator Vocabulary (Composition)
    // =========================================================================
    allOf: Option[::[JsonSchema]] = None,
    anyOf: Option[::[JsonSchema]] = None,
    oneOf: Option[::[JsonSchema]] = None,
    not: Option[JsonSchema] = None,
    // =========================================================================
    // Applicator Vocabulary (Conditional)
    // =========================================================================
    `if`: Option[JsonSchema] = None,
    `then`: Option[JsonSchema] = None,
    `else`: Option[JsonSchema] = None,
    // =========================================================================
    // Applicator Vocabulary (Object)
    // =========================================================================
    properties: Option[Map[String, JsonSchema]] = None,
    patternProperties: Option[Map[RegexPattern, JsonSchema]] = None,
    additionalProperties: Option[JsonSchema] = None,
    propertyNames: Option[JsonSchema] = None,
    dependentSchemas: Option[Map[String, JsonSchema]] = None,
    // =========================================================================
    // Applicator Vocabulary (Array)
    // =========================================================================
    prefixItems: Option[::[JsonSchema]] = None,
    items: Option[JsonSchema] = None,
    contains: Option[JsonSchema] = None,
    // =========================================================================
    // Unevaluated Vocabulary
    // =========================================================================
    unevaluatedProperties: Option[JsonSchema] = None,
    unevaluatedItems: Option[JsonSchema] = None,
    // =========================================================================
    // Validation Vocabulary (Type)
    // =========================================================================
    `type`: Option[SchemaType] = None,
    `enum`: Option[::[Json]] = None,
    const: Option[Json] = None,
    // =========================================================================
    // Validation Vocabulary (Numeric)
    // =========================================================================
    multipleOf: Option[PositiveNumber] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    minimum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    // =========================================================================
    // Validation Vocabulary (String)
    // =========================================================================
    minLength: Option[NonNegativeInt] = None,
    maxLength: Option[NonNegativeInt] = None,
    pattern: Option[RegexPattern] = None,
    // =========================================================================
    // Validation Vocabulary (Array)
    // =========================================================================
    minItems: Option[NonNegativeInt] = None,
    maxItems: Option[NonNegativeInt] = None,
    uniqueItems: Option[scala.Boolean] = None,
    minContains: Option[NonNegativeInt] = None,
    maxContains: Option[NonNegativeInt] = None,
    // =========================================================================
    // Validation Vocabulary (Object)
    // =========================================================================
    minProperties: Option[NonNegativeInt] = None,
    maxProperties: Option[NonNegativeInt] = None,
    required: Option[Set[String]] = None,
    dependentRequired: Option[Map[String, Set[String]]] = None,
    // =========================================================================
    // Format Vocabulary
    // =========================================================================
    format: Option[String] = None,
    // =========================================================================
    // Content Vocabulary
    // =========================================================================
    contentEncoding: Option[String] = None,
    contentMediaType: Option[String] = None,
    contentSchema: Option[JsonSchema] = None,
    // =========================================================================
    // Meta-Data Vocabulary
    // =========================================================================
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[Json] = None,
    deprecated: Option[scala.Boolean] = None,
    readOnly: Option[scala.Boolean] = None,
    writeOnly: Option[scala.Boolean] = None,
    examples: Option[::[Json]] = None,
    // =========================================================================
    // Extensions
    // =========================================================================
    /** Unrecognized keywords for round-trip fidelity and vendor extensions. */
    extensions: Map[String, Json] = Map.empty
  ) extends JsonSchema {

    override def toJson: Json = {
      val fields = Chunk.newBuilder[(String, Json)]

      // Core vocabulary
      $id.foreach(v => fields += ("$id" -> Json.String(v.value)))
      $schema.foreach(v => fields += ("$schema" -> Json.String(v.toString)))
      $anchor.foreach(v => fields += ("$anchor" -> Json.String(v.value)))
      $dynamicAnchor.foreach(v => fields += ("$dynamicAnchor" -> Json.String(v.value)))
      $ref.foreach(v => fields += ("$ref" -> Json.String(v.value)))
      $dynamicRef.foreach(v => fields += ("$dynamicRef" -> Json.String(v.value)))
      $vocabulary.foreach { v =>
        val vocabObj = Json.Object(Chunk.from(v.map { case (uri, req) => (uri.toString, Json.Boolean(req)) }))
        fields += ("$vocabulary" -> vocabObj)
      }
      $defs.foreach { d =>
        val defsObj = Json.Object(Chunk.from(d.map { case (name, schema) => (name, schema.toJson) }))
        fields += ("$defs" -> defsObj)
      }
      $comment.foreach(v => fields += ("$comment" -> Json.String(v)))

      // Applicator vocabulary (Composition)
      allOf.foreach(v => fields += ("allOf" -> Json.Array(v.map(_.toJson).toVector)))
      anyOf.foreach(v => fields += ("anyOf" -> Json.Array(v.map(_.toJson).toVector)))
      oneOf.foreach(v => fields += ("oneOf" -> Json.Array(v.map(_.toJson).toVector)))
      not.foreach(v => fields += ("not" -> v.toJson))

      // Applicator vocabulary (Conditional)
      `if`.foreach(v => fields += ("if" -> v.toJson))
      `then`.foreach(v => fields += ("then" -> v.toJson))
      `else`.foreach(v => fields += ("else" -> v.toJson))

      // Applicator vocabulary (Object)
      properties.foreach { p =>
        val propsObj = Json.Object(Chunk.from(p.map { case (name, schema) => (name, schema.toJson) }))
        fields += ("properties" -> propsObj)
      }
      patternProperties.foreach { p =>
        val propsObj = Json.Object(Chunk.from(p.map { case (pattern, schema) => (pattern.value, schema.toJson) }))
        fields += ("patternProperties" -> propsObj)
      }
      additionalProperties.foreach(v => fields += ("additionalProperties" -> v.toJson))
      propertyNames.foreach(v => fields += ("propertyNames" -> v.toJson))
      dependentSchemas.foreach { d =>
        val depsObj = Json.Object(Chunk.from(d.map { case (name, schema) => (name, schema.toJson) }))
        fields += ("dependentSchemas" -> depsObj)
      }

      // Applicator vocabulary (Array)
      prefixItems.foreach(v => fields += ("prefixItems" -> Json.Array(v.map(_.toJson).toVector)))
      items.foreach(v => fields += ("items" -> v.toJson))
      contains.foreach(v => fields += ("contains" -> v.toJson))

      // Unevaluated vocabulary
      unevaluatedProperties.foreach(v => fields += ("unevaluatedProperties" -> v.toJson))
      unevaluatedItems.foreach(v => fields += ("unevaluatedItems" -> v.toJson))

      // Validation vocabulary (Type)
      `type`.foreach(v => fields += ("type" -> v.toJson))
      `enum`.foreach(v => fields += ("enum" -> Json.Array(v.toVector)))
      const.foreach(v => fields += ("const" -> v))

      // Validation vocabulary (Numeric)
      multipleOf.foreach(v => fields += ("multipleOf" -> Json.Number(v.value.toString)))
      maximum.foreach(v => fields += ("maximum" -> Json.Number(v.toString)))
      exclusiveMaximum.foreach(v => fields += ("exclusiveMaximum" -> Json.Number(v.toString)))
      minimum.foreach(v => fields += ("minimum" -> Json.Number(v.toString)))
      exclusiveMinimum.foreach(v => fields += ("exclusiveMinimum" -> Json.Number(v.toString)))

      // Validation vocabulary (String)
      minLength.foreach(v => fields += ("minLength" -> Json.Number(v.value.toString)))
      maxLength.foreach(v => fields += ("maxLength" -> Json.Number(v.value.toString)))
      pattern.foreach(v => fields += ("pattern" -> Json.String(v.value)))

      // Validation vocabulary (Array)
      minItems.foreach(v => fields += ("minItems" -> Json.Number(v.value.toString)))
      maxItems.foreach(v => fields += ("maxItems" -> Json.Number(v.value.toString)))
      uniqueItems.foreach(v => fields += ("uniqueItems" -> Json.Boolean(v)))
      minContains.foreach(v => fields += ("minContains" -> Json.Number(v.value.toString)))
      maxContains.foreach(v => fields += ("maxContains" -> Json.Number(v.value.toString)))

      // Validation vocabulary (Object)
      minProperties.foreach(v => fields += ("minProperties" -> Json.Number(v.value.toString)))
      maxProperties.foreach(v => fields += ("maxProperties" -> Json.Number(v.value.toString)))
      required.foreach(v => fields += ("required" -> Json.Array(v.map(Json.String(_)).toVector)))
      dependentRequired.foreach { d =>
        val depsObj = Json.Object(Chunk.from(d.map { case (name, reqs) =>
          (name, Json.Array(reqs.map(Json.String(_)).toVector))
        }))
        fields += ("dependentRequired" -> depsObj)
      }

      // Format vocabulary
      format.foreach(v => fields += ("format" -> Json.String(v)))

      // Content vocabulary
      contentEncoding.foreach(v => fields += ("contentEncoding" -> Json.String(v)))
      contentMediaType.foreach(v => fields += ("contentMediaType" -> Json.String(v)))
      contentSchema.foreach(v => fields += ("contentSchema" -> v.toJson))

      // Meta-Data vocabulary
      title.foreach(v => fields += ("title" -> Json.String(v)))
      description.foreach(v => fields += ("description" -> Json.String(v)))
      default.foreach(v => fields += ("default" -> v))
      deprecated.foreach(v => fields += ("deprecated" -> Json.Boolean(v)))
      readOnly.foreach(v => fields += ("readOnly" -> Json.Boolean(v)))
      writeOnly.foreach(v => fields += ("writeOnly" -> Json.Boolean(v)))
      examples.foreach(v => fields += ("examples" -> Json.Array(v.toVector)))

      // Extensions
      extensions.foreach { case (k, v) => fields += (k -> v) }

      Json.Object(fields.result())
    }

    override def check(json: Json): Option[SchemaError] = {
      var errors: List[SchemaError.Single] = Nil

      def addError(trace: List[DynamicOptic.Node], message: String): Unit =
        errors = SchemaError.expectationMismatch(trace, message).errors.head :: errors

      def checkInner(j: Json, trace: List[DynamicOptic.Node]): Unit = {
        // Handle $ref first (short-circuit if present)
        $ref.foreach { refUri =>
          if (refUri.value.startsWith("#/$defs/")) {
            val defName = refUri.value.substring(8)
            $defs.flatMap(_.get(defName)) match {
              case Some(refSchema) =>
                refSchema.check(j).foreach(e => errors = e.errors.toList ++ errors)
              case None =>
                addError(trace, s"Cannot resolve $$ref: ${refUri.value}")
            }
          } else {
            addError(trace, s"Unsupported $$ref format: ${refUri.value}")
          }
        }

        // Type validation
        `type`.foreach { schemaType =>
          val typeMatches = j match {
            case Json.Null       => schemaType.contains(JsonType.Null)
            case _: Json.Boolean => schemaType.contains(JsonType.Boolean)
            case _: Json.String  => schemaType.contains(JsonType.String)
            case n: Json.Number  =>
              val isInt = n.numberValue.exists(bd => bd.isWhole)
              schemaType.contains(JsonType.Number) || (isInt && schemaType.contains(JsonType.Integer))
            case _: Json.Array  => schemaType.contains(JsonType.Array)
            case _: Json.Object => schemaType.contains(JsonType.Object)
          }
          if (!typeMatches) {
            val expected = schemaType match {
              case SchemaType.Single(t) => t.toJsonString
              case SchemaType.Union(ts) => ts.map(_.toJsonString).mkString(" or ")
            }
            addError(trace, s"Expected type $expected")
          }
        }

        // Enum validation
        `enum`.foreach { values =>
          if (!values.exists(_ == j)) {
            addError(trace, s"Value not in enum: ${values.map(_.print).mkString(", ")}")
          }
        }

        // Const validation
        const.foreach { constValue =>
          if (constValue != j) {
            addError(trace, s"Expected const value: ${constValue.print}")
          }
        }

        // Numeric validations
        j match {
          case n: Json.Number =>
            n.numberValue.foreach { value =>
              minimum.foreach { min =>
                if (value < min) addError(trace, s"Value $value is less than minimum $min")
              }
              maximum.foreach { max =>
                if (value > max) addError(trace, s"Value $value is greater than maximum $max")
              }
              exclusiveMinimum.foreach { min =>
                if (value <= min) addError(trace, s"Value $value is not greater than exclusiveMinimum $min")
              }
              exclusiveMaximum.foreach { max =>
                if (value >= max) addError(trace, s"Value $value is not less than exclusiveMaximum $max")
              }
              multipleOf.foreach { m =>
                if (value % m.value != 0) addError(trace, s"Value $value is not a multiple of ${m.value}")
              }
            }
          case _ => ()
        }

        // String validations
        j match {
          case s: Json.String =>
            val len = s.value.length
            minLength.foreach { min =>
              if (len < min.value) addError(trace, s"String length $len is less than minLength ${min.value}")
            }
            maxLength.foreach { max =>
              if (len > max.value) addError(trace, s"String length $len is greater than maxLength ${max.value}")
            }
            pattern.foreach { p =>
              p.compiled match {
                case Right(regex) =>
                  if (!regex.matcher(s.value).find()) {
                    addError(trace, s"String does not match pattern: ${p.value}")
                  }
                case Left(_) => () // Invalid pattern - skip validation
              }
            }
          case _ => ()
        }

        // Array validations
        j match {
          case a: Json.Array =>
            val len = a.value.length
            minItems.foreach { min =>
              if (len < min.value) addError(trace, s"Array length $len is less than minItems ${min.value}")
            }
            maxItems.foreach { max =>
              if (len > max.value) addError(trace, s"Array length $len is greater than maxItems ${max.value}")
            }
            uniqueItems.foreach { unique =>
              if (unique && a.value.distinct.length != len) {
                addError(trace, "Array items are not unique")
              }
            }

            prefixItems.foreach { schemas =>
              schemas.zipWithIndex.foreach { case (schema, idx) =>
                if (idx < a.value.length) {
                  schema.check(a.value(idx)).foreach(e => errors = e.errors.toList ++ errors)
                }
              }
            }

            items.foreach { itemSchema =>
              val startIdx = prefixItems.map(_.length).getOrElse(0)
              a.value.zipWithIndex.drop(startIdx).foreach { case (item, _) =>
                itemSchema.check(item).foreach(e => errors = e.errors.toList ++ errors)
              }
            }

            // Validate contains
            contains.foreach { containsSchema =>
              val matchCount = a.value.count(item => containsSchema.check(item).isEmpty)
              val minC       = minContains.map(_.value).getOrElse(1)
              val maxC       = maxContains.map(_.value)

              if (matchCount < minC) {
                addError(trace, s"Array must contain at least $minC matching items, found $matchCount")
              }
              maxC.foreach { max =>
                if (matchCount > max) {
                  addError(trace, s"Array must contain at most $max matching items, found $matchCount")
                }
              }
            }

          case _ => ()
        }

        // Object validations
        j match {
          case obj: Json.Object =>
            val fieldMap  = obj.value.toMap
            val fieldKeys = fieldMap.keySet

            // Required validation
            required.foreach { reqs =>
              reqs.foreach { req =>
                if (!fieldKeys.contains(req)) {
                  addError(trace, s"Missing required property: $req")
                }
              }
            }

            // Min/max properties
            minProperties.foreach { min =>
              if (fieldKeys.size < min.value) {
                addError(trace, s"Object has ${fieldKeys.size} properties, minimum is ${min.value}")
              }
            }
            maxProperties.foreach { max =>
              if (fieldKeys.size > max.value) {
                addError(trace, s"Object has ${fieldKeys.size} properties, maximum is ${max.value}")
              }
            }

            // Property names validation
            propertyNames.foreach { nameSchema =>
              fieldKeys.foreach { key =>
                nameSchema.check(Json.String(key)).foreach { e =>
                  addError(trace, s"Property name '$key' does not match propertyNames schema: ${e.message}")
                }
              }
            }

            // Track which properties are evaluated
            var evaluatedProps = Set.empty[String]

            properties.foreach { props =>
              props.foreach { case (propName, propSchema) =>
                fieldMap.get(propName).foreach { propValue =>
                  evaluatedProps += propName
                  propSchema.check(propValue).foreach(e => errors = e.errors.toList ++ errors)
                }
              }
            }

            patternProperties.foreach { patterns =>
              patterns.foreach { case (pattern, propSchema) =>
                pattern.compiled.foreach { regex =>
                  fieldKeys.foreach { key =>
                    if (regex.matcher(key).find()) {
                      evaluatedProps += key
                      fieldMap.get(key).foreach { propValue =>
                        propSchema.check(propValue).foreach(e => errors = e.errors.toList ++ errors)
                      }
                    }
                  }
                }
              }
            }

            additionalProperties.foreach { addlSchema =>
              val additionalKeys = fieldKeys -- evaluatedProps --
                properties.map(_.keySet).getOrElse(Set.empty)
              additionalKeys.foreach { key =>
                fieldMap.get(key).foreach { propValue =>
                  addlSchema.check(propValue).foreach(e => errors = e.errors.toList ++ errors)
                }
              }
            }

            // Dependent required validation
            dependentRequired.foreach { deps =>
              deps.foreach { case (propName, requiredProps) =>
                if (fieldKeys.contains(propName)) {
                  requiredProps.foreach { req =>
                    if (!fieldKeys.contains(req)) {
                      addError(trace, s"Property '$propName' requires '$req' to be present")
                    }
                  }
                }
              }
            }

            // Dependent schemas validation
            dependentSchemas.foreach { deps =>
              deps.foreach { case (propName, depSchema) =>
                if (fieldKeys.contains(propName)) {
                  depSchema.check(obj).foreach(e => errors = e.errors.toList ++ errors)
                }
              }
            }

          case _ => ()
        }

        // Composition validations
        allOf.foreach { schemas =>
          schemas.foreach { schema =>
            schema.check(j).foreach(e => errors = e.errors.toList ++ errors)
          }
        }

        anyOf.foreach { schemas =>
          val anyValid = schemas.exists(_.check(j).isEmpty)
          if (!anyValid) {
            addError(trace, "Value does not match any schema in anyOf")
          }
        }

        oneOf.foreach { schemas =>
          val validCount = schemas.count(_.check(j).isEmpty)
          if (validCount == 0) {
            addError(trace, "Value does not match any schema in oneOf")
          } else if (validCount > 1) {
            addError(trace, s"Value matches $validCount schemas in oneOf, expected exactly 1")
          }
        }

        not.foreach { notSchema =>
          if (notSchema.check(j).isEmpty) {
            addError(trace, "Value should not match the 'not' schema")
          }
        }

        // Conditional validation (if/then/else)
        `if`.foreach { ifSchema =>
          val ifValid = ifSchema.check(j).isEmpty
          if (ifValid) {
            `then`.foreach { thenSchema =>
              thenSchema.check(j).foreach(e => errors = e.errors.toList ++ errors)
            }
          } else {
            `else`.foreach { elseSchema =>
              elseSchema.check(j).foreach(e => errors = e.errors.toList ++ errors)
            }
          }
        }
      }

      checkInner(json, Nil)

      if (errors.isEmpty) None
      else {
        val nonEmpty = new ::(errors.head, errors.tail)
        Some(new SchemaError(nonEmpty))
      }
    }
  }

  object SchemaObject {
    val empty: SchemaObject = SchemaObject()
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  def ofType(t: JsonType): JsonSchema =
    SchemaObject(`type` = Some(SchemaType.Single(t)))

  def string(
    minLength: Option[NonNegativeInt] = None,
    maxLength: Option[NonNegativeInt] = None,
    pattern: Option[RegexPattern] = None,
    format: Option[String] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.String)),
    minLength = minLength,
    maxLength = maxLength,
    pattern = pattern,
    format = format
  )

  def number(
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    multipleOf: Option[PositiveNumber] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Number)),
    minimum = minimum,
    maximum = maximum,
    exclusiveMinimum = exclusiveMinimum,
    exclusiveMaximum = exclusiveMaximum,
    multipleOf = multipleOf
  )

  def integer(
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    multipleOf: Option[PositiveNumber] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Integer)),
    minimum = minimum,
    maximum = maximum,
    exclusiveMinimum = exclusiveMinimum,
    exclusiveMaximum = exclusiveMaximum,
    multipleOf = multipleOf
  )

  def array(
    items: Option[JsonSchema] = None,
    prefixItems: Option[::[JsonSchema]] = None,
    minItems: Option[NonNegativeInt] = None,
    maxItems: Option[NonNegativeInt] = None,
    uniqueItems: Option[scala.Boolean] = None,
    contains: Option[JsonSchema] = None,
    minContains: Option[NonNegativeInt] = None,
    maxContains: Option[NonNegativeInt] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Array)),
    items = items,
    prefixItems = prefixItems,
    minItems = minItems,
    maxItems = maxItems,
    uniqueItems = uniqueItems,
    contains = contains,
    minContains = minContains,
    maxContains = maxContains
  )

  def `object`(
    properties: Option[Map[String, JsonSchema]] = None,
    required: Option[Set[String]] = None,
    additionalProperties: Option[JsonSchema] = None,
    patternProperties: Option[Map[RegexPattern, JsonSchema]] = None,
    propertyNames: Option[JsonSchema] = None,
    minProperties: Option[NonNegativeInt] = None,
    maxProperties: Option[NonNegativeInt] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Object)),
    properties = properties,
    required = required,
    additionalProperties = additionalProperties,
    patternProperties = patternProperties,
    propertyNames = propertyNames,
    minProperties = minProperties,
    maxProperties = maxProperties
  )

  def enumOf(values: ::[Json]): JsonSchema =
    SchemaObject(`enum` = Some(values))

  def enumOfStrings(values: ::[String]): JsonSchema =
    SchemaObject(`enum` = Some(new ::(Json.String(values.head), values.tail.map(Json.String(_)))))

  def constOf(value: Json): JsonSchema =
    SchemaObject(const = Some(value))

  def ref(uri: UriReference): JsonSchema =
    SchemaObject($ref = Some(uri))

  def refString(uri: String): JsonSchema =
    SchemaObject($ref = Some(UriReference(uri)))

  val `null`: JsonSchema  = ofType(JsonType.Null)
  val boolean: JsonSchema = ofType(JsonType.Boolean)

  // ===========================================================================
  // Parsing Helpers
  // ===========================================================================

  private def parseSchemaObject(obj: Json.Object): Either[SchemaError, SchemaObject] = {
    val fieldMap = obj.value.toMap

    def getString(key: String): Option[String] =
      fieldMap.get(key).collect { case s: Json.String => s.value }

    def getBoolean(key: String): Option[scala.Boolean] =
      fieldMap.get(key).collect { case b: Json.Boolean => b.value }

    def getNumber(key: String): Option[BigDecimal] =
      fieldMap.get(key).collect { case n: Json.Number => n.numberValue }.flatten

    def getNonNegativeInt(key: String): Option[NonNegativeInt] =
      getNumber(key).flatMap(n => NonNegativeInt(n.toInt))

    def getPositiveNumber(key: String): Option[PositiveNumber] =
      getNumber(key).flatMap(PositiveNumber(_))

    def getSchema(key: String): Either[SchemaError, Option[JsonSchema]] =
      fieldMap.get(key) match {
        case Some(json) => fromJson(json).map(Some(_))
        case None       => Right(None)
      }

    def getSchemaList(key: String): Either[SchemaError, Option[::[JsonSchema]]] =
      fieldMap.get(key) match {
        case Some(arr: Json.Array) =>
          val results = arr.value.map(fromJson)
          val errs    = results.collect { case Left(e) => e }
          if (errs.nonEmpty) Left(errs.reduce(_ ++ _))
          else {
            val schemas = results.collect { case Right(s) => s }
            schemas.toList match {
              case head :: tail => Right(Some(new ::(head, tail)))
              case Nil          => Right(None)
            }
          }
        case Some(_) =>
          Left(SchemaError.expectationMismatch(Nil, s"Expected array for $key"))
        case None => Right(None)
      }

    def getSchemaMap(key: String): Either[SchemaError, Option[Map[String, JsonSchema]]] =
      fieldMap.get(key) match {
        case Some(o: Json.Object) =>
          val results = o.value.map { case (k, v) => fromJson(v).map(s => k -> s) }
          val errs    = results.collect { case Left(e) => e }
          if (errs.nonEmpty) Left(errs.reduce(_ ++ _))
          else Right(Some(results.collect { case Right(kv) => kv }.toMap))
        case Some(_) =>
          Left(SchemaError.expectationMismatch(Nil, s"Expected object for $key"))
        case None => Right(None)
      }

    def getStringSet(key: String): Option[Set[String]] =
      fieldMap.get(key).collect { case arr: Json.Array =>
        arr.value.collect { case s: Json.String => s.value }.toSet
      }

    def getJsonList(key: String): Option[::[Json]] =
      fieldMap
        .get(key)
        .collect { case arr: Json.Array =>
          arr.value.toList match {
            case head :: tail => Some(new ::(head, tail))
            case Nil          => None
          }
        }
        .flatten

    // Parse all fields
    for {
      allOfSchemas           <- getSchemaList("allOf")
      anyOfSchemas           <- getSchemaList("anyOf")
      oneOfSchemas           <- getSchemaList("oneOf")
      notSchema              <- getSchema("not")
      ifSchema               <- getSchema("if")
      thenSchema             <- getSchema("then")
      elseSchema             <- getSchema("else")
      propertiesSchemas      <- getSchemaMap("properties")
      additionalPropsSchema  <- getSchema("additionalProperties")
      propertyNamesSchema    <- getSchema("propertyNames")
      dependentSchemasMap    <- getSchemaMap("dependentSchemas")
      prefixItemsSchemas     <- getSchemaList("prefixItems")
      itemsSchema            <- getSchema("items")
      containsSchema         <- getSchema("contains")
      unevaluatedPropsSchema <- getSchema("unevaluatedProperties")
      unevaluatedItemsSchema <- getSchema("unevaluatedItems")
      defsSchemas            <- getSchemaMap("$defs")
      contentSchemaVal       <- getSchema("contentSchema")
    } yield {
      // Parse pattern properties specially
      val patternPropsOpt: Option[Map[RegexPattern, JsonSchema]] = fieldMap.get("patternProperties") match {
        case Some(o: Json.Object) =>
          val parsed = o.value.flatMap { case (pattern, json) =>
            fromJson(json).toOption.map(schema => RegexPattern.unsafe(pattern) -> schema)
          }
          if (parsed.nonEmpty) Some(parsed.toMap) else None
        case _ => None
      }

      // Parse dependent required
      val dependentRequiredOpt: Option[Map[String, Set[String]]] = fieldMap.get("dependentRequired") match {
        case Some(o: Json.Object) =>
          val parsed = o.value.map { case (k, v) =>
            v match {
              case arr: Json.Array => k -> arr.value.collect { case s: Json.String => s.value }.toSet
              case _               => k -> Set.empty[String]
            }
          }
          if (parsed.nonEmpty) Some(parsed.toMap) else None
        case _ => None
      }

      // Parse type
      val typeOpt = fieldMap.get("type").flatMap(j => SchemaType.fromJson(j).toOption)

      // Collect unknown extensions
      val knownKeys = Set(
        "$id",
        "$schema",
        "$anchor",
        "$dynamicAnchor",
        "$ref",
        "$dynamicRef",
        "$vocabulary",
        "$defs",
        "$comment",
        "allOf",
        "anyOf",
        "oneOf",
        "not",
        "if",
        "then",
        "else",
        "properties",
        "patternProperties",
        "additionalProperties",
        "propertyNames",
        "dependentSchemas",
        "prefixItems",
        "items",
        "contains",
        "unevaluatedProperties",
        "unevaluatedItems",
        "type",
        "enum",
        "const",
        "multipleOf",
        "maximum",
        "exclusiveMaximum",
        "minimum",
        "exclusiveMinimum",
        "minLength",
        "maxLength",
        "pattern",
        "minItems",
        "maxItems",
        "uniqueItems",
        "minContains",
        "maxContains",
        "minProperties",
        "maxProperties",
        "required",
        "dependentRequired",
        "format",
        "contentEncoding",
        "contentMediaType",
        "contentSchema",
        "title",
        "description",
        "default",
        "deprecated",
        "readOnly",
        "writeOnly",
        "examples"
      )
      val extensions = fieldMap.filterNot { case (k, _) => knownKeys.contains(k) }

      SchemaObject(
        $id = getString("$id").map(UriReference(_)),
        $schema = getString("$schema").flatMap(s =>
          try Some(new URI(s))
          catch { case _: Exception => None }
        ),
        $anchor = getString("$anchor").map(Anchor(_)),
        $dynamicAnchor = getString("$dynamicAnchor").map(Anchor(_)),
        $ref = getString("$ref").map(UriReference(_)),
        $dynamicRef = getString("$dynamicRef").map(UriReference(_)),
        $vocabulary = None, // Complex parsing, skip for now
        $defs = defsSchemas,
        $comment = getString("$comment"),
        allOf = allOfSchemas,
        anyOf = anyOfSchemas,
        oneOf = oneOfSchemas,
        not = notSchema,
        `if` = ifSchema,
        `then` = thenSchema,
        `else` = elseSchema,
        properties = propertiesSchemas,
        patternProperties = patternPropsOpt,
        additionalProperties = additionalPropsSchema,
        propertyNames = propertyNamesSchema,
        dependentSchemas = dependentSchemasMap,
        prefixItems = prefixItemsSchemas,
        items = itemsSchema,
        contains = containsSchema,
        unevaluatedProperties = unevaluatedPropsSchema,
        unevaluatedItems = unevaluatedItemsSchema,
        `type` = typeOpt,
        `enum` = getJsonList("enum"),
        const = fieldMap.get("const"),
        multipleOf = getPositiveNumber("multipleOf"),
        maximum = getNumber("maximum"),
        exclusiveMaximum = getNumber("exclusiveMaximum"),
        minimum = getNumber("minimum"),
        exclusiveMinimum = getNumber("exclusiveMinimum"),
        minLength = getNonNegativeInt("minLength"),
        maxLength = getNonNegativeInt("maxLength"),
        pattern = getString("pattern").map(RegexPattern.unsafe),
        minItems = getNonNegativeInt("minItems"),
        maxItems = getNonNegativeInt("maxItems"),
        uniqueItems = getBoolean("uniqueItems"),
        minContains = getNonNegativeInt("minContains"),
        maxContains = getNonNegativeInt("maxContains"),
        minProperties = getNonNegativeInt("minProperties"),
        maxProperties = getNonNegativeInt("maxProperties"),
        required = getStringSet("required"),
        dependentRequired = dependentRequiredOpt,
        format = getString("format"),
        contentEncoding = getString("contentEncoding"),
        contentMediaType = getString("contentMediaType"),
        contentSchema = contentSchemaVal,
        title = getString("title"),
        description = getString("description"),
        default = fieldMap.get("default"),
        deprecated = getBoolean("deprecated"),
        readOnly = getBoolean("readOnly"),
        writeOnly = getBoolean("writeOnly"),
        examples = getJsonList("examples"),
        extensions = extensions
      )
    }
  }
}
