package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.json.JsonSchema
import zio.blocks.typeid.{Owner, TypeId}

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
    Schema[DynamicValue].transform(
      to = dv =>
        check(dv) match {
          case Some(error) => throw error
          case scala.None  => dv
        },
      from = identity
    )

  /** Returns the documentation associated with this schema. */
  def doc: Doc = reflect.doc

  /** Returns a copy of this schema with updated documentation. */
  def doc(value: String): DynamicSchema = copy(reflect = reflect.doc(value))

  /** Returns the type id of the schema's root type. */
  def typeId: TypeId[_] = reflect.typeId

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
      case d: Reflect.Deferred[NoBinding, _]               =>
        val inner = DynamicSchema(d.value).defaultValue(value).reflect
        d.copy(
          _value = () => inner.asInstanceOf[Reflect[NoBinding, Any]],
          _typeId = d._typeId.asInstanceOf[Option[TypeId[Any]]]
        )
      case other => other
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
      case d: Reflect.Deferred[NoBinding, _]               =>
        val inner = DynamicSchema(d.value).examples(value, values: _*).reflect
        d.copy(
          _value = () => inner.asInstanceOf[Reflect[NoBinding, Any]],
          _typeId = d._typeId.asInstanceOf[Option[TypeId[Any]]]
        )
      case other => other
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

  /**
   * Rebinds this unbound schema using bindings from a
   * [[zio.blocks.schema.binding.BindingResolver BindingResolver]].
   *
   * This converts the structural schema information (types, fields, cases,
   * validations) into a fully operational `Schema[A]` by attaching runtime
   * bindings (constructors, deconstructors, matchers) from the resolver.
   *
   * The resolver must provide bindings for all types referenced in this schema:
   *   - Record types need `Binding.Record` entries
   *   - Variant types need `Binding.Variant` entries
   *   - Primitive types need `Binding.Primitive` entries (provided by
   *     `BindingResolver.defaults`)
   *   - Sequence types need `Binding.Seq` entries (provided by
   *     `BindingResolver.defaults`)
   *   - Map types need `Binding.Map` entries (provided by
   *     `BindingResolver.defaults`)
   *   - Wrapper types need `Binding.Wrapper` entries
   *
   * @param resolver
   *   The BindingResolver providing bindings for all types in this schema
   * @return
   *   A bound Schema that can construct and deconstruct values
   * @throws RebindException
   *   If any required binding is missing from the resolver
   *
   * @example
   *   {{{
   * case class Person(name: String, age: Int)
   *
   * val dynamicSchema: DynamicSchema = ...
   * val resolver = BindingResolver.empty.bind(Binding.of[Person]) ++ BindingResolver.defaults
   * val schema: Schema[Person] = dynamicSchema.rebind[Person](resolver)
   *   }}}
   */
  def rebind[A](resolver: BindingResolver): Schema[A] = {
    val transformer = new RebindTransformer(resolver)
    val bound       = reflect.transform(DynamicOptic.root, transformer).force
    new Schema(bound.asInstanceOf[Reflect.Bound[A]])
  }
}

object DynamicSchema extends TypeIdSchemas {

  private val zioBlocksSchemaOwner: Owner   = Owner.fromPackagePath("zio.blocks.schema")
  private val docOwner: Owner               = zioBlocksSchemaOwner.tpe("Doc")
  private val validationOwner: Owner        = zioBlocksSchemaOwner.tpe("Validation")
  private val validationNumericOwner: Owner = validationOwner.tpe("Numeric")
  private val validationStringOwner: Owner  = validationOwner.tpe("String")
  private val primitiveTypeOwner: Owner     = zioBlocksSchemaOwner.tpe("PrimitiveType")

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
            SchemaError.expectationMismatch(trace, s"Expected ${pt.typeId.name}, got ${pv.getClass.getSimpleName}")
          )
        } else {
          checkValidation(pt.validation, pv, trace)
        }
      case DynamicValue.Null if pt == PrimitiveType.Unit =>
        None
      case _ =>
        Some(SchemaError.expectationMismatch(trace, s"Expected Primitive(${pt.typeId.name}), got ${value.valueType}"))
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

  /** Schema for [[Doc.Empty]]. */
  private lazy val docEmptySchema: Schema[Doc.Empty.type] = new Schema(
    reflect = new Reflect.Record[Binding, Doc.Empty.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Doc.Empty.type]("Empty", docOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Doc.Empty.type](Doc.Empty),
        deconstructor = new ConstantDeconstructor[Doc.Empty.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Doc.Text]]. */
  private lazy val docTextSchema: Schema[Doc.Text] = new Schema(
    reflect = new Reflect.Record[Binding, Doc.Text](
      fields = Vector(Schema[String].reflect.asTerm("value")),
      typeId = TypeId.nominal[Doc.Text]("Text", docOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Doc.Text] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Doc.Text =
            Doc.Text(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Doc.Text] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Doc.Text): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Doc.Concat]]. */
  private lazy val docConcatSchema: Schema[Doc.Concat] = new Schema(
    reflect = new Reflect.Record[Binding, Doc.Concat](
      fields = Vector(Schema[IndexedSeq[Doc.Leaf]].reflect.asTerm("flatten")),
      typeId = TypeId.nominal[Doc.Concat]("Concat", docOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Doc.Concat] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): Doc.Concat =
            Doc.Concat(in.getObject(offset).asInstanceOf[IndexedSeq[Doc.Leaf]])
        },
        deconstructor = new Deconstructor[Doc.Concat] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Doc.Concat): Unit =
            out.setObject(offset, in.flatten)
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Doc.Leaf]]. */
  implicit lazy val docLeafSchema: Schema[Doc.Leaf] = new Schema(
    reflect = new Reflect.Variant[Binding, Doc.Leaf](
      cases = Vector(
        docEmptySchema.reflect.asTerm("Empty"),
        docTextSchema.reflect.asTerm("Text")
      ),
      typeId = TypeId.nominal[Doc.Leaf]("Leaf", docOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Doc.Leaf] {
          def discriminate(a: Doc.Leaf): Int = a match {
            case Doc.Empty   => 0
            case _: Doc.Text => 1
          }
        },
        matchers = Matchers(
          new Matcher[Doc.Empty.type] {
            def downcastOrNull(a: Any): Doc.Empty.type = a match {
              case Doc.Empty => Doc.Empty
              case _         => null.asInstanceOf[Doc.Empty.type]
            }
          },
          new Matcher[Doc.Text] {
            def downcastOrNull(a: Any): Doc.Text = a match {
              case x: Doc.Text => x
              case _           => null.asInstanceOf[Doc.Text]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Doc]]. */
  implicit lazy val docSchema: Schema[Doc] = new Schema(
    reflect = new Reflect.Variant[Binding, Doc](
      cases = Vector(
        docEmptySchema.reflect.asTerm("Empty"),
        docTextSchema.reflect.asTerm("Text"),
        docConcatSchema.reflect.asTerm("Concat")
      ),
      typeId = TypeId.nominal[Doc]("Doc", zioBlocksSchemaOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Doc] {
          def discriminate(a: Doc): Int = a match {
            case Doc.Empty     => 0
            case _: Doc.Text   => 1
            case _: Doc.Concat => 2
          }
        },
        matchers = Matchers(
          new Matcher[Doc.Empty.type] {
            def downcastOrNull(a: Any): Doc.Empty.type = a match {
              case Doc.Empty => Doc.Empty
              case _         => null.asInstanceOf[Doc.Empty.type]
            }
          },
          new Matcher[Doc.Text] {
            def downcastOrNull(a: Any): Doc.Text = a match {
              case x: Doc.Text => x
              case _           => null.asInstanceOf[Doc.Text]
            }
          },
          new Matcher[Doc.Concat] {
            def downcastOrNull(a: Any): Doc.Concat = a match {
              case x: Doc.Concat => x
              case _             => null.asInstanceOf[Doc.Concat]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  // ===========================================================================
  // Validation Schemas
  // ===========================================================================

  /** Schema for [[Validation.None]]. */
  private lazy val validationNoneSchema: Schema[Validation.None.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.None.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.None.type]("None", validationOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.None.type](Validation.None),
        deconstructor = new ConstantDeconstructor[Validation.None.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.Numeric.Positive]]. */
  private lazy val validationPositiveSchema: Schema[Validation.Numeric.Positive.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.Positive.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.Numeric.Positive.type]("Positive", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.Numeric.Positive.type](Validation.Numeric.Positive),
        deconstructor = new ConstantDeconstructor[Validation.Numeric.Positive.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.Numeric.Negative]]. */
  private lazy val validationNegativeSchema: Schema[Validation.Numeric.Negative.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.Negative.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.Numeric.Negative.type]("Negative", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.Numeric.Negative.type](Validation.Numeric.Negative),
        deconstructor = new ConstantDeconstructor[Validation.Numeric.Negative.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.Numeric.NonPositive]]. */
  private lazy val validationNonPositiveSchema: Schema[Validation.Numeric.NonPositive.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.NonPositive.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.Numeric.NonPositive.type]("NonPositive", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.Numeric.NonPositive.type](Validation.Numeric.NonPositive),
        deconstructor = new ConstantDeconstructor[Validation.Numeric.NonPositive.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.Numeric.NonNegative]]. */
  private lazy val validationNonNegativeSchema: Schema[Validation.Numeric.NonNegative.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.NonNegative.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.Numeric.NonNegative.type]("NonNegative", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.Numeric.NonNegative.type](Validation.Numeric.NonNegative),
        deconstructor = new ConstantDeconstructor[Validation.Numeric.NonNegative.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Helper to convert Any numeric value to DynamicValue. */
  private def numericToDynamicValue(v: Any): DynamicValue = v match {
    case v: Byte       => DynamicValue.Primitive(PrimitiveValue.Byte(v))
    case v: Short      => DynamicValue.Primitive(PrimitiveValue.Short(v))
    case v: Int        => DynamicValue.Primitive(PrimitiveValue.Int(v))
    case v: Long       => DynamicValue.Primitive(PrimitiveValue.Long(v))
    case v: Float      => DynamicValue.Primitive(PrimitiveValue.Float(v))
    case v: Double     => DynamicValue.Primitive(PrimitiveValue.Double(v))
    case v: BigInt     => DynamicValue.Primitive(PrimitiveValue.BigInt(v))
    case v: BigDecimal => DynamicValue.Primitive(PrimitiveValue.BigDecimal(v))
    case v             => DynamicValue.Primitive(PrimitiveValue.String(v.toString))
  }

  /** Helper to convert DynamicValue back to the underlying numeric value. */
  private def dynamicValueToNumeric(dv: DynamicValue): Any = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.Byte(v)       => v
        case PrimitiveValue.Short(v)      => v
        case PrimitiveValue.Int(v)        => v
        case PrimitiveValue.Long(v)       => v
        case PrimitiveValue.Float(v)      => v
        case PrimitiveValue.Double(v)     => v
        case PrimitiveValue.BigInt(v)     => v
        case PrimitiveValue.BigDecimal(v) => v
        case PrimitiveValue.String(v)     => v
        case _                            => null
      }
    case _ => null
  }

  /**
   * Helper to convert DynamicValue to Option[Any], handling Null and
   * non-primitives.
   */
  private def dynamicValueToNumericOpt(dv: DynamicValue): Option[Any] = dv match {
    case DynamicValue.Null => scala.None
    case other             =>
      val result = dynamicValueToNumeric(other)
      if (result == null) scala.None else Some(result)
  }

  /** Schema for [[Validation.Numeric.Range]] (type-erased). */
  private lazy val validationRangeSchema: Schema[Validation.Numeric.Range[_]] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.Range[_]](
      fields = Vector(
        Schema[Option[DynamicValue]].reflect.asTerm("min"),
        Schema[Option[DynamicValue]].reflect.asTerm("max")
      ),
      typeId = TypeId.nominal[Validation.Numeric.Range[_]]("Range", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Validation.Numeric.Range[_]] {
          def usedRegisters: RegisterOffset                                                 = 2
          def construct(in: Registers, offset: RegisterOffset): Validation.Numeric.Range[_] =
            Validation.Numeric.Range[Any](
              in.getObject(offset).asInstanceOf[Option[DynamicValue]].map(dynamicValueToNumeric),
              in.getObject(offset + 1).asInstanceOf[Option[DynamicValue]].map(dynamicValueToNumeric)
            )
        },
        deconstructor = new Deconstructor[Validation.Numeric.Range[_]] {
          def usedRegisters: RegisterOffset                                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Validation.Numeric.Range[_]): Unit = {
            out.setObject(offset, in.min.map(numericToDynamicValue))
            out.setObject(offset + 1, in.max.map(numericToDynamicValue))
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.Numeric.Set]] (type-erased). */
  private lazy val validationSetSchema: Schema[Validation.Numeric.Set[_]] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.Numeric.Set[_]](
      fields = Vector(Schema[Set[DynamicValue]].reflect.asTerm("values")),
      typeId = TypeId.nominal[Validation.Numeric.Set[_]]("Set", validationNumericOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Validation.Numeric.Set[_]] {
          def usedRegisters: RegisterOffset                                               = 1
          def construct(in: Registers, offset: RegisterOffset): Validation.Numeric.Set[_] =
            Validation.Numeric.Set[Any](in.getObject(offset).asInstanceOf[Set[DynamicValue]].map(dynamicValueToNumeric))
        },
        deconstructor = new Deconstructor[Validation.Numeric.Set[_]] {
          def usedRegisters: RegisterOffset                                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Validation.Numeric.Set[_]): Unit =
            out.setObject(offset, in.values.map(numericToDynamicValue))
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.NonEmpty]]. */
  private lazy val validationStringNonEmptySchema: Schema[Validation.String.NonEmpty.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.NonEmpty.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.String.NonEmpty.type]("NonEmpty", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.String.NonEmpty.type](Validation.String.NonEmpty),
        deconstructor = new ConstantDeconstructor[Validation.String.NonEmpty.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.Empty]]. */
  private lazy val validationStringEmptySchema: Schema[Validation.String.Empty.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.Empty.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.String.Empty.type]("Empty", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.String.Empty.type](Validation.String.Empty),
        deconstructor = new ConstantDeconstructor[Validation.String.Empty.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.Blank]]. */
  private lazy val validationStringBlankSchema: Schema[Validation.String.Blank.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.Blank.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.String.Blank.type]("Blank", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.String.Blank.type](Validation.String.Blank),
        deconstructor = new ConstantDeconstructor[Validation.String.Blank.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.NonBlank]]. */
  private lazy val validationStringNonBlankSchema: Schema[Validation.String.NonBlank.type] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.NonBlank.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[Validation.String.NonBlank.type]("NonBlank", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Validation.String.NonBlank.type](Validation.String.NonBlank),
        deconstructor = new ConstantDeconstructor[Validation.String.NonBlank.type]
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.Length]]. */
  private lazy val validationStringLengthSchema: Schema[Validation.String.Length] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.Length](
      fields = Vector(
        Schema[Option[Int]].reflect.asTerm("min"),
        Schema[Option[Int]].reflect.asTerm("max")
      ),
      typeId = TypeId.nominal[Validation.String.Length]("Length", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Validation.String.Length] {
          def usedRegisters: RegisterOffset                                              = 2
          def construct(in: Registers, offset: RegisterOffset): Validation.String.Length =
            Validation.String.Length(
              in.getObject(offset).asInstanceOf[Option[Int]],
              in.getObject(offset + 1).asInstanceOf[Option[Int]]
            )
        },
        deconstructor = new Deconstructor[Validation.String.Length] {
          def usedRegisters: RegisterOffset                                                           = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Validation.String.Length): Unit = {
            out.setObject(offset, in.min)
            out.setObject(offset + 1, in.max)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation.String.Pattern]]. */
  private lazy val validationStringPatternSchema: Schema[Validation.String.Pattern] = new Schema(
    reflect = new Reflect.Record[Binding, Validation.String.Pattern](
      fields = Vector(Schema[String].reflect.asTerm("regex")),
      typeId = TypeId.nominal[Validation.String.Pattern]("Pattern", validationStringOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Validation.String.Pattern] {
          def usedRegisters: RegisterOffset                                               = 1
          def construct(in: Registers, offset: RegisterOffset): Validation.String.Pattern =
            Validation.String.Pattern(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Validation.String.Pattern] {
          def usedRegisters: RegisterOffset                                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Validation.String.Pattern): Unit =
            out.setObject(offset, in.regex)
        }
      ),
      modifiers = Vector.empty
    )
  )

  /** Schema for [[Validation]] (type-erased). */
  implicit lazy val validationSchema: Schema[Validation[_]] = new Schema(
    reflect = new Reflect.Variant[Binding, Validation[_]](
      cases = Vector(
        validationNoneSchema.reflect.asTerm("None"),
        validationPositiveSchema.reflect.asTerm("Positive"),
        validationNegativeSchema.reflect.asTerm("Negative"),
        validationNonPositiveSchema.reflect.asTerm("NonPositive"),
        validationNonNegativeSchema.reflect.asTerm("NonNegative"),
        validationRangeSchema.reflect.asTerm("Range"),
        validationSetSchema.reflect.asTerm("Set"),
        validationStringNonEmptySchema.reflect.asTerm("StringNonEmpty"),
        validationStringEmptySchema.reflect.asTerm("StringEmpty"),
        validationStringBlankSchema.reflect.asTerm("StringBlank"),
        validationStringNonBlankSchema.reflect.asTerm("StringNonBlank"),
        validationStringLengthSchema.reflect.asTerm("StringLength"),
        validationStringPatternSchema.reflect.asTerm("StringPattern")
      ),
      typeId = TypeId.nominal[Validation[_]]("Validation", zioBlocksSchemaOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Validation[_]] {
          def discriminate(a: Validation[_]): Int = a match {
            case Validation.None                => 0
            case Validation.Numeric.Positive    => 1
            case Validation.Numeric.Negative    => 2
            case Validation.Numeric.NonPositive => 3
            case Validation.Numeric.NonNegative => 4
            case _: Validation.Numeric.Range[_] => 5
            case _: Validation.Numeric.Set[_]   => 6
            case Validation.String.NonEmpty     => 7
            case Validation.String.Empty        => 8
            case Validation.String.Blank        => 9
            case Validation.String.NonBlank     => 10
            case _: Validation.String.Length    => 11
            case _: Validation.String.Pattern   => 12
          }
        },
        matchers = Matchers(
          new Matcher[Validation.None.type] {
            def downcastOrNull(a: Any): Validation.None.type = a match {
              case Validation.None => Validation.None
              case _               => null.asInstanceOf[Validation.None.type]
            }
          },
          new Matcher[Validation.Numeric.Positive.type] {
            def downcastOrNull(a: Any): Validation.Numeric.Positive.type = a match {
              case Validation.Numeric.Positive => Validation.Numeric.Positive
              case _                           => null.asInstanceOf[Validation.Numeric.Positive.type]
            }
          },
          new Matcher[Validation.Numeric.Negative.type] {
            def downcastOrNull(a: Any): Validation.Numeric.Negative.type = a match {
              case Validation.Numeric.Negative => Validation.Numeric.Negative
              case _                           => null.asInstanceOf[Validation.Numeric.Negative.type]
            }
          },
          new Matcher[Validation.Numeric.NonPositive.type] {
            def downcastOrNull(a: Any): Validation.Numeric.NonPositive.type = a match {
              case Validation.Numeric.NonPositive => Validation.Numeric.NonPositive
              case _                              => null.asInstanceOf[Validation.Numeric.NonPositive.type]
            }
          },
          new Matcher[Validation.Numeric.NonNegative.type] {
            def downcastOrNull(a: Any): Validation.Numeric.NonNegative.type = a match {
              case Validation.Numeric.NonNegative => Validation.Numeric.NonNegative
              case _                              => null.asInstanceOf[Validation.Numeric.NonNegative.type]
            }
          },
          new Matcher[Validation.Numeric.Range[_]] {
            def downcastOrNull(a: Any): Validation.Numeric.Range[_] = a match {
              case x: Validation.Numeric.Range[_] => x
              case _                              => null.asInstanceOf[Validation.Numeric.Range[_]]
            }
          },
          new Matcher[Validation.Numeric.Set[_]] {
            def downcastOrNull(a: Any): Validation.Numeric.Set[_] = a match {
              case x: Validation.Numeric.Set[_] => x
              case _                            => null.asInstanceOf[Validation.Numeric.Set[_]]
            }
          },
          new Matcher[Validation.String.NonEmpty.type] {
            def downcastOrNull(a: Any): Validation.String.NonEmpty.type = a match {
              case Validation.String.NonEmpty => Validation.String.NonEmpty
              case _                          => null.asInstanceOf[Validation.String.NonEmpty.type]
            }
          },
          new Matcher[Validation.String.Empty.type] {
            def downcastOrNull(a: Any): Validation.String.Empty.type = a match {
              case Validation.String.Empty => Validation.String.Empty
              case _                       => null.asInstanceOf[Validation.String.Empty.type]
            }
          },
          new Matcher[Validation.String.Blank.type] {
            def downcastOrNull(a: Any): Validation.String.Blank.type = a match {
              case Validation.String.Blank => Validation.String.Blank
              case _                       => null.asInstanceOf[Validation.String.Blank.type]
            }
          },
          new Matcher[Validation.String.NonBlank.type] {
            def downcastOrNull(a: Any): Validation.String.NonBlank.type = a match {
              case Validation.String.NonBlank => Validation.String.NonBlank
              case _                          => null.asInstanceOf[Validation.String.NonBlank.type]
            }
          },
          new Matcher[Validation.String.Length] {
            def downcastOrNull(a: Any): Validation.String.Length = a match {
              case x: Validation.String.Length => x
              case _                           => null.asInstanceOf[Validation.String.Length]
            }
          },
          new Matcher[Validation.String.Pattern] {
            def downcastOrNull(a: Any): Validation.String.Pattern = a match {
              case x: Validation.String.Pattern => x
              case _                            => null.asInstanceOf[Validation.String.Pattern]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  // ===========================================================================
  // PrimitiveType Schemas
  // ===========================================================================

  /** Schema for [[PrimitiveType.Unit]]. */
  private lazy val primitiveTypeUnitSchema: Schema[PrimitiveType.Unit.type] = new Schema(
    reflect = new Reflect.Record[Binding, PrimitiveType.Unit.type](
      fields = Vector.empty,
      typeId = TypeId.nominal[PrimitiveType.Unit.type]("Unit", primitiveTypeOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PrimitiveType.Unit.type](PrimitiveType.Unit),
        deconstructor = new ConstantDeconstructor[PrimitiveType.Unit.type]
      ),
      modifiers = Vector.empty
    )
  )

  /**
   * Helper to create schemas for PrimitiveType case classes with validation.
   */
  private def primitiveTypeWithValidationSchema[A, V](
    name: String,
    make: Validation[V] => A,
    getValidation: A => Validation[V]
  ): Schema[A] = new Schema(
    reflect = new Reflect.Record[Binding, A](
      fields = Vector(validationSchema.reflect.asTerm("validation")),
      typeId = TypeId.nominal[A](name, primitiveTypeOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = 1
          def construct(in: Registers, offset: RegisterOffset): A =
            make(in.getObject(offset).asInstanceOf[Validation[V]])
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit =
            out.setObject(offset, getValidation(in))
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val primitiveTypeBooleanSchema: Schema[PrimitiveType.Boolean] =
    primitiveTypeWithValidationSchema("Boolean", PrimitiveType.Boolean(_), _.validation)

  private lazy val primitiveTypeByteSchema: Schema[PrimitiveType.Byte] =
    primitiveTypeWithValidationSchema("Byte", PrimitiveType.Byte(_), _.validation)

  private lazy val primitiveTypeShortSchema: Schema[PrimitiveType.Short] =
    primitiveTypeWithValidationSchema("Short", PrimitiveType.Short(_), _.validation)

  private lazy val primitiveTypeIntSchema: Schema[PrimitiveType.Int] =
    primitiveTypeWithValidationSchema("Int", PrimitiveType.Int(_), _.validation)

  private lazy val primitiveTypeLongSchema: Schema[PrimitiveType.Long] =
    primitiveTypeWithValidationSchema("Long", PrimitiveType.Long(_), _.validation)

  private lazy val primitiveTypeFloatSchema: Schema[PrimitiveType.Float] =
    primitiveTypeWithValidationSchema("Float", PrimitiveType.Float(_), _.validation)

  private lazy val primitiveTypeDoubleSchema: Schema[PrimitiveType.Double] =
    primitiveTypeWithValidationSchema("Double", PrimitiveType.Double(_), _.validation)

  private lazy val primitiveTypeCharSchema: Schema[PrimitiveType.Char] =
    primitiveTypeWithValidationSchema("Char", PrimitiveType.Char(_), _.validation)

  private lazy val primitiveTypeStringSchema: Schema[PrimitiveType.String] =
    primitiveTypeWithValidationSchema("String", PrimitiveType.String(_), _.validation)

  private lazy val primitiveTypeBigIntSchema: Schema[PrimitiveType.BigInt] =
    primitiveTypeWithValidationSchema("BigInt", PrimitiveType.BigInt(_), _.validation)

  private lazy val primitiveTypeBigDecimalSchema: Schema[PrimitiveType.BigDecimal] =
    primitiveTypeWithValidationSchema("BigDecimal", PrimitiveType.BigDecimal(_), _.validation)

  private lazy val primitiveTypeDayOfWeekSchema: Schema[PrimitiveType.DayOfWeek] =
    primitiveTypeWithValidationSchema("DayOfWeek", PrimitiveType.DayOfWeek(_), _.validation)

  private lazy val primitiveTypeDurationSchema: Schema[PrimitiveType.Duration] =
    primitiveTypeWithValidationSchema("Duration", PrimitiveType.Duration(_), _.validation)

  private lazy val primitiveTypeInstantSchema: Schema[PrimitiveType.Instant] =
    primitiveTypeWithValidationSchema("Instant", PrimitiveType.Instant(_), _.validation)

  private lazy val primitiveTypeLocalDateSchema: Schema[PrimitiveType.LocalDate] =
    primitiveTypeWithValidationSchema("LocalDate", PrimitiveType.LocalDate(_), _.validation)

  private lazy val primitiveTypeLocalDateTimeSchema: Schema[PrimitiveType.LocalDateTime] =
    primitiveTypeWithValidationSchema("LocalDateTime", PrimitiveType.LocalDateTime(_), _.validation)

  private lazy val primitiveTypeLocalTimeSchema: Schema[PrimitiveType.LocalTime] =
    primitiveTypeWithValidationSchema("LocalTime", PrimitiveType.LocalTime(_), _.validation)

  private lazy val primitiveTypeMonthSchema: Schema[PrimitiveType.Month] =
    primitiveTypeWithValidationSchema("Month", PrimitiveType.Month(_), _.validation)

  private lazy val primitiveTypeMonthDaySchema: Schema[PrimitiveType.MonthDay] =
    primitiveTypeWithValidationSchema("MonthDay", PrimitiveType.MonthDay(_), _.validation)

  private lazy val primitiveTypeOffsetDateTimeSchema: Schema[PrimitiveType.OffsetDateTime] =
    primitiveTypeWithValidationSchema("OffsetDateTime", PrimitiveType.OffsetDateTime(_), _.validation)

  private lazy val primitiveTypeOffsetTimeSchema: Schema[PrimitiveType.OffsetTime] =
    primitiveTypeWithValidationSchema("OffsetTime", PrimitiveType.OffsetTime(_), _.validation)

  private lazy val primitiveTypePeriodSchema: Schema[PrimitiveType.Period] =
    primitiveTypeWithValidationSchema("Period", PrimitiveType.Period(_), _.validation)

  private lazy val primitiveTypeYearSchema: Schema[PrimitiveType.Year] =
    primitiveTypeWithValidationSchema("Year", PrimitiveType.Year(_), _.validation)

  private lazy val primitiveTypeYearMonthSchema: Schema[PrimitiveType.YearMonth] =
    primitiveTypeWithValidationSchema("YearMonth", PrimitiveType.YearMonth(_), _.validation)

  private lazy val primitiveTypeZoneIdSchema: Schema[PrimitiveType.ZoneId] =
    primitiveTypeWithValidationSchema("ZoneId", PrimitiveType.ZoneId(_), _.validation)

  private lazy val primitiveTypeZoneOffsetSchema: Schema[PrimitiveType.ZoneOffset] =
    primitiveTypeWithValidationSchema("ZoneOffset", PrimitiveType.ZoneOffset(_), _.validation)

  private lazy val primitiveTypeZonedDateTimeSchema: Schema[PrimitiveType.ZonedDateTime] =
    primitiveTypeWithValidationSchema("ZonedDateTime", PrimitiveType.ZonedDateTime(_), _.validation)

  private lazy val primitiveTypeCurrencySchema: Schema[PrimitiveType.Currency] =
    primitiveTypeWithValidationSchema("Currency", PrimitiveType.Currency(_), _.validation)

  private lazy val primitiveTypeUUIDSchema: Schema[PrimitiveType.UUID] =
    primitiveTypeWithValidationSchema("UUID", PrimitiveType.UUID(_), _.validation)

  /** Schema for [[PrimitiveType]] (type-erased variant). */
  implicit lazy val primitiveTypeSchema: Schema[PrimitiveType[_]] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveType[_]](
      cases = Vector(
        primitiveTypeUnitSchema.reflect.asTerm("Unit"),
        primitiveTypeBooleanSchema.reflect.asTerm("Boolean"),
        primitiveTypeByteSchema.reflect.asTerm("Byte"),
        primitiveTypeShortSchema.reflect.asTerm("Short"),
        primitiveTypeIntSchema.reflect.asTerm("Int"),
        primitiveTypeLongSchema.reflect.asTerm("Long"),
        primitiveTypeFloatSchema.reflect.asTerm("Float"),
        primitiveTypeDoubleSchema.reflect.asTerm("Double"),
        primitiveTypeCharSchema.reflect.asTerm("Char"),
        primitiveTypeStringSchema.reflect.asTerm("String"),
        primitiveTypeBigIntSchema.reflect.asTerm("BigInt"),
        primitiveTypeBigDecimalSchema.reflect.asTerm("BigDecimal"),
        primitiveTypeDayOfWeekSchema.reflect.asTerm("DayOfWeek"),
        primitiveTypeDurationSchema.reflect.asTerm("Duration"),
        primitiveTypeInstantSchema.reflect.asTerm("Instant"),
        primitiveTypeLocalDateSchema.reflect.asTerm("LocalDate"),
        primitiveTypeLocalDateTimeSchema.reflect.asTerm("LocalDateTime"),
        primitiveTypeLocalTimeSchema.reflect.asTerm("LocalTime"),
        primitiveTypeMonthSchema.reflect.asTerm("Month"),
        primitiveTypeMonthDaySchema.reflect.asTerm("MonthDay"),
        primitiveTypeOffsetDateTimeSchema.reflect.asTerm("OffsetDateTime"),
        primitiveTypeOffsetTimeSchema.reflect.asTerm("OffsetTime"),
        primitiveTypePeriodSchema.reflect.asTerm("Period"),
        primitiveTypeYearSchema.reflect.asTerm("Year"),
        primitiveTypeYearMonthSchema.reflect.asTerm("YearMonth"),
        primitiveTypeZoneIdSchema.reflect.asTerm("ZoneId"),
        primitiveTypeZoneOffsetSchema.reflect.asTerm("ZoneOffset"),
        primitiveTypeZonedDateTimeSchema.reflect.asTerm("ZonedDateTime"),
        primitiveTypeCurrencySchema.reflect.asTerm("Currency"),
        primitiveTypeUUIDSchema.reflect.asTerm("UUID")
      ),
      typeId = TypeId.nominal[PrimitiveType[_]]("PrimitiveType", zioBlocksSchemaOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveType[_]] {
          def discriminate(a: PrimitiveType[_]): Int = a match {
            case PrimitiveType.Unit              => 0
            case _: PrimitiveType.Boolean        => 1
            case _: PrimitiveType.Byte           => 2
            case _: PrimitiveType.Short          => 3
            case _: PrimitiveType.Int            => 4
            case _: PrimitiveType.Long           => 5
            case _: PrimitiveType.Float          => 6
            case _: PrimitiveType.Double         => 7
            case _: PrimitiveType.Char           => 8
            case _: PrimitiveType.String         => 9
            case _: PrimitiveType.BigInt         => 10
            case _: PrimitiveType.BigDecimal     => 11
            case _: PrimitiveType.DayOfWeek      => 12
            case _: PrimitiveType.Duration       => 13
            case _: PrimitiveType.Instant        => 14
            case _: PrimitiveType.LocalDate      => 15
            case _: PrimitiveType.LocalDateTime  => 16
            case _: PrimitiveType.LocalTime      => 17
            case _: PrimitiveType.Month          => 18
            case _: PrimitiveType.MonthDay       => 19
            case _: PrimitiveType.OffsetDateTime => 20
            case _: PrimitiveType.OffsetTime     => 21
            case _: PrimitiveType.Period         => 22
            case _: PrimitiveType.Year           => 23
            case _: PrimitiveType.YearMonth      => 24
            case _: PrimitiveType.ZoneId         => 25
            case _: PrimitiveType.ZoneOffset     => 26
            case _: PrimitiveType.ZonedDateTime  => 27
            case _: PrimitiveType.Currency       => 28
            case _: PrimitiveType.UUID           => 29
          }
        },
        matchers = Matchers(
          new Matcher[PrimitiveType.Unit.type] {
            def downcastOrNull(a: Any): PrimitiveType.Unit.type = a match {
              case PrimitiveType.Unit => PrimitiveType.Unit
              case _                  => null.asInstanceOf[PrimitiveType.Unit.type]
            }
          },
          new Matcher[PrimitiveType.Boolean] {
            def downcastOrNull(a: Any): PrimitiveType.Boolean = a match {
              case x: PrimitiveType.Boolean => x
              case _                        => null.asInstanceOf[PrimitiveType.Boolean]
            }
          },
          new Matcher[PrimitiveType.Byte] {
            def downcastOrNull(a: Any): PrimitiveType.Byte = a match {
              case x: PrimitiveType.Byte => x
              case _                     => null.asInstanceOf[PrimitiveType.Byte]
            }
          },
          new Matcher[PrimitiveType.Short] {
            def downcastOrNull(a: Any): PrimitiveType.Short = a match {
              case x: PrimitiveType.Short => x
              case _                      => null.asInstanceOf[PrimitiveType.Short]
            }
          },
          new Matcher[PrimitiveType.Int] {
            def downcastOrNull(a: Any): PrimitiveType.Int = a match {
              case x: PrimitiveType.Int => x
              case _                    => null.asInstanceOf[PrimitiveType.Int]
            }
          },
          new Matcher[PrimitiveType.Long] {
            def downcastOrNull(a: Any): PrimitiveType.Long = a match {
              case x: PrimitiveType.Long => x
              case _                     => null.asInstanceOf[PrimitiveType.Long]
            }
          },
          new Matcher[PrimitiveType.Float] {
            def downcastOrNull(a: Any): PrimitiveType.Float = a match {
              case x: PrimitiveType.Float => x
              case _                      => null.asInstanceOf[PrimitiveType.Float]
            }
          },
          new Matcher[PrimitiveType.Double] {
            def downcastOrNull(a: Any): PrimitiveType.Double = a match {
              case x: PrimitiveType.Double => x
              case _                       => null.asInstanceOf[PrimitiveType.Double]
            }
          },
          new Matcher[PrimitiveType.Char] {
            def downcastOrNull(a: Any): PrimitiveType.Char = a match {
              case x: PrimitiveType.Char => x
              case _                     => null.asInstanceOf[PrimitiveType.Char]
            }
          },
          new Matcher[PrimitiveType.String] {
            def downcastOrNull(a: Any): PrimitiveType.String = a match {
              case x: PrimitiveType.String => x
              case _                       => null.asInstanceOf[PrimitiveType.String]
            }
          },
          new Matcher[PrimitiveType.BigInt] {
            def downcastOrNull(a: Any): PrimitiveType.BigInt = a match {
              case x: PrimitiveType.BigInt => x
              case _                       => null.asInstanceOf[PrimitiveType.BigInt]
            }
          },
          new Matcher[PrimitiveType.BigDecimal] {
            def downcastOrNull(a: Any): PrimitiveType.BigDecimal = a match {
              case x: PrimitiveType.BigDecimal => x
              case _                           => null.asInstanceOf[PrimitiveType.BigDecimal]
            }
          },
          new Matcher[PrimitiveType.DayOfWeek] {
            def downcastOrNull(a: Any): PrimitiveType.DayOfWeek = a match {
              case x: PrimitiveType.DayOfWeek => x
              case _                          => null.asInstanceOf[PrimitiveType.DayOfWeek]
            }
          },
          new Matcher[PrimitiveType.Duration] {
            def downcastOrNull(a: Any): PrimitiveType.Duration = a match {
              case x: PrimitiveType.Duration => x
              case _                         => null.asInstanceOf[PrimitiveType.Duration]
            }
          },
          new Matcher[PrimitiveType.Instant] {
            def downcastOrNull(a: Any): PrimitiveType.Instant = a match {
              case x: PrimitiveType.Instant => x
              case _                        => null.asInstanceOf[PrimitiveType.Instant]
            }
          },
          new Matcher[PrimitiveType.LocalDate] {
            def downcastOrNull(a: Any): PrimitiveType.LocalDate = a match {
              case x: PrimitiveType.LocalDate => x
              case _                          => null.asInstanceOf[PrimitiveType.LocalDate]
            }
          },
          new Matcher[PrimitiveType.LocalDateTime] {
            def downcastOrNull(a: Any): PrimitiveType.LocalDateTime = a match {
              case x: PrimitiveType.LocalDateTime => x
              case _                              => null.asInstanceOf[PrimitiveType.LocalDateTime]
            }
          },
          new Matcher[PrimitiveType.LocalTime] {
            def downcastOrNull(a: Any): PrimitiveType.LocalTime = a match {
              case x: PrimitiveType.LocalTime => x
              case _                          => null.asInstanceOf[PrimitiveType.LocalTime]
            }
          },
          new Matcher[PrimitiveType.Month] {
            def downcastOrNull(a: Any): PrimitiveType.Month = a match {
              case x: PrimitiveType.Month => x
              case _                      => null.asInstanceOf[PrimitiveType.Month]
            }
          },
          new Matcher[PrimitiveType.MonthDay] {
            def downcastOrNull(a: Any): PrimitiveType.MonthDay = a match {
              case x: PrimitiveType.MonthDay => x
              case _                         => null.asInstanceOf[PrimitiveType.MonthDay]
            }
          },
          new Matcher[PrimitiveType.OffsetDateTime] {
            def downcastOrNull(a: Any): PrimitiveType.OffsetDateTime = a match {
              case x: PrimitiveType.OffsetDateTime => x
              case _                               => null.asInstanceOf[PrimitiveType.OffsetDateTime]
            }
          },
          new Matcher[PrimitiveType.OffsetTime] {
            def downcastOrNull(a: Any): PrimitiveType.OffsetTime = a match {
              case x: PrimitiveType.OffsetTime => x
              case _                           => null.asInstanceOf[PrimitiveType.OffsetTime]
            }
          },
          new Matcher[PrimitiveType.Period] {
            def downcastOrNull(a: Any): PrimitiveType.Period = a match {
              case x: PrimitiveType.Period => x
              case _                       => null.asInstanceOf[PrimitiveType.Period]
            }
          },
          new Matcher[PrimitiveType.Year] {
            def downcastOrNull(a: Any): PrimitiveType.Year = a match {
              case x: PrimitiveType.Year => x
              case _                     => null.asInstanceOf[PrimitiveType.Year]
            }
          },
          new Matcher[PrimitiveType.YearMonth] {
            def downcastOrNull(a: Any): PrimitiveType.YearMonth = a match {
              case x: PrimitiveType.YearMonth => x
              case _                          => null.asInstanceOf[PrimitiveType.YearMonth]
            }
          },
          new Matcher[PrimitiveType.ZoneId] {
            def downcastOrNull(a: Any): PrimitiveType.ZoneId = a match {
              case x: PrimitiveType.ZoneId => x
              case _                       => null.asInstanceOf[PrimitiveType.ZoneId]
            }
          },
          new Matcher[PrimitiveType.ZoneOffset] {
            def downcastOrNull(a: Any): PrimitiveType.ZoneOffset = a match {
              case x: PrimitiveType.ZoneOffset => x
              case _                           => null.asInstanceOf[PrimitiveType.ZoneOffset]
            }
          },
          new Matcher[PrimitiveType.ZonedDateTime] {
            def downcastOrNull(a: Any): PrimitiveType.ZonedDateTime = a match {
              case x: PrimitiveType.ZonedDateTime => x
              case _                              => null.asInstanceOf[PrimitiveType.ZonedDateTime]
            }
          },
          new Matcher[PrimitiveType.Currency] {
            def downcastOrNull(a: Any): PrimitiveType.Currency = a match {
              case x: PrimitiveType.Currency => x
              case _                         => null.asInstanceOf[PrimitiveType.Currency]
            }
          },
          new Matcher[PrimitiveType.UUID] {
            def downcastOrNull(a: Any): PrimitiveType.UUID = a match {
              case x: PrimitiveType.UUID => x
              case _                     => null.asInstanceOf[PrimitiveType.UUID]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  /**
   * Schema for [[DynamicSchema]].
   *
   * Serializes the schema structure by converting the underlying
   * [[Reflect.Unbound]] to a [[DynamicValue]] representation and back. This
   * enables round-trip serialization while preserving full structural fidelity.
   */
  implicit lazy val schema: Schema[DynamicSchema] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchema](
      fields = Vector(Schema[DynamicValue].reflect.asTerm("reflect")),
      typeId = TypeId.nominal[DynamicSchema]("DynamicSchema", zioBlocksSchemaOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchema] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchema =
            fromDynamicValue(in.getObject(offset).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[DynamicSchema] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchema): Unit =
            out.setObject(offset, toDynamicValue(in))
        }
      ),
      modifiers = Vector.empty
    )
  )

  /**
   * Converts a [[DynamicSchema]] to a [[DynamicValue]] representation for
   * serialization.
   */
  def toDynamicValue(ds: DynamicSchema): DynamicValue =
    reflectToDynamicValue(ds.reflect)

  /**
   * Reconstructs a [[DynamicSchema]] from a [[DynamicValue]] representation.
   */
  def fromDynamicValue(dv: DynamicValue): DynamicSchema =
    new DynamicSchema(dynamicValueToReflect(dv))

  private def reflectToDynamicValue(reflect: Reflect[NoBinding, _]): DynamicValue = {
    import zio.blocks.chunk.Chunk

    def typeIdToDV(tid: TypeId[_]): DynamicValue = typeIdSchema.toDynamicValue(tid)

    def docToDV(doc: Doc): DynamicValue = doc match {
      case Doc.Empty       => DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))
      case Doc.Text(value) =>
        DynamicValue.Variant(
          "Text",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.String(value))))
        )
      case Doc.Concat(flatten) =>
        DynamicValue.Variant(
          "Concat",
          DynamicValue.Record(Chunk("flatten" -> DynamicValue.Sequence(Chunk.from(flatten.map(docToDV)))))
        )
    }

    def modifierToDV(m: Modifier.Reflect): DynamicValue = m match {
      case Modifier.config(key, value) =>
        DynamicValue.Variant(
          "config",
          DynamicValue.Record(
            Chunk(
              "key"   -> DynamicValue.Primitive(PrimitiveValue.String(key)),
              "value" -> DynamicValue.Primitive(PrimitiveValue.String(value))
            )
          )
        )
    }

    def validationToDV(v: Validation[_]): DynamicValue = v match {
      case Validation.None                    => DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
      case Validation.Numeric.Positive        => DynamicValue.Variant("Positive", DynamicValue.Record(Chunk.empty))
      case Validation.Numeric.Negative        => DynamicValue.Variant("Negative", DynamicValue.Record(Chunk.empty))
      case Validation.Numeric.NonPositive     => DynamicValue.Variant("NonPositive", DynamicValue.Record(Chunk.empty))
      case Validation.Numeric.NonNegative     => DynamicValue.Variant("NonNegative", DynamicValue.Record(Chunk.empty))
      case Validation.Numeric.Range(min, max) =>
        DynamicValue.Variant(
          "Range",
          DynamicValue.Record(
            Chunk(
              "min" -> min.map(numericToDynamicValue).getOrElse(DynamicValue.Null),
              "max" -> max.map(numericToDynamicValue).getOrElse(DynamicValue.Null)
            )
          )
        )
      case Validation.Numeric.Set(values) =>
        DynamicValue.Variant(
          "Set",
          DynamicValue.Record(
            Chunk(
              "values" -> DynamicValue.Sequence(
                Chunk.from(values.map(numericToDynamicValue).toSeq)
              )
            )
          )
        )
      case Validation.String.NonEmpty         => DynamicValue.Variant("StringNonEmpty", DynamicValue.Record(Chunk.empty))
      case Validation.String.Empty            => DynamicValue.Variant("StringEmpty", DynamicValue.Record(Chunk.empty))
      case Validation.String.Blank            => DynamicValue.Variant("StringBlank", DynamicValue.Record(Chunk.empty))
      case Validation.String.NonBlank         => DynamicValue.Variant("StringNonBlank", DynamicValue.Record(Chunk.empty))
      case Validation.String.Length(min, max) =>
        DynamicValue.Variant(
          "StringLength",
          DynamicValue.Record(
            Chunk(
              "min" -> min.map(v => DynamicValue.Primitive(PrimitiveValue.Int(v))).getOrElse(DynamicValue.Null),
              "max" -> max.map(v => DynamicValue.Primitive(PrimitiveValue.Int(v))).getOrElse(DynamicValue.Null)
            )
          )
        )
      case Validation.String.Pattern(regex) =>
        DynamicValue.Variant(
          "StringPattern",
          DynamicValue.Record(
            Chunk(
              "regex" -> DynamicValue.Primitive(PrimitiveValue.String(regex))
            )
          )
        )
    }

    def primitiveTypeToDV(pt: PrimitiveType[_]): DynamicValue = {
      val caseName = pt.typeId.name
      val payload  =
        if (caseName == "Unit")
          DynamicValue.Record(Chunk.empty)
        else
          DynamicValue.Record(Chunk("validation" -> validationToDV(pt.validation)))
      DynamicValue.Variant(caseName, payload)
    }

    def termToDV[S, A](term: Term[NoBinding, S, A]): DynamicValue = DynamicValue.Record(
      Chunk(
        "name"      -> DynamicValue.Primitive(PrimitiveValue.String(term.name)),
        "value"     -> reflectToDynamicValue(term.value),
        "doc"       -> docToDV(term.doc),
        "modifiers" -> DynamicValue.Sequence(Chunk.from(term.modifiers.map {
          case Modifier.transient() => DynamicValue.Variant("transient", DynamicValue.Record(Chunk.empty))
          case Modifier.rename(n)   =>
            DynamicValue.Variant(
              "rename",
              DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String(n))))
            )
          case Modifier.alias(n) =>
            DynamicValue.Variant(
              "alias",
              DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String(n))))
            )
          case Modifier.config(k, v) =>
            DynamicValue.Variant(
              "config",
              DynamicValue.Record(
                Chunk(
                  "key"   -> DynamicValue.Primitive(PrimitiveValue.String(k)),
                  "value" -> DynamicValue.Primitive(PrimitiveValue.String(v))
                )
              )
            )
        }))
      )
    )

    reflect match {
      case r: Reflect.Record[NoBinding, _] =>
        DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId"       -> typeIdToDV(r.typeId),
              "doc"          -> docToDV(r.doc),
              "modifiers"    -> DynamicValue.Sequence(Chunk.from(r.modifiers.map(modifierToDV))),
              "fields"       -> DynamicValue.Sequence(Chunk.from(r.fields.map(termToDV(_)))),
              "defaultValue" -> r.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"     -> DynamicValue.Sequence(Chunk.from(r.storedExamples))
            )
          )
        )

      case v: Reflect.Variant[NoBinding, _] =>
        DynamicValue.Variant(
          "Variant",
          DynamicValue.Record(
            Chunk(
              "typeId"       -> typeIdToDV(v.typeId),
              "doc"          -> docToDV(v.doc),
              "modifiers"    -> DynamicValue.Sequence(Chunk.from(v.modifiers.map(modifierToDV))),
              "cases"        -> DynamicValue.Sequence(Chunk.from(v.cases.map(termToDV(_)))),
              "defaultValue" -> v.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"     -> DynamicValue.Sequence(Chunk.from(v.storedExamples))
            )
          )
        )

      case s: Reflect.Sequence[NoBinding, _, _] @unchecked =>
        DynamicValue.Variant(
          "Sequence",
          DynamicValue.Record(
            Chunk(
              "typeId"       -> typeIdToDV(s.typeId),
              "doc"          -> docToDV(s.doc),
              "modifiers"    -> DynamicValue.Sequence(Chunk.from(s.modifiers.map(modifierToDV))),
              "element"      -> reflectToDynamicValue(s.element),
              "defaultValue" -> s.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"     -> DynamicValue.Sequence(Chunk.from(s.storedExamples))
            )
          )
        )

      case m: Reflect.Map[NoBinding, _, _, _] @unchecked =>
        DynamicValue.Variant(
          "Map",
          DynamicValue.Record(
            Chunk(
              "typeId"       -> typeIdToDV(m.typeId),
              "doc"          -> docToDV(m.doc),
              "modifiers"    -> DynamicValue.Sequence(Chunk.from(m.modifiers.map(modifierToDV))),
              "key"          -> reflectToDynamicValue(m.key),
              "value"        -> reflectToDynamicValue(m.value),
              "defaultValue" -> m.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"     -> DynamicValue.Sequence(Chunk.from(m.storedExamples))
            )
          )
        )

      case p: Reflect.Primitive[NoBinding, _] =>
        DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> typeIdToDV(p.typeId),
              "doc"           -> docToDV(p.doc),
              "modifiers"     -> DynamicValue.Sequence(Chunk.from(p.modifiers.map(modifierToDV))),
              "primitiveType" -> primitiveTypeToDV(p.primitiveType),
              "defaultValue"  -> p.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"      -> DynamicValue.Sequence(Chunk.from(p.storedExamples))
            )
          )
        )

      case w: Reflect.Wrapper[NoBinding, _, _] =>
        DynamicValue.Variant(
          "Wrapper",
          DynamicValue.Record(
            Chunk(
              "typeId"       -> typeIdToDV(w.typeId),
              "doc"          -> docToDV(w.doc),
              "modifiers"    -> DynamicValue.Sequence(Chunk.from(w.modifiers.map(modifierToDV))),
              "wrapped"      -> reflectToDynamicValue(w.wrapped),
              "defaultValue" -> w.storedDefaultValue.getOrElse(DynamicValue.Null),
              "examples"     -> DynamicValue.Sequence(Chunk.from(w.storedExamples))
            )
          )
        )

      case _: Reflect.Dynamic[NoBinding] =>
        DynamicValue.Variant("Dynamic", DynamicValue.Record(Chunk.empty))

      case d: Reflect.Deferred[NoBinding, _] =>
        reflectToDynamicValue(d.value)
    }
  }

  private def dynamicValueToReflect(dv: DynamicValue): Reflect.Unbound[_] = {
    import zio.blocks.chunk.Chunk

    def dvToTypeId(dv: DynamicValue): TypeId[Any] =
      typeIdSchema.fromDynamicValue(dv) match {
        case Right(tid) => tid.asInstanceOf[TypeId[Any]]
        case Left(_)    => TypeId.nominal[Any]("Unknown", Owner.Root)
      }

    def dvToDoc(dv: DynamicValue): Doc = dv match {
      case DynamicValue.Variant("Empty", _)                          => Doc.Empty
      case DynamicValue.Variant("Text", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        fieldMap.get("value") match {
          case Some(DynamicValue.Primitive(PrimitiveValue.String(s))) => Doc.Text(s)
          case _                                                      => Doc.Empty
        }
      case DynamicValue.Variant("Concat", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        fieldMap.get("flatten") match {
          case Some(DynamicValue.Sequence(elems)) =>
            Doc.Concat(elems.map(dvToDoc).collect { case l: Doc.Leaf => l }.toIndexedSeq)
          case _ => Doc.Empty
        }
      case _ => Doc.Empty
    }

    def dvToModifiers(dv: DynamicValue): Seq[Modifier.Reflect] = dv match {
      case DynamicValue.Sequence(elems) =>
        elems.flatMap {
          case DynamicValue.Variant("config", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            for {
              key   <- fieldMap.get("key").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }
              value <- fieldMap.get("value").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }
            } yield Modifier.config(key, value)
          case _ => scala.None
        }.toSeq
      case _ => Nil
    }

    def dvToTermModifiers(dv: DynamicValue): Seq[Modifier.Term] = dv match {
      case DynamicValue.Sequence(elems) =>
        elems.flatMap {
          case DynamicValue.Variant("transient", _)                        => Some(Modifier.transient())
          case DynamicValue.Variant("rename", DynamicValue.Record(fields)) =>
            fields.toMap.get("name").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
              Modifier.rename(s)
            }
          case DynamicValue.Variant("alias", DynamicValue.Record(fields)) =>
            fields.toMap.get("name").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
              Modifier.alias(s)
            }
          case DynamicValue.Variant("config", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            for {
              key   <- fieldMap.get("key").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }
              value <- fieldMap.get("value").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }
            } yield Modifier.config(key, value)
          case _ => scala.None
        }.toSeq
      case _ => Nil
    }

    def dvToOptionalDV(dv: DynamicValue): Option[DynamicValue] = dv match {
      case DynamicValue.Null => scala.None
      case other             => Some(other)
    }

    def dvToExamples(dv: DynamicValue): Seq[DynamicValue] = dv match {
      case DynamicValue.Sequence(elems) => elems.toSeq
      case _                            => Nil
    }

    def dvToTerm(dv: DynamicValue): Term[NoBinding, Any, Any] = dv match {
      case DynamicValue.Record(fields) =>
        val fieldMap = fields.toMap
        val name     = fieldMap("name") match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
          case _                                                => "unknown"
        }
        val value     = dynamicValueToReflect(fieldMap("value"))
        val doc       = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers = dvToTermModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        new Term(name, value.asInstanceOf[Reflect.Unbound[Any]], doc, modifiers)
      case _ => new Term("unknown", new Reflect.Dynamic[NoBinding](NoBinding()).asInstanceOf[Reflect.Unbound[Any]])
    }

    def dvToValidation(dv: DynamicValue): Validation[Any] = dv match {
      case DynamicValue.Variant("None", _)                            => Validation.None
      case DynamicValue.Variant("Positive", _)                        => Validation.Numeric.Positive
      case DynamicValue.Variant("Negative", _)                        => Validation.Numeric.Negative
      case DynamicValue.Variant("NonPositive", _)                     => Validation.Numeric.NonPositive
      case DynamicValue.Variant("NonNegative", _)                     => Validation.Numeric.NonNegative
      case DynamicValue.Variant("StringNonEmpty", _)                  => Validation.String.NonEmpty.asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("StringEmpty", _)                     => Validation.String.Empty.asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("StringBlank", _)                     => Validation.String.Blank.asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("StringNonBlank", _)                  => Validation.String.NonBlank.asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("Range", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        val min      = fieldMap.get("min").flatMap(dynamicValueToNumericOpt)
        val max      = fieldMap.get("max").flatMap(dynamicValueToNumericOpt)
        Validation.Numeric.Range(min, max).asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("Set", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        val values   = fieldMap.get("values") match {
          case Some(DynamicValue.Sequence(elems)) =>
            elems.flatMap(dynamicValueToNumericOpt).toSet
          case _ => Set.empty[Any]
        }
        Validation.Numeric.Set(values).asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("StringLength", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        val min      = fieldMap.get("min").flatMap {
          case DynamicValue.Primitive(PrimitiveValue.Int(i)) => Some(i)
          case DynamicValue.Null                             => scala.None
          case _                                             => scala.None
        }
        val max = fieldMap.get("max").flatMap {
          case DynamicValue.Primitive(PrimitiveValue.Int(i)) => Some(i)
          case DynamicValue.Null                             => scala.None
          case _                                             => scala.None
        }
        Validation.String.Length(min, max).asInstanceOf[Validation[Any]]
      case DynamicValue.Variant("StringPattern", DynamicValue.Record(fields)) =>
        val fieldMap = fields.toMap
        val regex    = fieldMap.get("regex").collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) => s }
        regex.map(r => Validation.String.Pattern(r).asInstanceOf[Validation[Any]]).getOrElse(Validation.None)
      case _ => Validation.None
    }

    def dvToPrimitiveType(dv: DynamicValue): PrimitiveType[Any] = dv match {
      case DynamicValue.Variant(typeName, DynamicValue.Record(fields)) =>
        val fieldMap            = fields.toMap
        val validation          = fieldMap.get("validation").map(dvToValidation).getOrElse(Validation.None)
        def v[A]: Validation[A] = validation.asInstanceOf[Validation[A]]
        typeName match {
          case "Unit"          => PrimitiveType.Unit.asInstanceOf[PrimitiveType[Any]]
          case "Boolean"       => PrimitiveType.Boolean(v[Boolean]).asInstanceOf[PrimitiveType[Any]]
          case "Byte"          => PrimitiveType.Byte(v[Byte]).asInstanceOf[PrimitiveType[Any]]
          case "Short"         => PrimitiveType.Short(v[Short]).asInstanceOf[PrimitiveType[Any]]
          case "Int"           => PrimitiveType.Int(v[Int]).asInstanceOf[PrimitiveType[Any]]
          case "Long"          => PrimitiveType.Long(v[Long]).asInstanceOf[PrimitiveType[Any]]
          case "Float"         => PrimitiveType.Float(v[Float]).asInstanceOf[PrimitiveType[Any]]
          case "Double"        => PrimitiveType.Double(v[Double]).asInstanceOf[PrimitiveType[Any]]
          case "Char"          => PrimitiveType.Char(v[Char]).asInstanceOf[PrimitiveType[Any]]
          case "String"        => PrimitiveType.String(v[String]).asInstanceOf[PrimitiveType[Any]]
          case "BigInt"        => PrimitiveType.BigInt(v[BigInt]).asInstanceOf[PrimitiveType[Any]]
          case "BigDecimal"    => PrimitiveType.BigDecimal(v[BigDecimal]).asInstanceOf[PrimitiveType[Any]]
          case "UUID"          => PrimitiveType.UUID(v[java.util.UUID]).asInstanceOf[PrimitiveType[Any]]
          case "Instant"       => PrimitiveType.Instant(v[java.time.Instant]).asInstanceOf[PrimitiveType[Any]]
          case "LocalDate"     => PrimitiveType.LocalDate(v[java.time.LocalDate]).asInstanceOf[PrimitiveType[Any]]
          case "LocalDateTime" =>
            PrimitiveType.LocalDateTime(v[java.time.LocalDateTime]).asInstanceOf[PrimitiveType[Any]]
          case "LocalTime"      => PrimitiveType.LocalTime(v[java.time.LocalTime]).asInstanceOf[PrimitiveType[Any]]
          case "Duration"       => PrimitiveType.Duration(v[java.time.Duration]).asInstanceOf[PrimitiveType[Any]]
          case "DayOfWeek"      => PrimitiveType.DayOfWeek(v[java.time.DayOfWeek]).asInstanceOf[PrimitiveType[Any]]
          case "Month"          => PrimitiveType.Month(v[java.time.Month]).asInstanceOf[PrimitiveType[Any]]
          case "MonthDay"       => PrimitiveType.MonthDay(v[java.time.MonthDay]).asInstanceOf[PrimitiveType[Any]]
          case "OffsetDateTime" =>
            PrimitiveType.OffsetDateTime(v[java.time.OffsetDateTime]).asInstanceOf[PrimitiveType[Any]]
          case "OffsetTime"    => PrimitiveType.OffsetTime(v[java.time.OffsetTime]).asInstanceOf[PrimitiveType[Any]]
          case "Period"        => PrimitiveType.Period(v[java.time.Period]).asInstanceOf[PrimitiveType[Any]]
          case "Year"          => PrimitiveType.Year(v[java.time.Year]).asInstanceOf[PrimitiveType[Any]]
          case "YearMonth"     => PrimitiveType.YearMonth(v[java.time.YearMonth]).asInstanceOf[PrimitiveType[Any]]
          case "ZoneId"        => PrimitiveType.ZoneId(v[java.time.ZoneId]).asInstanceOf[PrimitiveType[Any]]
          case "ZoneOffset"    => PrimitiveType.ZoneOffset(v[java.time.ZoneOffset]).asInstanceOf[PrimitiveType[Any]]
          case "ZonedDateTime" =>
            PrimitiveType.ZonedDateTime(v[java.time.ZonedDateTime]).asInstanceOf[PrimitiveType[Any]]
          case "Currency" => PrimitiveType.Currency(v[java.util.Currency]).asInstanceOf[PrimitiveType[Any]]
          case _          => PrimitiveType.String(v[String]).asInstanceOf[PrimitiveType[Any]]
        }
      case _ => PrimitiveType.String(Validation.None).asInstanceOf[PrimitiveType[Any]]
    }

    dv match {
      case DynamicValue.Variant("Record", DynamicValue.Record(fields)) =>
        val fieldMap   = fields.toMap
        val tid        = dvToTypeId(fieldMap("typeId"))
        val doc        = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers  = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val termFields = fieldMap("fields") match {
          case DynamicValue.Sequence(elems) => elems.map(dvToTerm).toVector
          case _                            => Vector.empty
        }
        val defaultValue = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples     = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Record[NoBinding, Any](
          fields = termFields,
          typeId = tid,
          recordBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Variant", DynamicValue.Record(fields)) =>
        val fieldMap  = fields.toMap
        val tid       = dvToTypeId(fieldMap("typeId"))
        val doc       = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val cases     = fieldMap("cases") match {
          case DynamicValue.Sequence(elems) => elems.map(dvToTerm).toVector
          case _                            => Vector.empty
        }
        val defaultValue = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples     = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Variant[NoBinding, Any](
          cases = cases,
          typeId = tid,
          variantBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Sequence", DynamicValue.Record(fields)) =>
        val fieldMap     = fields.toMap
        val tid          = dvToTypeId(fieldMap("typeId"))
        val doc          = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers    = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val element      = dynamicValueToReflect(fieldMap("element"))
        val defaultValue = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples     = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Sequence[NoBinding, Any, Seq](
          element = element.asInstanceOf[Reflect.Unbound[Any]],
          typeId = tid.asInstanceOf[TypeId[Seq[Any]]],
          seqBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Map", DynamicValue.Record(fields)) =>
        val fieldMap     = fields.toMap
        val tid          = dvToTypeId(fieldMap("typeId"))
        val doc          = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers    = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val key          = dynamicValueToReflect(fieldMap("key"))
        val value        = dynamicValueToReflect(fieldMap("value"))
        val defaultValue = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples     = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Map[NoBinding, Any, Any, scala.collection.immutable.Map](
          key = key.asInstanceOf[Reflect.Unbound[Any]],
          value = value.asInstanceOf[Reflect.Unbound[Any]],
          typeId = tid.asInstanceOf[TypeId[scala.collection.immutable.Map[Any, Any]]],
          mapBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Primitive", DynamicValue.Record(fields)) =>
        val fieldMap      = fields.toMap
        val tid           = dvToTypeId(fieldMap("typeId"))
        val doc           = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers     = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val primitiveType = dvToPrimitiveType(fieldMap("primitiveType"))
        val defaultValue  = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples      = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Primitive[NoBinding, Any](
          primitiveType = primitiveType,
          typeId = tid,
          primitiveBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Wrapper", DynamicValue.Record(fields)) =>
        val fieldMap     = fields.toMap
        val tid          = dvToTypeId(fieldMap("typeId"))
        val doc          = dvToDoc(fieldMap.getOrElse("doc", DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty))))
        val modifiers    = dvToModifiers(fieldMap.getOrElse("modifiers", DynamicValue.Sequence(Chunk.empty)))
        val wrapped      = dynamicValueToReflect(fieldMap("wrapped"))
        val defaultValue = dvToOptionalDV(fieldMap.getOrElse("defaultValue", DynamicValue.Null))
        val examples     = dvToExamples(fieldMap.getOrElse("examples", DynamicValue.Sequence(Chunk.empty)))
        new Reflect.Wrapper[NoBinding, Any, Any](
          wrapped = wrapped.asInstanceOf[Reflect.Unbound[Any]],
          typeId = tid,
          wrapperBinding = NoBinding(),
          doc = doc,
          modifiers = modifiers,
          storedDefaultValue = defaultValue,
          storedExamples = examples
        )

      case DynamicValue.Variant("Dynamic", _) =>
        new Reflect.Dynamic[NoBinding](NoBinding())

      case _ =>
        new Reflect.Dynamic[NoBinding](NoBinding())
    }
  }
}
