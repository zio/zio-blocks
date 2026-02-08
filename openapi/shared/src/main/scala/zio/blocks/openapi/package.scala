package zio.blocks

import zio.blocks.schema.Schema

package object openapi {

  type Discriminator = discriminator.Discriminator
  val Discriminator: discriminator.Discriminator.type = discriminator.Discriminator

  /**
   * Enriches Schema[A] with OpenAPI-specific operations.
   */
  implicit class SchemaOps[A](private val self: Schema[A]) extends AnyVal {

    /**
     * Converts this Schema to an OpenAPI SchemaObject.
     *
     * This method first converts the Schema to a JsonSchema using the existing
     * toJsonSchema method, then wraps it in a SchemaObject using
     * SchemaObject.fromJsonSchema.
     *
     * @return
     *   A SchemaObject representing this Schema's structure and constraints.
     */
    def toOpenAPISchema: SchemaObject = SchemaObject.fromJsonSchema(self.toJsonSchema)
  }
}
