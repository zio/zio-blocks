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
