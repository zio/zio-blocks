package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap}
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
final case class NonNegativeInt private[json] (value: Int) extends AnyVal

object NonNegativeInt extends NonNegativeIntCompanionVersionSpecific {

  /** Creates a NonNegativeInt from a runtime value, returning Option. */
  def apply(n: Int): Option[NonNegativeInt] =
    if (n >= 0) new Some(new NonNegativeInt(n)) else None

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
final case class PositiveNumber private[json] (value: BigDecimal) extends AnyVal

object PositiveNumber extends PositiveNumberCompanionVersionSpecific {

  /**
   * Creates a PositiveNumber from a runtime BigDecimal value, returning Option.
   */
  def apply(n: BigDecimal): Option[PositiveNumber] =
    if (n > 0) new Some(new PositiveNumber(n)) else None

  def unsafe(n: BigDecimal): PositiveNumber = {
    require(n > 0, s"PositiveNumber requires n > 0, got $n")
    new PositiveNumber(n)
  }

  def fromInt(n: Int): Option[PositiveNumber] =
    if (n > 0) new Some(new PositiveNumber(BigDecimal(n))) else None
}

/**
 * ECMA-262 regular expression pattern.
 */
final case class RegexPattern(value: String) extends AnyVal {

  /** Compiles the pattern to a Java Pattern for validation. */
  def compiled: Either[String, Pattern] =
    try new Right(Pattern.compile(value))
    catch {
      case e: PatternSyntaxException => new Left(e.getMessage)
    }
}

object RegexPattern {

  /** Validates and creates a RegexPattern. */
  def apply(value: String): Either[String, RegexPattern] =
    try {
      Pattern.compile(value)
      new Right(new RegexPattern(value))
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
    try new Right(base.resolve(value))
    catch {
      case e if NonFatal(e) => new Left(e.getMessage)
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
// Format Validation
// =============================================================================

/**
 * Validates string values against JSON Schema format specifications.
 *
 * Per JSON Schema 2020-12, the `format` keyword is an annotation by default.
 * This object provides optional validation for common formats that can be
 * enabled when needed.
 *
 * Supported formats:
 *   - date-time: RFC 3339 date-time (e.g., "2024-01-15T10:30:00Z")
 *   - date: RFC 3339 full-date (e.g., "2024-01-15")
 *   - time: RFC 3339 full-time (e.g., "10:30:00Z")
 *   - email: Simplified email validation
 *   - uuid: RFC 4122 UUID
 *   - uri: RFC 3986 URI
 *   - uri-reference: RFC 3986 URI-reference
 *   - ipv4: IPv4 address
 *   - ipv6: IPv6 address
 *   - hostname: RFC 1123 hostname
 *   - regex: ECMA-262 regular expression
 */
private[json] object FormatValidator {

  /**
   * Validates a string against a format specification. Returns None if valid,
   * Some(error) if invalid.
   */
  def validate(format: String, value: String): Option[String] =
    format match {
      case "date-time"     => validateDateTime(value)
      case "date"          => validateDate(value)
      case "time"          => validateTime(value)
      case "email"         => validateEmail(value)
      case "uuid"          => validateUuid(value)
      case "uri"           => validateUri(value)
      case "uri-reference" => validateUriReference(value)
      case "ipv4"          => validateIpv4(value)
      case "ipv6"          => validateIpv6(value)
      case "hostname"      => validateHostname(value)
      case "regex"         => validateRegex(value)
      case "duration"      => validateDuration(value)
      case "json-pointer"  => validateJsonPointer(value)
      case _               => None // Unknown formats pass validation (annotation-only)
    }

  private[this] val dateTimePattern: Pattern =
    Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$")

  private[this] val datePattern: Pattern =
    Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")

  private[this] val timePattern: Pattern =
    Pattern.compile("^\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})?$")

  private[this] val emailPattern: Pattern =
    Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

  private[this] val uuidPattern: Pattern =
    Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  private[this] val ipv4Pattern: Pattern =
    Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")

  private[this] val ipv6Pattern: Pattern =
    Pattern.compile(
      "^(" +
        "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
        "([0-9a-fA-F]{1,4}:){1,7}:|" +
        "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
        "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
        "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
        "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
        "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
        "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
        ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
        "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|" +
        "::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|" +
        "([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)" +
        ")$"
    )

  private[this] val hostnamePattern: Pattern =
    Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")

  private[this] val durationPattern: Pattern =
    Pattern.compile("^P(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?$")

  private[this] val jsonPointerPattern: Pattern =
    Pattern.compile("^(/([^~/]|~0|~1)*)*$")

  private[this] def validateDateTime(value: String): Option[String] =
    if (dateTimePattern.matcher(value).matches()) validateDateTimeSemantics(value)
    else new Some(s"String '$value' is not a valid date-time (RFC 3339)")

  private[this] def validateDateTimeSemantics(value: String): Option[String] =
    validateDateSemantics(value.substring(0, 10))

  private[this] def validateDate(value: String): Option[String] =
    if (datePattern.matcher(value).matches()) validateDateSemantics(value)
    else new Some(s"String '$value' is not a valid date (RFC 3339)")

  private[this] def validateDateSemantics(value: String): Option[String] =
    try {
      val year  = value.substring(0, 4).toInt
      val month = value.substring(5, 7).toInt
      val day   = value.substring(8, 10).toInt
      if (month < 1 || month > 12) new Some(s"Invalid month $month in date '$value'")
      else {
        val maxDays = month match {
          case 2                           => if (isLeapYear(year)) 29 else 28
          case 4 | 6 | 9 | 11              => 30
          case 1 | 3 | 5 | 7 | 8 | 10 | 12 => 31
          case _                           => 0
        }
        if (day < 1 || day > maxDays) new Some(s"Invalid day $day for month $month in date '$value'")
        else None
      }
    } catch {
      case _: NumberFormatException => new Some(s"String '$value' is not a valid date")
    }

  private[this] def isLeapYear(year: Int): scala.Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

  private[this] def validateTime(value: String): Option[String] =
    if (timePattern.matcher(value).matches()) validateTimeSemantics(value)
    else new Some(s"String '$value' is not a valid time (RFC 3339)")

  private[this] def validateTimeSemantics(value: String): Option[String] =
    try {
      val hour   = value.substring(0, 2).toInt
      val minute = value.substring(3, 5).toInt
      val second = value.substring(6, 8).toInt
      if (hour < 0 || hour > 23) new Some(s"Invalid hour $hour in time '$value'")
      else if (minute < 0 || minute > 59) new Some(s"Invalid minute $minute in time '$value'")
      else if (second < 0 || second > 60) { // 60 allowed for leap seconds
        new Some(s"Invalid second $second in time '$value'")
      } else None
    } catch {
      case _: NumberFormatException => new Some(s"String '$value' is not a valid time")
    }

  private[this] def validateEmail(value: String): Option[String] =
    if (emailPattern.matcher(value).matches() && value.length <= 254) None
    else new Some(s"String '$value' is not a valid email address")

  private[this] def validateUuid(value: String): Option[String] =
    if (uuidPattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid UUID (RFC 4122)")

  private[this] def validateUri(value: String): Option[String] =
    try {
      val uri = new URI(value)
      if (uri.getScheme == null) new Some(s"String '$value' is not a valid URI (missing scheme)")
      else None
    } catch {
      case e if NonFatal(e) => Some(s"String '$value' is not a valid URI: ${e.getMessage}")
    }

  private[this] def validateUriReference(value: String): Option[String] =
    try {
      new URI(value)
      None
    } catch {
      case e if NonFatal(e) => new Some(s"String '$value' is not a valid URI-reference: ${e.getMessage}")
    }

  private[this] def validateIpv4(value: String): Option[String] =
    if (ipv4Pattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid IPv4 address")

  private[this] def validateIpv6(value: String): Option[String] =
    if (ipv6Pattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid IPv6 address")

  private[this] def validateHostname(value: String): Option[String] =
    if (hostnamePattern.matcher(value).matches() && value.length <= 253) None
    else new Some(s"String '$value' is not a valid hostname (RFC 1123)")

  private[this] def validateRegex(value: String): Option[String] =
    try {
      Pattern.compile(value)
      None
    } catch {
      case e: PatternSyntaxException => new Some(s"String '$value' is not a valid regex: ${e.getMessage}")
    }

  private[this] def validateDuration(value: String): Option[String] =
    if (durationPattern.matcher(value).matches() && value != "P" && value != "PT") None
    else new Some(s"String '$value' is not a valid ISO 8601 duration")

  private[this] def validateJsonPointer(value: String): Option[String] =
    if (value.isEmpty || jsonPointerPattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid JSON Pointer (RFC 6901)")
}

/**
 * Options for JSON Schema validation behavior.
 *
 * @param validateFormats
 *   When true, the `format` keyword is validated (format assertion vocabulary).
 *   When false, `format` is treated as an annotation only (default per JSON
 *   Schema 2020-12).
 */
final case class ValidationOptions(validateFormats: scala.Boolean = true)

object ValidationOptions {
  val default: ValidationOptions         = ValidationOptions()
  val annotationOnly: ValidationOptions  = ValidationOptions(validateFormats = false)
  val formatAssertion: ValidationOptions = ValidationOptions(validateFormats = true)
}

/**
 * Result of schema validation that tracks evaluated properties and items.
 *
 * Per JSON Schema 2020-12, `unevaluatedProperties` and `unevaluatedItems` need
 * to know which properties/items were evaluated by any applicator keyword in
 * the schema tree.
 *
 * @param errors
 *   Accumulated validation errors
 * @param evaluatedProperties
 *   Set of property names that were evaluated by applicator keywords
 * @param evaluatedItems
 *   Set of array indices that were evaluated by applicator keywords
 */
final case class EvaluationResult(
  errors: List[SchemaError.Single],
  evaluatedProperties: Set[String],
  evaluatedItems: Set[Int]
) {
  def ++(other: EvaluationResult): EvaluationResult =
    EvaluationResult(
      errors ++ other.errors,
      evaluatedProperties ++ other.evaluatedProperties,
      evaluatedItems ++ other.evaluatedItems
    )

  def addError(trace: List[DynamicOptic.Node], message: String): EvaluationResult =
    copy(errors = SchemaError.expectationMismatch(trace, message).errors.head :: errors)

  def addErrors(newErrors: List[SchemaError.Single]): EvaluationResult = copy(errors = newErrors ++ errors)

  def withEvaluatedProperty(prop: String): EvaluationResult = copy(evaluatedProperties = evaluatedProperties + prop)

  def withEvaluatedProperties(props: Set[String]): EvaluationResult =
    copy(evaluatedProperties = evaluatedProperties ++ props)

  def withEvaluatedItem(idx: Int): EvaluationResult = copy(evaluatedItems = evaluatedItems + idx)

  def withEvaluatedItems(indices: Set[Int]): EvaluationResult = copy(evaluatedItems = evaluatedItems ++ indices)

  def toSchemaError: Option[SchemaError] =
    if (errors.isEmpty) None
    else new Some(new SchemaError(new ::(errors.head, errors.tail)))
}

object EvaluationResult {
  val empty: EvaluationResult = new EvaluationResult(Nil, Set.empty, Set.empty)

  def fromError(trace: List[DynamicOptic.Node], message: String): EvaluationResult =
    new EvaluationResult(
      List(SchemaError.expectationMismatch(trace, message).errors.head),
      Set.empty,
      Set.empty
    )
}

// =============================================================================
// JSON Schema Type Keyword Enumeration
// =============================================================================

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

  override def toString: String = toJson.print(WriterConfig.withIndentionStep2)

  /** Serialize this schema to its canonical JSON representation. */
  def toJson: Json

  /**
   * Validate a JSON value against this schema.
   *
   * @return
   *   None if valid, Some(error) with accumulated failures if invalid.
   */
  def check(json: Json): Option[SchemaError]

  /**
   * Validate a JSON value against this schema with custom options.
   *
   * @param json
   *   The JSON value to validate
   * @param options
   *   Validation options controlling behavior like format validation
   * @return
   *   None if valid, Some(error) with accumulated failures if invalid.
   */
  def check(json: Json, @scala.annotation.unused options: ValidationOptions): Option[SchemaError] = check(json)

  /** Returns true if the JSON value conforms to this schema. */
  def conforms(json: Json): scala.Boolean = check(json).isEmpty

  /**
   * Returns true if the JSON value conforms to this schema with custom options.
   */
  def conforms(json: Json, options: ValidationOptions): scala.Boolean = check(json, options).isEmpty

  // ===========================================================================
  // Combinators
  // ===========================================================================

  /** Combine with another schema using allOf. */
  def &&(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.False, _)                          => JsonSchema.False
    case (_, JsonSchema.False)                          => JsonSchema.False
    case (JsonSchema.True, s)                           => s
    case (s, JsonSchema.True)                           => s
    case (s1: JsonSchema.Object, s2: JsonSchema.Object) =>
      JsonSchema.Object(allOf = new Some((s1.allOf, s2.allOf) match {
        case (Some(a1), Some(a2)) => new ::(a1.head, a1.tail ++ a2)
        case (Some(a1), _)        => new ::(s2, a1)
        case (_, Some(a2))        => new ::(s1, a2)
        case _                    => new ::(s1, s2 :: Nil)
      }))
  }

  /** Combine with another schema using anyOf. */
  def ||(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _)                           => JsonSchema.True
    case (_, JsonSchema.True)                           => JsonSchema.True
    case (JsonSchema.False, s)                          => s
    case (s, JsonSchema.False)                          => s
    case (s1: JsonSchema.Object, s2: JsonSchema.Object) =>
      JsonSchema.Object(anyOf = Some((s1.anyOf, s2.anyOf) match {
        case (Some(a1), Some(a2)) => new ::(a1.head, a1.tail ++ a2)
        case (Some(a1), None)     => new ::(s2, a1)
        case (None, Some(a2))     => new ::(s1, a2)
        case (None, None)         => new ::(s1, s2 :: Nil)
      }))
  }

  /** Negate this schema. */
  def unary_! : JsonSchema = this match {
    case _: JsonSchema.True.type  => JsonSchema.False
    case _: JsonSchema.False.type => JsonSchema.True
    case s                        => new JsonSchema.Object(not = new Some(s))
  }

  /** Make this schema nullable (accepts null in addition to current types). */
  def withNullable: JsonSchema = this match {
    case _: JsonSchema.True.type  => JsonSchema.True
    case _: JsonSchema.False.type => JsonSchema.ofType(JsonSchemaType.Null)
    case s: JsonSchema.Object     =>
      s.`type` match {
        case Some(st) =>
          st match {
            case st: SchemaType.Single =>
              val t = st.value
              if (t eq JsonSchemaType.Null) s
              else s.copy(`type` = new Some(new SchemaType.Union(new ::(JsonSchemaType.Null, t :: Nil))))
            case ut: SchemaType.Union =>
              val ts = ut.values
              if (ts.contains(JsonSchemaType.Null)) s
              else s.copy(`type` = new Some(new SchemaType.Union(new ::(JsonSchemaType.Null, ts))))
          }
        case _ => new JsonSchema.Object(anyOf = new Some(new ::(JsonSchema.ofType(JsonSchemaType.Null), s :: Nil)))
      }
  }
}

object JsonSchema {

  // ===========================================================================
  // Type Aliases for Helper Types
  // ===========================================================================

  type NonNegativeInt = zio.blocks.schema.json.NonNegativeInt
  val NonNegativeInt = zio.blocks.schema.json.NonNegativeInt

  type PositiveNumber = zio.blocks.schema.json.PositiveNumber
  val PositiveNumber = zio.blocks.schema.json.PositiveNumber

  type RegexPattern = zio.blocks.schema.json.RegexPattern
  val RegexPattern = zio.blocks.schema.json.RegexPattern

  type UriReference = zio.blocks.schema.json.UriReference
  val UriReference = zio.blocks.schema.json.UriReference

  type Anchor = zio.blocks.schema.json.Anchor
  val Anchor = zio.blocks.schema.json.Anchor

  // ===========================================================================
  // Parsing
  // ===========================================================================

  /** Parse a JsonSchema from its JSON representation. */
  def fromJson(json: Json): Either[SchemaError, JsonSchema] = json match {
    case bool: Json.Boolean =>
      new Right({
        if (bool.value) True
        else False
      })
    case obj: Json.Object => parseObject(obj)
    case other            =>
      val msg = s"Expected JSON object or boolean, got ${other.getClass.getSimpleName}"
      new Left(SchemaError.expectationMismatch(Nil, msg))
  }

  /** Parse a JsonSchema from a JSON string. */
  def parse(jsonString: String): Either[SchemaError, JsonSchema] = Json.parse(jsonString) match {
    case Right(json) => fromJson(json)
    case l           => l.asInstanceOf[Either[SchemaError, JsonSchema]]
  }

  // ===========================================================================
  // Boolean Schemas
  // ===========================================================================

  /** Schema that accepts all instances. Equivalent to `{}`. */
  case object True extends JsonSchema {
    override def toJson: Json = Json.True

    override def check(json: Json): Option[SchemaError] = None
  }

  /** Schema that rejects all instances. Equivalent to `{"not": {}}`. */
  case object False extends JsonSchema {
    override def toJson: Json = Json.False

    override def check(json: Json): Option[SchemaError] =
      new Some(SchemaError.expectationMismatch(Nil, "Schema rejects all values"))
  }

  // ===========================================================================
  // Schema Object
  // ===========================================================================

  /**
   * A schema object containing keywords from JSON Schema 2020-12. All fields
   * are optional; an empty Object is equivalent to True.
   */
  final case class Object(
    // =========================================================================
    // Core Vocabulary
    // =========================================================================
    $id: Option[UriReference] = None,
    $schema: Option[URI] = None,
    $anchor: Option[Anchor] = None,
    $dynamicAnchor: Option[Anchor] = None,
    $ref: Option[UriReference] = None,
    $dynamicRef: Option[UriReference] = None,
    $vocabulary: Option[ChunkMap[URI, scala.Boolean]] = None,
    $defs: Option[ChunkMap[String, JsonSchema]] = None,
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
    properties: Option[ChunkMap[String, JsonSchema]] = None,
    patternProperties: Option[ChunkMap[RegexPattern, JsonSchema]] = None,
    additionalProperties: Option[JsonSchema] = None,
    propertyNames: Option[JsonSchema] = None,
    dependentSchemas: Option[ChunkMap[String, JsonSchema]] = None,
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
    dependentRequired: Option[ChunkMap[String, Set[String]]] = None,
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
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends JsonSchema {

    override def toJson: Json = {
      val fields = ChunkBuilder.make[(String, Json)]()

      // Core vocabulary
      $id match {
        case Some(v) => fields.addOne(("$id", new Json.String(v.value)))
        case _       =>
      }
      $schema match {
        case Some(v) => fields.addOne(("$schema", new Json.String(v.toString)))
        case _       =>
      }
      $anchor match {
        case Some(v) => fields.addOne(("$anchor", new Json.String(v.value)))
        case _       =>
      }
      $dynamicAnchor match {
        case Some(v) => fields.addOne(("$dynamicAnchor", new Json.String(v.value)))
        case _       =>
      }
      $ref match {
        case Some(v) => fields.addOne(("$ref", new Json.String(v.value)))
        case _       =>
      }
      $dynamicRef match {
        case Some(v) => fields.addOne(("$dynamicRef", new Json.String(v.value)))
        case _       =>
      }
      $vocabulary match {
        case Some(v) =>
          fields.addOne(("$vocabulary", toJsonObject[URI, Boolean](v, _.toString, Json.Boolean.apply)))
        case _ =>
      }
      $defs match {
        case Some(d) => fields.addOne(("$defs", toJsonObject[String, JsonSchema](d, identity, _.toJson)))
        case _       =>
      }
      $comment match {
        case Some(v) => fields.addOne(("$comment", new Json.String(v)))
        case _       =>
      }

      // Applicator vocabulary (Composition)
      allOf match {
        case Some(v) => fields.addOne(("allOf", new Json.Array(Chunk.from(v).map(_.toJson))))
        case _       =>
      }
      anyOf match {
        case Some(v) => fields.addOne(("anyOf", new Json.Array(Chunk.from(v).map(_.toJson))))
        case _       =>
      }
      oneOf match {
        case Some(v) => fields.addOne(("oneOf", new Json.Array(Chunk.from(v).map(_.toJson))))
        case _       =>
      }
      not match {
        case Some(v) => fields.addOne(("not", v.toJson))
        case _       =>
      }

      // Applicator vocabulary (Conditional)
      `if` match {
        case Some(v) => fields.addOne(("if", v.toJson))
        case _       =>
      }
      `then` match {
        case Some(v) => fields.addOne(("then", v.toJson))
        case _       =>
      }
      `else` match {
        case Some(v) => fields.addOne(("else", v.toJson))
        case _       =>
      }

      // Applicator vocabulary (Object)
      properties match {
        case Some(p) => fields.addOne(("properties", toJsonObject[String, JsonSchema](p, identity, _.toJson)))
        case _       =>
      }
      patternProperties match {
        case Some(p) =>
          fields.addOne(("patternProperties", toJsonObject[RegexPattern, JsonSchema](p, _.value, _.toJson)))
        case _ =>
      }
      additionalProperties match {
        case Some(v) => fields.addOne(("additionalProperties", v.toJson))
        case _       =>
      }
      propertyNames match {
        case Some(v) => fields.addOne(("propertyNames", v.toJson))
        case _       =>
      }
      dependentSchemas match {
        case Some(d) =>
          fields.addOne(("dependentSchemas", toJsonObject[String, JsonSchema](d, identity, _.toJson)))
        case _ =>
      }

      // Applicator vocabulary (Array)
      prefixItems match {
        case Some(v) => fields.addOne(("prefixItems", new Json.Array(Chunk.from(v).map(_.toJson))))
        case _       =>
      }
      items match {
        case Some(v) => fields.addOne(("items", v.toJson))
        case _       =>
      }
      contains match {
        case Some(v) => fields.addOne(("contains", v.toJson))
        case _       =>
      }

      // Unevaluated vocabulary
      unevaluatedProperties match {
        case Some(v) => fields.addOne(("unevaluatedProperties", v.toJson))
        case _       =>
      }
      unevaluatedItems match {
        case Some(v) => fields.addOne(("unevaluatedItems", v.toJson))
        case _       =>
      }

      // Validation vocabulary (Type)
      `type` match {
        case Some(v) => fields.addOne(("type", v.toJson))
        case _       =>
      }
      `enum` match {
        case Some(v) => fields.addOne(("enum", new Json.Array(Chunk.from(v))))
        case _       =>
      }
      const match {
        case Some(v) => fields.addOne(("const", v))
        case _       =>
      }

      // Validation vocabulary (Numeric)
      multipleOf match {
        case Some(v) => fields.addOne(("multipleOf", new Json.Number(v.value)))
        case _       =>
      }
      maximum match {
        case Some(v) => fields.addOne(("maximum", new Json.Number(v)))
        case _       =>
      }
      exclusiveMaximum match {
        case Some(v) => fields.addOne(("exclusiveMaximum", new Json.Number(v)))
        case _       =>
      }
      minimum match {
        case Some(v) => fields.addOne(("minimum", new Json.Number(v)))
        case _       =>
      }
      exclusiveMinimum match {
        case Some(v) => fields.addOne(("exclusiveMinimum", new Json.Number(v)))
        case _       =>
      }

      // Validation vocabulary (String)
      minLength match {
        case Some(v) => fields.addOne(("minLength", new Json.Number(v.value)))
        case _       =>
      }
      maxLength match {
        case Some(v) => fields.addOne(("maxLength", new Json.Number(v.value)))
        case _       =>
      }
      pattern match {
        case Some(v) => fields.addOne(("pattern", new Json.String(v.value)))
        case _       =>
      }

      // Validation vocabulary (Array)
      minItems match {
        case Some(v) => fields.addOne(("minItems", new Json.Number(v.value)))
        case _       =>
      }
      maxItems match {
        case Some(v) => fields.addOne(("maxItems", new Json.Number(v.value)))
        case _       =>
      }
      uniqueItems match {
        case Some(v) => fields.addOne(("uniqueItems", new Json.Boolean(v)))
        case _       =>
      }
      minContains match {
        case Some(v) => fields.addOne(("minContains", new Json.Number(v.value)))
        case _       =>
      }
      maxContains match {
        case Some(v) => fields.addOne(("maxContains", new Json.Number(v.value)))
        case _       =>
      }

      // Validation vocabulary (Object)
      minProperties match {
        case Some(v) => fields.addOne(("minProperties", new Json.Number(v.value)))
        case _       =>
      }
      maxProperties match {
        case Some(v) => fields.addOne(("maxProperties", new Json.Number(v.value)))
        case _       =>
      }
      required match {
        case Some(v) => fields.addOne(("required", new Json.Array(Chunk.from(v).map(new Json.String(_)))))
        case _       =>
      }
      dependentRequired match {
        case Some(d) =>
          fields.addOne(
            (
              "dependentRequired",
              toJsonObject[String, Set[String]](
                d,
                identity,
                vs => new Json.Array(Chunk.from(vs).map(new Json.String(_)))
              )
            )
          )
        case _ =>
      }

      // Format vocabulary
      format match {
        case Some(v) => fields.addOne(("format", new Json.String(v)))
        case _       =>
      }

      // Content vocabulary
      contentEncoding match {
        case Some(v) => fields.addOne(("contentEncoding", new Json.String(v)))
        case _       =>
      }
      contentMediaType match {
        case Some(v) => fields.addOne(("contentMediaType", new Json.String(v)))
        case _       =>
      }
      contentSchema match {
        case Some(v) => fields.addOne(("contentSchema", v.toJson))
        case _       =>
      }

      // Meta-Data vocabulary
      title match {
        case Some(v) => fields.addOne(("title", new Json.String(v)))
        case _       =>
      }
      description match {
        case Some(v) => fields.addOne(("description", new Json.String(v)))
        case _       =>
      }
      default match {
        case Some(v) => fields.addOne(("default", v))
        case _       =>
      }
      deprecated match {
        case Some(v) => fields.addOne(("deprecated", new Json.Boolean(v)))
        case _       =>
      }
      readOnly match {
        case Some(v) => fields.addOne(("readOnly", new Json.Boolean(v)))
        case _       =>
      }
      writeOnly match {
        case Some(v) => fields.addOne(("writeOnly", new Json.Boolean(v)))
        case _       =>
      }
      examples match {
        case Some(v) => fields.addOne(("examples", new Json.Array(Chunk.from(v))))
        case _       =>
      }

      // Extensions
      fields.addAll(extensions)

      new Json.Object(fields.result())
    }

    override def check(json: Json): Option[SchemaError] = check(json, ValidationOptions.default)

    override def check(json: Json, options: ValidationOptions): Option[SchemaError] =
      checkWithEvaluation(json, options, Nil).toSchemaError

    private[this] def toJsonObject[K1, V1](m: ChunkMap[K1, V1], f: K1 => String, g: V1 => Json): Json.Object =
      new Json.Object(Chunk.from(m).map(kv => (f(kv._1), g(kv._2))))

    /**
     * Internal validation that tracks evaluated properties and items for
     * unevaluatedProperties/unevaluatedItems support.
     */
    private[json] def checkWithEvaluation(
      json: Json,
      options: ValidationOptions,
      trace: List[DynamicOptic.Node]
    ): EvaluationResult = {
      var result = EvaluationResult.empty

      def addError(message: String): Unit = result = result.addError(trace, message)

      def collectFromSchema(schema: JsonSchema, j: Json): EvaluationResult = schema match {
        case s: Object => s.checkWithEvaluation(j, options, trace)
        case _         =>
          schema.check(j, options) match {
            case Some(e) => new EvaluationResult(e.errors, Set.empty, Set.empty)
            case _       => EvaluationResult.empty
          }
      }

      // Handle $ref first
      $ref.foreach { refUri =>
        val refUriVal = refUri.value
        if (refUriVal.startsWith("#/$defs/")) {
          $defs.flatMap(_.get(refUriVal.substring(8))) match {
            case Some(refSchema) => result = result ++ collectFromSchema(refSchema, json)
            case _               => addError(s"Cannot resolve $$ref: $refUriVal")
          }
        } else addError(s"Unsupported $$ref format: $refUriVal")
      }

      // Type validation
      `type`.foreach { schemaType =>
        val typeMatches = json match {
          case _: Json.String  => schemaType.contains(JsonSchemaType.String)
          case _: Json.Boolean => schemaType.contains(JsonSchemaType.Boolean)
          case n: Json.Number  =>
            val isInt = n.value.isWhole
            schemaType.contains(JsonSchemaType.Number) || (isInt && schemaType.contains(JsonSchemaType.Integer))
          case _: Json.Array  => schemaType.contains(JsonSchemaType.Array)
          case _: Json.Object => schemaType.contains(JsonSchemaType.Object)
          case _              => schemaType.contains(JsonSchemaType.Null)
        }
        if (!typeMatches) {
          val sb      = new java.lang.StringBuilder("Expected type ")
          val initLen = sb.length
          schemaType match {
            case s: SchemaType.Single => sb.append(s.value.toJsonString)
            case u: SchemaType.Union  =>
              u.values.foreach { t =>
                if (sb.length > initLen) sb.append(" or ")
                sb.append(t.toJsonString)
              }
          }
          addError(sb.toString)
        }
      }

      // Enum validation
      `enum`.foreach { values =>
        if (!values.contains(json)) {
          val sb      = new java.lang.StringBuilder("Value not in enum: ")
          val initLen = sb.length
          values.foreach { v =>
            if (sb.length > initLen) sb.append(", ")
            sb.append(v.print)
          }
          addError(sb.toString)
        }
      }

      // Const validation
      const.foreach { constValue =>
        if (constValue != json) addError(s"Expected const value: ${constValue.print}")
      }

      json match {
        case s: Json.String => // String validations
          val len = s.value.length
          minLength.foreach { min =>
            if (len < min.value) addError(s"String length $len is less than minLength ${min.value}")
          }
          maxLength.foreach { max =>
            if (len > max.value) addError(s"String length $len is greater than maxLength ${max.value}")
          }
          pattern.foreach { p =>
            p.compiled match {
              case Right(regex) =>
                if (!regex.matcher(s.value).find()) addError(s"String does not match pattern: ${p.value}")
              case _ => // Invalid pattern - skip validation
            }
          }
          format.foreach { fmt =>
            if (options.validateFormats) FormatValidator.validate(fmt, s.value).foreach(addError)
          }
        case n: Json.Number => // Numeric validations
          val value = n.value
          minimum.foreach { min =>
            if (value < min) addError(s"Value $value is less than minimum $min")
          }
          maximum.foreach { max =>
            if (value > max) addError(s"Value $value is greater than maximum $max")
          }
          exclusiveMinimum.foreach { min =>
            if (value <= min) addError(s"Value $value is not greater than exclusiveMinimum $min")
          }
          exclusiveMaximum.foreach { max =>
            if (value >= max) addError(s"Value $value is not less than exclusiveMaximum $max")
          }
          multipleOf.foreach { m =>
            try {
              if (value % m.value != 0) addError(s"Value $value is not a multiple of ${m.value}")
            } catch {
              case _: ArithmeticException =>
                addError(s"Value $value cannot be checked against multipleOf ${m.value}")
            }
          }
        case a: Json.Array => // Array validations
          val len = a.value.length
          minItems.foreach { min =>
            if (len < min.value) addError(s"Array length $len is less than minItems ${min.value}")
          }
          maxItems.foreach { max =>
            if (len > max.value) addError(s"Array length $len is greater than maxItems ${max.value}")
          }
          uniqueItems.foreach { unique =>
            if (unique && a.value.distinct.length != len) {
              addError("Array items are not unique")
            }
          }
          var evaluatedIndices = Set.empty[Int] // Track evaluated indices
          // prefixItems evaluates specific indices
          prefixItems.foreach { schemas =>
            schemas.zipWithIndex.foreach { case (schema, idx) =>
              if (idx < a.value.length) {
                evaluatedIndices += idx
                val itemResult = collectFromSchema(schema, a.value(idx))
                result = result.addErrors(itemResult.errors)
              }
            }
          }
          // items evaluates all remaining indices
          items.foreach { itemSchema =>
            val startIdx = prefixItems.map(_.length).getOrElse(0)
            a.value.zipWithIndex.drop(startIdx).foreach { case (item, idx) =>
              evaluatedIndices += idx
              val itemResult = collectFromSchema(itemSchema, item)
              result = result.addErrors(itemResult.errors)
            }
          }
          // Validate contains (does not mark items as evaluated per spec)
          contains.foreach { containsSchema =>
            val matchCount = a.value.count(item => containsSchema.check(item, options).isEmpty)
            val minC       = minContains.map(_.value).getOrElse(1)
            val maxC       = maxContains.map(_.value)

            if (matchCount < minC) {
              addError(s"Array must contain at least $minC matching items, found $matchCount")
            }
            maxC.foreach { max =>
              if (matchCount > max) {
                addError(s"Array must contain at most $max matching items, found $matchCount")
              }
            }
          }
          result = result.withEvaluatedItems(evaluatedIndices)
        case obj: Json.Object => // Object validations
          val fieldMap  = obj.value.toMap
          val fieldKeys = fieldMap.keySet
          // Required validation
          required.foreach { reqs =>
            reqs.foreach { req =>
              if (!fieldKeys.contains(req)) {
                addError(s"Missing required property: $req")
              }
            }
          }
          // Min/max properties
          minProperties.foreach { min =>
            if (fieldKeys.size < min.value) {
              addError(s"Object has ${fieldKeys.size} properties, minimum is ${min.value}")
            }
          }
          maxProperties.foreach { max =>
            if (fieldKeys.size > max.value) {
              addError(s"Object has ${fieldKeys.size} properties, maximum is ${max.value}")
            }
          }
          // Property names validation
          propertyNames.foreach { nameSchema =>
            fieldKeys.foreach { key =>
              nameSchema.check(Json.String(key), options).foreach { e =>
                addError(s"Property name '$key' does not match propertyNames schema: ${e.message}")
              }
            }
          }
          var localEvaluatedProps = Set.empty[String] // Track which properties are evaluated locally
          properties.foreach { props =>
            props.foreach { case (propName, propSchema) =>
              fieldMap.get(propName).foreach { propValue =>
                localEvaluatedProps += propName
                val propResult = collectFromSchema(propSchema, propValue)
                result = result.addErrors(propResult.errors)
              }
            }
          }
          patternProperties.foreach { patterns =>
            patterns.foreach { case (pattern, propSchema) =>
              pattern.compiled.foreach { regex =>
                fieldKeys.foreach { key =>
                  if (regex.matcher(key).find()) {
                    localEvaluatedProps += key
                    fieldMap.get(key).foreach { propValue =>
                      val propResult = collectFromSchema(propSchema, propValue)
                      result = result.addErrors(propResult.errors)
                    }
                  }
                }
              }
            }
          }
          additionalProperties.foreach { addlSchema =>
            val additionalKeys = fieldKeys -- localEvaluatedProps --
              properties.map(_.keySet).getOrElse(Set.empty)
            additionalKeys.foreach { key =>
              localEvaluatedProps += key
              fieldMap.get(key).foreach { propValue =>
                val propResult = collectFromSchema(addlSchema, propValue)
                result = result.addErrors(propResult.errors)
              }
            }
          }
          result = result.withEvaluatedProperties(localEvaluatedProps)
          // Dependent required validation
          dependentRequired.foreach { deps =>
            deps.foreach { case (propName, requiredProps) =>
              if (fieldKeys.contains(propName)) {
                requiredProps.foreach { req =>
                  if (!fieldKeys.contains(req)) {
                    addError(s"Property '$propName' requires '$req' to be present")
                  }
                }
              }
            }
          }
          // Dependent schemas validation (contributes to evaluation)
          dependentSchemas.foreach { deps =>
            deps.foreach { case (propName, depSchema) =>
              if (fieldKeys.contains(propName)) {
                val depResult = collectFromSchema(depSchema, obj)
                result = result ++ depResult
              }
            }
          }
        case _ =>
      }

      // Composition validations - collect evaluated properties/items from subschemas
      allOf.foreach { schemas =>
        schemas.foreach { schema =>
          val subResult = collectFromSchema(schema, json)
          result = result ++ subResult
        }
      }

      anyOf.foreach { schemas =>
        val results = schemas.map(s => collectFromSchema(s, json))
        val valid   = results.filter(_.errors.isEmpty)
        if (valid.isEmpty) {
          addError("Value does not match any schema in anyOf")
        } else {
          // Collect evaluated props/items from all valid schemas
          valid.foreach { r =>
            result = result.withEvaluatedProperties(r.evaluatedProperties)
            result = result.withEvaluatedItems(r.evaluatedItems)
          }
        }
      }

      oneOf.foreach { schemas =>
        val results    = schemas.map(s => collectFromSchema(s, json))
        val valid      = results.filter(_.errors.isEmpty)
        val validCount = valid.length
        if (validCount == 0) {
          addError("Value does not match any schema in oneOf")
        } else if (validCount > 1) {
          addError(s"Value matches $validCount schemas in oneOf, expected exactly 1")
        } else {
          // Collect evaluated props/items from the single valid schema
          result = result.withEvaluatedProperties(valid.head.evaluatedProperties)
          result = result.withEvaluatedItems(valid.head.evaluatedItems)
        }
      }

      not.foreach { notSchema =>
        if (notSchema.check(json, options).isEmpty) {
          addError("Value should not match the 'not' schema")
        }
        // 'not' does not contribute to evaluation per spec
      }

      // Conditional validation (if/then/else) - contributes to evaluation
      // Per spec, 'if' always contributes to evaluation regardless of whether it passes
      `if`.foreach { ifSchema =>
        val ifResult = collectFromSchema(ifSchema, json)
        val ifValid  = ifResult.errors.isEmpty
        result = result.withEvaluatedProperties(ifResult.evaluatedProperties)
        result = result.withEvaluatedItems(ifResult.evaluatedItems)
        if (ifValid) {
          `then`.foreach { thenSchema =>
            val thenResult = collectFromSchema(thenSchema, json)
            result = result ++ thenResult
          }
        } else {
          `else`.foreach { elseSchema =>
            val elseResult = collectFromSchema(elseSchema, json)
            result = result ++ elseResult
          }
        }
      }

      // unevaluatedItems validation - must run after all applicators
      json match {
        case a: Json.Array =>
          unevaluatedItems.foreach { unevalSchema =>
            val allEvaluated = result.evaluatedItems
            (0 until a.value.length).foreach { idx =>
              if (!allEvaluated.contains(idx)) {
                val itemResult = collectFromSchema(unevalSchema, a.value(idx))
                result = result.addErrors(itemResult.errors).withEvaluatedItem(idx)
              }
            }
          }
        case obj: Json.Object =>
          unevaluatedProperties.foreach { unevalSchema =>
            val fieldMap     = obj.value.toMap
            val fieldKeys    = fieldMap.keySet
            val allEvaluated = result.evaluatedProperties
            fieldKeys.foreach { key =>
              if (!allEvaluated.contains(key)) {
                fieldMap.get(key).foreach { propValue =>
                  val propResult = collectFromSchema(unevalSchema, propValue)
                  result = result.addErrors(propResult.errors).withEvaluatedProperty(key)
                }
              }
            }
          }
        case _ => ()
      }

      result
    }
  }

  object Object {
    val empty: Object = Object()
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  def ofType(t: JsonSchemaType): JsonSchema = new Object(`type` = new Some(new SchemaType.Single(t)))

  def string(
    minLength: Option[NonNegativeInt] = None,
    maxLength: Option[NonNegativeInt] = None,
    pattern: Option[RegexPattern] = None,
    format: Option[String] = None
  ): JsonSchema = new Object(
    `type` = new Some(new SchemaType.Single(JsonSchemaType.String)),
    minLength = minLength,
    maxLength = maxLength,
    pattern = pattern,
    format = format
  )

  /**
   * Convenience constructor for string schema with non-optional length
   * constraints.
   */
  def string(minLength: NonNegativeInt, maxLength: NonNegativeInt): JsonSchema =
    string(minLength = new Some(minLength), maxLength = new Some(maxLength))

  /** Convenience constructor for string schema with pattern. */
  def string(pattern: RegexPattern): JsonSchema = string(pattern = new Some(pattern))

  def number(
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    multipleOf: Option[PositiveNumber] = None
  ): JsonSchema = Object(
    `type` = new Some(new SchemaType.Single(JsonSchemaType.Number)),
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
  ): JsonSchema = Object(
    `type` = new Some(new SchemaType.Single(JsonSchemaType.Integer)),
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
    maxContains: Option[NonNegativeInt] = None,
    unevaluatedItems: Option[JsonSchema] = None
  ): JsonSchema = Object(
    `type` = new Some(new SchemaType.Single(JsonSchemaType.Array)),
    items = items,
    prefixItems = prefixItems,
    minItems = minItems,
    maxItems = maxItems,
    uniqueItems = uniqueItems,
    contains = contains,
    minContains = minContains,
    maxContains = maxContains,
    unevaluatedItems = unevaluatedItems
  )

  /**
   * Convenience constructor for array schema with items and length constraints.
   */
  def array(items: JsonSchema, minItems: NonNegativeInt, maxItems: NonNegativeInt): JsonSchema =
    array(items = new Some(items), minItems = new Some(minItems), maxItems = new Some(maxItems))

  /** Convenience constructor for array schema with just items. */
  def array(items: JsonSchema): JsonSchema = array(items = new Some(items))

  def obj(
    properties: Option[ChunkMap[String, JsonSchema]] = None,
    required: Option[Set[String]] = None,
    additionalProperties: Option[JsonSchema] = None,
    patternProperties: Option[ChunkMap[RegexPattern, JsonSchema]] = None,
    propertyNames: Option[JsonSchema] = None,
    minProperties: Option[NonNegativeInt] = None,
    maxProperties: Option[NonNegativeInt] = None,
    unevaluatedProperties: Option[JsonSchema] = None,
    title: Option[String] = None
  ): JsonSchema = Object(
    `type` = new Some(new SchemaType.Single(JsonSchemaType.Object)),
    properties = properties,
    required = required,
    additionalProperties = additionalProperties,
    patternProperties = patternProperties,
    propertyNames = propertyNames,
    minProperties = minProperties,
    maxProperties = maxProperties,
    unevaluatedProperties = unevaluatedProperties,
    title = title
  )

  def enumOf(values: ::[Json]): JsonSchema = new Object(`enum` = new Some(values))

  def enumOfStrings(values: ::[String]): JsonSchema =
    new Object(`enum` = new Some(values.map(new Json.String(_)).asInstanceOf[::[Json]]))

  def constOf(value: Json): JsonSchema = new Object(const = new Some(value))

  def ref(uri: UriReference): JsonSchema = new Object($ref = new Some(uri))

  def refString(uri: String): JsonSchema = new Object($ref = new Some(new UriReference(uri)))

  val nullSchema: JsonSchema = ofType(JsonSchemaType.Null)
  val boolean: JsonSchema    = ofType(JsonSchemaType.Boolean)

  // ===========================================================================
  // Parsing Helpers
  // ===========================================================================

  private def parseObject(obj: Json.Object): Either[SchemaError, Object] = {
    val fieldMap = obj.value.toMap

    def getString(key: String): Option[String] =
      fieldMap.get(key).collect { case s: Json.String => s.value }

    def getBoolean(key: String): Option[scala.Boolean] =
      fieldMap.get(key).collect { case b: Json.Boolean => b.value }

    def getNumber(key: String): Option[BigDecimal] =
      fieldMap.get(key).collect { case n: Json.Number => n.value }

    def getNonNegativeInt(key: String): Option[NonNegativeInt] =
      getNumber(key).flatMap(n => NonNegativeInt(n.toInt))

    def getPositiveNumber(key: String): Option[PositiveNumber] =
      getNumber(key).flatMap(PositiveNumber(_))

    def getSchema(key: String): Either[SchemaError, Option[JsonSchema]] =
      fieldMap.get(key) match {
        case Some(json) => fromJson(json).map(Some(_))
        case _          => new Right(None)
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
        case _ => Right(None)
      }

    def getSchemaMap(key: String): Either[SchemaError, Option[ChunkMap[String, JsonSchema]]] =
      fieldMap.get(key) match {
        case Some(o: Json.Object) =>
          val results = o.value.map { case (k, v) => fromJson(v).map(s => k -> s) }
          val errs    = results.collect { case Left(e) => e }
          if (errs.nonEmpty) Left(errs.reduce(_ ++ _))
          else Right(Some(ChunkMap.fromChunk(results.collect { case Right(kv) => kv })))
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
      val patternPropsOpt: Option[ChunkMap[RegexPattern, JsonSchema]] = fieldMap.get("patternProperties") match {
        case Some(o: Json.Object) =>
          val parsed = o.value.flatMap { case (pattern, json) =>
            fromJson(json).toOption.map(schema => RegexPattern.unsafe(pattern) -> schema)
          }
          if (parsed.nonEmpty) Some(ChunkMap.fromChunk(parsed)) else None
        case _ => None
      }

      // Parse dependent required
      val dependentRequiredOpt: Option[ChunkMap[String, Set[String]]] = fieldMap.get("dependentRequired") match {
        case Some(o: Json.Object) =>
          val parsed = o.value.map { case (k, v) =>
            v match {
              case arr: Json.Array => k -> arr.value.collect { case s: Json.String => s.value }.toSet
              case _               => k -> Set.empty[String]
            }
          }
          if (parsed.nonEmpty) Some(ChunkMap.fromChunk(parsed)) else None
        case _ => None
      }

      // Parse type
      val typeOpt = fieldMap.get("type").flatMap(j => SchemaType.fromJson(j).toOption)

      // Collect unknown extensions
      val extensions = ChunkMap.from(obj.value).filterNot(kv => knownKeys.contains(kv._1))

      new Object(
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

  private[this] val knownKeys = Set(
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
}
