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

package zio.blocks.openapi

import zio.blocks.schema.Schema

/**
 * Builds OpenAPI schema components from ZIO Blocks `Schema`s. Each schema is
 * named after the simple name of its type (`Pet`), so two types with the same
 * simple name, such as `List[Int]` and `List[String]`, get the same name.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Pet(id: Long, name: String)
 * object Pet {
 *   implicit val schema: Schema[Pet] = Schema.derived
 * }
 *
 * val (ref, definitions) = OpenAPIGen.schema[Pet]
 * // ref: Ref(Reference(#/components/schemas/Pet,None,None))
 * definitions.keys // Set(Pet)
 * OpenAPIGen.schemas(Schema[Pet], Schema[Int]).keySet // Set(Pet, Int)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/openapi/#converting-schemas-to-schemaobject Converting Schemas to SchemaObject]]
 */
object OpenAPIGen {

  /**
   * Returns a reference to `#/components/schemas/<name>` for the schema of `A`,
   * and a one-entry map from that name to its [[SchemaObject]]. The name is the
   * simple name of `A`.
   */
  def schema[A](implicit s: Schema[A]): (ReferenceOr[SchemaObject], Map[String, SchemaObject]) = {
    val name = s.reflect.typeId.name
    val obj  = s.toOpenAPISchema
    val ref  = ReferenceOr.Ref(Reference(`$ref` = s"#/components/schemas/$name"))
    (ref, Map(name -> obj))
  }

  /**
   * Returns a map from each schema's type name to its [[SchemaObject]], ready
   * for `Components.schemas`. When two schemas have the same type name, the
   * later one wins.
   */
  def schemas(ss: Schema[_]*): Map[String, SchemaObject] =
    ss.map(s => s.reflect.typeId.name -> SchemaObject.fromJsonSchema(s.toJsonSchema)).toMap
}
