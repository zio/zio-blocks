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

import scala.language.experimental.macros

import zio.blocks.chunk.Chunk

/**
 * A `TypeId[A]` describes the type `A` as a value: its name, its [[Owner]] (the
 * packages, objects and classes around its definition), its type parameters and
 * type arguments, what kind of definition it is (class, trait, object, enum,
 * alias, opaque type), its parents and its annotations. Derive one with
 * [[TypeId.of]], or ask for an implicit `TypeId[A]` and let [[TypeId.derived]]
 * supply it. Use it as a key in a type-indexed registry, or to describe a type
 * to a serializer or a code generator.
 *
 * Two ids are equal when they describe the same type after aliases are
 * resolved: the same full name, type parameters and type arguments
 * ([[TypeId.structurallyEqual]]). So the id of `type Age = Int` equals the id
 * of `Int`, while an opaque type stays different from its representation.
 *
 * On Scala 3, `A` is bounded by `AnyKind`, so it can be a type constructor:
 * `TypeId.of[Option]` describes `Option` itself. On Scala 2, `A` is a proper
 * type, and a type constructor is written with a wildcard:
 * `TypeId.of[Option[_]]`. Both give an id equal to [[TypeId.option]].
 *
 * {{{
 * final case class Point(x: Int, y: Int)
 *
 * val point = TypeId.of[Point]
 * point.name        // "Point"
 * point.isCaseClass // true
 *
 * val scores = TypeId.of[Map[String, Int]]
 * scores.fullName   // "scala.collection.immutable.Map"
 * scores.typeArgs   // List(Ref(String), Ref(Int))
 * scores.toString   // "Map[String, Int]"
 * }}}
 *
 * @tparam A
 *   the type this id describes
 * @see
 *   [[https://zioblocks.com/reference/typeid/ TypeId]]
 */
sealed trait TypeId[A] extends TypeIdPlatformSpecific {

  /**
   * The simple name of the type, such as `"Map"` for `Map[String, Int]`. For an
   * object it is the object's name, without the trailing dollar sign.
   *
   * On Scala 3, the id derived for a union type is named `"Union"` and the one
   * for an intersection type `"Intersection"`. On Scala 2, an intersection
   * `A with B` is named `"<refinement>"`.
   */
  def name: String

  /**
   * Where the type is defined: the packages, objects and enclosing classes
   * around its definition, such as `scala.collection.immutable` for
   * `List[Int]`. See [[Owner]].
   */
  def owner: Owner

  /**
   * The type parameters the type declares, in order, with their variance; empty
   * for a proper type such as `Int`. An applied type keeps the parameters of
   * its constructor, so `TypeId.of[List[Int]].typeParams` holds one covariant
   * parameter `A`.
   *
   * The macros record only the name, index and variance of a parameter: its
   * bounds are [[TypeBounds.Unbounded]] and its kind is [[Kind.Type]], even for
   * a higher-kinded parameter such as `F[_]` (a known bug). On Scala 3, the id
   * of an alias with no type parameters of its own, such as
   * `type Names = List[String]`, reports the type parameters of the aliased
   * type (a known bug).
   */
  def typeParams: List[TypeParam]

  /**
   * The type arguments of an applied type, such as
   * `List(Ref(String), Ref(Int))` for `Map[String, Int]`; empty when the type
   * is not applied.
   *
   * On Scala 3, the id of an applied alias with type parameters, such as
   * `StrMap[Int]` for `type StrMap[V] = Map[String, V]`, has no type arguments
   * (a known bug).
   */
  def typeArgs: List[TypeRepr]

  /**
   * What kind of definition the type is: a class (with its `case`, `final`,
   * `abstract` and value-class flags), trait, object, enum, enum case, alias,
   * opaque type or abstract type, together with its parents. The predefined ids
   * of `Int`, `List`, `Option` and most other standard library types have
   * [[TypeDefKind.Unknown]]. See [[TypeDefKind]].
   */
  def defKind: TypeDefKind

  /**
   * The self-type the trait or class declares, if any. The macros capture it as
   * an intersection: for `trait Named { self: Animal => }`, Scala 3 gives
   * `Animal & Named`, and Scala 2 an intersection of `Animal` and a refinement.
   */
  def selfType: Option[TypeRepr]

  /**
   * For a type alias, the type it stands for, such as `Ref(Int)` for
   * `type Age = Int`; `None` for other types. On Scala 3, the ids derived for
   * union and intersection types are aliases of a [[TypeRepr.Union]] or a
   * [[TypeRepr.Intersection]].
   */
  def aliasedTo: Option[TypeRepr]

  /**
   * For an opaque type, its underlying type, such as `Ref(String)` for
   * `opaque type Email = String`; `None` for other types. Opaque types exist
   * only on Scala 3, so the Scala 2 macro never sets it.
   */
  def representation: Option[TypeRepr]

  /**
   * The annotations on the type's definition, such as `@deprecated`, with the
   * arguments the macro can read. For `@deprecated("old", "1.0")`, Scala 2
   * records the arguments as [[AnnotationArg.Named]] values and Scala 3 as
   * positional [[AnnotationArg.Const]] values.
   */
  def annotations: List[Annotation]

  /**
   * Returns the runtime class of this type on the JVM, or `None` when there is
   * none. A primitive gives its primitive class (`int` for `Int`), an applied
   * type its erased class (`List` for `List[Int]`), and an object the class of
   * its single instance. Aliases, opaque types, `Any`, `Nothing`, and classes
   * that cannot be loaded give `None`. On Scala.js it returns `None`.
   */
  final def clazz: Option[Class[_]] = TypeIdPlatformMethods.getClass(this)

  /**
   * Creates a value of this type from `args`, on the JVM. Some standard types
   * are built directly: `List`, `Vector`, `Set`, `Seq`, `IndexedSeq`, `Map`
   * (keys and values interleaved), `Option` (no argument for `None`), `Some`,
   * `None`, `Either` (a `java.lang.Boolean` that is `true` for `Right`, then
   * the value), `Tuple2`, `Tuple3` and several `java.time` types. Any other
   * type is built by reflection, with the first public constructor whose
   * parameter count and types fit `args`; pass primitive values boxed.
   *
   * Returns `Left` with a message when no class or constructor fits, or when
   * the constructor throws. On Scala.js it returns `Left` for every type.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   *
   * final case class Point(x: Int, y: Int)
   *
   * TypeId.of[Point].construct(Chunk[AnyRef](Int.box(1), Int.box(2)))
   * // Right(Point(1,2))
   * TypeId.of[List[String]].construct(Chunk[AnyRef]("a", "b"))
   * // Right(List(a, b))
   * }}}
   */
  final def construct(args: Chunk[AnyRef]): Either[String, Any] = TypeIdPlatformMethods.construct(this, args)

  /**
   * The parents recorded in [[defKind]]. For a derived class, trait or object,
   * they are its base classes and traits in linearization order, without their
   * type arguments (a parent `Ordered[Square]` is recorded as `Ordered`), and
   * without `Any`, `AnyRef`, `Object`, `Matchable`, `Product`, `Equals`,
   * `java.io.Serializable` and the `Mirror` types; for a Scala 3 enum case, its
   * enum. Aliases, opaque types and the predefined ids of `Int`, `List`,
   * `Option` and most other standard library types have none.
   *
   * {{{
   * trait Animal
   * trait Pet extends Animal
   * class Dog extends Pet
   *
   * TypeId.of[Dog].parents.map(_.toString) // List(Ref(Pet), Ref(Animal))
   * }}}
   */
  final def parents: List[TypeRepr] = defKind.baseTypes

  /**
   * Returns `true` if this id has type arguments, as the id of `List[Int]`
   * does. The id of a type constructor, such as [[TypeId.list]], is not
   * applied.
   */
  final def isApplied: Boolean = typeArgs.nonEmpty

  /**
   * Returns this id typed as [[TypeId.Erased]], so that ids of different types
   * can be stored together, for example as the keys of one map. It returns the
   * same object.
   *
   * {{{
   * val ids: List[TypeId.Erased] = List(TypeId.of[Int].erased, TypeId.of[String].erased)
   * ids.map(_.name) // List(Int, String)
   * }}}
   */
  final def erased: TypeId.Erased = this.asInstanceOf[TypeId.Erased]

  /**
   * The number of type parameters the type declares, `typeParams.size`. An
   * applied type keeps its constructor's parameters, so `List[Int]` has arity 1.
   */
  final def arity: Int = typeParams.size

  /**
   * The owner and the name joined with a dot, such as
   * `"scala.collection.immutable.List"`, or the name alone when the owner is
   * [[Owner.Root]]. A type declared inside an object includes the object, as in
   * `"com.example.Outer.Inner"`.
   */
  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  /**
   * Returns `true` if the type declares no type parameters (`arity == 0`). An
   * applied type such as `List[Int]` keeps its constructor's parameters, so it
   * gives `false`.
   */
  final def isProperType: Boolean = arity == 0

  /**
   * Returns `true` if the type declares type parameters (`arity > 0`). An
   * applied type such as `List[Int]` keeps its constructor's parameters, so it
   * gives `true`.
   */
  final def isTypeConstructor: Boolean = arity > 0

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Class]]: a class, case
   * class or value class. The predefined ids of `Int`, `List`, `Option` and
   * most other standard library types give `false`, as their kind is
   * [[TypeDefKind.Unknown]].
   */
  final def isClass: Boolean = defKind.isInstanceOf[TypeDefKind.Class]

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Trait]].
   */
  final def isTrait: Boolean = defKind.isInstanceOf[TypeDefKind.Trait]

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Object]], as for the
   * derived id of an object or a case object.
   */
  final def isObject: Boolean = defKind.isInstanceOf[TypeDefKind.Object]

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Enum]], a Scala 3 enum.
   * The id of one of its cases gives `false`: its kind is
   * [[TypeDefKind.EnumCase]].
   */
  final def isEnum: Boolean = defKind.isInstanceOf[TypeDefKind.Enum]

  /**
   * Returns `true` if [[defKind]] is [[TypeDefKind.TypeAlias]]. On Scala 3, the
   * ids derived for union and intersection types are aliases too.
   */
  final def isAlias: Boolean = defKind == TypeDefKind.TypeAlias

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.OpaqueType]], a Scala 3
   * opaque type.
   */
  final def isOpaque: Boolean = defKind.isInstanceOf[TypeDefKind.OpaqueType]

  /**
   * Returns `true` if [[defKind]] is [[TypeDefKind.AbstractType]]: an abstract
   * type member, or a type parameter given to [[TypeId.of]]. An abstract class
   * gives `false`: its kind is a [[TypeDefKind.Class]] with `isAbstract` set.
   */
  final def isAbstract: Boolean = defKind == TypeDefKind.AbstractType

  /**
   * Returns `true` for a sealed trait. A sealed abstract class and a Scala 3
   * enum give `false`; read their [[defKind]] instead.
   */
  final def isSealed: Boolean = defKind match {
    case TypeDefKind.Trait(isSealed, _) => isSealed
    case _                              => false
  }

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Class]] with `isCase` set:
   * a case class, including a tuple class such as `Tuple2`. A case object gives
   * `false`, as its kind is [[TypeDefKind.Object]].
   */
  final def isCaseClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, isCase, _, _) => isCase
    case _                                     => false
  }

  /**
   * Returns `true` if [[defKind]] is a [[TypeDefKind.Class]] with `isValue`
   * set: a class that extends `AnyVal`, such as
   * `final case class UserId(value: Long) extends AnyVal`. The predefined ids
   * of `Int` and the other primitive types give `false`.
   */
  final def isValueClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, _, isValue, _) => isValue
    case _                                      => false
  }

  /**
   * A `ClassTag` for creating arrays of this type at runtime. For `Int`,
   * `Long`, `Float`, `Double`, `Boolean`, `Byte`, `Short`, `Char` and `Unit`
   * (an id in package `scala` with one of those names), it is the primitive
   * `ClassTag`; for every other type it is `ClassTag.AnyRef`, not a tag for the
   * type's own class.
   */
  lazy val classTag: scala.reflect.ClassTag[_] = {
    import scala.reflect.ClassTag
    if (owner == Owner.scala) name match {
      case "Int"     => ClassTag.Int
      case "Long"    => ClassTag.Long
      case "Float"   => ClassTag.Float
      case "Double"  => ClassTag.Double
      case "Boolean" => ClassTag.Boolean
      case "Byte"    => ClassTag.Byte
      case "Short"   => ClassTag.Short
      case "Char"    => ClassTag.Char
      case "Unit"    => ClassTag.Unit
      case _         => ClassTag.AnyRef
    }
    else ClassTag.AnyRef
  }

  /**
   * Returns `true` if the type, after [[TypeId.normalize]], is in package
   * `scala` and its name starts with `Tuple`: `Tuple1` to `Tuple22`, and on
   * Scala 3 also `Tuple`. A Scala 3 named tuple gives `false`: it is an opaque
   * `NamedTuple`.
   *
   * On Scala 3, an alias of a tuple such as `type Coords = (Int, Int)` gives
   * `false` (a known bug); on Scala 2 it gives `true`.
   */
  final def isTuple: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Tuple")
  }

  /**
   * Returns `true` if the type, after [[TypeId.normalize]], is `scala.Product`
   * or one of `scala.Product1` to `scala.Product22`. A case class gives
   * `false`; use [[isCaseClass]] for it.
   */
  final def isProduct: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Product")
  }

  /**
   * Returns `true` if the type, after [[TypeId.normalize]], is `scala.Option`
   * or a type named `Either` in package `scala`. The id of
   * `Either[String, Int]` is `scala.util.Either`, so it gives `false` (a known
   * bug); use [[isEither]] for it.
   */
  final def isSum: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && (norm.name == "Either" || norm.name == "Option")
  }

  /**
   * Returns `true` if the type, after [[TypeId.normalize]], is
   * `scala.util.Either`, as `TypeId.of[Either[String, Int]]` is. `Left` and
   * `Right` give `false`.
   */
  final def isEither: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala.util") && norm.name == "Either"
  }

  /**
   * Returns `true` if the type, after [[TypeId.normalize]], is `scala.Option`.
   * `Some` and `None` give `false`.
   */
  final def isOption: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name == "Option"
  }

  /**
   * Returns `true` if the type is `Maybe` from the `zio.blocks.maybe` package
   * (or its package object). Unlike [[isOption]], it does not resolve aliases
   * first.
   */
  final def isMaybe: Boolean =
    (owner == Owner.fromPackagePath("zio.blocks.maybe") ||
      owner == Owner.fromPackagePath("zio.blocks.maybe.package")) && name == "Maybe"

  /**
   * Returns `true` when this type is known to be a subtype of `other`, judged
   * from the data in the two ids. It returns `true` when the ids are equal,
   * when this type is `Nothing`, when `other` is `Any`, and when `other` equals
   * one of this type's parents (for a derived class, its parents include the
   * indirect ones). For two applications of the same type constructor, it
   * compares each type argument by the parameter's variance: `List[Circle]` is
   * a subtype of `List[Shape]`, and `Set[Circle]` is not a subtype of
   * `Set[Shape]`.
   *
   * On Scala 3, it also returns `true` when `other` is a union type, or an
   * alias of one, that lists this type, and when this type is an intersection
   * type, or an alias of one, with `other` among its members. On Scala 2, the
   * id of an alias of `A with B` has no parents, so it is not a subtype of `A`.
   *
   * The check sees only what the ids store, so it misses some real subtypes:
   *
   *   - the predefined ids of `List`, `Option`, `Some`, `Int` and most other
   *     standard library types store no parents, so `Some[Int]` is not a
   *     subtype of `Option[Int]`, and `List[Int]` is not a subtype of
   *     `Seq[Int]`;
   *   - derived parents leave out `AnyRef`, `Product` and `Serializable`, so a
   *     case class is not a subtype of `Product`;
   *   - parents are stored without their type arguments, so for
   *     `final class IntBox extends Box[Int]`, `IntBox` is not a subtype of
   *     `Box[Int]`, and a case class that extends `Ordered[Price]` is not a
   *     subtype of `Ordered[Price]` (a known bug). On Scala 3, `IntBox` is not
   *     a subtype of `TypeId.of[Box]` either;
   *   - on Scala 3, an enum case is compared with its enum only, so for
   *     `enum Planet extends HasMass`, `Planet.Earth` is not a subtype of
   *     `HasMass` (a known bug);
   *   - an alias on the left is not resolved: for `type MyDog = Dog`, `MyDog`
   *     is not a subtype of `Dog`'s parents.
   *
   * {{{
   * sealed trait Shape
   * final case class Circle(radius: Double) extends Shape
   *
   * TypeId.of[Circle].isSubtypeOf(TypeId.of[Shape])                   // true
   * TypeId.of[List[Circle]].isSubtypeOf(TypeId.of[List[Shape]])       // true
   * TypeId.of[Some[Int]].isSubtypeOf(TypeId.of[Option[Int]])          // false
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/typeid/#subtype-relationships Subtype Relationships]]
   */
  def isSubtypeOf(other: TypeId[_]): Boolean = {
    if (TypeId.structurallyEqual(this, other)) return true

    if (this.fullName == "scala.Nothing") return true

    if (other.fullName == "scala.Any") return true

    if (this.isApplied && other.isApplied && this.fullName == other.fullName) {
      return TypeIdOps.checkAppliedSubtyping(this, other)
    }

    TypeIdOps.checkParents(this.defKind.baseTypes, other, Set(this.fullName))
  }

  /**
   * Returns `other.isSubtypeOf(this)`; see [[isSubtypeOf]] for what the check
   * covers.
   */
  def isSupertypeOf(other: TypeId[_]): Boolean = other.isSubtypeOf(this)

  /**
   * Returns `true` when each type is a subtype of the other by [[isSubtypeOf]],
   * as for two equal ids, or for `type Age = Int` and `Int`.
   */
  def isEquivalentTo(other: TypeId[_]): Boolean =
    this.isSubtypeOf(other) && other.isSubtypeOf(this)

  // equals and hashCode are overridden in TypeId.Impl with cached hash for performance.
  // The sealed trait delegates to structurallyEqual/structuralHash as the default.
  /**
   * Returns `true` when `other` is a `TypeId` that describes the same type, by
   * [[TypeId.structurallyEqual]].
   */
  override def equals(other: Any): Boolean = other match {
    case that: TypeId[_] => TypeId.structurallyEqual(this, that)
    case _               => false
  }

  /**
   * Returns [[TypeId.structuralHash]] of this id, so equal ids have equal hash
   * codes.
   */
  override def hashCode(): Int = TypeId.structuralHash(this)

  /**
   * Renders the type with [[TypeIdPrinter]], such as `"Map[String, Int]"`.
   * Types print by their simple name, except types from a `java` package
   * outside `java.lang` and its subpackages, which print by full name
   * (`"java.util.UUID"`). A type constructor prints with its parameters
   * (`"List[+A]"`); on Scala 3, the id of a union type prints as `"Union"`.
   */
  override def toString: String = TypeIdPrinter.render(this)
}

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
  implicit def derived[A]: TypeId[A] = macro TypeIdMacros.derivedImpl[A]
}

/**
 * Derives, builds and compares [[TypeId]] values. [[of]] derives the id of a
 * type at compile time, and the implicit [[TypeIdLowPriority#derived]] supplies
 * one wherever a `TypeId[A]` is required. The companion also holds predefined
 * ids for common types, such as [[TypeId.int]], [[TypeId.string]] and
 * [[TypeId.list]]; constructors that build ids by hand (`nominal`, [[alias]],
 * [[opaque]], [[applied]]); extractors that classify an id in a pattern match;
 * and the helpers [[normalize]], [[structurallyEqual]] and [[unapplied]].
 *
 * {{{
 * final case class Point(x: Int, y: Int)
 *
 * def describe[A](implicit id: TypeId[A]): String = id.name
 *
 * describe[Point] // "Point"
 *
 * TypeId.of[Option[Int]] match {
 *   case TypeId.Alias(name, _, _, _)          => "alias " + name
 *   case TypeId.Nominal(name, owner, _, _, _) => owner.asString + "." + name
 *   case _                                    => "other"
 * }
 * // "scala.Option"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/typeid/ TypeId]]
 */
object TypeId extends TypeIdInstances with TypeIdLowPriority {

  private[typeid] final class Impl[A](
    val name: String,
    val owner: Owner,
    val typeParams: List[TypeParam],
    val typeArgs: List[TypeRepr],
    val defKind: TypeDefKind,
    val selfType: Option[TypeRepr],
    val aliasedTo: Option[TypeRepr],
    val representation: Option[TypeRepr],
    val annotations: List[Annotation]
  ) extends TypeId[A] {
    private val cachedHash: Int = TypeIdOps.structuralHash(this)

    override def hashCode(): Int = cachedHash

    override def equals(other: Any): Boolean = other match {
      case that: TypeId[_] =>
        (this eq that) || (this.cachedHash == that.hashCode() && TypeId.structurallyEqual(this, that))
      case _ => false
    }
  }

  private[blocks] def makeImpl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    typeArgs: List[TypeRepr],
    defKind: TypeDefKind,
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ): TypeId[A] =
    new Impl[A](name, owner, typeParams, typeArgs, defKind, selfType, aliasedTo, representation, annotations)

  /**
   * Creates the id of a nominal type (a class, trait, object or enum) from its
   * name, owner and kind, with no type parameters, type arguments, self-type or
   * annotations. [[of]] derives ids from real types; this constructor is for
   * tests and generated code.
   */
  def nominal[A](
    name: String,
    owner: Owner,
    kind: TypeDefKind
  ): TypeId[A] = new Impl[A](name, owner, Nil, Nil, kind, None, None, None, Nil)

  /**
   * Creates the id of a nominal type (a class, trait, object or enum) from its
   * parts. A hand-built id equals a derived one when the full name, type
   * parameters and type arguments match.
   *
   * {{{
   * val point = TypeId.nominal[Any](
   *   "Point",
   *   Owner.fromPackagePath("com.example"),
   *   defKind = TypeDefKind.Class(isCase = true)
   * )
   * point.fullName    // "com.example.Point"
   * point.isCaseClass // true
   * }}}
   */
  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    typeArgs: List[TypeRepr] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = new Impl[A](
    name,
    owner,
    typeParams,
    typeArgs,
    defKind,
    selfType,
    None,
    None,
    annotations
  )

  /**
   * Creates the id of a type alias named `name` that stands for `aliased`, with
   * kind [[TypeDefKind.TypeAlias]]. The id equals the id of the type it stands
   * for once aliases are resolved.
   *
   * {{{
   * val age = TypeId.alias[Any](
   *   "Age",
   *   Owner.fromPackagePath("com.example"),
   *   aliased = TypeRepr.Ref(TypeId.int)
   * )
   * age.isAlias        // true
   * age == TypeId.int  // true
   * }}}
   */
  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = new Impl[A](
    name,
    owner,
    typeParams,
    typeArgs,
    TypeDefKind.TypeAlias,
    None,
    Some(aliased),
    None,
    annotations
  )

  /**
   * Creates the id of an opaque type named `name` with the given representation
   * and public bounds, with kind [[TypeDefKind.OpaqueType]]. Unlike an alias,
   * it is not equal to the id of its representation.
   */
  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = new Impl[A](
    name,
    owner,
    typeParams,
    typeArgs,
    TypeDefKind.OpaqueType(publicBounds),
    None,
    None,
    Some(representation),
    annotations
  )

  /**
   * Applies the type constructor `typeConstructor` to `args`. The result keeps
   * the constructor's name, owner, type parameters, kind, self-type, alias
   * target, representation and annotations, and takes `args` as its type
   * arguments in place of any it had.
   *
   * {{{
   * val listOfInt = TypeId.applied[List[Int]](TypeId.list, TypeRepr.Ref(TypeId.int))
   * listOfInt == TypeId.of[List[Int]] // true
   * }}}
   */
  def applied[A](
    typeConstructor: TypeId[_],
    args: TypeRepr*
  ): TypeId[A] = new Impl[A](
    typeConstructor.name,
    typeConstructor.owner,
    typeConstructor.typeParams,
    args.toList,
    typeConstructor.defKind,
    typeConstructor.selfType,
    typeConstructor.aliasedTo,
    typeConstructor.representation,
    typeConstructor.annotations
  )

  /**
   * Matches an id that is neither an alias nor an opaque type (its
   * [[TypeId#aliasedTo]] and [[TypeId#representation]] are empty), and gives
   * its name, owner, type parameters, kind and parents. Classes, traits,
   * objects, enums, enum cases and the predefined ids match. On Scala 3, the
   * ids of union and intersection types are aliases, so they do not match.
   */
  object Nominal {

    /**
     * Returns the name, owner, type parameters, kind and parents of an id that
     * is neither an alias nor an opaque type, and `None` for the others.
     */
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeDefKind, List[TypeRepr])] =
      if (id.aliasedTo.isEmpty && id.representation.isEmpty)
        Some((id.name, id.owner, id.typeParams, id.defKind, id.parents))
      else None
  }

  /**
   * Matches an id with an alias target ([[TypeId#aliasedTo]]), and gives its
   * name, owner, type parameters and that target.
   */
  object Alias {

    /**
     * Returns the name, owner, type parameters and alias target of an id with
     * an alias target, and `None` otherwise.
     */
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] =
      id.aliasedTo.map(a => (id.name, id.owner, id.typeParams, a))
  }

  /**
   * Matches the id of an opaque type (kind [[TypeDefKind.OpaqueType]] with a
   * [[TypeId#representation]]), and gives its name, owner, type parameters,
   * representation and public bounds.
   */
  object Opaque {

    /**
     * Returns the name, owner, type parameters, representation and public
     * bounds of an opaque type id, and `None` otherwise.
     */
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr, TypeBounds)] =
      (id.defKind, id.representation) match {
        case (TypeDefKind.OpaqueType(bounds), Some(repr)) =>
          Some((id.name, id.owner, id.typeParams, repr, bounds))
        case _ => None
      }
  }

  /**
   * Matches the id of a sealed trait and gives its name. A sealed abstract
   * class and a Scala 3 enum do not match.
   */
  object Sealed {

    /**
     * Returns the name of an id whose kind is a sealed trait, and `None`
     * otherwise.
     */
    def unapply(id: TypeId[_]): Option[String] =
      id.defKind match {
        case TypeDefKind.Trait(true, _) => Some(id.name)
        case _                          => None
      }
  }

  /**
   * Matches the id of a Scala 3 enum, not of one of its cases, and gives its
   * name and owner.
   */
  object Enum {

    /**
     * Returns the name and owner of an id whose kind is a Scala 3 enum, and
     * `None` otherwise.
     */
    def unapply(id: TypeId[_]): Option[(String, Owner)] =
      id.defKind match {
        case TypeDefKind.Enum(_) => Some((id.name, id.owner))
        case _                   => None
      }
  }

  /**
   * Derives the id of `A` at compile time. The primitive types, and `String`,
   * `BigInt` and `BigDecimal` written with those names, give their predefined
   * ids, such as [[TypeId.int]]. `java.lang.String` written in full gives an
   * equal id read from the definition, while `scala.math.BigInt` and
   * `scala.math.BigDecimal` written in full give ids named that way, which are
   * not equal to [[TypeId.bigInt]] and [[TypeId.bigDecimal]] (named
   * `scala.BigInt` and `scala.BigDecimal`). For an applied type whose
   * constructor has a predefined id, such as `List[Int]` or `Map[String, Int]`,
   * it takes that id and adds the type arguments. For other types the macro
   * reads the definition: name, owner, type parameters, kind, parents,
   * self-type and annotations.
   *
   * A type alias keeps its own name, with [[TypeId#aliasedTo]] set to the type
   * it stands for, so the id of `type Age = Int` is named `Age` (and equals the
   * id of `Int`). On Scala 3, `of` also derives opaque types, enums and their
   * cases, and union types; an intersection type gets an id on both versions
   * (named `"Intersection"` on Scala 3 and `"<refinement>"` on Scala 2).
   *
   * On Scala 3, the predefined constructor is chosen by its simple name, so an
   * applied type of your own named `List`, `Option`, `Some`, `Map`, `Either`,
   * `Set`, `Vector`, `Seq` or `IndexedSeq` gets the standard library's name and
   * owner (a known bug). On both versions, a type argument or parent of your
   * own named `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`,
   * `Double`, `Unit`, `String`, `List`, `Option`, `Map`, `Either`, `Set` or
   * `Vector` is recorded as the standard library's id (a known bug).
   *
   * {{{
   * final case class Point(x: Int, y: Int)
   *
   * TypeId.of[Point].name                      // "Point"
   * TypeId.of[Option[Int]].typeArgs            // List(Ref(Int))
   * TypeId.of[Either[String, Int]].fullName    // "scala.util.Either"
   * TypeId.of[Int] eq TypeId.int               // true
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/typeid/#automatic-derivation Automatic Derivation]]
   */
  def of[A]: TypeId[A] = macro TypeIdMacros.ofImpl[A]

  /**
   * Resolves aliases: follows [[TypeId#aliasedTo]] through chains of aliases
   * and substitutes type arguments, so `type Age2 = Age` with `type Age = Int`
   * gives the id of `Int`, and `type Names = List[String]` gives
   * `List[String]`. An id that is not an alias comes back unchanged, and an
   * opaque type is not unwrapped. An alias of a union or intersection type
   * stays as it is.
   *
   * On Scala 3, an alias of a tuple type and an applied alias with type
   * parameters come back unchanged (a known bug).
   */
  def normalize(id: TypeId[_]): TypeId[_] = TypeIdOps.normalize(id)

  /**
   * Returns `true` when `a` and `b` describe the same type: after
   * [[normalize]], they have the same full name, the same type parameters and
   * equal type arguments (aliases inside the arguments are resolved too, so
   * `List[Age]` equals `List[Int]` for `type Age = Int`), and, for an alias
   * that remains after normalizing, such as a union, the same target. Kind,
   * parents and annotations are not compared. An opaque type is equal only to
   * an opaque type with the same full name, type parameters and type arguments.
   * `==` on ids uses this test.
   */
  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = TypeIdOps.structurallyEqual(a, b)

  /**
   * Returns a hash code consistent with [[structurallyEqual]]: equal ids get
   * equal hashes. `hashCode` on an id returns it.
   */
  def structuralHash(id: TypeId[_]): Int = TypeIdOps.structuralHash(id)

  /**
   * Returns `id` without its type arguments, the type constructor of an applied
   * type, so that it can be looked up in a registry keyed by type constructor.
   * An id that is not applied comes back unchanged.
   *
   * On Scala 2, `TypeId.unapplied(TypeId.of[List[Int]])` also equals
   * `TypeId.of[List[_]]`. On Scala 3, `TypeId.of[List]` derives the
   * `scala.package.List` alias instead, which does not equal it (a known bug);
   * compare with [[TypeId.list]].
   *
   * {{{
   * TypeId.unapplied(TypeId.of[List[Int]]) == TypeId.list // true
   * }}}
   */
  def unapplied(id: TypeId[_]): TypeId[_] = TypeIdOps.unapplied(id)

  // ========== Erased TypeId for Type-Indexed Maps ==========

  /**
   * The placeholder type argument of [[Erased]].
   */
  type Unknown

  /**
   * An id whose type parameter is unknown, used to store the ids of different
   * types together. [[TypeId#erased]] returns one.
   */
  type Erased = TypeId[Unknown]
}
