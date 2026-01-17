package zio.blocks.schema.json

import zio.blocks.schema.SchemaError

/**
 * Represents a JSON Schema for validation.
 *
 * Placeholder - actual implementation TBD.
 */
sealed trait JsonSchema

object JsonSchema {
  private case object EmptySchema extends JsonSchema

  /**
   * An empty schema that accepts all JSON values.
   */
  val any: JsonSchema = EmptySchema

  /**
   * Validates a JSON value against this schema.
   *
   * @param json
   *   The JSON value to validate
   * @param schema
   *   The schema to validate against
   * @return
   *   `None` if valid, `Some(error)` if invalid
   */
  def validate(json: Json, schema: JsonSchema): Option[SchemaError] = None // Placeholder
}
