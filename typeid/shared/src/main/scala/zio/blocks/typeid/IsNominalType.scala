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

package zio.blocks.typeid

/**
 * A compile-time witness that `A` is a nominal type.
 *
 * A nominal type is any fully-known, concrete named type: a class, trait,
 * object, enum, or applied type constructor (e.g. `List[Int]`). Abstract type
 * parameters, union types (`A | B`), intersection types (`A & B`), structural
 * refinements, and type lambdas are not nominal and will cause derivation to
 * fail with a compile error.
 *
 * `IsNominalType[A]` is automatically derived by a macro when `A` is nominal.
 * The key effect of requiring `IsNominalType[A]` in a method signature is that
 * it prevents the method from being called with an unresolved type parameter,
 * ensuring the type is concrete at the call site.
 *
 * @tparam A
 *   The type being witnessed as nominal.
 */
trait IsNominalType[A] {
  def typeId: TypeId[A]
  def typeIdErased: TypeId.Erased
}

object IsNominalType extends IsNominalTypeVersionSpecific {
  def apply[A](implicit ev: IsNominalType[A]): IsNominalType[A] = ev
}
