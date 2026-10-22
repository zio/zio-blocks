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

/** Adds `structural` to [[Schema]]. */
trait SchemaVersionSpecific[A] { self: Schema[A] =>

  /**
   * Converts this schema to a schema of the structural type of `A`: a
   * refinement with one member per field, such as
   * `{ def title: String; def year: Int }` for
   * `case class Book(title: String, year: Int)`. Use it to work with values by
   * their shape rather than by their nominal type.
   *
   * On Scala 3 the result has the structural type. On Scala 2 its static type
   * is `Schema[ts.StructuralType]`: the compiler does not see the refinement,
   * so the result cannot be assigned to a `Schema` of the structural type.
   *
   * The conversion is available on the JVM only, because structural types need
   * reflection.
   *
   * {{{
   * case class Book(title: String, year: Int)
   *
   * val structural = Schema.derived[Book].structural
   * }}}
   *
   * @param ts
   *   the macro-generated conversion to the structural type
   * @see
   *   [[https://zioblocks.com/reference/schema/structural-types/ Structural Types]]
   */
  def structural(implicit ts: ToStructural[A]): Schema[ts.StructuralType] = ts(this)
}
