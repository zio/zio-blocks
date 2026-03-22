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

package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaRepr
import scala.annotation.tailrec

/**
 * Provides matching logic for SchemaRepr patterns against Json instances.
 *
 * This object implements structural pattern matching between schema
 * representations and JSON values, enabling the search optic to find JSON
 * values that match a given schema pattern.
 *
 * ==Matching Rules==
 *
 *   - '''Wildcard''': Matches any Json value
 *   - '''Primitive(name)''': Matches Json primitive types:
 *     - "string" matches Json.String
 *     - "boolean" matches Json.Boolean
 *     - "int", "long", "short", "byte", "float", "double", "number", "bigint",
 *       "bigdecimal" all match Json.Number
 *     - "null" matches Json.Null
 *   - '''Record(fields)''': Matches Json.Object using subset matching - all
 *     specified fields must exist with matching types, but extra fields are
 *     allowed
 *   - '''Variant(cases)''': Returns false (JSON doesn't have tagged variants)
 *   - '''Sequence(elem)''': Matches Json.Array if all elements match the
 *     element pattern (empty array always matches)
 *   - '''Map(k, v)''': Matches Json.Object if key pattern is "string" and all
 *     values match the value pattern (empty object always matches)
 *   - '''Optional(inner)''': Matches Json.Null always, otherwise checks inner
 *     pattern
 *   - '''Nominal(name)''': Returns false (requires schema context not available
 *     in JSON)
 */
object JsonMatch {

  /**
   * Tests whether a Json value matches a SchemaRepr pattern.
   *
   * @param pattern
   *   the schema pattern to match against
   * @param json
   *   the JSON value to check
   * @return
   *   true if the JSON matches the pattern, false otherwise
   */
  @tailrec
  def matches(pattern: SchemaRepr, json: Json): Boolean = pattern match {
    case _: SchemaRepr.Wildcard.type => true
    case p: SchemaRepr.Primitive     => primitiveTypeMatches(p.name, json)
    case r: SchemaRepr.Record        =>
      json match {
        case obj: Json.Object => recordMatches(r.fields, obj.value)
        case _                => false
      }
    case s: SchemaRepr.Sequence =>
      json match {
        case arr: Json.Array => sequenceMatches(s.element, arr.value)
        case _               => false
      }
    case m: SchemaRepr.Map =>
      json match {
        case obj: Json.Object => mapMatches(m.key, m.value, obj.value)
        case _                => false
      }
    case o: SchemaRepr.Optional =>
      if (json eq Json.Null) true
      else matches(o.inner, json)
    case _ => false
  }

  /**
   * Tests whether a Json value matches a primitive type name. The comparison is
   * case-insensitive.
   */
  private[this] def primitiveTypeMatches(name: String, json: Json): Boolean = json match {
    case _: Json.String =>
      name.compareToIgnoreCase("string") == 0
    case _: Json.Number => // JSON numbers can match various numeric primitive types
      name.compareToIgnoreCase("number") == 0 ||
      name.compareToIgnoreCase("int") == 0 ||
      name.compareToIgnoreCase("long") == 0 ||
      name.compareToIgnoreCase("short") == 0 ||
      name.compareToIgnoreCase("byte") == 0 ||
      name.compareToIgnoreCase("float") == 0 ||
      name.compareToIgnoreCase("double") == 0 ||
      name.compareToIgnoreCase("bigint") == 0 ||
      name.compareToIgnoreCase("bigdecimal") == 0
    case _: Json.Boolean =>
      name.compareToIgnoreCase("boolean") == 0
    case _: Json.Null.type =>
      name.compareToIgnoreCase("null") == 0
    case Json.Object.empty =>
      name.compareToIgnoreCase("unit") == 0
    case _ =>
      false
  }

  /**
   * Tests whether a Json.Object matches a Record pattern using subset matching.
   * All fields specified in the pattern must exist in the object with matching
   * types. The object may have additional fields that are not in the pattern.
   */
  private[this] def recordMatches(
    patternFields: IndexedSeq[(String, SchemaRepr)],
    actualFields: Chunk[(String, Json)]
  ): Boolean =
    patternFields.forall { case (patternName, patternRepr) =>
      actualFields.exists { case (actualName, actualValue) =>
        actualName == patternName && matches(patternRepr, actualValue)
      }
    }

  /**
   * Tests whether a Json.Array matches a Sequence pattern. Empty arrays always
   * match. For non-empty arrays, all elements must match the element pattern.
   */
  private[this] def sequenceMatches(elemPattern: SchemaRepr, elements: Chunk[Json]): Boolean =
    elements.forall(elem => matches(elemPattern, elem))

  /**
   * Tests whether a Json.Object matches a Map pattern. JSON objects have string
   * keys, so we check if the key pattern matches "string" and all values match
   * the value pattern. Empty objects always match.
   */
  private[this] def mapMatches(
    keyPattern: SchemaRepr,
    valuePattern: SchemaRepr,
    fields: Chunk[(String, Json)]
  ): Boolean =
    (keyPattern match { // JSON object keys are always strings, so check if key pattern is compatible
      case p: SchemaRepr.Primitive     => p.name.compareToIgnoreCase("string") == 0
      case _: SchemaRepr.Wildcard.type => true
      case _                           => false
    }) && fields.forall(kv => matches(valuePattern, kv._2))
}
