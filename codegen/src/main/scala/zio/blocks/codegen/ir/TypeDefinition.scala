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

package zio.blocks.codegen.ir

/**
 * A Scala type definition in the codegen IR: a case class, sealed trait, trait,
 * abstract class, enum, object, opaque type, newtype or type alias. Put
 * definitions in a [[ScalaFile]] (or nest them in an object with
 * [[ObjectMember.NestedType]]) and turn them into source text with
 * `zio.blocks.codegen.emit.ScalaEmitter`.
 *
 * {{{
 * import zio.blocks.codegen.ir._
 *
 * val definitions: List[TypeDefinition] = List(
 *   CaseClass("Invoice", List(Field("total", TypeRef.BigDecimal))),
 *   OpaqueType("InvoiceId", TypeRef.String),
 *   ObjectDef("Invoices")
 * )
 * definitions.map(_.name) // List(Invoice, InvoiceId, Invoices)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/codegen/type-definition/ TypeDefinition]]
 */
sealed trait TypeDefinition {

  /**
   * The name of the type. The emitter wraps it in backquotes when it is a Scala
   * keyword or not a plain identifier.
   */
  def name: String

  /**
   * Annotations applied to the type, each emitted on its own line above it.
   */
  def annotations: List[Annotation]

  /**
   * Text that the emitter writes as a Scaladoc comment above the type. The text
   * is not escaped: a comment opener or closer in it ends the comment early and
   * breaks the generated file (a known bug).
   */
  def doc: Option[String]
}

/**
 * A Scala case class in the IR. The emitter writes each field on its own line,
 * adds `extends` for `extendsTypes` (joined with `with`), and writes the
 * companion object after the class.
 *
 * {{{
 * import zio.blocks.codegen.ir._
 * import zio.blocks.codegen.emit.{EmitterConfig, ScalaEmitter}
 *
 * val person = CaseClass(
 *   "Person",
 *   fields = List(
 *     Field("name", TypeRef.String),
 *     Field("age", TypeRef.Int)
 *   ),
 *   derives = List("Schema")
 * )
 * ScalaEmitter.emitCaseClass(person, EmitterConfig.default)
 * // case class Person(
 * //   name: String,
 * //   age: Int,
 * // ) derives Schema
 * }}}
 *
 * @param name
 *   The name of the case class
 * @param fields
 *   The fields (constructor parameters) of the case class
 * @param typeParams
 *   Type parameters for generic case classes (defaults to empty list)
 * @param extendsTypes
 *   Types that this case class extends (defaults to empty list)
 * @param derives
 *   Type classes for a `derives` clause (defaults to empty list). The emitter
 *   writes the clause only when `EmitterConfig.scala3Syntax` is true and drops
 *   it otherwise, because Scala 2 has no `derives`.
 * @param annotations
 *   Annotations on the case class (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 * @param isValueClass
 *   Whether the class extends `AnyVal` (defaults to false). When true, the
 *   emitter puts `AnyVal` first in the `extends` list unless `extendsTypes`
 *   already names it.
 * @see
 *   [[https://zioblocks.com/reference/codegen/case-class/ CaseClass]]
 */
final case class CaseClass(
  name: String,
  fields: List[Field],
  typeParams: List[TypeParam] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  derives: List[String] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None,
  isValueClass: Boolean = false
) extends TypeDefinition

/**
 * A Scala sealed trait and its cases in the IR. The emitter writes the trait,
 * then a companion object that holds the cases followed by the members of
 * `companion`. A case class case gets the trait added to its `extends` list
 * unless it already extends a type with the trait's name. A case object case
 * extends the trait applied to the trait's own type parameter names, so for a
 * generic trait it refers to a type parameter it does not declare (`case object
 * Err extends Result[A]`) and the output does not compile (a known bug).
 *
 * {{{
 * import zio.blocks.codegen.ir._
 * import zio.blocks.codegen.emit.{EmitterConfig, ScalaEmitter}
 *
 * val shape = SealedTrait(
 *   "Shape",
 *   cases = List(
 *     SealedTraitCase.CaseClassCase(CaseClass("Circle", List(Field("radius", TypeRef.Double)))),
 *     SealedTraitCase.CaseObjectCase("Unknown")
 *   )
 * )
 * ScalaEmitter.emitSealedTrait(shape, EmitterConfig.default)
 * // sealed trait Shape
 * //
 * // object Shape {
 * //   case class Circle(
 * //     radius: Double,
 * //   ) extends Shape
 * //   case object Unknown extends Shape
 * // }
 * }}}
 *
 * @param name
 *   The name of the sealed trait
 * @param typeParams
 *   Type parameters for generic sealed traits (defaults to empty list)
 * @param extendsTypes
 *   Types that this sealed trait extends (defaults to empty list)
 * @param cases
 *   The cases (subtypes) of the sealed trait (defaults to empty list)
 * @param derives
 *   Type classes for a `derives` clause (defaults to empty list). The emitter
 *   never writes it, in either dialect (a known bug).
 * @param annotations
 *   Annotations on the sealed trait (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None); its members follow the cases
 * @param doc
 *   Optional documentation (defaults to None)
 * @param selfType
 *   Optional self type (defaults to None), emitted as `{ self: T => }` after
 *   the trait header
 * @see
 *   [[https://zioblocks.com/reference/codegen/sealed-trait/ SealedTrait]]
 */
final case class SealedTrait(
  name: String,
  typeParams: List[TypeParam] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  cases: List[SealedTraitCase] = Nil,
  derives: List[String] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None,
  selfType: Option[TypeRef] = None
) extends TypeDefinition

/**
 * Represents a non-sealed Scala trait in the IR.
 *
 * @param name
 *   The name of the trait
 * @param typeParams
 *   Type parameters for generic traits (defaults to empty list)
 * @param extendsTypes
 *   Types that this trait extends (defaults to empty list)
 * @param members
 *   Members defined within the trait body (defaults to empty list)
 * @param annotations
 *   Annotations on the trait (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 * @param selfType
 *   Optional self type (defaults to None), emitted as the first line of the
 *   body, `self: T =>`
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val hasId = Trait(
 *   "HasId",
 *   members = List(
 *     ObjectMember.DefMember(Method("id", returnType = TypeRef.Long))
 *   )
 * )
 *   }}}
 */
final case class Trait(
  name: String,
  typeParams: List[TypeParam] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  members: List[ObjectMember] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None,
  selfType: Option[TypeRef] = None
) extends TypeDefinition

/**
 * Represents a Scala abstract class in the IR.
 *
 * @param name
 *   The name of the abstract class
 * @param fields
 *   The fields (constructor parameters) of the abstract class
 * @param typeParams
 *   Type parameters for generic abstract classes (defaults to empty list)
 * @param extendsTypes
 *   Types that this abstract class extends (defaults to empty list)
 * @param members
 *   Members defined within the class body (defaults to empty list)
 * @param annotations
 *   Annotations on the abstract class (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 * @param isSealed
 *   Whether the class is meant to be `sealed` (defaults to false). The emitter
 *   ignores it and always writes `abstract class` (a known bug).
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val entity = AbstractClass(
 *   "Entity",
 *   fields = List(Field("id", TypeRef.Long))
 * )
 *   }}}
 */
final case class AbstractClass(
  name: String,
  fields: List[Field] = Nil,
  typeParams: List[TypeParam] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  members: List[ObjectMember] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None,
  isSealed: Boolean = false
) extends TypeDefinition

/**
 * Represents a Scala 3 opaque type alias in the IR.
 *
 * With `EmitterConfig.scala3Syntax` it is emitted as
 * `opaque type Name = UnderlyingType`; otherwise as a plain type alias,
 * `type Name = UnderlyingType`.
 *
 * @param name
 *   The name of the opaque type
 * @param underlyingType
 *   The underlying type being aliased
 * @param upperBound
 *   Optional upper bound constraint (defaults to None)
 * @param annotations
 *   Annotations on the opaque type (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val userId = OpaqueType("UserId", underlyingType = TypeRef.String)
 *   }}}
 */
final case class OpaqueType(
  name: String,
  underlyingType: TypeRef,
  upperBound: Option[TypeRef] = None,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a case within a sealed trait hierarchy.
 */
sealed trait SealedTraitCase

/** The two kinds of [[SealedTrait]] case: a case class or a case object. */
object SealedTraitCase {

  /**
   * A case class variant of a sealed trait.
   *
   * @param cc
   *   The case class definition
   */
  final case class CaseClassCase(cc: CaseClass) extends SealedTraitCase

  /**
   * A case object variant of a sealed trait.
   *
   * @param name
   *   The name of the case object
   */
  final case class CaseObjectCase(name: String) extends SealedTraitCase
}

/**
 * An enum in the IR. With `EmitterConfig.scala3Syntax` the emitter writes a
 * Scala 3 `enum`; otherwise it writes a sealed trait whose companion holds a
 * case object per simple case and a case class per parameterized case. The
 * emitter drops `typeParams`, `derives` and `companion` in both dialects (a
 * known bug).
 *
 * @param name
 *   The name of the enum
 * @param cases
 *   The enum cases
 * @param typeParams
 *   Type parameters (defaults to empty list); never emitted (a known bug)
 * @param extendsTypes
 *   Types that this enum extends (defaults to empty list)
 * @param derives
 *   Type classes for a `derives` clause (defaults to empty list); never emitted
 *   (a known bug)
 * @param annotations
 *   Annotations on the enum (defaults to empty list)
 * @param companion
 *   Optional companion object (defaults to None); never emitted (a known bug)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val color = Enum(
 *   "Color",
 *   cases = List(
 *     EnumCase.SimpleCase("Red"),
 *     EnumCase.SimpleCase("Green"),
 *     EnumCase.SimpleCase("Blue")
 *   )
 * )
 *   }}}
 */
final case class Enum(
  name: String,
  cases: List[EnumCase],
  typeParams: List[TypeParam] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  derives: List[String] = Nil,
  annotations: List[Annotation] = Nil,
  companion: Option[CompanionObject] = None,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a case within an [[Enum]].
 */
sealed trait EnumCase

/** The two kinds of [[Enum]] case: simple and parameterized. */
object EnumCase {

  /**
   * A simple enum case without parameters.
   *
   * @param name
   *   The name of the enum case
   */
  final case class SimpleCase(name: String) extends EnumCase

  /**
   * A parameterized enum case with fields. In Scala 3 output the fields are
   * written on one line, each with its annotations before it.
   *
   * @param name
   *   The name of the enum case
   * @param fields
   *   The fields of the parameterized case
   * @param extendsTypes
   *   Types the case extends (defaults to empty list). The emitter ignores them
   *   in both dialects (a known bug).
   */
  final case class ParameterizedCase(name: String, fields: List[Field], extendsTypes: List[TypeRef] = Nil)
      extends EnumCase
}

/**
 * Represents a Scala object definition in the IR.
 *
 * @param name
 *   The name of the object
 * @param members
 *   The members of the object (defaults to empty list)
 * @param extendsTypes
 *   Types that this object extends (defaults to empty list)
 * @param isCaseObject
 *   Whether this is a case object (defaults to false)
 * @param annotations
 *   Annotations on the object (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val utils = ObjectDef(
 *   "Utils",
 *   members = List(
 *     ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3")
 *   )
 * )
 *   }}}
 */
final case class ObjectDef(
  name: String,
  members: List[ObjectMember] = Nil,
  extendsTypes: List[TypeRef] = Nil,
  isCaseObject: Boolean = false,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * Represents a member within an object, trait, abstract class or companion
 * object.
 */
sealed trait ObjectMember

/**
 * The kinds of [[ObjectMember]]: vals, defs, type aliases, nested types and
 * extension blocks.
 */
object ObjectMember {

  /**
   * A val member within an object.
   *
   * @param name
   *   The name of the val
   * @param typeRef
   *   The type of the val
   * @param value
   *   The value expression, as Scala source text written after `=`
   * @param isLazy
   *   Whether this is a lazy val (defaults to false)
   * @param isOverride
   *   Whether this val overrides a parent member (defaults to false)
   * @param isImplicit
   *   Whether this val is implicit (defaults to false). `EmitterConfig.scala2`
   *   writes `implicit val`. With `scala3Syntax` the emitter writes
   *   `given val`, which Scala 3 rejects (a known bug).
   */
  final case class ValMember(
    name: String,
    typeRef: TypeRef,
    value: String,
    isLazy: Boolean = false,
    isOverride: Boolean = false,
    isImplicit: Boolean = false
  ) extends ObjectMember

  /**
   * A def member within an object.
   *
   * @param method
   *   The method definition
   */
  final case class DefMember(method: Method) extends ObjectMember

  /**
   * A type alias member within an object.
   *
   * @param name
   *   The name of the type alias
   * @param typeRef
   *   The aliased type
   */
  final case class TypeAlias(name: String, typeRef: TypeRef) extends ObjectMember

  /**
   * A nested type definition within an object.
   *
   * @param typeDef
   *   The nested type definition
   */
  final case class NestedType(typeDef: TypeDefinition) extends ObjectMember

  /**
   * A block of extension methods.
   *
   * With `EmitterConfig.scala3Syntax` it is emitted as `extension (param)`
   * followed by the methods, in braces when there is more than one. Otherwise
   * it is emitted as `implicit class <Type>Ops(val param) extends AnyVal`
   * holding the methods.
   *
   * @param on
   *   The parameter to extend on
   * @param methods
   *   The extension methods
   */
  final case class ExtensionBlock(on: MethodParam, methods: List[Method]) extends ObjectMember
}

/**
 * Represents a companion object in the IR. The emitter writes it as
 * `object <TypeName>` after the type it belongs to.
 *
 * @param members
 *   The members of the companion object (defaults to empty list)
 */
final case class CompanionObject(
  members: List[ObjectMember] = Nil
)

/**
 * Represents a newtype wrapper in the IR. The emitter writes
 * `object Name extends Newtype[Wrapped]` followed by `type Name = Name.Type`.
 * This library does not provide `Newtype`, so the generated file needs one in
 * scope, such as the one in ZIO Prelude.
 *
 * @param name
 *   The name of the newtype
 * @param wrappedType
 *   The underlying type being wrapped
 * @param annotations
 *   Annotations on the newtype (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 *
 * @example
 *   {{{
 * import zio.blocks.codegen.ir._
 *
 * val userId = Newtype("UserId", wrappedType = TypeRef.Long)
 *   }}}
 */
final case class Newtype(
  name: String,
  wrappedType: TypeRef,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition

/**
 * A type alias definition in the IR, emitted as `type Name[A] = Target`. Inside
 * an object, [[ObjectMember.TypeAlias]] is the shorter form without type
 * parameters, annotations or documentation.
 *
 * @param name
 *   The name of the alias
 * @param typeParams
 *   Type parameters of the alias (defaults to empty list)
 * @param typeRef
 *   The aliased type
 * @param annotations
 *   Annotations on the alias (defaults to empty list)
 * @param doc
 *   Optional documentation (defaults to None)
 */
final case class TypeAlias(
  name: String,
  typeParams: List[TypeParam] = Nil,
  typeRef: TypeRef,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
) extends TypeDefinition
