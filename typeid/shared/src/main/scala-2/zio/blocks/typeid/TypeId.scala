package zio.blocks.typeid

import scala.language.experimental.macros

import zio.blocks.chunk.Chunk

/**
 * Represents the identity of a type or type constructor.
 *
 * TypeId provides rich type identity information including:
 *   - The type's name
 *   - The owner (package/class/object where it's defined)
 *   - Type parameters (for type constructors)
 *   - Classification (nominal, alias, or opaque)
 *   - Parent types and self type
 *   - Annotations
 *
 * The phantom type parameter `A` ensures type safety when working with TypeId
 * values. In Scala 2, use existential types like `List[_]` or `Map[_, _]` for
 * type constructors.
 *
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
sealed trait TypeId[A] extends TypeIdPlatformSpecific {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def typeArgs: List[TypeRepr]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]
  def representation: Option[TypeRepr]
  def annotations: List[Annotation]

  final def clazz: Option[Class[_]] = TypeIdPlatformMethods.getClass(this)

  final def construct(args: Chunk[AnyRef]): Either[String, Any] = TypeIdPlatformMethods.construct(this, args)

  final def parents: List[TypeRepr] = defKind.baseTypes

  final def isApplied: Boolean = typeArgs.nonEmpty

  final def erased: TypeId.Erased = this.asInstanceOf[TypeId.Erased]

  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  final def isProperType: Boolean      = arity == 0
  final def isTypeConstructor: Boolean = arity > 0

  final def isClass: Boolean    = defKind.isInstanceOf[TypeDefKind.Class]
  final def isTrait: Boolean    = defKind.isInstanceOf[TypeDefKind.Trait]
  final def isObject: Boolean   = defKind.isInstanceOf[TypeDefKind.Object]
  final def isEnum: Boolean     = defKind.isInstanceOf[TypeDefKind.Enum]
  final def isAlias: Boolean    = defKind == TypeDefKind.TypeAlias
  final def isOpaque: Boolean   = defKind.isInstanceOf[TypeDefKind.OpaqueType]
  final def isAbstract: Boolean = defKind == TypeDefKind.AbstractType

  final def isSealed: Boolean = defKind match {
    case TypeDefKind.Trait(isSealed, _, _) => isSealed
    case _                                 => false
  }

  final def isCaseClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, isCase, _, _) => isCase
    case _                                     => false
  }

  final def isValueClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, _, isValue, _) => isValue
    case _                                      => false
  }

  /**
   * Returns a ClassTag for this type, using the correct primitive ClassTag for
   * primitive types (Int, Long, Float, Double, Boolean, Byte, Short, Char,
   * Unit) and ClassTag.AnyRef for all reference types.
   *
   * This is useful for creating properly-typed arrays at runtime.
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

  final def enumCases: List[EnumCaseInfo] = defKind match {
    case TypeDefKind.Enum(cases, _) => cases
    case _                          => Nil
  }

  final def knownSubtypes: List[TypeRepr] = defKind match {
    case TypeDefKind.Trait(_, subtypes, _) => subtypes
    case _                                 => Nil
  }

  final def isTuple: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Tuple")
  }

  final def isProduct: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Product")
  }

  final def isSum: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && (norm.name == "Either" || norm.name == "Option")
  }

  final def isEither: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala.util") && norm.name == "Either"
  }

  final def isOption: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name == "Option"
  }

  def isSubtypeOf(other: TypeId[_]): Boolean = {
    if (TypeId.structurallyEqual(this, other)) return true

    if (this.fullName == "scala.Nothing") return true

    if (other.fullName == "scala.Any") return true

    if (this.isApplied && other.isApplied && this.fullName == other.fullName) {
      return TypeIdOps.checkAppliedSubtyping(this, other)
    }

    TypeIdOps.checkParents(this.defKind.baseTypes, other, Set(this.fullName))
  }

  def isSupertypeOf(other: TypeId[_]): Boolean = other.isSubtypeOf(this)

  def isEquivalentTo(other: TypeId[_]): Boolean =
    this.isSubtypeOf(other) && other.isSubtypeOf(this)

  override def equals(other: Any): Boolean = other match {
    case that: TypeId[_] => TypeId.structurallyEqual(this, that)
    case _               => false
  }

  override def hashCode(): Int = TypeId.structuralHash(this)

  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr  =
      if (aliasedTo.isDefined) "alias"
      else if (representation.isDefined) "opaque"
      else "nominal"
    s"TypeId.$kindStr($fullName$paramStr)"
  }
}

trait TypeIdLowPriority {
  implicit def derived[A]: TypeId[A] = macro TypeIdMacros.derivedImpl[A]
}

object TypeId extends TypeIdInstances with TypeIdLowPriority {

  private[typeid] final case class Impl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    typeArgs: List[TypeRepr],
    defKind: TypeDefKind,
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ) extends TypeId[A]

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
  ): TypeId[A] = Impl[A](name, owner, typeParams, typeArgs, defKind, selfType, aliasedTo, representation, annotations)

  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    typeArgs: List[TypeRepr] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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

  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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

  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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
   * Creates an applied type from a type constructor and type arguments. For
   * example: applied(TypeId.list, TypeRepr.Ref(TypeId.int)) creates List[Int]
   */
  def applied[A](
    typeConstructor: TypeId[_],
    args: TypeRepr*
  ): TypeId[A] = Impl[A](
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

  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeDefKind, List[TypeRepr])] =
      if (id.aliasedTo.isEmpty && id.representation.isEmpty)
        Some((id.name, id.owner, id.typeParams, id.defKind, id.parents))
      else None
  }

  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] =
      id.aliasedTo.map(a => (id.name, id.owner, id.typeParams, a))
  }

  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr, TypeBounds)] =
      (id.defKind, id.representation) match {
        case (TypeDefKind.OpaqueType(bounds), Some(repr)) =>
          Some((id.name, id.owner, id.typeParams, repr, bounds))
        case _ => None
      }
  }

  object Sealed {
    def unapply(id: TypeId[_]): Option[(String, List[TypeRepr])] =
      id.defKind match {
        case TypeDefKind.Trait(true, subtypes, _) => Some((id.name, subtypes))
        case _                                    => None
      }
  }

  object Enum {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[EnumCaseInfo])] =
      id.defKind match {
        case TypeDefKind.Enum(cases, _) => Some((id.name, id.owner, cases))
        case _                          => None
      }
  }

  def of[A]: TypeId[A] = macro TypeIdMacros.ofImpl[A]

  def normalize(id: TypeId[_]): TypeId[_] = TypeIdOps.normalize(id)

  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = TypeIdOps.structurallyEqual(a, b)

  def structuralHash(id: TypeId[_]): Int = TypeIdOps.structuralHash(id)

  /**
   * Returns the type constructor by stripping all type arguments.
   *
   * For example, `TypeId.unapplied(TypeId.of[List[Int]])` returns a TypeId
   * equivalent to `TypeId.of[List[_]]` (the unapplied type constructor).
   *
   * This is useful for TypeRegistry lookups where Seq/Map bindings are stored
   * by their type constructor rather than applied types.
   *
   * @param id
   *   The TypeId to unapply
   * @return
   *   A TypeId with empty typeArgs representing the type constructor
   */
  def unapplied(id: TypeId[_]): TypeId[_] = TypeIdOps.unapplied(id)

  // ========== Erased TypeId for Type-Indexed Maps ==========

  type Unknown

  type Erased = TypeId[Unknown]
}
