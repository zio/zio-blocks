package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaRepr

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
  def matches(pattern: SchemaRepr, json: Json): Boolean = (pattern, json) match {
    case (SchemaRepr.Wildcard, _) =>
      true

    case (SchemaRepr.Primitive(name), _) =>
      primitiveTypeMatches(name, json)

    case (SchemaRepr.Record(fields), obj: Json.Object) =>
      recordMatches(fields, obj.value)

    case (SchemaRepr.Sequence(elemPattern), arr: Json.Array) =>
      sequenceMatches(elemPattern, arr.value)

    case (SchemaRepr.Map(keyPattern, valuePattern), obj: Json.Object) =>
      // JSON objects have string keys, so we check if keyPattern matches string
      // and all values match the value pattern
      mapMatches(keyPattern, valuePattern, obj.value)

    case (SchemaRepr.Optional(_), Json.Null) =>
      true

    case (SchemaRepr.Optional(inner), value) =>
      matches(inner, value)

    case (SchemaRepr.Variant(cases), _) =>
      // JSON doesn't have tagged variants, so we can't match by case name
      // However, we could try to match the value against any of the case patterns
      variantMatches(cases, json)

    case (SchemaRepr.Nominal(_), _) =>
      // Nominal types require schema context not available in JSON
      false

    case _ =>
      // Type mismatch (e.g., Record pattern against Array value)
      false
  }

  /**
   * Tests whether a Json value matches a primitive type name. The comparison is
   * case-insensitive.
   */
  private def primitiveTypeMatches(name: String, json: Json): Boolean = {
    val lowerName = name.toLowerCase
    json match {
      case _: Json.String =>
        lowerName == "string"

      case _: Json.Number =>
        // JSON numbers can match various numeric primitive types
        lowerName == "number" || lowerName == "int" || lowerName == "long" ||
        lowerName == "short" || lowerName == "byte" || lowerName == "float" ||
        lowerName == "double" || lowerName == "bigint" || lowerName == "bigdecimal"

      case _: Json.Boolean =>
        lowerName == "boolean"

      case Json.Null =>
        lowerName == "null" || lowerName == "unit"

      case _ =>
        false
    }
  }

  /**
   * Tests whether a Json.Object matches a Record pattern using subset matching.
   * All fields specified in the pattern must exist in the object with matching
   * types. The object may have additional fields that are not in the pattern.
   */
  private def recordMatches(
    patternFields: Vector[(String, SchemaRepr)],
    actualFields: Chunk[(String, Json)]
  ): Boolean = {
    // Convert to map for O(1) lookup
    val fieldMap = actualFields.iterator.toMap
    patternFields.forall { case (patternName, patternRepr) =>
      fieldMap.get(patternName).exists(actualValue => matches(patternRepr, actualValue))
    }
  }

  /**
   * Tests whether a Json value matches any of the Variant cases. Since JSON
   * doesn't have tagged variants, we try to match the value against any of the
   * case patterns.
   */
  private def variantMatches(
    patternCases: Vector[(String, SchemaRepr)],
    json: Json
  ): Boolean =
    // Try to match against any case pattern
    patternCases.exists { case (_, patternRepr) =>
      matches(patternRepr, json)
    }

  /**
   * Tests whether a Json.Array matches a Sequence pattern. Empty arrays always
   * match. For non-empty arrays, all elements must match the element pattern.
   */
  private def sequenceMatches(
    elemPattern: SchemaRepr,
    elements: Chunk[Json]
  ): Boolean =
    if (elements.isEmpty) true
    else elements.forall(elem => matches(elemPattern, elem))

  /**
   * Tests whether a Json.Object matches a Map pattern. JSON objects have string
   * keys, so we check if the key pattern matches "string" and all values match
   * the value pattern. Empty objects always match.
   */
  private def mapMatches(
    keyPattern: SchemaRepr,
    valuePattern: SchemaRepr,
    fields: Chunk[(String, Json)]
  ): Boolean = {
    // JSON object keys are always strings, so check if key pattern is compatible
    val keyMatches = keyPattern match {
      case SchemaRepr.Primitive(name) => name.toLowerCase == "string"
      case SchemaRepr.Wildcard        => true
      case _                          => false
    }
    if (!keyMatches) return false

    if (fields.isEmpty) true
    else fields.forall { case (_, v) => matches(valuePattern, v) }
  }
}
