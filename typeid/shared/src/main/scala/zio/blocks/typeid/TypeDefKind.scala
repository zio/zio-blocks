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
 * What kind of definition a [[TypeId]] describes, as [[TypeId#defKind]] returns
 * it: a [[TypeDefKind.Class]] with its `final`, `abstract`, `case` and
 * value-class flags, a [[TypeDefKind.Trait]] (sealed or not), an
 * [[TypeDefKind.Object]], a Scala 3 [[TypeDefKind.Enum]] or one of its cases
 * ([[TypeDefKind.EnumCase]]), a [[TypeDefKind.TypeAlias]], a Scala 3
 * [[TypeDefKind.OpaqueType]], an [[TypeDefKind.AbstractType]], or
 * [[TypeDefKind.Unknown]]. Classes, traits, objects and enums carry their
 * parents in [[baseTypes]].
 *
 * The predefined ids of `Int`, `List`, `Option` and most other standard library
 * types have kind [[TypeDefKind.Unknown]].
 *
 * {{{
 * sealed trait Shape
 * final case class Circle(radius: Double) extends Shape
 *
 * TypeId.of[Shape].defKind // Trait(true,List())
 *
 * TypeId.of[Circle].defKind match {
 *   case TypeDefKind.Class(isFinal, _, isCase, _, bases) => (isFinal, isCase, bases.size)
 *   case _                                               => (false, false, 0)
 * }
 * // (true,true,1)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/typeid/#typedefkind-reference TypeDefKind Reference]]
 */
sealed trait TypeDefKind {

  /**
   * The parents this definition carries: the bases of a class, trait, object or
   * enum, and the enum of an enum case. Empty for the other kinds.
   */
  def baseTypes: List[TypeRepr] = Nil
}

object TypeDefKind {

  /**
   * A class definition, including case classes and value classes.
   *
   * @param isFinal
   *   whether the class is final
   * @param isAbstract
   *   whether the class is abstract
   * @param isCase
   *   whether the class is a case class
   * @param isValue
   *   whether the class extends `AnyVal`
   * @param bases
   *   its base classes and traits in linearization order, without `Any`,
   *   `AnyRef`, `Object`, `Matchable`, `Product`, `Equals`,
   *   `java.io.Serializable` and the `Mirror` types
   */
  final case class Class(
    isFinal: Boolean = false,
    isAbstract: Boolean = false,
    isCase: Boolean = false,
    isValue: Boolean = false,
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A trait definition.
   *
   * @param isSealed
   *   whether the trait is sealed
   * @param bases
   *   its base classes and traits in linearization order, without `Any`,
   *   `AnyRef`, `Object`, `Matchable`, `Product`, `Equals`,
   *   `java.io.Serializable` and the `Mirror` types
   */
  final case class Trait(
    isSealed: Boolean = false,
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A singleton object, including a case object.
   *
   * @param bases
   *   its base classes and traits in linearization order, without `Any`,
   *   `AnyRef`, `Object`, `Matchable`, `Product`, `Equals`,
   *   `java.io.Serializable` and the `Mirror` types
   */
  final case class Object(
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A Scala 3 enum definition, such as `enum Color { case Red, Green }`.
   *
   * @param bases
   *   its base classes and traits in linearization order, without `Any`,
   *   `AnyRef`, `Object`, `Matchable`, `Product`, `Equals`,
   *   `java.io.Serializable` and the `Mirror` types, such as
   *   `scala.reflect.Enum`
   */
  final case class Enum(
    bases: List[TypeRepr] = Nil
  ) extends TypeDefKind {
    override def baseTypes: List[TypeRepr] = bases
  }

  /**
   * A case of a Scala 3 enum, such as `Color.Red`.
   *
   * @param parentEnum
   *   a reference to the enum the case belongs to
   * @param ordinal
   *   the position of the case in its enum; the Scala 3 macro writes `-1` (a
   *   known bug)
   * @param isObjectCase
   *   `true` for a case without parameters (`case Red`), `false` for a case
   *   with parameters (`case Custom(rgb: Int)`)
   */
  final case class EnumCase(
    parentEnum: TypeRepr,
    ordinal: Int,
    isObjectCase: Boolean
  ) extends TypeDefKind {
    // The base type of an enum case is its parent enum
    override def baseTypes: List[TypeRepr] = List(parentEnum)
  }

  /**
   * A type alias, `type Age = Int`. On Scala 3, the ids derived for union and
   * intersection types have this kind too.
   */
  case object TypeAlias extends TypeDefKind

  /**
   * A Scala 3 opaque type, `opaque type Email = String`.
   *
   * @param publicBounds
   *   the bounds visible outside the scope that defines the type; the Scala 3
   *   macro leaves them [[TypeBounds.Unbounded]]
   */
  final case class OpaqueType(
    publicBounds: TypeBounds = TypeBounds.Unbounded
  ) extends TypeDefKind

  /**
   * An abstract type: an abstract type member, or a type parameter given to
   * [[TypeId.of]].
   */
  case object AbstractType extends TypeDefKind

  /**
   * No known kind. The predefined ids of `Int`, `List`, `Option` and most other
   * standard library types have it, and it is the default `defKind` of
   * `TypeId.nominal`.
   */
  case object Unknown extends TypeDefKind

  // Predefined instances for common patterns (reduces macro-generated bytecode)
  val basicClass: Class          = Class()
  val basicFinalClass: Class     = Class(isFinal = true)
  val basicCaseClass: Class      = Class(isCase = true)
  val basicFinalCaseClass: Class = Class(isFinal = true, isCase = true)
  val unsealedTrait: Trait       = Trait()
  val sealedTrait: Trait         = Trait(isSealed = true)
  val basicObject: Object        = Object()
  val basicEnum: Enum            = Enum()
}

/**
 * A parameter of an enum case.
 *
 * @param name
 *   The parameter name
 * @param tpe
 *   The parameter type
 */
final case class EnumCaseParam(
  name: String,
  tpe: TypeRepr
)
