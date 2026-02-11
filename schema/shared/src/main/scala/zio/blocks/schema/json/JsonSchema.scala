package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap, NonEmptyChunk}
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
final case class RegexPattern(value: String) {

  /** Compiles the pattern to a Java Pattern for validation. */
  lazy val compiled: Either[String, Pattern] =
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
      case e: PatternSyntaxException => new Left(e.getMessage)
    }

  /** Creates a RegexPattern without validation (for trusted input). */
  def unsafe(value: String): RegexPattern = new RegexPattern(value)
}

/**
 * URI-Reference per RFC 3986 (may be relative).
 */
final case class UriReference private[json] (value: String) extends AnyVal {

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
final case class Anchor private[json] (value: String) extends AnyVal

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
  def validate(format: String, value: String): Option[String] = format match {
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
    Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$")
  private[this] val datePattern: Pattern  = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")
  private[this] val timePattern: Pattern  = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$")
  private[this] val emailPattern: Pattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  private[this] val uuidPattern: Pattern  =
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
  private[this] val jsonPointerPattern: Pattern = Pattern.compile("^(/([^~/]|~0|~1)*)*$")

  private[this] def validateDateTime(value: String): Option[String] =
    if (dateTimePattern.matcher(value).matches()) validateDateTimeSemantics(value)
    else new Some(s"String '$value' is not a valid date-time (RFC 3339)")

  private[this] def validateDateTimeSemantics(value: String): Option[String] =
    validateDateSemantics(value).orElse(validateTimeSemantics(value.substring(11)))

  private[this] def validateDate(value: String): Option[String] =
    if (datePattern.matcher(value).matches()) validateDateSemantics(value)
    else new Some(s"String '$value' is not a valid date (RFC 3339)")

  private[this] def validateDateSemantics(value: String): Option[String] = {
    val year  = toInt(value, 0, 4)
    val month = toInt(value, 5, 7)
    val day   = toInt(value, 8, 10)
    if (month < 1 || month > 12) new Some(s"Invalid month $month in date '$value'")
    else if (
      day < 1 || day > {
        if (month != 2) month >> 3 ^ (month | 0x1e)
        else if (isLeapYear(year)) 29
        else 28
      }
    ) new Some(s"Invalid day $day for month $month in date '$value'")
    else None
  }

  private[this] def isLeapYear(year: Int): scala.Boolean = // year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    (year & 0x3) == 0 && (year * -1030792151 - 2061584303 > -1975684958 || (year & 0xf) == 0)

  private[this] def validateTime(value: String): Option[String] =
    if (timePattern.matcher(value).matches()) validateTimeSemantics(value)
    else new Some(s"String '$value' is not a valid time (RFC 3339)")

  private[this] def validateTimeSemantics(value: String): Option[String] = {
    val hour   = toInt(value, 0, 2)
    val minute = toInt(value, 3, 5)
    val second = toInt(value, 6, 8)
    if (hour < 0 || hour > 23) new Some(s"Invalid hour $hour in time '$value'")
    else if (minute < 0 || minute > 59) new Some(s"Invalid minute $minute in time '$value'")
    else if (second < 0 || second > 60) new Some(s"Invalid second $second in time '$value'")
    else None
  }

  private[this] def validateEmail(value: String): Option[String] =
    if (value.length <= 254 && emailPattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid email address")

  private[this] def validateUuid(value: String): Option[String] =
    if (uuidPattern.matcher(value).matches()) None
    else new Some(s"String '$value' is not a valid UUID (RFC 4122)")

  private[this] def validateUri(value: String): Option[String] =
    try {
      if (new URI(value).getScheme eq null) new Some(s"String '$value' is not a valid URI (missing scheme)")
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

  private[this] def toInt(value: String, from: Int, to: Int): Int = {
    var n   = 0
    var idx = from
    while (idx < to) {
      n = n * 10 + (value.charAt(idx) - '0')
      idx += 1
    }
    n
  }
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
    else new Some(new SchemaError(errors.asInstanceOf[::[SchemaError.Single]]))
}

object EvaluationResult {
  val empty: EvaluationResult = new EvaluationResult(Nil, Set.empty, Set.empty)

  def fromError(trace: List[DynamicOptic.Node], message: String): EvaluationResult =
    new EvaluationResult(List(SchemaError.expectationMismatch(trace, message).errors.head), Set.empty, Set.empty)
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
        case (Some(a1), Some(a2)) => a1 ++ a2
        case (Some(a1), _)        => s2 +: a1
        case (_, Some(a2))        => s1 +: a2
        case _                    => NonEmptyChunk(s1, s2)
      }))
  }

  /** Combine with another schema using anyOf. */
  def ||(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _)                           => JsonSchema.True
    case (_, JsonSchema.True)                           => JsonSchema.True
    case (JsonSchema.False, s)                          => s
    case (s, JsonSchema.False)                          => s
    case (s1: JsonSchema.Object, s2: JsonSchema.Object) =>
      JsonSchema.Object(anyOf = new Some((s1.anyOf, s2.anyOf) match {
        case (Some(a1), Some(a2)) => a1 ++ a2
        case (Some(a1), None)     => s2 +: a1
        case (None, Some(a2))     => s1 +: a2
        case (None, None)         => NonEmptyChunk(s1, s2)
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
              else s.copy(`type` = new Some(new SchemaType.Union(NonEmptyChunk(JsonSchemaType.Null, t))))
            case ut: SchemaType.Union =>
              val ts = ut.values
              if (ts.contains(JsonSchemaType.Null)) s
              else s.copy(`type` = new Some(new SchemaType.Union(JsonSchemaType.Null +: ts)))
          }
        case _ =>
          new JsonSchema.Object(anyOf = new Some(NonEmptyChunk(JsonSchema.ofType(JsonSchemaType.Null), s)))
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
    allOf: Option[NonEmptyChunk[JsonSchema]] = None,
    anyOf: Option[NonEmptyChunk[JsonSchema]] = None,
    oneOf: Option[NonEmptyChunk[JsonSchema]] = None,
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
    prefixItems: Option[NonEmptyChunk[JsonSchema]] = None,
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
    `enum`: Option[NonEmptyChunk[Json]] = None,
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
    examples: Option[NonEmptyChunk[Json]] = None,
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
        case Some(v) => fields.addOne(("$vocabulary", toJsonObject[URI, Boolean](v, _.toString, Json.Boolean.apply)))
        case _       =>
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
        case Some(d) => fields.addOne(("dependentSchemas", toJsonObject[String, JsonSchema](d, identity, _.toJson)))
        case _       =>
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

    private[this] def toJsonObject[K, V](m: ChunkMap[K, V], f: K => String, g: V => Json): Json.Object =
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
      $ref match {
        case Some(refUri) =>
          val refUriVal = refUri.value
          if (refUriVal.startsWith("#/$defs/")) {
            $defs.flatMap(_.get(refUriVal.substring(8))) match {
              case Some(refSchema) => result = result ++ collectFromSchema(refSchema, json, options, trace)
              case _               => result = result.addError(trace, s"Cannot resolve $$ref: $refUriVal")
            }
          } else result = result.addError(trace, s"Unsupported $$ref format: $refUriVal")
        case _ =>
      }
      `type` match {
        case Some(schemaType) =>
          val typeMatches = json match {
            case _: Json.String  => schemaType.contains(JsonSchemaType.String)
            case _: Json.Boolean => schemaType.contains(JsonSchemaType.Boolean)
            case n: Json.Number  =>
              schemaType.contains(JsonSchemaType.Number) ||
              (n.value.isWhole && schemaType.contains(JsonSchemaType.Integer))
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
            result = result.addError(trace, sb.toString)
          }
        case _ =>
      }
      `enum` match {
        case Some(values) =>
          if (!values.contains(json)) {
            val sb  = new java.lang.StringBuilder("Value not in enum: ")
            val len = Math.min(values.length, 10)
            var idx = 0
            while (idx < len) { // Take up to 10 values to avoid too-long error messages
              if (idx > 0) sb.append(", ")
              sb.append(values(idx).print)
              idx += 1
            }
            if (idx != values.length) sb.append(", ...")
            result = result.addError(trace, sb.toString)
          }
        case _ =>
      }
      const match {
        case Some(constValue) if constValue != json =>
          result = result.addError(trace, s"Expected const value ${constValue.print}")
        case _ =>
      }
      json match {
        case s: Json.String => // String validations
          val len = s.value.length
          minLength match {
            case Some(min) if len < min.value =>
              result = result.addError(trace, s"String length $len is less than minLength ${min.value}")
            case _ =>
          }
          maxLength match {
            case Some(max) if len > max.value =>
              result = result.addError(trace, s"String length $len is greater than maxLength ${max.value}")
            case _ =>
          }
          pattern match {
            case Some(p) =>
              p.compiled match {
                case Right(regex) =>
                  if (!regex.matcher(s.value).find()) {
                    result = result.addError(trace, s"String does not match pattern '${p.value}'")
                  }
                case _ => // Invalid pattern - skip validation
              }
            case _ =>
          }
          format match {
            case Some(fmt) =>
              if (options.validateFormats) {
                FormatValidator.validate(fmt, s.value) match {
                  case Some(message) => result = result.addError(trace, message)
                  case _             =>
                }
              }
            case _ =>
          }
        case n: Json.Number => // Numeric validations
          val value = n.value
          minimum match {
            case Some(min) if value < min =>
              result = result.addError(trace, s"Value $value is less than minimum $min")
            case _ =>
          }
          maximum match {
            case Some(max) if value > max =>
              result = result.addError(trace, s"Value $value is greater than maximum $max")
            case _ =>
          }
          exclusiveMinimum match {
            case Some(min) if value <= min =>
              result = result.addError(trace, s"Value $value is not greater than exclusiveMinimum $min")
            case _ =>
          }
          exclusiveMaximum match {
            case Some(max) if value >= max =>
              result = result.addError(trace, s"Value $value is not less than exclusiveMaximum $max")
            case _ =>
          }
          multipleOf match {
            case Some(m) =>
              try {
                if (value % m.value != 0)
                  result = result.addError(trace, s"Value $value is not a multiple of ${m.value}")
              } catch {
                case _: ArithmeticException =>
                  result = result.addError(trace, s"Value $value cannot be checked against multipleOf ${m.value}")
              }
            case _ =>
          }
        case a: Json.Array => // Array validations
          val len = a.value.length
          minItems match {
            case Some(min) if len < min.value =>
              result = result.addError(trace, s"Array length $len is less than minItems ${min.value}")
            case _ =>
          }
          maxItems match {
            case Some(max) if len > max.value =>
              result = result.addError(trace, s"Array length $len is greater than maxItems ${max.value}")
            case _ =>
          }
          uniqueItems match {
            case Some(true) if !uniqueCheck(a.value) =>
              result = result.addError(trace, "Array items are not unique")
            case _ =>
          }
          var evaluatedIndices = Set.empty[Int] // Track evaluated indices
          // prefixItems evaluates specific indices
          prefixItems match {
            case Some(schemas) =>
              schemas.foreach {
                var idx = 0
                schema =>
                  if (idx < len) {
                    evaluatedIndices += idx
                    val trace_ = new DynamicOptic.Node.AtIndex(idx) :: trace
                    result = result.addErrors(collectFromSchema(schema, a.value(idx), options, trace_).errors)
                  }
                  idx += 1
              }
            case _ =>
          }
          // items evaluates all remaining indices
          items match {
            case Some(itemSchema) =>
              var idx = prefixItems match {
                case Some(pis) => pis.length
                case _         => 0
              }
              val jsons = a.value
              val len   = jsons.length
              while (idx < len) {
                evaluatedIndices += idx
                val trace_ = new DynamicOptic.Node.AtIndex(idx) :: trace
                result = result.addErrors(collectFromSchema(itemSchema, jsons(idx), options, trace_).errors)
                idx += 1
              }
            case _ =>
          }
          // Validate contains (does not mark items as evaluated per spec)
          contains match {
            case Some(containsSchema) =>
              val matchCount = a.value.count(item => containsSchema.check(item, options).isEmpty)
              val minC       = minContains match {
                case Some(mc) => mc.value
                case _        => 1
              }
              if (matchCount < minC) {
                result = result.addError(trace, s"Array must contain at least $minC matching items, found $matchCount")
              }
              maxContains match {
                case Some(NonNegativeInt(maxC)) if matchCount > maxC =>
                  result = result.addError(trace, s"Array must contain at most $maxC matching items, found $matchCount")
                case _ =>
              }
            case _ =>
          }
          result = result.withEvaluatedItems(evaluatedIndices)
        case obj: Json.Object => // Object validations
          val fieldMap  = obj.value.toMap
          val fieldKeys = fieldMap.keySet
          // Required validation
          required match {
            case Some(reqs) =>
              reqs.foreach { req =>
                if (!fieldKeys.contains(req)) {
                  result = result.addError(trace, s"Missing required property '$req'")
                }
              }
            case _ =>
          }
          // Min/max properties
          minProperties match {
            case Some(min) if fieldKeys.size < min.value =>
              result = result.addError(trace, s"Object has ${fieldKeys.size} properties, minimum is ${min.value}")
            case _ =>
          }
          maxProperties match {
            case Some(max) if fieldKeys.size > max.value =>
              result = result.addError(trace, s"Object has ${fieldKeys.size} properties, maximum is ${max.value}")
            case _ =>
          }
          // Property names validation
          propertyNames match {
            case Some(nameSchema) =>
              fieldKeys.foreach { key =>
                nameSchema.check(new Json.String(key), options) match {
                  case Some(e) =>
                    result =
                      result.addError(trace, s"Property name '$key' does not match propertyNames schema: ${e.message}")
                  case _ =>
                }
              }
            case _ =>
          }
          var localEvaluatedProps = Set.empty[String] // Track which properties are evaluated locally
          properties match {
            case Some(props) =>
              props.foreach { kv =>
                val propName = kv._1
                fieldMap.get(propName) match {
                  case Some(propValue) =>
                    localEvaluatedProps += propName
                    val trace_ = new DynamicOptic.Node.Field(propName) :: trace
                    result = result.addErrors(collectFromSchema(kv._2, propValue, options, trace_).errors)
                  case _ =>
                }
              }
            case _ =>
          }
          patternProperties match {
            case Some(patterns) =>
              patterns.foreach { case (pattern, propSchema) =>
                pattern.compiled match {
                  case Right(regex) =>
                    fieldMap.foreach { kv =>
                      val key = kv._1
                      if (regex.matcher(key).find()) {
                        localEvaluatedProps += key
                        val trace_ = new DynamicOptic.Node.Field(key) :: trace
                        result = result.addErrors(collectFromSchema(propSchema, kv._2, options, trace_).errors)
                      }
                    }
                  case _ =>
                }
              }
            case _ =>
          }
          additionalProperties match {
            case Some(addSchema) =>
              var additionalKeys = fieldKeys -- localEvaluatedProps
              properties match {
                case Some(props) => additionalKeys = additionalKeys -- props.keySet
                case _           =>
              }
              additionalKeys.foreach { key =>
                localEvaluatedProps += key
                fieldMap.get(key) match {
                  case Some(propValue) =>
                    val trace_ = new DynamicOptic.Node.Field(key) :: trace
                    result = result.addErrors(collectFromSchema(addSchema, propValue, options, trace_).errors)
                  case _ =>
                }
              }
            case _ =>
          }
          result = result.withEvaluatedProperties(localEvaluatedProps)
          // Dependent required validation
          dependentRequired match {
            case Some(deps) =>
              deps.foreach { case (propName, requiredProps) =>
                if (fieldKeys.contains(propName)) {
                  requiredProps.foreach { req =>
                    if (!fieldKeys.contains(req)) {
                      result = result.addError(trace, s"Property '$propName' requires '$req' to be present")
                    }
                  }
                }
              }
            case _ =>
          }
          // Dependent schemas validation (contributes to evaluation)
          dependentSchemas match {
            case Some(deps) =>
              deps.foreach { case (propName, depSchema) =>
                if (fieldKeys.contains(propName)) {
                  result = result ++ collectFromSchema(depSchema, obj, options, trace)
                }
              }
            case _ =>
          }
        case _ =>
      }
      // Composition validations - collect evaluated properties/items from subschemas
      allOf match {
        case Some(schemas) =>
          schemas.foreach(schema => result = result ++ collectFromSchema(schema, json, options, trace))
        case _ =>
      }
      anyOf match {
        case Some(schemas) =>
          val results = schemas.map(s => collectFromSchema(s, json, options, trace))
          val valid   = results.filter(_.errors.isEmpty)
          if (valid.isEmpty) result = result.addError(trace, "Value does not match any schema in anyOf")
          else {
            // Collect evaluated props/items from all valid schemas
            valid.foreach { r =>
              result = result.withEvaluatedProperties(r.evaluatedProperties)
              result = result.withEvaluatedItems(r.evaluatedItems)
            }
          }
        case _ =>
      }
      oneOf match {
        case Some(schemas) =>
          val results    = schemas.map(s => collectFromSchema(s, json, options, trace))
          val valid      = results.filter(_.errors.isEmpty)
          val validCount = valid.length
          if (validCount == 0) result = result.addError(trace, "Value does not match any schema in oneOf")
          else if (validCount > 1) {
            result = result.addError(trace, s"Value matches $validCount schemas in oneOf, expected exactly 1")
          } else {
            // Collect evaluated props/items from the single valid schema
            result = result.withEvaluatedProperties(valid.head.evaluatedProperties)
            result = result.withEvaluatedItems(valid.head.evaluatedItems)
          }
        case _ =>
      }
      not match {
        case Some(notSchema) if notSchema.check(json, options).isEmpty =>
          result = result.addError(trace, "Value should not match the 'not' schema")
        // 'not' does not contribute to evaluation per spec
        case _ =>
      }
      // Conditional validation (if/then/else) - contributes to evaluation
      // Per spec, 'if' always contributes to evaluation regardless of whether it passes
      `if` match {
        case Some(ifSchema) =>
          val ifResult = collectFromSchema(ifSchema, json, options, trace)
          val ifValid  = ifResult.errors.isEmpty
          result = result.withEvaluatedProperties(ifResult.evaluatedProperties)
          result = result.withEvaluatedItems(ifResult.evaluatedItems)
          if (ifValid) {
            `then` match {
              case Some(thenSchema) => result = result ++ collectFromSchema(thenSchema, json, options, trace)
              case _                =>
            }
          } else {
            `else` match {
              case Some(elseSchema) => result = result ++ collectFromSchema(elseSchema, json, options, trace)
              case _                =>
            }
          }
        case _ =>
      }
      // unevaluatedItems validation - must run after all applicators
      json match {
        case a: Json.Array =>
          unevaluatedItems match {
            case Some(unevalSchema) =>
              val allEvaluated = result.evaluatedItems
              val jsons        = a.value
              val len          = jsons.length
              var idx          = 0
              while (idx < len) {
                if (!allEvaluated.contains(idx)) {
                  val trace_ = new DynamicOptic.Node.AtIndex(idx) :: trace
                  result = result.addErrors(collectFromSchema(unevalSchema, jsons(idx), options, trace_).errors)
                  result = result.withEvaluatedItem(idx)
                }
                idx += 1
              }
            case _ =>
          }
        case obj: Json.Object =>
          unevaluatedProperties match {
            case Some(unevalSchema) =>
              obj.value.toMap.foreach {
                val allEvaluated = result.evaluatedProperties
                kv =>
                  val key = kv._1
                  if (!allEvaluated.contains(key)) {
                    val trace_ = new DynamicOptic.Node.Field(key) :: trace
                    result = result.addErrors(collectFromSchema(unevalSchema, kv._2, options, trace_).errors)
                    result = result.withEvaluatedProperty(key)
                  }
              }
            case _ =>
          }
        case _ => ()
      }
      result
    }

    private[this] def collectFromSchema(
      schema: JsonSchema,
      json: Json,
      options: ValidationOptions,
      trace: List[DynamicOptic.Node]
    ): EvaluationResult = schema match {
      case s: Object => s.checkWithEvaluation(json, options, trace)
      case _         =>
        schema.check(json, options) match {
          case Some(e) => new EvaluationResult(e.errors, Set.empty, Set.empty)
          case _       => EvaluationResult.empty
        }
    }

    private[this] def uniqueCheck(values: Chunk[Json]): Boolean = values.forall {
      val seen = new java.util.HashSet[Json](values.length) // Java's HashSet is not affected by hash collision vulns
      v => seen.add(v)
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
    prefixItems: Option[NonEmptyChunk[JsonSchema]] = None,
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

  def enumOf(values: NonEmptyChunk[Json]): JsonSchema = new Object(`enum` = new Some(values))

  def enumOfStrings(values: NonEmptyChunk[String]): JsonSchema =
    new Object(`enum` = new Some(values.map(new Json.String(_))))

  def constOf(value: Json): JsonSchema = new Object(const = new Some(value))

  def ref(uri: UriReference): JsonSchema = new Object($ref = new Some(uri))

  def refString(uri: String): JsonSchema = new Object($ref = new Some(UriReference(uri)))

  val nullSchema: JsonSchema = ofType(JsonSchemaType.Null)
  val boolean: JsonSchema    = ofType(JsonSchemaType.Boolean)

  // ===========================================================================
  // Parsing Helpers
  // ===========================================================================

  private def parseObject(obj: Json.Object): Either[SchemaError, Object] = {
    val fieldMap = obj.value.toMap

    def getString(key: String): Option[String] = fieldMap.get(key) match {
      case Some(s: Json.String) => new Some(s.value)
      case _                    => None
    }

    def getBoolean(key: String): Option[scala.Boolean] = fieldMap.get(key) match {
      case Some(b: Json.Boolean) => new Some(b.value)
      case _                     => None
    }

    def getNumber(key: String): Option[BigDecimal] = fieldMap.get(key) match {
      case Some(n: Json.Number) => new Some(n.value)
      case _                    => None
    }

    def getNonNegativeInt(key: String): Option[NonNegativeInt] = getNumber(key) match {
      case Some(n) => NonNegativeInt(n.intValue)
      case _       => None
    }

    def getPositiveNumber(key: String): Option[PositiveNumber] = getNumber(key) match {
      case Some(n) => PositiveNumber(n)
      case _       => None
    }

    def getSchema(key: String): Either[SchemaError, Option[JsonSchema]] = fieldMap.get(key) match {
      case Some(json) =>
        fromJson(json) match {
          case Right(schema) => new Right(new Some(schema))
          case l             => l.asInstanceOf[Either[SchemaError, Option[JsonSchema]]]
        }
      case _ => new Right(None)
    }

    def getAnchor(key: String): Option[Anchor] = fieldMap.get(key) match {
      case Some(s: Json.String) => new Some(Anchor(s.value))
      case _                    => None
    }

    def getUriReference(key: String): Option[UriReference] = fieldMap.get(key) match {
      case Some(s: Json.String) => new Some(UriReference(s.value))
      case _                    => None
    }

    def getSchemaList(key: String): Either[SchemaError, Option[NonEmptyChunk[JsonSchema]]] = fieldMap.get(key) match {
      case Some(arr: Json.Array) =>
        val schemas            = ChunkBuilder.make[JsonSchema]()
        var error: SchemaError = null
        arr.value.foreach { json =>
          fromJson(json) match {
            case Right(s) =>
              if (error eq null) schemas.addOne(s)
            case Left(e) =>
              if (error eq null) error = e
              else error = error ++ e
          }
        }
        if (error eq null) new Right(NonEmptyChunk.fromChunk(schemas.result()))
        else new Left(error)
      case v =>
        if (v eq None) new Right(None)
        else new Left(SchemaError.expectationMismatch(Nil, s"Expected array for $key"))
    }

    def getSchemaMap(key: String): Either[SchemaError, Option[ChunkMap[String, JsonSchema]]] = fieldMap.get(key) match {
      case Some(o: Json.Object) =>
        val schemaMap          = new ChunkMap.ChunkMapBuilder[String, JsonSchema]
        var error: SchemaError = null
        o.value.foreach { kv =>
          fromJson(kv._2) match {
            case Right(s) =>
              if (error eq null) schemaMap.addOne((kv._1, s))
            case Left(e) =>
              if (error eq null) error = e
              else error = error ++ e
          }
        }
        if (error eq null) new Right(new Some(schemaMap.result()))
        else new Left(error)
      case v =>
        if (v eq None) new Right(None)
        else new Left(SchemaError.expectationMismatch(Nil, s"Expected object for $key"))
    }

    def getStringSet(key: String): Option[Set[String]] = fieldMap.get(key) match {
      case Some(arr: Json.Array) =>
        new Some(
          arr.value
            .foldLeft(Set.newBuilder[String])((acc, json) =>
              json match {
                case s: Json.String => acc.addOne(s.value)
                case _              => acc
              }
            )
            .result()
        )
      case _ => None
    }

    def getJsonList(key: String): Option[NonEmptyChunk[Json]] = fieldMap.get(key) match {
      case Some(arr: Json.Array) => NonEmptyChunk.fromChunk(arr.value)
      case _                     => None
    }

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
      val patternPropsOpt = fieldMap.get("patternProperties") match {
        case Some(o: Json.Object) =>
          val schemas       = ChunkBuilder.make[JsonSchema]()
          val regexPatterns = ChunkBuilder.make[RegexPattern]()
          o.value.foreach { kv =>
            fromJson(kv._2) match {
              case Right(schema) =>
                schemas.addOne(schema)
                regexPatterns.addOne(new RegexPattern(kv._1))
              case _ =>
            }
          }
          if (regexPatterns.knownSize == 0) None
          else new Some(ChunkMap.fromChunks(regexPatterns.result(), schemas.result()))
        case _ => None
      }
      val dependentRequiredOpt = fieldMap.get("dependentRequired") match {
        case Some(o: Json.Object) =>
          val keys = o.value.map(_._1)
          val sets = o.value.map { kv =>
            kv._2 match {
              case arr: Json.Array =>
                arr.value
                  .foldLeft(Set.newBuilder[String]) { (acc, json) =>
                    json match {
                      case s: Json.String => acc.addOne(s.value)
                      case _              => acc
                    }
                  }
                  .result()
              case _ => Set.empty[String]
            }
          }
          if (keys.nonEmpty) new Some(ChunkMap.fromChunks(keys, sets))
          else None
        case _ => None
      }
      val typeOpt = fieldMap.get("type") match {
        case Some(j) =>
          SchemaType.fromJson(j) match {
            case Right(t) => new Some(t)
            case _        => None
          }
        case _ => None
      }
      val extensions = ChunkMap.from(obj.value).filterNot(kv => knownKeys.contains(kv._1))
      new Object(
        $id = getUriReference("$id"),
        $schema = getString("$schema") match {
          case Some(s) =>
            try new Some(new URI(s))
            catch {
              case err if NonFatal(err) => None
            }
          case _ => None
        },
        $anchor = getAnchor("$anchor"),
        $dynamicAnchor = getAnchor("$dynamicAnchor"),
        $ref = getUriReference("$ref"),
        $dynamicRef = getUriReference("$dynamicRef"),
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
