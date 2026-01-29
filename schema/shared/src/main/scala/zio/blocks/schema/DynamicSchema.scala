package zio.blocks.schema

import zio.blocks.schema.binding.NoBinding
import zio.blocks.schema.json.JsonSchema

/**
 * A type-erased schema representation that can validate [[DynamicValue]]
 * instances at runtime.
 *
 * `DynamicSchema` wraps an unbound [[Reflect]] structure (one with no runtime
 * binding), enabling schema-based validation and manipulation without
 * compile-time type information. This is useful for scenarios where schemas are
 * loaded dynamically, such as from configuration files, databases, or network
 * services.
 *
 * Key capabilities:
 *   - '''Validation''': Check whether a [[DynamicValue]] conforms to this
 *     schema's structure and constraints using [[check]] or [[conforms]]
 *   - '''Schema Conversion''': Convert to a `Schema[DynamicValue]` that only
 *     accepts conforming values via [[toSchema]]
 *   - '''Metadata Access''': Access documentation, type names, modifiers,
 *     defaults, and examples
 *   - '''Structure Navigation''': Navigate the schema structure using
 *     [[DynamicOptic]] paths
 *
 * @example
 *   {{{
 * // Create a DynamicSchema from an existing Schema
 * val personSchema: Schema[Person] = Schema.derived[Person]
 * val dynamicSchema: DynamicSchema = personSchema.toDynamicSchema
 *
 * // Validate a DynamicValue against the schema
 * val value = DynamicValue.Record(Chunk(
 *   "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
 *   "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
 * ))
 *
 * dynamicSchema.check(value) // None if valid, Some(SchemaError) if invalid
 * dynamicSchema.conforms(value) // true if valid
 *   }}}
 *
 * @param reflect
 *   The underlying unbound reflect structure representing the schema
 *
 * @see
 *   [[Schema.toDynamicSchema]] for creating a DynamicSchema from a typed Schema
 * @see
 *   [[DynamicValue]] for the runtime value representation
 */
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {

  /**
   * Validates a [[DynamicValue]] against this schema.
   *
   * Performs recursive structural validation, checking that:
   *   - Record fields match expected names and types (extra fields are
   *     rejected)
   *   - Variant case names are valid and case values match their schemas
   *   - Sequence elements conform to the element schema
   *   - Map keys and values conform to their respective schemas
   *   - Primitive values have matching types and pass any validation
   *     constraints
   *
   * @param value
   *   The dynamic value to validate
   * @return
   *   `None` if the value conforms to this schema, or `Some(SchemaError)`
   *   describing the first validation failure encountered
   */
  def check(value: DynamicValue): Option[SchemaError] = DynamicSchema.checkValue(reflect, value, Nil)

  /**
   * Tests whether a [[DynamicValue]] conforms to this schema.
   *
   * @param value
   *   The dynamic value to test
   * @return
   *   `true` if the value passes all validation checks, `false` otherwise
   */
  def conforms(value: DynamicValue): Boolean = check(value).isEmpty

  /**
   * Creates a `Schema[DynamicValue]` that only accepts values conforming to
   * this schema.
   *
   * The returned schema wraps the base `Schema[DynamicValue]` with a validation
   * layer that rejects any `DynamicValue` that doesn't conform to this schema's
   * structure.
   *
   * @return
   *   A schema that validates incoming dynamic values against this schema's
   *   structure
   */
  def toSchema: Schema[DynamicValue] =
    Schema[DynamicValue].transformOrFail(
      to = dv =>
        check(dv) match {
          case Some(error) => Left(error)
          case scala.None  => Right(dv)
        },
      from = identity
    )

  /** Returns the documentation associated with this schema. */
  def doc: Doc = reflect.doc

  /** Returns a copy of this schema with updated documentation. */
  def doc(value: String): DynamicSchema = copy(reflect = reflect.doc(value))

  /** Returns the type name of the schema's root type. */
  def typeName: TypeName[_] = reflect.typeName

  /** Returns the modifiers attached to this schema. */
  def modifiers: Seq[Modifier.Reflect] = reflect.modifiers

  /** Returns a copy of this schema with an additional modifier. */
  def modifier(m: Modifier.Reflect): DynamicSchema = copy(reflect = reflect.modifier(m))

  /**
   * Navigates to a nested schema element using a [[DynamicOptic]] path.
   *
   * @param optic
   *   The path to navigate (e.g., `DynamicOptic.root.field("name")`)
   * @return
   *   The nested reflect structure if the path is valid, `None` otherwise
   */
  def get(optic: DynamicOptic): Option[Reflect.Unbound[_]] = reflect.get(optic)

  /** Returns the default value for this schema, if one is defined. */
  def getDefaultValue: Option[DynamicValue] = reflect match {
    case r: Reflect.Record[NoBinding, _]                 => r.storedDefaultValue
    case v: Reflect.Variant[NoBinding, _]                => v.storedDefaultValue
    case s: Reflect.Sequence[NoBinding, _, _] @unchecked => s.storedDefaultValue
    case m: Reflect.Map[NoBinding, _, _, _] @unchecked   => m.storedDefaultValue
    case p: Reflect.Primitive[NoBinding, _]              => p.storedDefaultValue
    case w: Reflect.Wrapper[NoBinding, _, _]             => w.storedDefaultValue
    case _: Reflect.Dynamic[NoBinding]                   => None
    case d: Reflect.Deferred[NoBinding, _]               => DynamicSchema(d.value).getDefaultValue
  }

  /** Returns the example values for this schema, if any are defined. */
  def examples: Seq[DynamicValue] = reflect match {
    case r: Reflect.Record[NoBinding, _]                 => r.storedExamples
    case v: Reflect.Variant[NoBinding, _]                => v.storedExamples
    case s: Reflect.Sequence[NoBinding, _, _] @unchecked => s.storedExamples
    case m: Reflect.Map[NoBinding, _, _, _] @unchecked   => m.storedExamples
    case p: Reflect.Primitive[NoBinding, _]              => p.storedExamples
    case w: Reflect.Wrapper[NoBinding, _, _]             => w.storedExamples
    case _: Reflect.Dynamic[NoBinding]                   => Nil
    case d: Reflect.Deferred[NoBinding, _]               => DynamicSchema(d.value).examples
  }

  /** Returns a copy of this schema with the specified default value. */
  def defaultValue(value: DynamicValue): DynamicSchema = {
    val updatedReflect = reflect match {
      case r: Reflect.Record[NoBinding, _]                 => r.copy(storedDefaultValue = Some(value))
      case v: Reflect.Variant[NoBinding, _]                => v.copy(storedDefaultValue = Some(value))
      case s: Reflect.Sequence[NoBinding, _, _] @unchecked => s.copy(storedDefaultValue = Some(value))
      case m: Reflect.Map[NoBinding, _, _, _] @unchecked   => m.copy(storedDefaultValue = Some(value))
      case p: Reflect.Primitive[NoBinding, _]              => p.copy(storedDefaultValue = Some(value))
      case w: Reflect.Wrapper[NoBinding, _, _]             => w.copy(storedDefaultValue = Some(value))
      case other                                           => other
    }
    new DynamicSchema(updatedReflect.asInstanceOf[Reflect.Unbound[_]])
  }

  /** Returns a copy of this schema with the specified example values. */
  def examples(value: DynamicValue, values: DynamicValue*): DynamicSchema = {
    val allExamples    = value +: values
    val updatedReflect = reflect match {
      case r: Reflect.Record[NoBinding, _]                 => r.copy(storedExamples = allExamples)
      case v: Reflect.Variant[NoBinding, _]                => v.copy(storedExamples = allExamples)
      case s: Reflect.Sequence[NoBinding, _, _] @unchecked => s.copy(storedExamples = allExamples)
      case m: Reflect.Map[NoBinding, _, _, _] @unchecked   => m.copy(storedExamples = allExamples)
      case p: Reflect.Primitive[NoBinding, _]              => p.copy(storedExamples = allExamples)
      case w: Reflect.Wrapper[NoBinding, _, _]             => w.copy(storedExamples = allExamples)
      case other                                           => other
    }
    new DynamicSchema(updatedReflect.asInstanceOf[Reflect.Unbound[_]])
  }

  /**
   * Derives a JSON Schema from this schema.
   *
   * Note: Currently returns the generic `Schema[DynamicValue]` JSON Schema,
   * which represents the DynamicValue ADT structure rather than this specific
   * schema's structure. A future enhancement could derive structure-specific
   * JSON Schema directly from the unbound reflect.
   */
  def toJsonSchema: JsonSchema = Schema[DynamicValue].toJsonSchema
}

object DynamicSchema {

  private def checkValue(
    reflect: Reflect[NoBinding, _],
    value: DynamicValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    def resolveReflect(r: Reflect[NoBinding, _]): Reflect[NoBinding, _] = r match {
      case d: Reflect.Deferred[NoBinding, _] => resolveReflect(d.value)
      case other                             => other
    }

    val resolved = resolveReflect(reflect)

    resolved match {
      case _: Reflect.Dynamic[NoBinding] =>
        None

      case r: Reflect.Record[NoBinding, _] =>
        value match {
          case DynamicValue.Record(fields) =>
            checkRecord(r, fields, trace)
          case _ =>
            Some(SchemaError.expectationMismatch(trace, s"Expected Record, got ${value.valueType}"))
        }

      case v: Reflect.Variant[NoBinding, _] =>
        value match {
          case DynamicValue.Variant(caseName, caseValue) =>
            checkVariant(v, caseName, caseValue, trace)
          case _ =>
            Some(SchemaError.expectationMismatch(trace, s"Expected Variant, got ${value.valueType}"))
        }

      case s: Reflect.Sequence[NoBinding, _, _] @unchecked =>
        value match {
          case DynamicValue.Sequence(elements) =>
            checkSequence(s, elements, trace)
          case _ =>
            Some(SchemaError.expectationMismatch(trace, s"Expected Sequence, got ${value.valueType}"))
        }

      case m: Reflect.Map[NoBinding, _, _, _] @unchecked =>
        value match {
          case DynamicValue.Map(entries) =>
            checkMap(m, entries, trace)
          case _ =>
            Some(SchemaError.expectationMismatch(trace, s"Expected Map, got ${value.valueType}"))
        }

      case p: Reflect.Primitive[NoBinding, _] =>
        checkPrimitive(p, value, trace)

      case w: Reflect.Wrapper[NoBinding, _, _] =>
        checkValue(w.wrapped, value, DynamicOptic.Node.Wrapped :: trace)

      case d: Reflect.Deferred[NoBinding, _] =>
        checkValue(d.value, value, trace)
    }
  }

  private def checkRecord(
    record: Reflect.Record[NoBinding, _],
    fields: zio.blocks.chunk.Chunk[(String, DynamicValue)],
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    val fieldMap         = fields.toMap
    val schemaFieldNames = record.fields.map(_.name).toSet
    val providedNames    = fields.map(_._1).toSet

    val missingFields = schemaFieldNames -- providedNames
    if (missingFields.nonEmpty) {
      return Some(SchemaError.missingField(trace, missingFields.head))
    }

    val extraFields = providedNames -- schemaFieldNames
    if (extraFields.nonEmpty) {
      return Some(
        SchemaError.expectationMismatch(trace, s"Unknown field '${extraFields.head}' in Record")
      )
    }

    var error: Option[SchemaError] = None
    record.fields.foreach { term =>
      if (error.isEmpty) {
        val fieldValue = fieldMap(term.name)
        val fieldTrace = DynamicOptic.Node.Field(term.name) :: trace
        checkValue(term.value.asInstanceOf[Reflect.Unbound[_]], fieldValue, fieldTrace) match {
          case Some(e) => error = Some(e)
          case None    => ()
        }
      }
    }
    error
  }

  private def checkVariant(
    variant: Reflect.Variant[NoBinding, _],
    caseName: String,
    caseValue: DynamicValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] =
    variant.cases.find(_.name == caseName) match {
      case Some(caseEntry) =>
        val caseTrace = DynamicOptic.Node.Case(caseName) :: trace
        checkValue(caseEntry.value.asInstanceOf[Reflect.Unbound[_]], caseValue, caseTrace)
      case None =>
        Some(SchemaError.unknownCase(trace, caseName))
    }

  private def checkSequence[A, C[_]](
    sequence: Reflect.Sequence[NoBinding, A, C],
    elements: zio.blocks.chunk.Chunk[DynamicValue],
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    var error: Option[SchemaError] = None
    var idx                        = 0
    val elemReflect                = sequence.element.asInstanceOf[Reflect.Unbound[_]]
    while (idx < elements.length && error.isEmpty) {
      val elemTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      checkValue(elemReflect, elements(idx), elemTrace) match {
        case Some(e) => error = Some(e)
        case None    => ()
      }
      idx += 1
    }
    error
  }

  private def checkMap[K, V, M[_, _]](
    map: Reflect.Map[NoBinding, K, V, M],
    entries: zio.blocks.chunk.Chunk[(DynamicValue, DynamicValue)],
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    var error: Option[SchemaError] = None
    val keyReflect                 = map.key.asInstanceOf[Reflect.Unbound[_]]
    val valueReflect               = map.value.asInstanceOf[Reflect.Unbound[_]]
    var idx                        = 0
    while (idx < entries.length && error.isEmpty) {
      val (key, value) = entries(idx)
      val keyTrace     = DynamicOptic.Node.MapKeys :: trace
      checkValue(keyReflect, key, keyTrace) match {
        case Some(e) => error = Some(e)
        case None    =>
          val valueTrace = DynamicOptic.Node.AtMapKey(key) :: trace
          checkValue(valueReflect, value, valueTrace) match {
            case Some(e) => error = Some(e)
            case None    => ()
          }
      }
      idx += 1
    }
    error
  }

  private def checkPrimitive(
    primitive: Reflect.Primitive[NoBinding, _],
    value: DynamicValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    val pt = primitive.primitiveType
    value match {
      case DynamicValue.Primitive(pv) =>
        if (!primitiveTypeMatches(pt, pv)) {
          Some(
            SchemaError.expectationMismatch(trace, s"Expected ${pt.typeName.name}, got ${pv.getClass.getSimpleName}")
          )
        } else {
          checkValidation(pt.validation, pv, trace)
        }
      case DynamicValue.Null if pt == PrimitiveType.Unit =>
        None
      case _ =>
        Some(SchemaError.expectationMismatch(trace, s"Expected Primitive(${pt.typeName.name}), got ${value.valueType}"))
    }
  }

  private def primitiveTypeMatches(pt: PrimitiveType[_], pv: PrimitiveValue): Boolean =
    (pt, pv) match {
      case (PrimitiveType.Unit, PrimitiveValue.Unit)                           => true
      case (_: PrimitiveType.Boolean, _: PrimitiveValue.Boolean)               => true
      case (_: PrimitiveType.Byte, _: PrimitiveValue.Byte)                     => true
      case (_: PrimitiveType.Short, _: PrimitiveValue.Short)                   => true
      case (_: PrimitiveType.Int, _: PrimitiveValue.Int)                       => true
      case (_: PrimitiveType.Long, _: PrimitiveValue.Long)                     => true
      case (_: PrimitiveType.Float, _: PrimitiveValue.Float)                   => true
      case (_: PrimitiveType.Double, _: PrimitiveValue.Double)                 => true
      case (_: PrimitiveType.Char, _: PrimitiveValue.Char)                     => true
      case (_: PrimitiveType.String, _: PrimitiveValue.String)                 => true
      case (_: PrimitiveType.BigInt, _: PrimitiveValue.BigInt)                 => true
      case (_: PrimitiveType.BigDecimal, _: PrimitiveValue.BigDecimal)         => true
      case (_: PrimitiveType.DayOfWeek, _: PrimitiveValue.DayOfWeek)           => true
      case (_: PrimitiveType.Duration, _: PrimitiveValue.Duration)             => true
      case (_: PrimitiveType.Instant, _: PrimitiveValue.Instant)               => true
      case (_: PrimitiveType.LocalDate, _: PrimitiveValue.LocalDate)           => true
      case (_: PrimitiveType.LocalDateTime, _: PrimitiveValue.LocalDateTime)   => true
      case (_: PrimitiveType.LocalTime, _: PrimitiveValue.LocalTime)           => true
      case (_: PrimitiveType.Month, _: PrimitiveValue.Month)                   => true
      case (_: PrimitiveType.MonthDay, _: PrimitiveValue.MonthDay)             => true
      case (_: PrimitiveType.OffsetDateTime, _: PrimitiveValue.OffsetDateTime) => true
      case (_: PrimitiveType.OffsetTime, _: PrimitiveValue.OffsetTime)         => true
      case (_: PrimitiveType.Period, _: PrimitiveValue.Period)                 => true
      case (_: PrimitiveType.Year, _: PrimitiveValue.Year)                     => true
      case (_: PrimitiveType.YearMonth, _: PrimitiveValue.YearMonth)           => true
      case (_: PrimitiveType.ZoneId, _: PrimitiveValue.ZoneId)                 => true
      case (_: PrimitiveType.ZoneOffset, _: PrimitiveValue.ZoneOffset)         => true
      case (_: PrimitiveType.ZonedDateTime, _: PrimitiveValue.ZonedDateTime)   => true
      case (_: PrimitiveType.Currency, _: PrimitiveValue.Currency)             => true
      case (_: PrimitiveType.UUID, _: PrimitiveValue.UUID)                     => true
      case _                                                                   => false
    }

  private def checkValidation(
    validation: Validation[_],
    pv: PrimitiveValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    def fail(msg: Predef.String): Option[SchemaError] =
      Some(SchemaError.conversionFailed(trace, s"Validation failed: $msg"))

    validation match {
      case Validation.None => scala.None

      case Validation.Numeric.Positive =>
        pv match {
          case PrimitiveValue.Byte(v) if v > 0       => scala.None
          case PrimitiveValue.Short(v) if v > 0      => scala.None
          case PrimitiveValue.Int(v) if v > 0        => scala.None
          case PrimitiveValue.Long(v) if v > 0       => scala.None
          case PrimitiveValue.Float(v) if v > 0      => scala.None
          case PrimitiveValue.Double(v) if v > 0     => scala.None
          case PrimitiveValue.BigInt(v) if v > 0     => scala.None
          case PrimitiveValue.BigDecimal(v) if v > 0 => scala.None
          case _                                     => fail("value must be positive")
        }

      case Validation.Numeric.Negative =>
        pv match {
          case PrimitiveValue.Byte(v) if v < 0       => scala.None
          case PrimitiveValue.Short(v) if v < 0      => scala.None
          case PrimitiveValue.Int(v) if v < 0        => scala.None
          case PrimitiveValue.Long(v) if v < 0       => scala.None
          case PrimitiveValue.Float(v) if v < 0      => scala.None
          case PrimitiveValue.Double(v) if v < 0     => scala.None
          case PrimitiveValue.BigInt(v) if v < 0     => scala.None
          case PrimitiveValue.BigDecimal(v) if v < 0 => scala.None
          case _                                     => fail("value must be negative")
        }

      case Validation.Numeric.NonPositive =>
        pv match {
          case PrimitiveValue.Byte(v) if v <= 0       => scala.None
          case PrimitiveValue.Short(v) if v <= 0      => scala.None
          case PrimitiveValue.Int(v) if v <= 0        => scala.None
          case PrimitiveValue.Long(v) if v <= 0       => scala.None
          case PrimitiveValue.Float(v) if v <= 0      => scala.None
          case PrimitiveValue.Double(v) if v <= 0     => scala.None
          case PrimitiveValue.BigInt(v) if v <= 0     => scala.None
          case PrimitiveValue.BigDecimal(v) if v <= 0 => scala.None
          case _                                      => fail("value must be non-positive")
        }

      case Validation.Numeric.NonNegative =>
        pv match {
          case PrimitiveValue.Byte(v) if v >= 0       => scala.None
          case PrimitiveValue.Short(v) if v >= 0      => scala.None
          case PrimitiveValue.Int(v) if v >= 0        => scala.None
          case PrimitiveValue.Long(v) if v >= 0       => scala.None
          case PrimitiveValue.Float(v) if v >= 0      => scala.None
          case PrimitiveValue.Double(v) if v >= 0     => scala.None
          case PrimitiveValue.BigInt(v) if v >= 0     => scala.None
          case PrimitiveValue.BigDecimal(v) if v >= 0 => scala.None
          case _                                      => fail("value must be non-negative")
        }

      case r: Validation.Numeric.Range[_] =>
        checkNumericRange(r, pv, trace)

      case s: Validation.Numeric.Set[_] =>
        checkNumericSet(s, pv, trace)

      case Validation.String.NonEmpty =>
        pv match {
          case PrimitiveValue.String(v) if v.nonEmpty => scala.None
          case _                                      => fail("string must be non-empty")
        }

      case Validation.String.Empty =>
        pv match {
          case PrimitiveValue.String(v) if v.isEmpty => scala.None
          case _                                     => fail("string must be empty")
        }

      case Validation.String.Blank =>
        pv match {
          case PrimitiveValue.String(v) if v.trim.isEmpty => scala.None
          case _                                          => fail("string must be blank")
        }

      case Validation.String.NonBlank =>
        pv match {
          case PrimitiveValue.String(v) if v.trim.nonEmpty => scala.None
          case _                                           => fail("string must be non-blank")
        }

      case Validation.String.Length(min, max) =>
        pv match {
          case PrimitiveValue.String(v) =>
            val len   = v.length
            val minOk = min.forall(len >= _)
            val maxOk = max.forall(len <= _)
            if (minOk && maxOk) scala.None
            else fail(s"string length $len not in range [${min.getOrElse("-∞")}, ${max.getOrElse("∞")}]")
          case _ => fail("expected string for Length validation")
        }

      case Validation.String.Pattern(regex) =>
        pv match {
          case PrimitiveValue.String(v) if v.matches(regex) => scala.None
          case PrimitiveValue.String(_)                     => fail(s"string does not match pattern '$regex'")
          case _                                            => fail("expected string for Pattern validation")
        }
    }
  }

  private def checkNumericRange(
    range: Validation.Numeric.Range[_],
    pv: PrimitiveValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    def fail(msg: Predef.String): Option[SchemaError] =
      Some(SchemaError.conversionFailed(trace, s"Validation failed: $msg"))

    def checkRange[A](v: A, min: Option[A], max: Option[A])(implicit ord: Ordering[A]): Boolean = {
      val minOk = min.forall(ord.gteq(v, _))
      val maxOk = max.forall(ord.lteq(v, _))
      minOk && maxOk
    }

    pv match {
      case PrimitiveValue.Byte(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Byte]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.Short(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Short]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.Int(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Int]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.Long(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Long]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.Float(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Float]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.Double(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.Double]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.BigInt(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.BigInt]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case PrimitiveValue.BigDecimal(v) =>
        val typedRange = range.asInstanceOf[Validation.Numeric.Range[scala.BigDecimal]]
        if (checkRange(v, typedRange.min, typedRange.max)) scala.None
        else fail(s"value $v not in range")
      case _ =>
        fail("Range validation only applies to numeric types")
    }
  }

  private def checkNumericSet(
    set: Validation.Numeric.Set[_],
    pv: PrimitiveValue,
    trace: List[DynamicOptic.Node]
  ): Option[SchemaError] = {
    def fail(msg: Predef.String): Option[SchemaError] =
      Some(SchemaError.conversionFailed(trace, s"Validation failed: $msg"))

    pv match {
      case PrimitiveValue.Byte(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Byte]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.Short(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Short]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.Int(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Int]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.Long(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Long]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.Float(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Float]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.Double(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.Double]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.BigInt(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.BigInt]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case PrimitiveValue.BigDecimal(v) =>
        val typedSet = set.asInstanceOf[Validation.Numeric.Set[scala.BigDecimal]]
        if (typedSet.values.contains(v)) scala.None else fail(s"value $v not in allowed set")
      case _ =>
        fail("Set validation only applies to numeric types")
    }
  }
}
