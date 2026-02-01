package zio.blocks.typeid

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
 * values. In Scala 3, `A` is bounded by `AnyKind` to support higher-kinded
 * types.
 *
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
sealed trait TypeId[A <: AnyKind] extends TypeIdPlatformSpecific {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def typeArgs: List[TypeRepr]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]      // For type aliases
  def representation: Option[TypeRepr] // For opaque types
  def annotations: List[Annotation]

  final def clazz: Option[Class[?]] = TypeIdPlatformMethods.getClass(this)

  final def construct(args: Chunk[AnyRef]): Either[String, Any] = TypeIdPlatformMethods.construct(this, args)

  final def parents: List[TypeRepr] = defKind.baseTypes

  /** Returns true if this is an applied type (has type arguments) */
  final def isApplied: Boolean = typeArgs.nonEmpty

  final def erased: TypeId.Erased = this.asInstanceOf[TypeId.Erased]

  // Derived properties
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
  lazy val classTag: scala.reflect.ClassTag[?] = {
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

  /** Get enum cases if this is an enum */
  final def enumCases: List[EnumCaseInfo] = defKind match {
    case TypeDefKind.Enum(cases, _) => cases
    case _                          => Nil
  }

  /** Get known subtypes if this is a sealed trait */
  final def knownSubtypes: List[TypeRepr] = defKind match {
    case TypeDefKind.Trait(_, subtypes, _) => subtypes
    case _                                 => Nil
  }

  /** Checks if the normalized type is a Scala Tuple */
  final def isTuple: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Tuple")
  }

  /** Checks if the normalized type is a Scala Product */
  final def isProduct: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Product")
  }

  /** Checks if the normalized type is a Scala sum type (Either or Option) */
  final def isSum: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && (norm.name == "Either" || norm.name == "Option")
  }

  /** Checks if the normalized type is scala.util.Either */
  final def isEither: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala.util") && norm.name == "Either"
  }

  /** Checks if the normalized type is scala.Option */
  final def isOption: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name == "Option"
  }

  /**
   * Checks if this type is a subtype of another type.
   *
   * This method checks the type hierarchy using the extracted parent types. It
   * handles:
   *   - Direct subtype relationships (class extends trait)
   *   - Enum cases being subtypes of their parent enum
   *   - Sealed trait subtypes
   *   - Transitive inheritance
   *   - Variance-aware subtyping for applied types (List[Dog] <: List[Animal]
   *     for covariant)
   *
   * Note: This is a best-effort check based on compile-time extracted
   * information. For complex cases involving type parameters or implicit
   * conversions, the check may return false even when a true subtype
   * relationship exists at runtime.
   *
   * @param other
   *   The potential supertype to check against
   * @return
   *   true if this type is a subtype of other
   */
  def isSubtypeOf(other: TypeId[?]): Boolean = {
    if (TypeId.structurallyEqual(this, other)) return true

    if (this.fullName == "scala.Nothing") return true

    if (other.fullName == "scala.Any") return true

    other.aliasedTo match {
      case Some(TypeRepr.Union(members)) =>
        if (TypeId.appearsInUnion(this, members)) return true
      case _ => ()
    }

    this.aliasedTo match {
      case Some(TypeRepr.Intersection(members)) =>
        if (members.exists(m => TypeId.typeReprContains(m, other))) return true
      case _ => ()
    }

    if (this.isApplied && other.isApplied && this.fullName == other.fullName) {
      return TypeIdOps.checkAppliedSubtyping(this, other)
    }

    defKind match {
      case TypeDefKind.EnumCase(parentEnum, _, _) =>
        parentEnum match {
          case TypeRepr.Ref(id)                      => TypeId.structurallyEqual(id, other)
          case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.fullName == other.fullName
          case _                                     => false
        }
      case _ =>
        TypeIdOps.checkParents(this.defKind.baseTypes, other, Set(this.fullName))
    }
  }

  /**
   * Checks if this type is a supertype of another type.
   *
   * @param other
   *   The potential subtype to check against
   * @return
   *   true if this type is a supertype of other
   */
  def isSupertypeOf(other: TypeId[?]): Boolean = other.isSubtypeOf(this)

  /**
   * Checks if this type is equivalent to another type (mutually subtypes).
   *
   * @param other
   *   The type to compare with
   * @return
   *   true if both types are subtypes of each other
   */
  def isEquivalentTo(other: TypeId[?]): Boolean =
    this.isSubtypeOf(other) && other.isSubtypeOf(this)

  override def equals(other: Any): Boolean = other match {
    case that: TypeId[?] => TypeId.structurallyEqual(this, that)
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

object TypeId extends TypeIdInstances with TypeIdLowPriority {

  // Private implementation case class
  private final case class Impl[A <: AnyKind](
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

  // ========== Smart Constructors ==========

  def nominal[A <: AnyKind](
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

  def alias[A <: AnyKind](
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

  def opaque[A <: AnyKind](
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
  def applied[A <: AnyKind](
    typeConstructor: TypeId[?],
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

  // ========== Extractors ==========

  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeDefKind, List[TypeRepr])] =
      if (id.aliasedTo.isEmpty && id.representation.isEmpty)
        Some((id.name, id.owner, id.typeParams, id.defKind, id.parents))
      else None
  }

  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] =
      id.aliasedTo.map(a => (id.name, id.owner, id.typeParams, a))
  }

  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr, TypeBounds)] =
      (id.defKind, id.representation) match {
        case (TypeDefKind.OpaqueType(bounds), Some(repr)) =>
          Some((id.name, id.owner, id.typeParams, repr, bounds))
        case _ => None
      }
  }

  object Sealed {
    def unapply(id: TypeId[?]): Option[(String, List[TypeRepr])] =
      id.defKind match {
        case TypeDefKind.Trait(true, subtypes, _) => Some((id.name, subtypes))
        case _                                    => None
      }
  }

  object Enum {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[EnumCaseInfo])] =
      id.defKind match {
        case TypeDefKind.Enum(cases, _) => Some((id.name, id.owner, cases))
        case _                          => None
      }
  }

  // ========== Macro Derivation ==========

  inline def of[A <: AnyKind]: TypeId[A] = ${ TypeIdMacros.ofImpl[A] }

  // ========== Normalization and Equality ==========

  def normalize(id: TypeId[?]): TypeId[?] = TypeIdOps.normalize(id)

  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean = TypeIdOps.structurallyEqual(a, b)

  def structuralHash(id: TypeId[?]): Int = TypeIdOps.structuralHash(id)

  /**
   * Returns the type constructor by stripping all type arguments.
   *
   * For example, `TypeId.unapplied(TypeId.of[List[Int]])` returns a TypeId
   * equivalent to `TypeId.of[List]` (the unapplied type constructor).
   *
   * This is useful for TypeRegistry lookups where Seq/Map bindings are stored
   * by their type constructor rather than applied types.
   *
   * @param id
   *   The TypeId to unapply
   * @return
   *   A TypeId with empty typeArgs representing the type constructor
   */
  def unapplied(id: TypeId[?]): TypeId[?] = TypeIdOps.unapplied(id)

  // ========== Predefined TypeIds for Common Types ==========

  // ========== Scala 3-only Helpers ==========

  private def appearsInUnion(id: TypeId[?], members: List[TypeRepr]): Boolean =
    members.exists {
      case TypeRepr.Ref(memberId)   => structurallyEqual(id, memberId)
      case TypeRepr.Applied(ref, _) =>
        ref match {
          case TypeRepr.Ref(memberId) => id.fullName == memberId.fullName
          case _                      => false
        }
      case TypeRepr.Union(nestedMembers) => appearsInUnion(id, nestedMembers)
      case _                             => false
    }

  private def typeReprContains(repr: TypeRepr, target: TypeId[?]): Boolean = repr match {
    case TypeRepr.Ref(id)                      => structurallyEqual(id, target)
    case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.fullName == target.fullName
    case TypeRepr.Intersection(members)        => members.exists(m => typeReprContains(m, target))
    case TypeRepr.Union(members)               => members.exists(m => typeReprContains(m, target))
    case _                                     => false
  }

  // ========== Predefined TypeIds for Common Types ==========

  given iarray: TypeId[IArray] =
    nominal[IArray]("IArray", Owner.scala.term("IArray$package"), List(TypeParam("T", 0, Variance.Covariant)))

  // ========== Erased TypeId for Type-Indexed Maps ==========

  type Unknown <: AnyKind

  type Erased = TypeId[Unknown]
}
