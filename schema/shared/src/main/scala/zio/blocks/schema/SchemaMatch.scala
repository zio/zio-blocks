package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * Provides matching logic for SchemaRepr patterns against DynamicValue
 * instances.
 *
 * This object implements structural pattern matching between schema
 * representations and runtime dynamic values, enabling the search optic to find
 * values that match a given schema pattern.
 *
 * ==Matching Rules==
 *
 *   - '''Wildcard''': Matches any DynamicValue
 *   - '''Primitive(name)''': Matches DynamicValue.Primitive where the
 *     underlying PrimitiveValue type matches the name (case-insensitive)
 *   - '''Record(fields)''': Matches DynamicValue.Record using subset matching -
 *     all specified fields must exist with matching types, but extra fields are
 *     allowed
 *   - '''Variant(cases)''': Matches DynamicValue.Variant if the case name
 *     matches one of the pattern cases and the payload matches that case's
 *     pattern
 *   - '''Sequence(elem)''': Matches DynamicValue.Sequence if all elements match
 *     the element pattern (empty sequence always matches)
 *   - '''Map(k, v)''': Matches DynamicValue.Map if all entries have matching
 *     key and value types (empty map always matches)
 *   - '''Optional(inner)''': Matches DynamicValue.Null always, otherwise checks
 *     inner pattern
 *   - '''Nominal(name)''': Returns false (requires schema context not available
 *     in DynamicValue)
 */
object SchemaMatch {

  /**
   * Tests whether a DynamicValue matches a SchemaRepr pattern.
   *
   * @param pattern
   *   the schema pattern to match against
   * @param value
   *   the dynamic value to check
   * @return
   *   true if the value matches the pattern, false otherwise
   */
  def matches(pattern: SchemaRepr, value: DynamicValue): Boolean = (pattern, value) match {
    case (SchemaRepr.Wildcard, _) =>
      true

    case (SchemaRepr.Primitive(name), DynamicValue.Primitive(pv)) =>
      primitiveTypeMatches(name, pv)

    case (SchemaRepr.Record(fields), DynamicValue.Record(actualFields)) =>
      recordMatches(fields, actualFields)

    case (SchemaRepr.Variant(cases), DynamicValue.Variant(caseName, payload)) =>
      variantMatches(cases, caseName, payload)

    case (SchemaRepr.Sequence(elemPattern), DynamicValue.Sequence(elements)) =>
      sequenceMatches(elemPattern, elements)

    case (SchemaRepr.Map(keyPattern, valuePattern), DynamicValue.Map(entries)) =>
      mapMatches(keyPattern, valuePattern, entries)

    case (SchemaRepr.Optional(_), DynamicValue.Null) =>
      true

    case (SchemaRepr.Optional(inner), value) =>
      matches(inner, value)

    case (SchemaRepr.Nominal(_), _) =>
      // Nominal types require schema context not available in DynamicValue
      false

    case _ =>
      // Type mismatch (e.g., Primitive pattern against Record value)
      false
  }

  /**
   * Tests whether a PrimitiveValue's type matches a primitive type name. The
   * comparison is case-insensitive.
   */
  private def primitiveTypeMatches(name: String, pv: PrimitiveValue): Boolean = {
    val lowerName = name.toLowerCase
    pv match {
      case PrimitiveValue.Unit              => lowerName == "unit"
      case PrimitiveValue.Boolean(_)        => lowerName == "boolean"
      case PrimitiveValue.Byte(_)           => lowerName == "byte"
      case PrimitiveValue.Short(_)          => lowerName == "short"
      case PrimitiveValue.Int(_)            => lowerName == "int"
      case PrimitiveValue.Long(_)           => lowerName == "long"
      case PrimitiveValue.Float(_)          => lowerName == "float"
      case PrimitiveValue.Double(_)         => lowerName == "double"
      case PrimitiveValue.Char(_)           => lowerName == "char"
      case PrimitiveValue.String(_)         => lowerName == "string"
      case PrimitiveValue.BigInt(_)         => lowerName == "bigint"
      case PrimitiveValue.BigDecimal(_)     => lowerName == "bigdecimal"
      case PrimitiveValue.DayOfWeek(_)      => lowerName == "dayofweek"
      case PrimitiveValue.Duration(_)       => lowerName == "duration"
      case PrimitiveValue.Instant(_)        => lowerName == "instant"
      case PrimitiveValue.LocalDate(_)      => lowerName == "localdate"
      case PrimitiveValue.LocalDateTime(_)  => lowerName == "localdatetime"
      case PrimitiveValue.LocalTime(_)      => lowerName == "localtime"
      case PrimitiveValue.Month(_)          => lowerName == "month"
      case PrimitiveValue.MonthDay(_)       => lowerName == "monthday"
      case PrimitiveValue.OffsetDateTime(_) => lowerName == "offsetdatetime"
      case PrimitiveValue.OffsetTime(_)     => lowerName == "offsettime"
      case PrimitiveValue.Period(_)         => lowerName == "period"
      case PrimitiveValue.Year(_)           => lowerName == "year"
      case PrimitiveValue.YearMonth(_)      => lowerName == "yearmonth"
      case PrimitiveValue.ZoneId(_)         => lowerName == "zoneid"
      case PrimitiveValue.ZoneOffset(_)     => lowerName == "zoneoffset"
      case PrimitiveValue.ZonedDateTime(_)  => lowerName == "zoneddatetime"
      case PrimitiveValue.Currency(_)       => lowerName == "currency"
      case PrimitiveValue.UUID(_)           => lowerName == "uuid"
    }
  }

  /**
   * Tests whether a DynamicValue.Record matches a Record pattern using subset
   * matching. All fields specified in the pattern must exist in the actual
   * record with matching types. The actual record may have additional fields
   * that are not in the pattern.
   */
  private def recordMatches(
    patternFields: Vector[(String, SchemaRepr)],
    actualFields: Chunk[(String, DynamicValue)]
  ): Boolean =
    patternFields.forall { case (patternName, patternRepr) =>
      actualFields.exists { case (actualName, actualValue) =>
        actualName == patternName && matches(patternRepr, actualValue)
      }
    }

  /**
   * Tests whether a DynamicValue.Variant matches a Variant pattern. The
   * variant's case name must match one of the pattern's cases, and the payload
   * must match that case's pattern.
   */
  private def variantMatches(
    patternCases: Vector[(String, SchemaRepr)],
    caseName: String,
    payload: DynamicValue
  ): Boolean =
    patternCases.exists { case (patternCaseName, patternRepr) =>
      patternCaseName == caseName && matches(patternRepr, payload)
    }

  /**
   * Tests whether a DynamicValue.Sequence matches a Sequence pattern. Empty
   * sequences always match. For non-empty sequences, all elements must match
   * the element pattern.
   */
  private def sequenceMatches(
    elemPattern: SchemaRepr,
    elements: Chunk[DynamicValue]
  ): Boolean =
    if (elements.isEmpty) true
    else elements.forall(elem => matches(elemPattern, elem))

  /**
   * Tests whether a DynamicValue.Map matches a Map pattern. Empty maps always
   * match. For non-empty maps, all entries must have keys matching the key
   * pattern and values matching the value pattern.
   */
  private def mapMatches(
    keyPattern: SchemaRepr,
    valuePattern: SchemaRepr,
    entries: Chunk[(DynamicValue, DynamicValue)]
  ): Boolean =
    if (entries.isEmpty) true
    else entries.forall { case (k, v) => matches(keyPattern, k) && matches(valuePattern, v) }
}
