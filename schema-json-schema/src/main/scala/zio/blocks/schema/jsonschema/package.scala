package zio.blocks.schema

package object jsonschema {

  /**
   * Extension methods for Schema to easily derive JSON Schema.
   */
  implicit class SchemaJsonSchemaOps[A](private val schema: Schema[A]) extends AnyVal {

    /**
     * Derives a JSON Schema from this Schema.
     */
    def toJsonSchema: JsonSchema[A] = schema.derive(JsonSchemaFormat.deriver)
  }
}
