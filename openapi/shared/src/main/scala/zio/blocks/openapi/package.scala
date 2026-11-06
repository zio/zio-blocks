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

  /**
   * The OpenAPI Discriminator Object, an alias for
   * `zio.blocks.openapi.discriminator.Discriminator`.
   */
  type Discriminator = discriminator.Discriminator

  /** The companion of the [[Discriminator]] alias. */
  val Discriminator: discriminator.Discriminator.type = discriminator.Discriminator

  /**
   * Enriches Schema[A] with OpenAPI-specific operations: converting it to a
   * [[SchemaObject]] and to a `#/components/schemas/...` reference.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * final case class Owner(name: String)
   * object Owner {
   *   implicit val schema: Schema[Owner] = Schema.derived
   * }
   *
   * Schema[Owner].schemaName      // Owner
   * Schema[Owner].toRefSchema._1 // Ref(Reference(#/components/schemas/Owner,None,None))
   * Schema[Owner].toOpenAPISchema.toJson.print
   * // {"properties":{"name":{"type":"string"}},"type":"object","required":["name"],"title":"Owner"}
   * }}}
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

    /**
     * The simple name of the schema's type, without its package or type
     * arguments: `Owner`, `List` for `List[Int]`, `Option` for `Option[Owner]`.
     */
    def schemaName: String = self.reflect.typeId.name

    /** Returns [[toOpenAPISchema]] wrapped in `ReferenceOr.Value`. */
    def toInlineSchema: ReferenceOr[SchemaObject] = ReferenceOr.Value(toOpenAPISchema)

    /**
     * Returns a reference to `#/components/schemas/<schemaName>` and the
     * `(name, SchemaObject)` pair to put in `Components.schemas` under that
     * name.
     */
    def toRefSchema: (ReferenceOr[SchemaObject], (String, SchemaObject)) = toRefSchema(schemaName)

    /**
     * Returns a reference to `#/components/schemas/<name>` and the
     * `(name, SchemaObject)` pair to put in `Components.schemas` under that
     * name. The name is not escaped, so it should hold only characters that are
     * valid in a JSON pointer without escaping.
     */
    def toRefSchema(name: String): (ReferenceOr[SchemaObject], (String, SchemaObject)) = {
      val ref        = ReferenceOr.Ref(Reference(`$ref` = s"#/components/schemas/$name"))
      val definition = (name, toOpenAPISchema)
      (ref, definition)
    }
  }
}
