package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.blocks.chunk.Chunk

/**
 * Codec for encoding/decoding JsonSchema to/from Json.
 */
object JsonSchemaCodec {

  // ─────────────────────────────────────────────────────────────────────────
  // Encoding
  // ─────────────────────────────────────────────────────────────────────────

  def encode(schema: JsonSchema): Json = schema match {
    case JsonSchema.True              => Json.True
    case JsonSchema.False             => Json.False
    case obj: JsonSchema.SchemaObject => encodeSchemaObject(obj)
  }

  private def encodeSchemaObject(schema: JsonSchema.SchemaObject): Json = {
    val fields = new scala.collection.mutable.ArrayBuffer[(String, Json)]()

    // Core vocabulary
    schema.`$id`.foreach(v => fields += "$id" -> Json.str(v))
    schema.`$schema`.foreach(v => fields += "$schema" -> Json.str(v))
    schema.`$ref`.foreach(v => fields += "$ref" -> Json.str(v))
    schema.`$anchor`.foreach(v => fields += "$anchor" -> Json.str(v))
    schema.`$defs`.foreach { defs =>
      fields += "$defs" -> Json.obj(defs.map { case (k, v) => k -> encode(v) }.toSeq: _*)
    }
    schema.`$comment`.foreach(v => fields += "$comment" -> Json.str(v))
    schema.`$dynamicAnchor`.foreach(v => fields += "$dynamicAnchor" -> Json.str(v))
    schema.`$dynamicRef`.foreach(v => fields += "$dynamicRef" -> Json.str(v))

    // Type
    schema.`type`.foreach(t => fields += "type" -> encodeType(t))

    // String constraints
    schema.minLength.foreach(v => fields += "minLength" -> Json.number(v))
    schema.maxLength.foreach(v => fields += "maxLength" -> Json.number(v))
    schema.pattern.foreach(v => fields += "pattern" -> Json.str(v))
    schema.format.foreach(v => fields += "format" -> Json.str(v))

    // Content vocabulary
    schema.contentEncoding.foreach(v => fields += "contentEncoding" -> Json.str(v))
    schema.contentMediaType.foreach(v => fields += "contentMediaType" -> Json.str(v))
    schema.contentSchema.foreach(v => fields += "contentSchema" -> encode(v))

    // Numeric constraints
    schema.minimum.foreach(v => fields += "minimum" -> Json.number(v))
    schema.maximum.foreach(v => fields += "maximum" -> Json.number(v))
    schema.exclusiveMinimum.foreach(v => fields += "exclusiveMinimum" -> Json.number(v))
    schema.exclusiveMaximum.foreach(v => fields += "exclusiveMaximum" -> Json.number(v))
    schema.multipleOf.foreach(v => fields += "multipleOf" -> Json.number(v))

    // Array constraints
    schema.items.foreach(v => fields += "items" -> encode(v))
    schema.prefixItems.foreach { items =>
      fields += "prefixItems" -> Json.arr(items.map(encode): _*)
    }
    schema.contains.foreach(v => fields += "contains" -> encode(v))
    schema.minContains.foreach(v => fields += "minContains" -> Json.number(v))
    schema.maxContains.foreach(v => fields += "maxContains" -> Json.number(v))
    schema.minItems.foreach(v => fields += "minItems" -> Json.number(v))
    schema.maxItems.foreach(v => fields += "maxItems" -> Json.number(v))
    schema.uniqueItems.foreach(v => fields += "uniqueItems" -> Json.bool(v))
    schema.unevaluatedItems.foreach(v => fields += "unevaluatedItems" -> encode(v))

    // Object constraints
    schema.properties.foreach { props =>
      fields += "properties" -> Json.obj(props.map { case (k, v) => k -> encode(v) }.toSeq: _*)
    }
    schema.patternProperties.foreach { props =>
      fields += "patternProperties" -> Json.obj(props.map { case (k, v) => k -> encode(v) }.toSeq: _*)
    }
    schema.additionalProperties.foreach(v => fields += "additionalProperties" -> encode(v))
    schema.required.foreach { req =>
      fields += "required" -> Json.arr(req.map(Json.str).toSeq: _*)
    }
    schema.minProperties.foreach(v => fields += "minProperties" -> Json.number(v))
    schema.maxProperties.foreach(v => fields += "maxProperties" -> Json.number(v))
    schema.propertyNames.foreach(v => fields += "propertyNames" -> encode(v))
    schema.unevaluatedProperties.foreach(v => fields += "unevaluatedProperties" -> encode(v))
    schema.dependentRequired.foreach { deps =>
      fields += "dependentRequired" -> Json.obj(deps.map { case (k, v) =>
        k -> Json.arr(v.map(Json.str).toSeq: _*)
      }.toSeq: _*)
    }
    schema.dependentSchemas.foreach { deps =>
      fields += "dependentSchemas" -> Json.obj(deps.map { case (k, v) =>
        k -> encode(v)
      }.toSeq: _*)
    }

    // Composition
    schema.allOf.foreach(v => fields += "allOf" -> Json.arr(v.map(encode): _*))
    schema.anyOf.foreach(v => fields += "anyOf" -> Json.arr(v.map(encode): _*))
    schema.oneOf.foreach(v => fields += "oneOf" -> Json.arr(v.map(encode): _*))
    schema.not.foreach(v => fields += "not" -> encode(v))

    // Conditionals
    schema.`if`.foreach(v => fields += "if" -> encode(v))
    schema.`then`.foreach(v => fields += "then" -> encode(v))
    schema.`else`.foreach(v => fields += "else" -> encode(v))

    // Enumeration
    schema.`enum`.foreach(v => fields += "enum" -> Json.arr(v: _*))
    schema.const.foreach(v => fields += "const" -> v)

    // Metadata
    schema.title.foreach(v => fields += "title" -> Json.str(v))
    schema.description.foreach(v => fields += "description" -> Json.str(v))
    schema.default.foreach(v => fields += "default" -> v)
    schema.examples.foreach(v => fields += "examples" -> Json.arr(v: _*))
    schema.deprecated.foreach(v => fields += "deprecated" -> Json.bool(v))
    schema.readOnly.foreach(v => fields += "readOnly" -> Json.bool(v))
    schema.writeOnly.foreach(v => fields += "writeOnly" -> Json.bool(v))

    // Extensions (unknown keywords preserved from parsing)
    schema.extensions.foreach { exts =>
      exts.foreach { case (k, v) => fields += k -> v }
    }

    Json.Object(Chunk.from(fields.toSeq))
  }

  private def encodeType(t: JsonType): Json = t match {
    case JsonType.Union(types) =>
      Json.arr(types.map(t => Json.str(t.name)).toSeq: _*)
    case single =>
      Json.str(single.name)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Decoding
  // ─────────────────────────────────────────────────────────────────────────

  def decode(json: Json): Either[JsonSchemaError, JsonSchema] = json match {
    case Json.True        => Right(JsonSchema.True)
    case Json.False       => Right(JsonSchema.False)
    case obj: Json.Object => decodeSchemaObject(obj)
    case _                =>
      Left(
        JsonSchemaError(
          JsonSchemaError.SchemaParseError(DynamicOptic.root, "Schema must be boolean or object")
        )
      )
  }

  private def decodeSchemaObject(obj: Json.Object): Either[JsonSchemaError, JsonSchema.SchemaObject] = {
    val fields = obj.value.toMap

    for {
      typeOpt                  <- decodeTypeOpt(fields)
      defsOpt                  <- decodeDefsOpt(fields)
      propertiesOpt            <- decodePropertiesOpt(fields, "properties")
      patternPropsOpt          <- decodePropertiesOpt(fields, "patternProperties")
      additionalPropsOpt       <- decodeSchemaOpt(fields, "additionalProperties")
      itemsOpt                 <- decodeSchemaOpt(fields, "items")
      prefixItemsOpt           <- decodeSchemaArrayOpt(fields, "prefixItems")
      containsOpt              <- decodeSchemaOpt(fields, "contains")
      propertyNamesOpt         <- decodeSchemaOpt(fields, "propertyNames")
      dependentSchemasOpt      <- decodePropertiesOpt(fields, "dependentSchemas")
      allOfOpt                 <- decodeSchemaArrayOpt(fields, "allOf")
      anyOfOpt                 <- decodeSchemaArrayOpt(fields, "anyOf")
      oneOfOpt                 <- decodeSchemaArrayOpt(fields, "oneOf")
      notOpt                   <- decodeSchemaOpt(fields, "not")
      ifOpt                    <- decodeSchemaOpt(fields, "if")
      thenOpt                  <- decodeSchemaOpt(fields, "then")
      elseOpt                  <- decodeSchemaOpt(fields, "else")
      contentSchemaOpt         <- decodeSchemaOpt(fields, "contentSchema")
      unevaluatedItemsOpt      <- decodeSchemaOpt(fields, "unevaluatedItems")
      unevaluatedPropertiesOpt <- decodeSchemaOpt(fields, "unevaluatedProperties")
    } yield JsonSchema.SchemaObject(
      `$id` = getStringOpt(fields, "$id"),
      `$schema` = getStringOpt(fields, "$schema"),
      `$ref` = getStringOpt(fields, "$ref"),
      `$anchor` = getStringOpt(fields, "$anchor"),
      `$defs` = defsOpt,
      `$comment` = getStringOpt(fields, "$comment"),
      `$dynamicAnchor` = getStringOpt(fields, "$dynamicAnchor"),
      `$dynamicRef` = getStringOpt(fields, "$dynamicRef"),
      `type` = typeOpt,
      minLength = getIntOpt(fields, "minLength"),
      maxLength = getIntOpt(fields, "maxLength"),
      pattern = getStringOpt(fields, "pattern"),
      format = getStringOpt(fields, "format"),
      contentEncoding = getStringOpt(fields, "contentEncoding"),
      contentMediaType = getStringOpt(fields, "contentMediaType"),
      contentSchema = contentSchemaOpt,
      minimum = getBigDecimalOpt(fields, "minimum"),
      maximum = getBigDecimalOpt(fields, "maximum"),
      exclusiveMinimum = getBigDecimalOpt(fields, "exclusiveMinimum"),
      exclusiveMaximum = getBigDecimalOpt(fields, "exclusiveMaximum"),
      multipleOf = getBigDecimalOpt(fields, "multipleOf"),
      items = itemsOpt,
      prefixItems = prefixItemsOpt,
      contains = containsOpt,
      minContains = getIntOpt(fields, "minContains"),
      maxContains = getIntOpt(fields, "maxContains"),
      minItems = getIntOpt(fields, "minItems"),
      maxItems = getIntOpt(fields, "maxItems"),
      uniqueItems = getBooleanOpt(fields, "uniqueItems"),
      unevaluatedItems = unevaluatedItemsOpt,
      properties = propertiesOpt,
      patternProperties = patternPropsOpt,
      additionalProperties = additionalPropsOpt,
      required = getStringSetOpt(fields, "required"),
      minProperties = getIntOpt(fields, "minProperties"),
      maxProperties = getIntOpt(fields, "maxProperties"),
      propertyNames = propertyNamesOpt,
      unevaluatedProperties = unevaluatedPropertiesOpt,
      dependentRequired = getDependentRequiredOpt(fields),
      dependentSchemas = dependentSchemasOpt,
      allOf = allOfOpt,
      anyOf = anyOfOpt,
      oneOf = oneOfOpt,
      not = notOpt,
      `if` = ifOpt,
      `then` = thenOpt,
      `else` = elseOpt,
      `enum` = getJsonArrayOpt(fields, "enum"),
      const = fields.get("const"),
      title = getStringOpt(fields, "title"),
      description = getStringOpt(fields, "description"),
      default = fields.get("default"),
      examples = getJsonArrayOpt(fields, "examples"),
      deprecated = getBooleanOpt(fields, "deprecated"),
      readOnly = getBooleanOpt(fields, "readOnly"),
      writeOnly = getBooleanOpt(fields, "writeOnly"),
      extensions = collectExtensions(fields)
    )
  }

  // Known keywords for extensions filtering
  private val knownKeywords: Set[String] = Set(
    "$id",
    "$schema",
    "$ref",
    "$anchor",
    "$defs",
    "definitions",
    "$comment",
    "$dynamicAnchor",
    "$dynamicRef",
    "type",
    "minLength",
    "maxLength",
    "pattern",
    "format",
    "contentEncoding",
    "contentMediaType",
    "contentSchema",
    "minimum",
    "maximum",
    "exclusiveMinimum",
    "exclusiveMaximum",
    "multipleOf",
    "items",
    "prefixItems",
    "contains",
    "minContains",
    "maxContains",
    "minItems",
    "maxItems",
    "uniqueItems",
    "unevaluatedItems",
    "properties",
    "patternProperties",
    "additionalProperties",
    "required",
    "minProperties",
    "maxProperties",
    "propertyNames",
    "unevaluatedProperties",
    "dependentRequired",
    "dependentSchemas",
    "allOf",
    "anyOf",
    "oneOf",
    "not",
    "if",
    "then",
    "else",
    "enum",
    "const",
    "title",
    "description",
    "default",
    "examples",
    "deprecated",
    "readOnly",
    "writeOnly"
  )

  private def collectExtensions(fields: Map[String, Json]): Option[Map[String, Json]] = {
    val exts = fields.view.filterKeys(!knownKeywords.contains(_)).toMap
    if (exts.isEmpty) None else Some(exts)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helper decoders
  // ─────────────────────────────────────────────────────────────────────────

  private def decodeTypeOpt(fields: Map[String, Json]): Either[JsonSchemaError, Option[JsonType]] =
    fields.get("type") match {
      case None                    => Right(None)
      case Some(Json.String(name)) =>
        JsonType.fromString(name) match {
          case Some(t) => Right(Some(t))
          case None    =>
            Left(
              JsonSchemaError(
                JsonSchemaError.SchemaParseError(DynamicOptic.root, s"Unknown type: $name")
              )
            )
        }
      case Some(arr: Json.Array) =>
        val types = arr.value.collect { case Json.String(name) =>
          JsonType.fromString(name)
        }.flatten
        if (types.isEmpty)
          Left(
            JsonSchemaError(
              JsonSchemaError.SchemaParseError(DynamicOptic.root, "Invalid type array")
            )
          )
        else Right(Some(JsonType.Union(types.toSet)))
      case Some(_) =>
        Left(
          JsonSchemaError(
            JsonSchemaError.SchemaParseError(DynamicOptic.root, "type must be string or array")
          )
        )
    }

  private def decodeDefsOpt(fields: Map[String, Json]): Either[JsonSchemaError, Option[Map[String, JsonSchema]]] =
    fields.get("$defs").orElse(fields.get("definitions")) match {
      case None                   => Right(None)
      case Some(obj: Json.Object) =>
        val results = obj.value.map { case (k, v) => k -> decode(v) }
        val errors  = results.collect { case (_, Left(e)) => e }
        if (errors.nonEmpty) Left(errors.head)
        else Right(Some(results.collect { case (k, Right(v)) => k -> v }.toMap))
      case Some(_) =>
        Left(
          JsonSchemaError(
            JsonSchemaError.SchemaParseError(DynamicOptic.root, "$defs must be object")
          )
        )
    }

  private def decodePropertiesOpt(
    fields: Map[String, Json],
    key: String
  ): Either[JsonSchemaError, Option[Map[String, JsonSchema]]] =
    fields.get(key) match {
      case None                   => Right(None)
      case Some(obj: Json.Object) =>
        val results = obj.value.map { case (k, v) => k -> decode(v) }
        val errors  = results.collect { case (_, Left(e)) => e }
        if (errors.nonEmpty) Left(errors.head)
        else Right(Some(results.collect { case (k, Right(v)) => k -> v }.toMap))
      case Some(_) =>
        Left(
          JsonSchemaError(
            JsonSchemaError.SchemaParseError(DynamicOptic.root, s"$key must be object")
          )
        )
    }

  private def decodeSchemaOpt(fields: Map[String, Json], key: String): Either[JsonSchemaError, Option[JsonSchema]] =
    fields.get(key) match {
      case None       => Right(None)
      case Some(json) => decode(json).map(Some(_))
    }

  private def decodeSchemaArrayOpt(
    fields: Map[String, Json],
    key: String
  ): Either[JsonSchemaError, Option[Vector[JsonSchema]]] =
    fields.get(key) match {
      case None                  => Right(None)
      case Some(arr: Json.Array) =>
        val results = arr.value.map(decode)
        val errors  = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.head)
        else Right(Some(results.collect { case Right(v) => v }))
      case Some(_) =>
        Left(
          JsonSchemaError(
            JsonSchemaError.SchemaParseError(DynamicOptic.root, s"$key must be array")
          )
        )
    }

  private def getStringOpt(fields: Map[String, Json], key: String): Option[String] =
    fields.get(key).flatMap(_.stringValue)

  private def getIntOpt(fields: Map[String, Json], key: String): Option[Int] =
    fields.get(key).flatMap(_.numberValue).map(_.toInt)

  private def getBigDecimalOpt(fields: Map[String, Json], key: String): Option[BigDecimal] =
    fields.get(key).flatMap(_.numberValue)

  private def getBooleanOpt(fields: Map[String, Json], key: String): Option[Boolean] =
    fields.get(key).flatMap(_.booleanValue)

  private def getStringSetOpt(fields: Map[String, Json], key: String): Option[Set[String]] =
    fields.get(key).flatMap {
      case arr: Json.Array => Some(arr.value.collect { case Json.String(s) => s }.toSet)
      case _               => None
    }

  private def getJsonArrayOpt(fields: Map[String, Json], key: String): Option[Vector[Json]] =
    fields.get(key).flatMap {
      case arr: Json.Array => Some(arr.value)
      case _               => None
    }

  private def getDependentRequiredOpt(fields: Map[String, Json]): Option[Map[String, Set[String]]] =
    fields.get("dependentRequired").flatMap {
      case obj: Json.Object =>
        Some(obj.value.collect { case (k, arr: Json.Array) =>
          k -> arr.value.collect { case Json.String(s) => s }.toSet
        }.toMap)
      case _ => None
    }
}
