package zio.blocks.schema.json

import java.net.URI
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object SharedSchemas {
  implicit val uriSchema: Schema[URI] = Schema.string.transformOrFail(
    (s: String) =>
      try scala.util.Right(new URI(s))
      catch { case e: Exception => scala.util.Left(e.getMessage) },
    (uri: URI) => uri.toString
  )

  implicit def nonEmptyListSchema[A](implicit element: Schema[A]): Schema[::[A]] =
    Schema.list(element).transformOrFail(
      (l: List[A]) =>
        l match {
          case h :: t => scala.util.Right(::(h, t))
          case Nil    => scala.util.Left("List must not be empty")
        },
      (l: ::[A]) => l
    )
}

// =============================================================================
// Newtypes for Precision
// =============================================================================

/** Non-negative integer (>= 0). Used for minLength, maxLength, minItems, etc. */
final case class NonNegativeInt private (value: Int) extends AnyVal
object NonNegativeInt {
  def apply(n: Int): Option[NonNegativeInt] = if (n >= 0) Some(new NonNegativeInt(n)) else None
  def unsafe(n: Int): NonNegativeInt = new NonNegativeInt(n)
  implicit val schema: Schema[NonNegativeInt] = Schema.int.wrap(
    (n: Int) => apply(n).toRight(s"Value must be non-negative, got $n"),
    (b: NonNegativeInt) => b.value
  )
}

/** Strictly positive number (> 0). Used for multipleOf. */
final case class PositiveNumber private (value: BigDecimal) extends AnyVal
object PositiveNumber {
  def apply(n: BigDecimal): Option[PositiveNumber] = if (n > 0) Some(new PositiveNumber(n)) else None
  def unsafe(n: BigDecimal): PositiveNumber = new PositiveNumber(n)
  implicit val schema: Schema[PositiveNumber] = Schema.bigDecimal.wrap(
    (n: BigDecimal) => apply(n).toRight(s"Value must be strictly positive, got $n"),
    (b: PositiveNumber) => b.value
  )
}

/** ECMA-262 regular expression pattern. */
final case class RegexPattern(value: String) extends AnyVal
object RegexPattern {
  implicit val schema: Schema[RegexPattern] = Schema.string.wrapTotal((s: String) => RegexPattern(s), (b: RegexPattern) => b.value)
}

/** URI-Reference per RFC 3986 (may be relative). */
final case class UriReference(value: String) extends AnyVal
object UriReference {
  implicit val schema: Schema[UriReference] = Schema.string.wrapTotal((s: String) => UriReference(s), (b: UriReference) => b.value)
}

/** Anchor name (plain name fragment without #). */
final case class Anchor(value: String) extends AnyVal
object Anchor {
  implicit val schema: Schema[Anchor] = Schema.string.wrapTotal((s: String) => Anchor(s), (b: Anchor) => b.value)
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

  implicit val schema: Schema[JsonType] = Schema.string.wrap(
    (s: String) => fromString(s).toRight(s"Unknown JSON type: $s"),
    (b: JsonType) => b.toJsonString
  )
}

// =============================================================================
// Type Keyword: Single Type or Array of Types
// =============================================================================

sealed trait SchemaType extends Product with Serializable {
  def toJson: Json = this match {
    case SchemaType.Single(t) => Json.str(t.toJsonString)
    case SchemaType.Union(ts) => Json.arr(ts.map(t => Json.str(t.toJsonString)): _*)
  }
}

object SchemaType {
  final case class Single(value: JsonType) extends SchemaType
  final case class Union(values: List[JsonType]) extends SchemaType

  def fromJson(json: Json): Either[SchemaError, SchemaType] = json match {
    case s: Json.String => JsonType.fromString(s.value).map(Single(_)).toRight(SchemaError.expectationMismatch(Nil, s"Unknown JSON type: ${s.value}"))
    case arr: Json.Array => 
      val types = arr.value.collect { case s: Json.String => JsonType.fromString(s.value) }.flatten.toList
      if (types.isEmpty) scala.util.Left(SchemaError.expectationMismatch(Nil, "Empty type array"))
      else scala.util.Right(Union(types))
    case _ => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected string or string array for 'type' keyword"))
  }

  implicit val schema: Schema[SchemaType] =
    Schema.json.transformOrFail(json => fromJson(json).left.map(_.message), _.toJson)
}

// =============================================================================
// JSON Schema 2020-12 ADT
// =============================================================================

sealed trait JsonSchema extends Product with Serializable {
  /** Serialize this schema to its canonical JSON representation. */
  def toJson: Json

  /**
   * Validate a JSON value against this schema.
   * Returns None if valid, Some(error) with accumulated failures if invalid.
   */
  def check(json: Json): Option[SchemaError]

  /** Returns true if the JSON value conforms to this schema. */
  def conforms(json: Json): Boolean = check(json).isEmpty

  // ===========================================================================
  // Combinators
  // ===========================================================================

  /** Combine with another schema using allOf. */
  def &&(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _) => that
    case (_, JsonSchema.True) => this
    case (JsonSchema.False, _) => JsonSchema.False
    case (_, JsonSchema.False) => JsonSchema.False
    case (l: JsonSchema.SchemaObject, r: JsonSchema.SchemaObject) if l.allOf.isDefined && r.allOf.isDefined =>
      l.copy(allOf = Some(l.allOf.get ++ List(this, that)))
    case (l: JsonSchema.SchemaObject, _) if l.allOf.isDefined =>
      l.copy(allOf = Some(l.allOf.get ++ List(that)))
    case (_, r: JsonSchema.SchemaObject) if r.allOf.isDefined =>
      r.copy(allOf = Some(r.allOf.get ++ List(this)))
    case _ => JsonSchema.SchemaObject(allOf = Some(List(this, that)))
  }

  /** Combine with another schema using anyOf. */
  def ||(that: JsonSchema): JsonSchema = (this, that) match {
    case (JsonSchema.True, _) => JsonSchema.True
    case (_, JsonSchema.True) => JsonSchema.True
    case (JsonSchema.False, _) => that
    case (_, JsonSchema.False) => this
    case (l: JsonSchema.SchemaObject, r: JsonSchema.SchemaObject) if l.anyOf.isDefined && r.anyOf.isDefined =>
      l.copy(anyOf = Some(l.anyOf.get ++ List(this, that)))
    case (l: JsonSchema.SchemaObject, _) if l.anyOf.isDefined =>
      l.copy(anyOf = Some(l.anyOf.get ++ List(that)))
    case (_, r: JsonSchema.SchemaObject) if r.anyOf.isDefined =>
      r.copy(anyOf = Some(r.anyOf.get ++ List(this)))
    case _ => JsonSchema.SchemaObject(anyOf = Some(List(this, that)))
  }

  /** Negate this schema. */
  def unary_! : JsonSchema = JsonSchema.SchemaObject(not = Some(this))

  /** Make this schema nullable (accepts the original value or null). */
  def withNullable: JsonSchema = this || JsonSchema.`null`
}

object JsonSchema {

  // ===========================================================================
  // Parsing
  // ===========================================================================

  /** Parse a JsonSchema from its JSON representation. */
  def fromJson(json: Json): Either[SchemaError, JsonSchema] = json match {
    case Json.True     => scala.util.Right(True)
    case Json.False    => scala.util.Right(False)
    case j if j.isBoolean => scala.util.Right(if (j.booleanValue.getOrElse(false)) True else False)
    case obj: Json.Object =>
      val fields = obj.value.toMap
      
      parseCore(fields).flatMap { core =>
        parseComposition(fields).flatMap { comp =>
          parseConditionals(fields).flatMap { cond =>
            parseObjectStructure(fields).flatMap { objStruct =>
              parseArrayStructure(fields).flatMap { arrStruct =>
                parseValidationType(fields).flatMap { valType =>
                  parseValidationFormat(fields).flatMap { valFmt =>
                    parseMeta(fields).map { meta =>
                      val valNum = parseValidationNumeric(fields)
                      val valStr = parseValidationString(fields)
                      val valArr = parseValidationArray(fields)
                      val valObj = parseValidationObject(fields)
                      
                      SchemaObject(
                        $id = core._1,
                        $schema = core._2,
                        $anchor = core._3,
                        $dynamicAnchor = core._4,
                        $ref = core._5,
                        $dynamicRef = core._6,
                        $vocabulary = core._7,
                        $defs = core._8,
                        $comment = core._9,
                        
                        allOf = comp._1,
                        anyOf = comp._2,
                        oneOf = comp._3,
                        not = comp._4,
                        
                        `if` = cond._1,
                        `then` = cond._2,
                        `else` = cond._3,
                        
                        properties = objStruct._1,
                        patternProperties = objStruct._2,
                        additionalProperties = objStruct._3,
                        propertyNames = objStruct._4,
                        dependentSchemas = objStruct._5,
                        unevaluatedProperties = objStruct._6,

                        prefixItems = arrStruct._1,
                        items = arrStruct._2,
                        contains = arrStruct._3,
                        unevaluatedItems = arrStruct._4,

                        `type` = valType._1,
                        `enum` = valType._2,
                        const = valType._3,
                        multipleOf = valNum._1,
                        maximum = valNum._2,
                        exclusiveMaximum = valNum._3,
                        minimum = valNum._4,
                        exclusiveMinimum = valNum._5,
                        minLength = valStr._1,
                        maxLength = valStr._2,
                        pattern = valStr._3,
                        minItems = valArr._1,
                        maxItems = valArr._2,
                        uniqueItems = valArr._3,
                        minContains = valArr._4,
                        maxContains = valArr._5,
                        minProperties = valObj._1,
                        maxProperties = valObj._2,
                        required = valObj._3,
                        dependentRequired = valObj._4,
                        format = valFmt._1,
                        contentEncoding = valFmt._2,
                        contentMediaType = valFmt._3,
                        contentSchema = valFmt._4,

                        title = meta._1,
                        description = meta._2,
                        default = meta._3,
                        deprecated = meta._4,
                        readOnly = meta._5,
                        writeOnly = meta._6,
                        examples = meta._7,
                        extensions = meta._8
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    case _ => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected boolean or object for JsonSchema"))
  }

  private def parseSchemaList(json: Json): Either[SchemaError, List[JsonSchema]] = json match {
    case arr: Json.Array if arr.value.nonEmpty =>
      val results = arr.value.map(j => fromJson(j))
      val errors  = results.collect { case scala.util.Left(e) => e }
      if (errors.nonEmpty) scala.util.Left(SchemaError(::(errors.head.errors.head, errors.flatMap(_.errors.tail).toList)))
      else {
        val successes = results.collect { case scala.util.Right(a) => a }
        scala.util.Right(successes.toList)
      }
    case _: Json.Array => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected non-empty array"))
    case _             => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected array"))
  }


  private def parseCore(fields: Map[String, Json]): Either[SchemaError, (Option[UriReference], Option[URI], Option[Anchor], Option[Anchor], Option[UriReference], Option[UriReference], Option[Map[URI, Boolean]], Option[Map[String, JsonSchema]], Option[String])] = {
       val $id           = parseOptRef(fields, "$id", UriReference(_))
       val $schema       = fields.get("$schema").flatMap(_.stringValue).flatMap(s => try Some(new URI(s)) catch { case _ => None })
       val $anchor       = parseOptRef(fields, "$anchor", Anchor(_))
       val $dynamicAnchor = parseOptRef(fields, "$dynamicAnchor", Anchor(_))
       val $ref          = parseOptRef(fields, "$ref", UriReference(_))
       val $dynamicRef   = parseOptRef(fields, "$dynamicRef", UriReference(_))
       val $vocabulary   = parseVocabulary(fields)
       val $comment      = parseOptStr(fields, "$comment")
       
       parseOpt(fields, "$defs", parseMap(_, j => fromJson(j))).map { $defs =>
         ($id, $schema, $anchor, $dynamicAnchor, $ref, $dynamicRef, $vocabulary, $defs, $comment)
       }
  }

  private def parseComposition(fields: Map[String, Json]): Either[SchemaError, (Option[List[JsonSchema]], Option[List[JsonSchema]], Option[List[JsonSchema]], Option[JsonSchema])] =
    for {
        allOf                <- parseOpt(fields, "allOf", parseSchemaList)
        anyOf                <- parseOpt(fields, "anyOf", parseSchemaList)
        oneOf                <- parseOpt(fields, "oneOf", parseSchemaList)
        not                  <- parseOpt(fields, "not", j => fromJson(j))
    } yield (allOf, anyOf, oneOf, not)

  private def parseConditionals(fields: Map[String, Json]): Either[SchemaError, (Option[JsonSchema], Option[JsonSchema], Option[JsonSchema])] =
    for {
        `if`                 <- parseOpt(fields, "if", j => fromJson(j))
        `then`               <- parseOpt(fields, "then", j => fromJson(j))
        `else`               <- parseOpt(fields, "else", j => fromJson(j))
    } yield (`if`, `then`, `else`)

  private def parseObjectStructure(fields: Map[String, Json]): Either[SchemaError, (Option[Map[String, JsonSchema]], Option[Map[RegexPattern, JsonSchema]], Option[JsonSchema], Option[JsonSchema], Option[Map[String, JsonSchema]], Option[JsonSchema])] =
    for {
        properties           <- parseOpt(fields, "properties", parseMap(_, j => fromJson(j)))
        patternProps         <- fields.get("patternProperties") match {
                                   case Some(obj: Json.Object) =>
                                     val results = obj.value.map { case (k, v) => fromJson(v).map(s => (RegexPattern(k), s)) }
                                     val errors = results.collect { case scala.util.Left(e) => e }
                                     if (errors.nonEmpty) scala.util.Left(SchemaError(::(errors.head.errors.head, errors.flatMap(_.errors.tail).toList)))
                                     else scala.util.Right(Some(results.collect { case scala.util.Right(r) => r }.toMap))
                                   case _ => scala.util.Right(None)
                                 }
        additionalProperties <- parseOpt(fields, "additionalProperties", j => fromJson(j))
        propertyNames        <- parseOpt(fields, "propertyNames", j => fromJson(j))
        dependentSchemas     <- parseOpt(fields, "dependentSchemas", parseMap(_, j => fromJson(j)))
        unevaluatedProperties <- parseOpt(fields, "unevaluatedProperties", j => fromJson(j))
    } yield (properties, patternProps, additionalProperties, propertyNames, dependentSchemas, unevaluatedProperties)

  private def parseArrayStructure(fields: Map[String, Json]): Either[SchemaError, (Option[List[JsonSchema]], Option[JsonSchema], Option[JsonSchema], Option[JsonSchema])] =
    for {
        prefixItems          <- parseOpt(fields, "prefixItems", parseSchemaList)
        items                <- parseOpt(fields, "items", j => fromJson(j))
        contains             <- parseOpt(fields, "contains", j => fromJson(j))
        unevaluatedItems     <- parseOpt(fields, "unevaluatedItems", j => fromJson(j))
    } yield (prefixItems, items, contains, unevaluatedItems)

  private def parseValidationType(fields: Map[String, Json]): Either[SchemaError, (Option[SchemaType], Option[List[Json]], Option[Json])] =
    for {
        `type`               <- parseOpt(fields, "type", SchemaType.fromJson)
        `enum`               <- parseOpt(fields, "enum", {
                                   case arr: Json.Array => scala.util.Right(arr.value.toList)
                                   case _ => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected array for enum"))
                                 })
        const                <- scala.util.Right(fields.get("const"))
    } yield (`type`, `enum`, const)

  private def parseValidationNumeric(fields: Map[String, Json]): (Option[PositiveNumber], Option[BigDecimal], Option[BigDecimal], Option[BigDecimal], Option[BigDecimal]) = {
        val multipleOf       = parseOptNum(fields, "multipleOf").flatMap(PositiveNumber(_))
        val maximum          = parseOptNum(fields, "maximum")
        val exclusiveMaximum = parseOptNum(fields, "exclusiveMaximum")
        val minimum          = parseOptNum(fields, "minimum")
        val exclusiveMinimum = parseOptNum(fields, "exclusiveMinimum")
        (multipleOf, maximum, exclusiveMaximum, minimum, exclusiveMinimum)
  }

  private def parseValidationString(fields: Map[String, Json]): (Option[NonNegativeInt], Option[NonNegativeInt], Option[RegexPattern]) = {
        val minLength        = fields.get("minLength").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val maxLength        = fields.get("maxLength").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val pattern          = parseOptStr(fields, "pattern").map(RegexPattern(_))
        (minLength, maxLength, pattern)
  }

  private def parseValidationArray(fields: Map[String, Json]): (Option[NonNegativeInt], Option[NonNegativeInt], Option[Boolean], Option[NonNegativeInt], Option[NonNegativeInt]) = {
        val minItems         = fields.get("minItems").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val maxItems         = fields.get("maxItems").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val uniqueItems      = parseOptBool(fields, "uniqueItems")
        val minContains      = fields.get("minContains").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val maxContains      = fields.get("maxContains").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        (minItems, maxItems, uniqueItems, minContains, maxContains)
  }

  private def parseValidationObject(fields: Map[String, Json]): (Option[NonNegativeInt], Option[NonNegativeInt], Option[Set[String]], Option[Map[String, Set[String]]]) = {
        val minProperties    = fields.get("minProperties").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val maxProperties    = fields.get("maxProperties").flatMap(_.numberValue).map(_.toInt).flatMap(NonNegativeInt(_))
        val required         = parseRequired(fields)
        val dependentRequired = parseDependentRequired(fields)
        (minProperties, maxProperties, required, dependentRequired)
  }

  private def parseValidationFormat(fields: Map[String, Json]): Either[SchemaError, (Option[String], Option[String], Option[String], Option[JsonSchema])] =
    for {
        format               <- scala.util.Right(parseOptStr(fields, "format"))
        contentEncoding      <- scala.util.Right(parseOptStr(fields, "contentEncoding"))
        contentMediaType     <- scala.util.Right(parseOptStr(fields, "contentMediaType"))
        contentSchema        <- parseOpt(fields, "contentSchema", j => fromJson(j))
    } yield (format, contentEncoding, contentMediaType, contentSchema)

  private def parseMeta(fields: Map[String, Json]): Either[SchemaError, (Option[String], Option[String], Option[Json], Option[Boolean], Option[Boolean], Option[Boolean], Option[List[Json]], Map[String, Json])] =
    for {
        title                <- scala.util.Right(parseOptStr(fields, "title"))
        description          <- scala.util.Right(parseOptStr(fields, "description"))
        default              <- scala.util.Right(fields.get("default"))
        deprecated           <- scala.util.Right(parseOptBool(fields, "deprecated"))
        readOnly             <- scala.util.Right(parseOptBool(fields, "readOnly"))
        writeOnly            <- scala.util.Right(parseOptBool(fields, "writeOnly"))
        examples             <- parseOpt(fields, "examples", {
                                   case arr: Json.Array if arr.value.nonEmpty => scala.util.Right(arr.value.toList)
                                   case _ => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected non-empty array for examples"))
                                 })
    } yield (title, description, default, deprecated, readOnly, writeOnly, examples, Map.empty)

  private def parseOpt[A](fields: Map[String, Json], key: String, f: Json => Either[SchemaError, A]): Either[SchemaError, Option[A]] =
    fields.get(key).map(f).map(_.map(Some(_))).getOrElse(scala.util.Right(None))

  private def parseOptRef[A](fields: Map[String, Json], key: String, f: String => A): Option[A] =
    fields.get(key).flatMap(_.stringValue).map(f)


  private def parseOptNum(fields: Map[String, Json], key: String): Option[BigDecimal] =
    fields.get(key).flatMap(_.numberValue)

  private def parseOptStr(fields: Map[String, Json], key: String): Option[String] =
    fields.get(key).flatMap(_.stringValue)

  private def parseOptBool(fields: Map[String, Json], key: String): Option[Boolean] =
    fields.get(key).flatMap(_.booleanValue)

  private def parseVocabulary(fields: Map[String, Json]): Option[Map[URI, Boolean]] = {
    fields.get("$vocabulary").flatMap(j => asObjectOption(j)).map(_.map { case (k, v) => (new URI(k), v.booleanValue.getOrElse(false)) }.toMap)
  }

  private def parseRequired(fields: Map[String, Json]): Option[Set[String]] = {
    fields.get("required").flatMap(j => asArrayOption(j)).map(_.collect { case s: Json.String => s.value }.toSet)
  }

  private def parseDependentRequired(fields: Map[String, Json]): Option[Map[String, Set[String]]] = {
    fields.get("dependentRequired").flatMap(j => asObjectOption(j)).map(_.map { case (k, v) => 
      (k, asArrayOption(v).map(_.collect { case s: Json.String => s.value }.toSet).getOrElse(Set.empty)) 
    }.toMap)
  }

  private def parseMap[A](json: Json, f: Json => Either[SchemaError, A]): Either[SchemaError, Map[String, A]] = json match {
    case obj: Json.Object =>
      val results = obj.value.map { case (k, v) => f(v).map(k -> _) }
      val errors  = results.collect { case scala.util.Left(e) => e }
      if (errors.nonEmpty) scala.util.Left(SchemaError(::(errors.head.errors.head, errors.flatMap(_.errors.tail).toList)))
      else scala.util.Right(results.collect { case scala.util.Right(kv) => kv }.toMap)
    case _ => scala.util.Left(SchemaError.expectationMismatch(Nil, "Expected object"))
  }

  private def asObjectOption(json: Json): Option[Map[String, Json]] = json match {
    case obj: Json.Object => Some(obj.value.toMap)
    case _ => None
  }

  private def asArrayOption(json: Json): Option[Chunk[Json]] = json match {
    case arr: Json.Array => Some(Chunk.fromIterable(arr.value))
    case _ => None
  }

  /** Parse a JsonSchema from a JSON string. */
  def parse(jsonString: String): Either[SchemaError, JsonSchema] =
    Json.parse(jsonString).left.map(e => SchemaError.expectationMismatch(Nil, e.message)).flatMap(fromJson)

  // implicit val schema: Schema[JsonSchema] =
  //   Schema.json.transformOrFail(json => fromJson(json).left.map(_.message), _.toJson)

  // ===========================================================================
  // Boolean Schemas
  // ===========================================================================

  /** Schema that accepts all instances. Equivalent to `{}`. */
  case object True extends JsonSchema {
    override def toJson: Json = Json.obj()
    override def check(json: Json): Option[SchemaError] = None
  }

  /** Schema that rejects all instances. Equivalent to `{"not": {}}`. */
  case object False extends JsonSchema {
    override def toJson: Json = Json.obj("not" -> Json.obj())
    override def check(json: Json): Option[SchemaError] = 
      Some(SchemaError.expectationMismatch(Nil, "Schema rejects all values"))
  }

  // ===========================================================================
  // Schema Object
  // ===========================================================================

  /**
   * A schema object containing keywords from JSON Schema 2020-12.
   * All fields are optional; an empty SchemaObject is equivalent to True.
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
    $vocabulary: Option[Map[URI, Boolean]] = None,
    $defs: Option[Map[String, JsonSchema]] = None,
    $comment: Option[String] = None,

    // =========================================================================
    // Applicator Vocabulary (Composition)
    // =========================================================================
    allOf: Option[List[JsonSchema]] = None,
    anyOf: Option[List[JsonSchema]] = None,
    oneOf: Option[List[JsonSchema]] = None,
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
    prefixItems: Option[List[JsonSchema]] = None,
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
    `enum`: Option[List[Json]] = None,
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
    uniqueItems: Option[Boolean] = None,
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
    deprecated: Option[Boolean] = None,
    readOnly: Option[Boolean] = None,
    writeOnly: Option[Boolean] = None,
    examples: Option[List[Json]] = None,

    // =========================================================================
    // Extensions
    // =========================================================================
    /** Unrecognized keywords for round-trip fidelity and vendor extensions. */
    extensions: Map[String, Json] = Map.empty
  ) extends JsonSchema {

    override def toJson: Json = {
      val fields = Chunk.newBuilder[(String, Json)]
      
      $id.foreach(v => fields.addOne(("$id", Json.str(v.value))))
      $schema.foreach(v => fields.addOne(("$schema", Json.str(v.toString))))
      $anchor.foreach(v => fields.addOne(("$anchor", Json.str(v.value))))
      $dynamicAnchor.foreach(v => fields.addOne(("$dynamicAnchor", Json.str(v.value))))
      $ref.foreach(v => fields.addOne(("$ref", Json.str(v.value))))
      $dynamicRef.foreach(v => fields.addOne(("$dynamicRef", Json.str(v.value))))
      $vocabulary.foreach { v =>
        fields.addOne(("$vocabulary", Json.obj(v.map { case (k, b) => (k.toString, Json.bool(b)) }.toSeq: _*)))
      }
      $defs.foreach { v =>
        fields.addOne(("$defs", Json.obj(v.map { case (k, s) => (k, s.toJson) }.toSeq: _*)))
      }
      $comment.foreach(v => fields.addOne(("$comment", Json.str(v))))

      allOf.foreach(v => fields.addOne(("allOf", Json.arr(v.map(_.toJson).toSeq: _*))))
      anyOf.foreach(v => fields.addOne(("anyOf", Json.arr(v.map(_.toJson).toSeq: _*))))
      oneOf.foreach(v => fields.addOne(("oneOf", Json.arr(v.map(_.toJson).toSeq: _*))))
      not.foreach(v => fields.addOne(("not", v.toJson)))

      `if`.foreach(v => fields.addOne(("if", v.toJson)))
      `then`.foreach(v => fields.addOne(("then", v.toJson)))
      `else`.foreach(v => fields.addOne(("else", v.toJson)))

      properties.foreach { v =>
        fields.addOne(("properties", Json.obj(v.map { case (k, s) => (k, s.toJson) }.toSeq: _*)))
      }
      patternProperties.foreach { v =>
        fields.addOne(("patternProperties", Json.obj(v.map { case (k, s) => (k.value, s.toJson) }.toSeq: _*)))
      }
      additionalProperties.foreach(v => fields.addOne(("additionalProperties", v.toJson)))
      propertyNames.foreach(v => fields.addOne(("propertyNames", v.toJson)))
      dependentSchemas.foreach { v =>
        fields.addOne(("dependentSchemas", Json.obj(v.map { case (k, s) => (k, s.toJson) }.toSeq: _*)))
      }

      prefixItems.foreach(v => fields.addOne(("prefixItems", Json.arr(v.map(_.toJson).toSeq: _*))))
      items.foreach(v => fields.addOne(("items", v.toJson)))
      contains.foreach(v => fields.addOne(("contains", v.toJson)))

      unevaluatedProperties.foreach(v => fields.addOne(("unevaluatedProperties", v.toJson)))
      unevaluatedItems.foreach(v => fields.addOne(("unevaluatedItems", v.toJson)))

      `type`.foreach(v => fields.addOne(("type", v.toJson)))
      `enum`.foreach(v => fields.addOne(("enum", Json.arr(v.toList: _*))))
      const.foreach(v => fields.addOne(("const", v)))

      multipleOf.foreach(v => fields.addOne(("multipleOf", Json.number(v.value))))
      maximum.foreach(v => fields.addOne(("maximum", Json.number(v))))
      exclusiveMaximum.foreach(v => fields.addOne(("exclusiveMaximum", Json.number(v))))
      minimum.foreach(v => fields.addOne(("minimum", Json.number(v))))
      exclusiveMinimum.foreach(v => fields.addOne(("exclusiveMinimum", Json.number(v))))

      minLength.foreach(v => fields.addOne(("minLength", Json.number(v.value))))
      maxLength.foreach(v => fields.addOne(("maxLength", Json.number(v.value))))
      pattern.foreach(v => fields.addOne(("pattern", Json.str(v.value))))

      minItems.foreach(v => fields.addOne(("minItems", Json.number(v.value))))
      maxItems.foreach(v => fields.addOne(("maxItems", Json.number(v.value))))
      uniqueItems.foreach(v => fields.addOne(("uniqueItems", Json.bool(v))))
      minContains.foreach(v => fields.addOne(("minContains", Json.number(v.value))))
      maxContains.foreach(v => fields.addOne(("maxContains", Json.number(v.value))))

      minProperties.foreach(v => fields.addOne(("minProperties", Json.number(v.value))))
      maxProperties.foreach(v => fields.addOne(("maxProperties", Json.number(v.value))))
      required.foreach(v => fields.addOne(("required", Json.arr(v.map(Json.str(_)).toSeq: _*))))
      dependentRequired.foreach { v =>
        fields.addOne(("dependentRequired", Json.obj(v.map { case (k, s) => (k, Json.arr(s.map(Json.str(_)).toSeq: _*)) }.toSeq: _*)))
      }

      format.foreach(v => fields.addOne(("format", Json.str(v))))
      contentEncoding.foreach(v => fields.addOne(("contentEncoding", Json.str(v))))
      contentMediaType.foreach(v => fields.addOne(("contentMediaType", Json.str(v))))
      contentSchema.foreach(v => fields.addOne(("contentSchema", v.toJson)))

      title.foreach(v => fields.addOne(("title", Json.str(v))))
      description.foreach(v => fields.addOne(("description", Json.str(v))))
      default.foreach(v => fields.addOne(("default", v)))
      deprecated.foreach(v => fields.addOne(("deprecated", Json.bool(v))))
      readOnly.foreach(v => fields.addOne(("readOnly", Json.bool(v))))
      writeOnly.foreach(v => fields.addOne(("writeOnly", Json.bool(v))))
      examples.foreach(v => fields.addOne(("examples", Json.arr(v.toList: _*))))

      extensions.foreach { case (k, v) => fields.addOne((k, v)) }

      Json.obj(fields.result().toSeq: _*)
    }


    override def check(json: Json): Option[SchemaError] = {
      val errors = List.newBuilder[SchemaError.Single]

      def addError(trace: List[DynamicOptic.Node], msg: String): Unit = {
        errors.addOne(SchemaError.ExpectationMismatch(toDynamicOptic(trace), msg))
      }

      def toDynamicOptic(trace: List[DynamicOptic.Node]): DynamicOptic = {
        val nodes = trace.toArray
        var idx1 = 0
        var idx2 = nodes.length - 1
        while (idx1 < idx2) {
          val node = nodes(idx1)
          nodes(idx1) = nodes(idx2)
          nodes(idx2) = node
          idx1 += 1
          idx2 -= 1
        }
        new DynamicOptic(zio.blocks.chunk.Chunk.fromArray(nodes))
      }

      // 1. Validation Vocabulary (Type)
      `type`.foreach {
        case SchemaType.Single(t) => if (!matchesType(json, t)) addError(Nil, s"Expected type ${t.toJsonString}, but got type index ${json.typeIndex}")
        case SchemaType.Union(ts) => if (!ts.exists(matchesType(json, _))) addError(Nil, s"Expected one of types ${ts.map(_.toJsonString).mkString(", ")}, but got type index ${json.typeIndex}")
      }

      // 2. Enum and Const
      `enum`.foreach { values =>
        if (!values.exists(_ == json)) addError(Nil, "Value not in enum")
      }
      const.foreach { value =>
        if (value != json) addError(Nil, "Value does not match constant")
      }

      // 3. Numeric Constraints
      if (json.isNumber) {
        val n = json.numberValue.get
        multipleOf.foreach(v => if (n % v.value != 0) addError(Nil, s"Value must be a multiple of ${v.value}"))
        maximum.foreach(v => if (n > v) addError(Nil, s"Value must be <= $v"))
        exclusiveMaximum.foreach(v => if (n >= v) addError(Nil, s"Value must be < $v"))
        minimum.foreach(v => if (n < v) addError(Nil, s"Value must be >= $v"))
        exclusiveMinimum.foreach(v => if (n <= v) addError(Nil, s"Value must be > $v"))
      }

      // 4. String Constraints
      if (json.isString) {
        val s = json.stringValue.get
        minLength.foreach(v => if (s.length < v.value) addError(Nil, s"String length must be at least ${v.value}"))
        maxLength.foreach(v => if (s.length > v.value) addError(Nil, s"String length must be at most ${v.value}"))
        pattern.foreach(v => if (!s.matches(v.value)) addError(Nil, s"String does not match pattern ${v.value}"))
      }

      // 5. Array Constraints
      if (json.isArray) {
        val arr = json.elements
        minItems.foreach(v => if (arr.length < v.value) addError(Nil, s"Array must have at least ${v.value} items"))
        maxItems.foreach(v => if (arr.length > v.value) addError(Nil, s"Array must have at most ${v.value} items"))
        uniqueItems.foreach(v => if (v && arr.toSet.size != arr.length) addError(Nil, "Array items must be unique"))
        
        arr.zipWithIndex.foreach { case (item, idx) =>
          val prefixSchema = prefixItems.flatMap(_.lift(idx))
          val schema = prefixSchema.orElse(items)
          
          schema.foreach { s =>
              s.check(item).foreach { e =>
              e.errors.foreach(err => addError(DynamicOptic.Node.AtIndex(idx) :: err.source.nodes.toList, err.message))
            }
          }
        }
        
        contains.foreach { schema =>
          if (!arr.exists(schema.check(_).isEmpty)) addError(Nil, "Array does not contain any item matching the schema")
        }
      }

      // 6. Object Constraints
      if (json.isObject) {
        val fieldsMap = json.fields.toMap
        minProperties.foreach(v => if (fieldsMap.size < v.value) addError(Nil, s"Object must have at least ${v.value} properties"))
        maxProperties.foreach(v => if (fieldsMap.size > v.value) addError(Nil, s"Object must have at most ${v.value} properties"))
        required.foreach(v => v.foreach(f => if (!fieldsMap.contains(f)) addError(Nil, s"Missing required property '$f'")))
        
        properties.foreach { props =>
          fieldsMap.foreach { case (k, v) =>
            props.get(k).foreach { schema =>
              schema.check(v).foreach { e =>
                e.errors.foreach(err => addError(DynamicOptic.Node.Field(k) :: err.source.nodes.toList, err.message))
              }
            }
          }
        }
        
        additionalProperties.foreach { schema =>
          fieldsMap.foreach { case (k, v) =>
            val isDefined = properties.exists(_.contains(k)) || patternProperties.exists(_.exists { case (p, _) => k.matches(p.value) })
            if (!isDefined) {
               schema.check(v).foreach { e =>
                 e.errors.foreach(err => addError(DynamicOptic.Node.Field(k) :: err.source.nodes.toList, err.message))
               }
            }
          }
        }
      }

      // 7. Composition
      allOf.foreach(ss => ss.foreach(s => s.check(json).foreach(e => e.errors.foreach(errors.addOne))))
      anyOf.foreach(ss => if (!ss.exists(_.conforms(json))) addError(Nil, "Value does not match any of 'anyOf' schemas"))
      oneOf.foreach(ss => {
        val matches = ss.count(_.conforms(json))
        if (matches != 1) addError(Nil, s"Value must match exactly one of 'oneOf' schemas, but matched $matches")
      })
      not.foreach(s => if (s.conforms(json)) addError(Nil, "Value matches 'not' schema"))

      val allErrors = errors.result()
      if (allErrors.isEmpty) None
      else Some(SchemaError(::(allErrors.head, allErrors.tail)))
    }

    private def matchesType(json: Json, t: JsonType): Boolean = t match {
      case JsonType.Null    => json.isNull
      case JsonType.Boolean => json.isBoolean
      case JsonType.String  => json.isString
      case JsonType.Number  => json.isNumber
      case JsonType.Integer => json.isNumber && {
        val n = json.numberValue.get
        n.isWhole
      }
      case JsonType.Array   => json.isArray
      case JsonType.Object  => json.isObject
    }
  }

  object SchemaObject {
    val empty: SchemaObject = SchemaObject()
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  def ofType(t: JsonType): JsonSchema = SchemaObject(`type` = Some(SchemaType.Single(t)))

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
    multipleOf: Option[PositiveNumber] = None,
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Number)),
    multipleOf = multipleOf,
    minimum = minimum,
    maximum = maximum,
    exclusiveMinimum = exclusiveMinimum,
    exclusiveMaximum = exclusiveMaximum
  )

  def integer(
    multipleOf: Option[PositiveNumber] = None,
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Integer)),
    multipleOf = multipleOf,
    minimum = minimum,
    maximum = maximum,
    exclusiveMinimum = exclusiveMinimum,
    exclusiveMaximum = exclusiveMaximum
  )

  def array(
    items: Option[JsonSchema] = None,
    minItems: Option[NonNegativeInt] = None,
    maxItems: Option[NonNegativeInt] = None,
    uniqueItems: Option[Boolean] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Array)),
    items = items,
    minItems = minItems,
    maxItems = maxItems,
    uniqueItems = uniqueItems
  )

  def `object`(
    properties: Option[Map[String, JsonSchema]] = None,
    required: Option[Set[String]] = None,
    additionalProperties: Option[JsonSchema] = None
  ): JsonSchema = SchemaObject(
    `type` = Some(SchemaType.Single(JsonType.Object)),
    properties = properties,
    required = required,
    additionalProperties = additionalProperties
  )

  def enumOf(values: List[Json]): JsonSchema =
    SchemaObject(`enum` = if (values.isEmpty) None else Some(values))

  def constOf(value: Json): JsonSchema = SchemaObject(const = Some(value))

  def ref(uri: UriReference): JsonSchema = SchemaObject($ref = Some(uri))

  val `null`: JsonSchema = ofType(JsonType.Null)

  val boolean: JsonSchema = ofType(JsonType.Boolean)
}

object JsonSchemaInstances {
  implicit val schema: Schema[JsonSchema] =
    Schema.json.transformOrFail(json => JsonSchema.fromJson(json).left.map(_.message), _.toJson)
}
