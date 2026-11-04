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
 * Compile-time evidence that `A` is a nominal type: a concrete, named type.
 * Classes, traits and objects are nominal, and so are aliases of them, such
 * types applied to type arguments (`List[Int]`, and also `List[B]` for a type
 * parameter `B`), and on Scala 3 opaque types and enums. Type parameters,
 * abstract type members, unions, intersections, structural types and literal
 * types such as `1` are not. For those, the implicit is not found, and
 * compilation fails with a message that starts with "Cannot derive
 * IsNominalType for non-nominal type".
 *
 * Require it in a method's signature to stop generic code from calling the
 * method with an unresolved type parameter: the call site must name a concrete
 * type. [[typeId]] then gives the id of that type; for an alias, the id of the
 * type the alias stands for.
 *
 * The singleton type `x.type` of a value is nominal when the value's type is,
 * and gives the id of that type. Two cases differ between Scala 2 and Scala 3:
 * for a value `x` of an intersection type, `x.type` is accepted on Scala 2 and
 * rejected on Scala 3; for `final val lit = 1`, `lit.type` is accepted on Scala
 * 3 (its id is `Int`) and rejected on Scala 2.
 *
 * {{{
 * final case class Point(x: Int, y: Int)
 *
 * def describe[A](implicit ev: IsNominalType[A]): String = ev.typeId.name
 *
 * describe[Point]     // "Point"
 * describe[List[Int]] // "List"
 * // describe[Point with Serializable] does not compile
 * }}}
 *
 * @tparam A
 *   the type witnessed as nominal
 * @see
 *   [[https://zioblocks.com/reference/typeid/ TypeId]]
 */
trait IsNominalType[A] {

  /**
   * The id of `A`, or of the type `A` stands for when `A` is an alias.
   */
  def typeId: TypeId[A]

  /**
   * [[typeId]] as a [[TypeId.Erased]], to use as a key among ids of different
   * types.
   */
  def typeIdErased: TypeId.Erased
}

/**
 * Summons and derives [[IsNominalType]] instances. The macro that derives them
 * runs wherever an implicit `IsNominalType[A]` is required.
 */
object IsNominalType extends IsNominalTypeVersionSpecific {

  /**
   * Returns the implicit `IsNominalType[A]`, as `IsNominalType[Point]`.
   */
  def apply[A](implicit ev: IsNominalType[A]): IsNominalType[A] = ev
}
