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

package zio.blocks.schema

/**
 * Scala 2 version-specific methods for Schema instances.
 */
trait SchemaVersionSpecific[A] { self: Schema[A] =>

  /**
   * Convert this schema to a structural type schema.
   *
   * The structural type represents the "shape" of A without its nominal
   * identity. This enables duck typing and structural validation.
   *
   * Example:
   * {{{
   * case class Person(name: String, age: Int)
   * val structuralSchema: Schema[{ def name: String; def age: Int }] =
   *   Schema.derived[Person].structural
   * }}}
   *
   * Note: This is JVM-only due to reflection requirements for structural types.
   *
   * @param ts
   *   Macro-generated conversion to structural representation
   * @return
   *   Schema for the structural type corresponding to A
   */
  def structural(implicit ts: ToStructural[A]): Schema[ts.StructuralType] = ts(this)
}
