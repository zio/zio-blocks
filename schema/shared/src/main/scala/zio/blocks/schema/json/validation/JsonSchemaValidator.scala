package zio.blocks.schema.json.validation

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json._
import scala.util.matching.Regex

/**
 * Core JSON Schema 2020-12 validator.
 *
 * Validates JSON values against JsonSchema instances, collecting all validation
 * errors with path information. Fully implements unevaluatedProperties and
 * unevaluatedItems per the 2020-12 specification.
 */
object JsonSchemaValidator {

  /**
   * Tracks which properties and items have been evaluated during validation.
   * This is required for unevaluatedProperties and unevaluatedItems keywords.
   */
  final case class EvaluationContext(
    evaluatedProperties: Set[String] = Set.empty,
    evaluatedItems: Set[Int] = Set.empty
  ) {
    def withProperty(prop: String): EvaluationContext =
      copy(evaluatedProperties = evaluatedProperties + prop)

    def withProperties(props: Iterable[String]): EvaluationContext =
      copy(evaluatedProperties = evaluatedProperties ++ props)

    def withItem(idx: Int): EvaluationContext =
      copy(evaluatedItems = evaluatedItems + idx)

    def withItems(indices: Iterable[Int]): EvaluationContext =
      copy(evaluatedItems = evaluatedItems ++ indices)

    def merge(other: EvaluationContext): EvaluationContext =
      EvaluationContext(
        evaluatedProperties ++ other.evaluatedProperties,
        evaluatedItems ++ other.evaluatedItems
      )
  }

  object EvaluationContext {
    val empty: EvaluationContext = EvaluationContext()
  }

  /**
   * Result of validation including evaluation tracking.
   */
  final case class ValidationResult(
    errors: List[JsonSchemaError.Single],
    context: EvaluationContext
  ) {
    def isValid: Boolean = errors.isEmpty

    def ++(other: ValidationResult): ValidationResult =
      ValidationResult(errors ++ other.errors, context.merge(other.context))

    def withError(err: JsonSchemaError.Single): ValidationResult =
      copy(errors = errors :+ err)

    def withErrors(errs: List[JsonSchemaError.Single]): ValidationResult =
      copy(errors = errors ++ errs)
  }

  object ValidationResult {
    val success: ValidationResult = ValidationResult(Nil, EvaluationContext.empty)

    def error(err: JsonSchemaError.Single): ValidationResult =
      ValidationResult(List(err), EvaluationContext.empty)

    def fromEither(result: Either[JsonSchemaError, Unit], ctx: EvaluationContext): ValidationResult =
      result match {
        case Right(_)  => ValidationResult(Nil, ctx)
        case Left(err) => ValidationResult(err.errors.toList, ctx)
      }
  }

  /**
   * Validates a JSON value against a schema.
   *
   * @param schema
   *   The schema to validate against
   * @param json
   *   The JSON value to validate
   * @param path
   *   Current path in the JSON document
   * @param registry
   *   Schema registry for $ref resolution
   * @return
   *   Right(()) if valid, Left(errors) if invalid
   */
  def validate(
    schema: JsonSchema,
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry
  ): Either[JsonSchemaError, Unit] = {
    val result = validateWithTracking(schema, json, path, registry, schema)
    if (result.errors.isEmpty) Right(())
    else
      result.errors match {
        case head :: tail => Left(JsonSchemaError(new ::(head, tail)))
        case Nil          => Right(())
      }
  }

  /**
   * Internal validation that tracks evaluated properties/items.
   */
  private def validateWithTracking(
    schema: JsonSchema,
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = schema match {
    case JsonSchema.True  => ValidationResult.success
    case JsonSchema.False =>
      ValidationResult.error(JsonSchemaError.ConstraintViolation(path, "false schema", "any"))
    case obj: JsonSchema.SchemaObject =>
      validateSchemaObject(obj, json, path, registry, rootSchema)
  }

  private def validateSchemaObject(
    schema: JsonSchema.SchemaObject,
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    val errors  = List.newBuilder[JsonSchemaError.Single]
    var evalCtx = EvaluationContext.empty

    // Handle $ref first (takes precedence)
    schema.`$ref`.foreach { ref =>
      registry.resolve(ref, rootSchema) match {
        case Right(refSchema) =>
          val refResult = validateWithTracking(refSchema, json, path, registry, rootSchema)
          errors ++= refResult.errors
          evalCtx = evalCtx.merge(refResult.context)
        case Left(err) => errors ++= err.errors
      }
    }

    // Type validation
    schema.`type`.foreach { expectedType =>
      validateType(expectedType, json, path) match {
        case Some(err) => errors += err
        case None      => ()
      }
    }

    // Enum validation
    schema.`enum`.foreach { allowed =>
      if (!allowed.exists(_ == json)) {
        errors += JsonSchemaError.NotInEnum(path, json, allowed)
      }
    }

    // Const validation
    schema.const.foreach { expected =>
      if (json != expected) {
        errors += JsonSchemaError.ConstMismatch(path, expected, json)
      }
    }

    // Type-specific validations with evaluation tracking
    json match {
      case str: Json.String =>
        validateString(schema, str.value, path, errors)
      case num: Json.Number =>
        num.numberValue.foreach(n => validateNumber(schema, n, path, errors))
      case arr: Json.Array =>
        val arrResult = validateArray(schema, arr, path, registry, rootSchema)
        errors ++= arrResult.errors
        evalCtx = evalCtx.merge(arrResult.context)
      case obj: Json.Object =>
        val objResult = validateObject(schema, obj, path, registry, rootSchema)
        errors ++= objResult.errors
        evalCtx = evalCtx.merge(objResult.context)
      case _ => ()
    }

    // Composition keywords - with evaluation tracking
    schema.allOf.foreach { schemas =>
      val allOfResult = validateAllOfWithTracking(schemas, json, path, registry, rootSchema)
      errors ++= allOfResult.errors
      evalCtx = evalCtx.merge(allOfResult.context)
    }

    schema.anyOf.foreach { schemas =>
      val anyOfResult = validateAnyOfWithTracking(schemas, json, path, registry, rootSchema)
      errors ++= anyOfResult.errors
      evalCtx = evalCtx.merge(anyOfResult.context)
    }

    schema.oneOf.foreach { schemas =>
      val oneOfResult = validateOneOfWithTracking(schemas, json, path, registry, rootSchema)
      errors ++= oneOfResult.errors
      evalCtx = evalCtx.merge(oneOfResult.context)
    }

    schema.not.foreach { notSchema =>
      if (validateWithTracking(notSchema, json, path, registry, rootSchema).isValid) {
        errors += JsonSchemaError.CompositionFailed(path, "not", "value must not match schema")
      }
    }

    // Conditional keywords with evaluation tracking
    (schema.`if`, schema.`then`, schema.`else`) match {
      case (Some(ifSchema), thenSchema, elseSchema) =>
        val ifResult = validateWithTracking(ifSchema, json, path, registry, rootSchema)
        if (ifResult.isValid) {
          evalCtx = evalCtx.merge(ifResult.context)
          thenSchema.foreach { ts =>
            val thenResult = validateWithTracking(ts, json, path, registry, rootSchema)
            errors ++= thenResult.errors
            evalCtx = evalCtx.merge(thenResult.context)
          }
        } else {
          elseSchema.foreach { es =>
            val elseResult = validateWithTracking(es, json, path, registry, rootSchema)
            errors ++= elseResult.errors
            evalCtx = evalCtx.merge(elseResult.context)
          }
        }
      case _ => ()
    }

    // unevaluatedProperties validation
    json match {
      case obj: Json.Object =>
        schema.unevaluatedProperties.foreach { unevalSchema =>
          obj.value.foreach { case (propName, propValue) =>
            if (!evalCtx.evaluatedProperties.contains(propName)) {
              val propResult = validateWithTracking(unevalSchema, propValue, path.field(propName), registry, rootSchema)
              if (!propResult.isValid) {
                unevalSchema match {
                  case JsonSchema.False =>
                    errors += JsonSchemaError.AdditionalPropertyNotAllowed(path, propName)
                  case _ =>
                    errors ++= propResult.errors
                }
              }
            }
          }
        }
      case _ => ()
    }

    // unevaluatedItems validation
    json match {
      case arr: Json.Array =>
        schema.unevaluatedItems.foreach { unevalSchema =>
          arr.value.zipWithIndex.foreach { case (item, idx) =>
            if (!evalCtx.evaluatedItems.contains(idx)) {
              val itemResult = validateWithTracking(unevalSchema, item, path.at(idx), registry, rootSchema)
              if (!itemResult.isValid) {
                unevalSchema match {
                  case JsonSchema.False =>
                    errors += JsonSchemaError.ConstraintViolation(
                      path.at(idx),
                      "unevaluatedItems",
                      s"item at index $idx is not allowed"
                    )
                  case _ =>
                    errors ++= itemResult.errors
                }
              }
            }
          }
        }
      case _ => ()
    }

    ValidationResult(errors.result(), evalCtx)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Validation
  // ─────────────────────────────────────────────────────────────────────────

  private def validateType(
    expected: JsonType,
    json: Json,
    path: DynamicOptic
  ): Option[JsonSchemaError.Single] = {
    val actual = JsonType.of(json)
    expected match {
      case JsonType.Union(types) =>
        // Check if actual matches any type (with integer<->numeric compatibility)
        val matches = types.exists { t =>
          t == actual || (t == JsonType.Integer && isInteger(
            json
          )) || (t == JsonType.Number && actual == JsonType.Integer)
        }
        if (matches) None
        else Some(JsonSchemaError.TypeMismatch(path, expected, actual))

      case JsonType.Integer =>
        if (isInteger(json)) None
        else Some(JsonSchemaError.TypeMismatch(path, expected, actual))

      case JsonType.Number =>
        if (actual == JsonType.Number || actual == JsonType.Integer) None
        else Some(JsonSchemaError.TypeMismatch(path, expected, actual))

      case _ =>
        if (actual == expected) None
        else Some(JsonSchemaError.TypeMismatch(path, expected, actual))
    }
  }

  private def isInteger(json: Json): Boolean = json match {
    case n: Json.Number =>
      n.numberValue.exists { bd =>
        try {
          bd.isWhole || bd.toBigIntExact.isDefined
        } catch {
          case _: ArithmeticException => false
        }
      }
    case _ => false
  }

  // ─────────────────────────────────────────────────────────────────────────
  // String Validation
  // ─────────────────────────────────────────────────────────────────────────

  private def validateString(
    schema: JsonSchema.SchemaObject,
    value: String,
    path: DynamicOptic,
    errors: collection.mutable.Builder[JsonSchemaError.Single, List[JsonSchemaError.Single]]
  ): Unit = {
    val length = value.codePointCount(0, value.length)

    schema.minLength.foreach { min =>
      if (length < min) {
        errors += JsonSchemaError.LengthViolated(path, "minLength", min, length)
      }
    }

    schema.maxLength.foreach { max =>
      if (length > max) {
        errors += JsonSchemaError.LengthViolated(path, "maxLength", max, length)
      }
    }

    schema.pattern.foreach { pattern =>
      try {
        val regex = new Regex(pattern)
        if (!regex.findFirstIn(value).isDefined) {
          errors += JsonSchemaError.PatternMismatch(path, pattern, value)
        }
      } catch {
        case _: Exception =>
          errors += JsonSchemaError.ConstraintViolation(path, s"invalid pattern: $pattern", value)
      }
    }

    schema.format.foreach { format =>
      if (!FormatValidators.validate(format, value)) {
        errors += JsonSchemaError.FormatInvalid(path, format, value)
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Numeric Validation
  // ─────────────────────────────────────────────────────────────────────────

  private def validateNumber(
    schema: JsonSchema.SchemaObject,
    value: BigDecimal,
    path: DynamicOptic,
    errors: collection.mutable.Builder[JsonSchemaError.Single, List[JsonSchemaError.Single]]
  ): Unit = {
    schema.minimum.foreach { min =>
      if (value < min) {
        errors += JsonSchemaError.MinimumViolated(path, min, value, exclusive = false)
      }
    }

    schema.maximum.foreach { max =>
      if (value > max) {
        errors += JsonSchemaError.MaximumViolated(path, max, value, exclusive = false)
      }
    }

    schema.exclusiveMinimum.foreach { min =>
      if (value <= min) {
        errors += JsonSchemaError.MinimumViolated(path, min, value, exclusive = true)
      }
    }

    schema.exclusiveMaximum.foreach { max =>
      if (value >= max) {
        errors += JsonSchemaError.MaximumViolated(path, max, value, exclusive = true)
      }
    }

    schema.multipleOf.foreach { mult =>
      if (mult != BigDecimal(0)) {
        if ((value / mult) % 1 != BigDecimal(0)) {
          errors += JsonSchemaError.MultipleOfViolated(path, mult, value)
        }
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Array Validation
  // ─────────────────────────────────────────────────────────────────────────

  private def validateArray(
    schema: JsonSchema.SchemaObject,
    arr: Json.Array,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    val errors         = List.newBuilder[JsonSchemaError.Single]
    var evaluatedItems = Set.empty[Int]
    val items          = arr.value
    val size           = items.length

    schema.minItems.foreach { min =>
      if (size < min) {
        errors += JsonSchemaError.ItemsCountViolated(path, "minItems", min, size)
      }
    }

    schema.maxItems.foreach { max =>
      if (size > max) {
        errors += JsonSchemaError.ItemsCountViolated(path, "maxItems", max, size)
      }
    }

    schema.uniqueItems.foreach { unique =>
      if (unique) {
        val seen = collection.mutable.Set.empty[Json]
        items.zipWithIndex.foreach { case (item, idx) =>
          if (seen.contains(item)) {
            errors += JsonSchemaError.UniqueItemsViolated(path, idx)
          } else {
            seen += item
          }
        }
      }
    }

    // prefixItems validation
    schema.prefixItems.foreach { prefixSchemas =>
      prefixSchemas.zipWithIndex.foreach { case (itemSchema, idx) =>
        if (idx < items.length) {
          evaluatedItems += idx
          val result = validateWithTracking(itemSchema, items(idx), path.at(idx), registry, rootSchema)
          errors ++= result.errors
        }
      }
    }

    // items validation (for items beyond prefixItems)
    schema.items.foreach { itemSchema =>
      val startIdx = schema.prefixItems.map(_.length).getOrElse(0)
      items.drop(startIdx).zipWithIndex.foreach { case (item, relIdx) =>
        val idx = startIdx + relIdx
        evaluatedItems += idx
        val result = validateWithTracking(itemSchema, item, path.at(idx), registry, rootSchema)
        errors ++= result.errors
      }
    }

    // contains validation
    schema.contains.foreach { containsSchema =>
      val matchingIndices = items.zipWithIndex.collect {
        case (item, idx) if validateWithTracking(containsSchema, item, path, registry, rootSchema).isValid => idx
      }
      val matchCount  = matchingIndices.length
      val minContains = schema.minContains.getOrElse(1)
      val maxContains = schema.maxContains

      // Items matching contains are evaluated
      evaluatedItems ++= matchingIndices

      if (matchCount < minContains) {
        errors += JsonSchemaError.ContainsNotSatisfied(path, minContains, maxContains, matchCount)
      }
      maxContains.foreach { max =>
        if (matchCount > max) {
          errors += JsonSchemaError.ContainsNotSatisfied(path, minContains, maxContains, matchCount)
        }
      }
    }

    ValidationResult(errors.result(), EvaluationContext(evaluatedItems = evaluatedItems))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Object Validation
  // ─────────────────────────────────────────────────────────────────────────

  private def validateObject(
    schema: JsonSchema.SchemaObject,
    obj: Json.Object,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    val errors         = List.newBuilder[JsonSchemaError.Single]
    var evaluatedProps = Set.empty[String]
    val fields         = obj.value
    val fieldMap       = fields.toMap
    val propCount      = fields.length

    schema.minProperties.foreach { min =>
      if (propCount < min) {
        errors += JsonSchemaError.PropertiesCountViolated(path, "minProperties", min, propCount)
      }
    }

    schema.maxProperties.foreach { max =>
      if (propCount > max) {
        errors += JsonSchemaError.PropertiesCountViolated(path, "maxProperties", max, propCount)
      }
    }

    // Required validation
    schema.required.foreach { requiredFields =>
      requiredFields.foreach { field =>
        if (!fieldMap.contains(field)) {
          errors += JsonSchemaError.RequiredMissing(path, field)
        }
      }
    }

    // Properties validation
    schema.properties.foreach { propSchemas =>
      propSchemas.foreach { case (propName, propSchema) =>
        fieldMap.get(propName).foreach { propValue =>
          evaluatedProps += propName
          val result = validateWithTracking(propSchema, propValue, path.field(propName), registry, rootSchema)
          errors ++= result.errors
        }
      }
    }

    // Pattern properties validation
    schema.patternProperties.foreach { patternSchemas =>
      patternSchemas.foreach { case (pattern, propSchema) =>
        try {
          val regex = new Regex(pattern)
          fields.foreach { case (propName, propValue) =>
            if (regex.findFirstIn(propName).isDefined) {
              evaluatedProps += propName
              val result = validateWithTracking(propSchema, propValue, path.field(propName), registry, rootSchema)
              errors ++= result.errors
            }
          }
        } catch {
          case _: Exception =>
            errors += JsonSchemaError.ConstraintViolation(path, s"invalid pattern: $pattern", "")
        }
      }
    }

    // Additional properties validation
    schema.additionalProperties.foreach { additionalSchema =>
      fields.foreach { case (propName, propValue) =>
        if (!evaluatedProps.contains(propName)) {
          evaluatedProps += propName // additionalProperties evaluates these properties
          val result = validateWithTracking(additionalSchema, propValue, path.field(propName), registry, rootSchema)
          if (!result.isValid) {
            additionalSchema match {
              case JsonSchema.False =>
                errors += JsonSchemaError.AdditionalPropertyNotAllowed(path, propName)
              case _ =>
                errors ++= result.errors
            }
          }
        }
      }
    }

    // Property names validation
    schema.propertyNames.foreach { nameSchema =>
      fields.foreach { case (propName, _) =>
        val result = validateWithTracking(nameSchema, Json.str(propName), path.field(propName), registry, rootSchema)
        if (!result.isValid) {
          errors += JsonSchemaError.PropertyNameInvalid(
            path,
            propName,
            result.errors.headOption.map(_.message).getOrElse("invalid")
          )
        }
      }
    }

    // Dependent required validation
    schema.dependentRequired.foreach { deps =>
      deps.foreach { case (trigger, required) =>
        if (fieldMap.contains(trigger)) {
          required.foreach { field =>
            if (!fieldMap.contains(field)) {
              errors += JsonSchemaError.RequiredMissing(path, s"$field (required when $trigger is present)")
            }
          }
        }
      }
    }

    // Dependent schemas validation
    schema.dependentSchemas.foreach { deps =>
      deps.foreach { case (trigger, depSchema) =>
        if (fieldMap.contains(trigger)) {
          val result = validateWithTracking(depSchema, obj, path, registry, rootSchema)
          errors ++= result.errors
          evaluatedProps ++= result.context.evaluatedProperties
        }
      }
    }

    ValidationResult(errors.result(), EvaluationContext(evaluatedProperties = evaluatedProps))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Composition Validation with Tracking
  // ─────────────────────────────────────────────────────────────────────────

  private def validateAllOfWithTracking(
    schemas: Vector[JsonSchema],
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    var combined = ValidationResult.success
    schemas.foreach { schema =>
      val result = validateWithTracking(schema, json, path, registry, rootSchema)
      combined = combined ++ result
    }
    combined
  }

  private def validateAnyOfWithTracking(
    schemas: Vector[JsonSchema],
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    val results = schemas.map { schema =>
      validateWithTracking(schema, json, path, registry, rootSchema)
    }
    val validResults = results.filter(_.isValid)
    if (validResults.isEmpty) {
      ValidationResult.error(JsonSchemaError.CompositionFailed(path, "anyOf", "no schema matched"))
    } else {
      // Merge contexts from all valid results (properties are evaluated if ANY subschema evaluated them)
      validResults.foldLeft(ValidationResult.success)((acc, r) =>
        ValidationResult(acc.errors, acc.context.merge(r.context))
      )
    }
  }

  private def validateOneOfWithTracking(
    schemas: Vector[JsonSchema],
    json: Json,
    path: DynamicOptic,
    registry: SchemaRegistry,
    rootSchema: JsonSchema
  ): ValidationResult = {
    val results = schemas.map { schema =>
      validateWithTracking(schema, json, path, registry, rootSchema)
    }
    val validResults = results.filter(_.isValid)
    val matchCount   = validResults.length

    if (matchCount == 0) {
      ValidationResult.error(JsonSchemaError.CompositionFailed(path, "oneOf", "no schema matched"))
    } else if (matchCount > 1) {
      ValidationResult.error(
        JsonSchemaError.CompositionFailed(path, "oneOf", s"$matchCount schemas matched, expected exactly 1")
      )
    } else {
      // Return context from the single matching schema
      validResults.head
    }
  }
}
