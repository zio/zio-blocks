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

object OpenAPIGen {

  /**
   * Builds the OpenAPI schema for `A` as a symbolic reference plus its
   * component definitions.
   *
   * ==Refs in / refs out==
   *   - References are preserved symbolically: the returned schema is always a
   *     `ReferenceOr.Ref` pointing at `#/components/schemas/<name>`, and the
   *     definitions map holds the inline `SchemaObject`. No resolution or
   *     dereferencing pass runs here; resolving refs against a document is
   *     downstream work (see `resolveRefs`).
   */
  def schema[A](implicit s: Schema[A]): (ReferenceOr[SchemaObject], Map[String, SchemaObject]) = {
    val name = s.reflect.typeId.name
    val obj  = s.toOpenAPISchema
    val ref  = ReferenceOr.Ref(Reference(`$ref` = s"#/components/schemas/$name"))
    (ref, Map(name -> obj))
  }

  def schemas(ss: Schema[_]*): Map[String, SchemaObject] =
    ss.map(s => s.reflect.typeId.name -> SchemaObject.fromJsonSchema(s.toJsonSchema)).toMap

  /**
   * Resolves the local `#/components/schemas/...` references of a document
   * against the given component definitions.
   *
   * Currently a documented extension point: reference resolution lives
   * downstream, so this stub throws instead of silently returning a partially
   * resolved document. References produced by `schema`/`schemas` round-trip
   * unchanged until resolution is implemented.
   *
   * @throws java.lang.UnsupportedOperationException
   *   always (resolution is not implemented yet)
   */
  def resolveRefs(document: OpenAPI, schemas: Map[String, SchemaObject]): OpenAPI =
    throw new UnsupportedOperationException(
      "OpenAPIGen.resolveRefs is not implemented: references stay symbolic; resolve them downstream"
    )
}
