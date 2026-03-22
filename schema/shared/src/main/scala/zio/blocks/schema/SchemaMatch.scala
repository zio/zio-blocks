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
import scala.annotation.tailrec

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
  @tailrec
  def matches(pattern: SchemaRepr, value: DynamicValue): Boolean = pattern match {
    case _: SchemaRepr.Wildcard.type => true
    case p: SchemaRepr.Primitive     =>
      value match {
        case dv: DynamicValue.Primitive => primitiveTypeMatches(p.name, dv.value)
        case _                          => false
      }
    case r: SchemaRepr.Record =>
      value match {
        case dv: DynamicValue.Record => recordMatches(r.fields, dv.fields)
        case _                       => false
      }
    case v: SchemaRepr.Variant =>
      value match {
        case dv: DynamicValue.Variant => variantMatches(v.cases, dv.caseNameValue, dv.value)
        case _                        => false
      }
    case s: SchemaRepr.Sequence =>
      value match {
        case dv: DynamicValue.Sequence => sequenceMatches(s.element, dv.elements)
        case _                         => false
      }
    case m: SchemaRepr.Map =>
      value match {
        case dv: DynamicValue.Map => mapMatches(m.key, m.value, dv.entries)
        case _                    => false
      }
    case o: SchemaRepr.Optional =>
      if (value eq DynamicValue.Null) true
      else matches(o.inner, value)
    case _ => false
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
   * that are not in the pattern.
   */
  private[this] def recordMatches(
    patternFields: IndexedSeq[(String, SchemaRepr)],
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
  private[this] def variantMatches(
    patternCases: IndexedSeq[(String, SchemaRepr)],
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
  private[this] def sequenceMatches(elemPattern: SchemaRepr, elements: Chunk[DynamicValue]): Boolean =
    elements.forall(elem => matches(elemPattern, elem))

  /**
   * Tests whether a DynamicValue.Map matches a Map pattern. Empty maps always
   * match. For non-empty maps, all entries must have keys matching the key
   * pattern and values matching the value pattern.
   */
  private[this] def mapMatches(
    keyPattern: SchemaRepr,
    valuePattern: SchemaRepr,
    entries: Chunk[(DynamicValue, DynamicValue)]
  ): Boolean = entries.forall { case (k, v) => matches(keyPattern, k) && matches(valuePattern, v) }
}
