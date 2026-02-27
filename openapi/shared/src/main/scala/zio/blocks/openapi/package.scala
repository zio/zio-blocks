package zio.blocks

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId

package object openapi {

  implicit def chunkMapSchema[K: Schema, V: Schema](implicit tid: TypeId[ChunkMap[K, V]]): Schema[ChunkMap[K, V]] =
    Schema
      .map[K, V]
      .transform[ChunkMap[K, V]](
        map => ChunkMap.from(map),
        chunkMap => chunkMap
      )

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

    def schemaName: String = self.reflect.typeId.name

    def toInlineSchema: ReferenceOr[SchemaObject] = ReferenceOr.Value(toOpenAPISchema)

    def toRefSchema: (ReferenceOr[SchemaObject], (String, SchemaObject)) = toRefSchema(schemaName)

    def toRefSchema(name: String): (ReferenceOr[SchemaObject], (String, SchemaObject)) = {
      val ref        = ReferenceOr.Ref(Reference(`$ref` = s"#/components/schemas/$name"))
      val definition = (name, toOpenAPISchema)
      (ref, definition)
    }
  }
}
