/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 *   - '''Nominal(name)''': indeterminate without schema context (see
 *     `matchesOption` — `matches` treats it as `false`)
 */
object SchemaMatch {

  /**
   * Tests whether a DynamicValue matches a SchemaRepr pattern.
   *
   * A `Nominal` pattern (or a pattern containing one nested inside) cannot be
   * decided from the `DynamicValue` alone and is treated as `false`. Use
   * `matchesOption` to distinguish "does not match" (`Some(false)`) from
   * "cannot be decided" (`None`).
   *
   * @param pattern
   *   the schema pattern to match against
   * @param value
   *   the dynamic value to check
   * @return
   *   true if the value matches the pattern, false otherwise
   */
  def matches(pattern: SchemaRepr, value: DynamicValue): Boolean =
    matchesOption(pattern, value).getOrElse(false)

  /**
   * Tests whether a DynamicValue matches a SchemaRepr pattern, preserving
   * indeterminacy.
   *
   * @param pattern
   *   the schema pattern to match against
   * @param value
   *   the dynamic value to check
   * @return
   *   `Some(true)` / `Some(false)` when the pattern can be decided, `None` when
   *   the pattern is (or contains) a `Nominal` reference that requires schema
   *   context not available in `DynamicValue`
   */
  def matchesOption(pattern: SchemaRepr, value: DynamicValue): Option[Boolean] = pattern match {
    case _: SchemaRepr.Wildcard.type => new Some(true)
    case p: SchemaRepr.Primitive     =>
      value match {
        case dv: DynamicValue.Primitive => new Some(primitiveTypeMatches(p.name, dv.value))
        case _                          => new Some(false)
      }
    case r: SchemaRepr.Record =>
      value match {
        case dv: DynamicValue.Record => recordMatchesOption(r.fields, dv.fields)
        case _                       => new Some(false)
      }
    case v: SchemaRepr.Variant =>
      value match {
        case dv: DynamicValue.Variant => variantMatchesOption(v.cases, dv.caseNameValue, dv.value)
        case _                        => new Some(false)
      }
    case s: SchemaRepr.Sequence =>
      value match {
        case dv: DynamicValue.Sequence => sequenceMatchesOption(s.element, dv.elements)
        case _                         => new Some(false)
      }
    case m: SchemaRepr.Map =>
      value match {
        case dv: DynamicValue.Map => mapMatchesOption(m.key, m.value, dv.entries)
        case _                    => new Some(false)
      }
    case o: SchemaRepr.Optional =>
      if (value eq DynamicValue.Null) new Some(true)
      else matchesOption(o.inner, value)
    case _ => None
  }

  /**
   * Tests whether a PrimitiveValue's type matches a primitive type name. The
   * comparison is case-insensitive.
   */
  private def primitiveTypeMatches(name: String, pv: PrimitiveValue): Boolean = name.compareToIgnoreCase(pv match {
    case _: PrimitiveValue.Unit.type      => "unit"
    case _: PrimitiveValue.Boolean        => "boolean"
    case _: PrimitiveValue.Byte           => "byte"
    case _: PrimitiveValue.Short          => "short"
    case _: PrimitiveValue.Int            => "int"
    case _: PrimitiveValue.Long           => "long"
    case _: PrimitiveValue.Float          => "float"
    case _: PrimitiveValue.Double         => "double"
    case _: PrimitiveValue.Char           => "char"
    case _: PrimitiveValue.String         => "string"
    case _: PrimitiveValue.BigInt         => "bigint"
    case _: PrimitiveValue.BigDecimal     => "bigdecimal"
    case _: PrimitiveValue.DayOfWeek      => "dayofweek"
    case _: PrimitiveValue.Duration       => "duration"
    case _: PrimitiveValue.Instant        => "instant"
    case _: PrimitiveValue.LocalDate      => "localdate"
    case _: PrimitiveValue.LocalDateTime  => "localdatetime"
    case _: PrimitiveValue.LocalTime      => "localtime"
    case _: PrimitiveValue.Month          => "month"
    case _: PrimitiveValue.MonthDay       => "monthday"
    case _: PrimitiveValue.OffsetDateTime => "offsetdatetime"
    case _: PrimitiveValue.OffsetTime     => "offsettime"
    case _: PrimitiveValue.Period         => "period"
    case _: PrimitiveValue.Year           => "year"
    case _: PrimitiveValue.YearMonth      => "yearmonth"
    case _: PrimitiveValue.ZoneId         => "zoneid"
    case _: PrimitiveValue.ZoneOffset     => "zoneoffset"
    case _: PrimitiveValue.ZonedDateTime  => "zoneddatetime"
    case _: PrimitiveValue.Currency       => "currency"
    case _: PrimitiveValue.UUID           => "uuid"
  }) == 0

  /**
   * Tests whether a DynamicValue.Record matches a Record pattern using subset
   * matching. All fields specified in the pattern must exist in the actual
   * record with matching types. The actual record may have additional fields
   * that are not in the pattern. Returns `None` when any nested pattern is
   * indeterminate (see `matchesOption`).
   */
  private[this] def recordMatchesOption(
    patternFields: IndexedSeq[(String, SchemaRepr)],
    actualFields: Chunk[(String, DynamicValue)]
  ): Option[Boolean] = {
    var result = true
    val it     = patternFields.iterator
    while (result && it.hasNext) {
      val (patternName, patternRepr) = it.next()
      var found                      = false
      var unknown                    = false
      val jt                         = actualFields.iterator
      while (!found && !unknown && jt.hasNext) {
        val (actualName, actualValue) = jt.next()
        if (actualName == patternName) {
          matchesOption(patternRepr, actualValue) match {
            case Some(matched) => found = matched
            case None          => unknown = true
          }
        }
      }
      if (unknown) return None
      if (!found) result = false
    }
    new Some(result)
  }

  /**
   * Tests whether a DynamicValue.Variant matches a Variant pattern. The
   * variant's case name must match one of the pattern's cases, and the payload
   * must match that case's pattern. Returns `None` when the matching case
   * payload is indeterminate (see `matchesOption`).
   */
  private[this] def variantMatchesOption(
    patternCases: IndexedSeq[(String, SchemaRepr)],
    caseName: String,
    payload: DynamicValue
  ): Option[Boolean] = {
    var result  = false
    var unknown = false
    val it      = patternCases.iterator
    while (!result && !unknown && it.hasNext) {
      val (patternCaseName, patternRepr) = it.next()
      if (patternCaseName == caseName) {
        matchesOption(patternRepr, payload) match {
          case Some(matched) => result = matched
          case None          => unknown = true
        }
      }
    }
    if (unknown) None else new Some(result)
  }

  /**
   * Tests whether a DynamicValue.Sequence matches a Sequence pattern. Empty
   * sequences always match. For non-empty sequences, all elements must match
   * the element pattern. Returns `None` when any element is indeterminate (see
   * `matchesOption`).
   */
  private[this] def sequenceMatchesOption(
    elemPattern: SchemaRepr,
    elements: Chunk[DynamicValue]
  ): Option[Boolean] = {
    var result  = true
    var unknown = false
    val it      = elements.iterator
    while (result && !unknown && it.hasNext) {
      matchesOption(elemPattern, it.next()) match {
        case Some(matched) => result = matched
        case None          => unknown = true
      }
    }
    if (unknown) None else new Some(result)
  }

  /**
   * Tests whether a DynamicValue.Map matches a Map pattern. Empty maps always
   * match. For non-empty maps, all entries must have keys matching the key
   * pattern and values matching the value pattern. Returns `None` when any
   * entry is indeterminate (see `matchesOption`).
   */
  private[this] def mapMatchesOption(
    keyPattern: SchemaRepr,
    valuePattern: SchemaRepr,
    entries: Chunk[(DynamicValue, DynamicValue)]
  ): Option[Boolean] = {
    var result  = true
    var unknown = false
    val it      = entries.iterator
    while (result && !unknown && it.hasNext) {
      val (k, v) = it.next()
      matchesOption(keyPattern, k) match {
        case Some(matched) => result = matched
        case None          => unknown = true
      }
      if (result && !unknown) {
        matchesOption(valuePattern, v) match {
          case Some(matched) => result = matched
          case None          => unknown = true
        }
      }
    }
    if (unknown) None else new Some(result)
  }
}
