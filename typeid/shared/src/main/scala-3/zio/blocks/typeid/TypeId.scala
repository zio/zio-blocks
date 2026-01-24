package zio.blocks.typeid

import scala.annotation.tailrec

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
sealed trait TypeId[A <: AnyKind] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]      // For type aliases
  def representation: Option[TypeRepr] // For opaque types
  def annotations: List[Annotation]

  final def parents: List[TypeRepr] = defKind.baseTypes

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
    // A type is a subtype of itself
    if (TypeId.structurallyEqual(this, other)) return true

    // Check enum case -> parent enum relationship
    defKind match {
      case TypeDefKind.EnumCase(parentEnum, _, _) =>
        parentEnum match {
          case TypeRepr.Ref(id)                      => TypeId.structurallyEqual(id, other)
          case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.fullName == other.fullName
          case _                                     => false
        }
      case _ =>
        // Check if other appears in our parent types (transitive)
        // Use defKind.baseTypes which is populated by the macro, not parents which defaults to Nil
        TypeId.checkParents(this.defKind.baseTypes, other, Set(this.fullName))
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

object TypeId {

  // Private implementation case class
  private final case class Impl[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    defKind: TypeDefKind,
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ) extends TypeId[A]

  // ========== Smart Constructors ==========

  def nominal[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
    name,
    owner,
    typeParams,
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
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
    name,
    owner,
    typeParams,
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
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
    name,
    owner,
    typeParams,
    TypeDefKind.OpaqueType(publicBounds),
    None,
    None,
    Some(representation),
    annotations
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

  /** Derives a TypeId for any type or type constructor using macros. */
  inline def derived[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  // ========== Normalization and Equality ==========

  /**
   * Normalizes a TypeId by following type alias chains. Aliases are resolved to
   * their underlying type, while nominal and opaque types remain unchanged.
   */
  @tailrec
  def normalize(id: TypeId[?]): TypeId[?] = id.aliasedTo match {
    case Some(TypeRepr.Ref(aliased))                      => normalize(aliased)
    case Some(TypeRepr.Applied(TypeRepr.Ref(aliased), _)) => normalize(aliased)
    case _                                                => id
  }

  /**
   * Compares two TypeIds for structural equality. Type aliases are transparent:
   * TypeId.derived[Age] == TypeId.derived[Int] where type Age = Int. Opaque
   * types maintain nominal identity.
   */
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean = {
    val normA = normalize(a)
    val normB = normalize(b)

    // Opaque types only equal if they have the same fullName (nominal identity)
    if (normA.isOpaque && normB.isOpaque) {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams
    } else if (normA.isOpaque || normB.isOpaque) {
      // Opaque type is never equal to non-opaque
      false
    } else {
      // For nominal types and resolved aliases, compare by fullName and typeParams
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams
    }
  }

  /**
   * Computes a hash code for a TypeId that is consistent with structural
   * equality.
   */
  def structuralHash(id: TypeId[?]): Int = {
    val norm = normalize(id)
    if (norm.isOpaque) {
      // Include "opaque" marker to distinguish from nominal with same name
      ("opaque", norm.fullName, norm.typeParams).hashCode()
    } else {
      (norm.fullName, norm.typeParams).hashCode()
    }
  }

  // ========== Subtyping Helpers (private) ==========

  /**
   * Recursively checks if any parent type matches the target TypeId.
   */
  private def checkParents(parents: List[TypeRepr], target: TypeId[?], visited: Set[String]): Boolean =
    parents.exists {
      case TypeRepr.Ref(id) =>
        if (visited.contains(id.fullName)) false
        else if (structurallyEqual(id, target)) true
        else checkParents(id.defKind.baseTypes, target, visited + id.fullName)
      case TypeRepr.Applied(TypeRepr.Ref(id), _) =>
        if (visited.contains(id.fullName)) false
        else if (id.fullName == target.fullName) true
        else checkParents(id.defKind.baseTypes, target, visited + id.fullName)
      case _ => false
    }

  // ========== Predefined TypeIds for Common Types ==========

  // Primitives
  given unit: TypeId[Unit]             = nominal[Unit]("Unit", Owner.scala)
  given boolean: TypeId[Boolean]       = nominal[Boolean]("Boolean", Owner.scala)
  given byte: TypeId[Byte]             = nominal[Byte]("Byte", Owner.scala)
  given short: TypeId[Short]           = nominal[Short]("Short", Owner.scala)
  given int: TypeId[Int]               = nominal[Int]("Int", Owner.scala)
  given long: TypeId[Long]             = nominal[Long]("Long", Owner.scala)
  given float: TypeId[Float]           = nominal[Float]("Float", Owner.scala)
  given double: TypeId[Double]         = nominal[Double]("Double", Owner.scala)
  given char: TypeId[Char]             = nominal[Char]("Char", Owner.scala)
  given string: TypeId[String]         = nominal[String]("String", Owner.javaLang)
  given bigInt: TypeId[BigInt]         = nominal[BigInt]("BigInt", Owner.scala)
  given bigDecimal: TypeId[BigDecimal] = nominal[BigDecimal]("BigDecimal", Owner.scala)

  // Collections
  given option: TypeId[Option]  = nominal[Option]("Option", Owner.scala, List(TypeParam.A))
  given some: TypeId[Some]      = nominal[Some]("Some", Owner.scala, List(TypeParam.A))
  given none: TypeId[None.type] = nominal[None.type]("None", Owner.scala)
  given list: TypeId[List]      = nominal[List]("List", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given vector: TypeId[Vector]  = nominal[Vector]("Vector", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given set: TypeId[Set]        = nominal[Set]("Set", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given map: TypeId[Map]        = nominal[Map]("Map", Owner.scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  given either: TypeId[Either]  = nominal[Either]("Either", Owner.scala, List(TypeParam.A, TypeParam.B))

  // java.time
  given dayOfWeek: TypeId[java.time.DayOfWeek]         = nominal[java.time.DayOfWeek]("DayOfWeek", Owner.javaTime)
  given duration: TypeId[java.time.Duration]           = nominal[java.time.Duration]("Duration", Owner.javaTime)
  given instant: TypeId[java.time.Instant]             = nominal[java.time.Instant]("Instant", Owner.javaTime)
  given localDate: TypeId[java.time.LocalDate]         = nominal[java.time.LocalDate]("LocalDate", Owner.javaTime)
  given localDateTime: TypeId[java.time.LocalDateTime] =
    nominal[java.time.LocalDateTime]("LocalDateTime", Owner.javaTime)
  given localTime: TypeId[java.time.LocalTime]           = nominal[java.time.LocalTime]("LocalTime", Owner.javaTime)
  given month: TypeId[java.time.Month]                   = nominal[java.time.Month]("Month", Owner.javaTime)
  given monthDay: TypeId[java.time.MonthDay]             = nominal[java.time.MonthDay]("MonthDay", Owner.javaTime)
  given offsetDateTime: TypeId[java.time.OffsetDateTime] =
    nominal[java.time.OffsetDateTime]("OffsetDateTime", Owner.javaTime)
  given offsetTime: TypeId[java.time.OffsetTime]       = nominal[java.time.OffsetTime]("OffsetTime", Owner.javaTime)
  given period: TypeId[java.time.Period]               = nominal[java.time.Period]("Period", Owner.javaTime)
  given year: TypeId[java.time.Year]                   = nominal[java.time.Year]("Year", Owner.javaTime)
  given yearMonth: TypeId[java.time.YearMonth]         = nominal[java.time.YearMonth]("YearMonth", Owner.javaTime)
  given zoneId: TypeId[java.time.ZoneId]               = nominal[java.time.ZoneId]("ZoneId", Owner.javaTime)
  given zoneOffset: TypeId[java.time.ZoneOffset]       = nominal[java.time.ZoneOffset]("ZoneOffset", Owner.javaTime)
  given zonedDateTime: TypeId[java.time.ZonedDateTime] =
    nominal[java.time.ZonedDateTime]("ZonedDateTime", Owner.javaTime)

  // java.util
  given currency: TypeId[java.util.Currency] = nominal[java.util.Currency]("Currency", Owner.javaUtil)
  given uuid: TypeId[java.util.UUID]         = nominal[java.util.UUID]("UUID", Owner.javaUtil)

  // Allow accessing private owner constants from within zio.blocks.typeid package
  private[typeid] object Owner {
    val scala: zio.blocks.typeid.Owner                    = zio.blocks.typeid.Owner.fromPackagePath("scala")
    val scalaCollectionImmutable: zio.blocks.typeid.Owner =
      zio.blocks.typeid.Owner.fromPackagePath("scala.collection.immutable")
    val javaLang: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.lang")
    val javaTime: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.time")
    val javaUtil: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.util")
  }
}
