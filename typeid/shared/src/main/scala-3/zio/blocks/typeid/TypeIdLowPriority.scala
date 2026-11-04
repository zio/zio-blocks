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
 * Holds the implicit [[derived]] that [[TypeId]]'s companion mixes in. Its
 * priority is lower than that of the predefined instances in
 * [[TypeIdInstances]], so implicit search finds [[TypeId.int]] for `Int` and
 * [[TypeId.string]] for `String`, and derives an id for other types.
 */
trait TypeIdLowPriority {

  /**
   * Supplies an implicit `TypeId[A]` for any `A` that has no predefined
   * instance, derived by a macro like [[TypeId.of]]. Ask for one with an
   * implicit parameter (`def describe[A](implicit id: TypeId[A])`) or, on Scala
   * 3, a `using` clause.
   *
   * Called directly, `TypeId.derived[Int]` reads the definition of `Int`
   * instead of returning [[TypeId.int]]: [[TypeId.of]] has a shortcut for
   * primitive types that this macro lacks. The two ids are equal.
   *
   * On Scala 2, for an applied type written with an alias from the `scala`
   * package or `Predef`, such as `List[Int]` or `Map[String, Int]`, it derives
   * the alias itself (`scala.package.List`), with the type arguments only
   * inside [[TypeId#aliasedTo]] (a known bug). That id equals
   * `TypeId.of[List[Int]]`, but its `fullName`, `typeArgs` and `toString`
   * differ; call [[TypeId.of]] to get the id of the class.
   */
  inline given derived[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.derivedImpl[A] }
}
