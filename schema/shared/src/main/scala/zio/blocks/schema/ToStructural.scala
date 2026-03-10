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
 * Type class for converting nominal schemas to structural schemas.
 *
 * Given a Schema[A] for a nominal type like a case class, this type class
 * generates the corresponding structural type and provides a conversion method.
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 *
 * val nominalSchema: Schema[Person] = Schema.derived[Person]
 * val structuralSchema: Schema[{ def name: String; def age: Int }] = nominalSchema.structural
 * }}}
 *
 * Note: This is JVM-only due to reflection requirements for structural types.
 */
trait ToStructural[A] {
  type StructuralType
  def apply(schema: Schema[A]): Schema[StructuralType]
}

object ToStructural extends ToStructuralVersionSpecific {
  type Aux[A, S] = ToStructural[A] { type StructuralType = S }
}
