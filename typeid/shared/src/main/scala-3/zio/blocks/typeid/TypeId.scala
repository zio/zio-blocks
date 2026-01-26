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
  def typeArgs: List[TypeRepr]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]      // For type aliases
  def representation: Option[TypeRepr] // For opaque types
  def annotations: List[Annotation]

  final def parents: List[TypeRepr] = defKind.baseTypes

  /** Returns true if this is an applied type (has type arguments) */
  final def isApplied: Boolean = typeArgs.nonEmpty

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
    // A type is a subtype of itself
    if (TypeId.structurallyEqual(this, other)) return true

    // Special case: Nothing is subtype of everything
    if (this.fullName == "scala.Nothing") return true

    // Special case: Everything is subtype of Any
    if (other.fullName == "scala.Any") return true

    // Check if this type appears in a union type (A <: A | B)
    other.aliasedTo match {
      case Some(TypeRepr.Union(members)) =>
        if (TypeId.appearsInUnion(this, members)) return true
      case _ => ()
    }

    // Check if this is an intersection type that is subtype of its members (A & B <: A)
    this.aliasedTo match {
      case Some(TypeRepr.Intersection(members)) =>
        if (members.exists(m => TypeId.typeReprContains(m, other))) return true
      case _ => ()
    }

    // Check applied types with variance
    if (this.isApplied && other.isApplied && this.fullName == other.fullName) {
      return TypeId.checkAppliedSubtyping(this, other)
    }

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
    typeArgs: List[TypeRepr],
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

  inline def of[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  inline def derived[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  // ========== Normalization and Equality ==========

  /**
   * Normalizes a TypeId by following type alias chains and preserving applied
   * type arguments. When an alias points to an Applied type (e.g., type Foo[A] =
   * Bar[A]), the normalized result will have the underlying type with the
   * applied type arguments resolved through the alias chain.
   */
  def normalize(id: TypeId[?]): TypeId[?] = normalizeWithArgs(id, id.typeArgs)

  @tailrec
  private def normalizeWithArgs(id: TypeId[?], accumulatedArgs: List[TypeRepr]): TypeId[?] = id.aliasedTo match {
    case Some(TypeRepr.Ref(aliased)) =>
      normalizeWithArgs(aliased, accumulatedArgs)
    case Some(TypeRepr.Applied(TypeRepr.Ref(aliased), appliedArgs)) =>
      val resolvedArgs = resolveTypeArgs(id.typeParams, accumulatedArgs, appliedArgs)
      normalizeWithArgs(aliased, resolvedArgs)
    case _ =>
      if (accumulatedArgs == id.typeArgs) id
      else
        Impl(
          id.name,
          id.owner,
          id.typeParams,
          accumulatedArgs,
          id.defKind,
          id.selfType,
          id.aliasedTo,
          id.representation,
          id.annotations
        )
  }

  private def resolveTypeArgs(
    aliasParams: List[TypeParam],
    actualArgs: List[TypeRepr],
    targetArgs: List[TypeRepr]
  ): List[TypeRepr] =
    if (aliasParams.isEmpty || actualArgs.isEmpty) targetArgs
    else {
      val paramToArg: Map[Int, TypeRepr] = aliasParams
        .zip(actualArgs)
        .map { case (param, arg) =>
          param.index -> arg
        }
        .toMap
      targetArgs.map(substituteTypeRepr(_, paramToArg))
    }

  private def substituteTypeRepr(repr: TypeRepr, subst: Map[Int, TypeRepr]): TypeRepr = repr match {
    case TypeRepr.ParamRef(param, _) =>
      subst.getOrElse(param.index, repr)
    case TypeRepr.Applied(tycon, args) =>
      TypeRepr.Applied(substituteTypeRepr(tycon, subst), args.map(substituteTypeRepr(_, subst)))
    case TypeRepr.Ref(id) if id.isAlias =>
      val normId = normalize(id)
      if (normId eq id) repr else TypeRepr.Ref(normId)
    case _ => repr
  }

  /**
   * Compares two TypeIds for structural equality. Type aliases are transparent:
   * TypeId.of[Age] == TypeId.of[Int] where type Age = Int. Opaque types
   * maintain nominal identity. Applied types with different type arguments are
   * NOT equal (List[Int] != List[String]). Union/Intersection aliases are equal
   * only if their member types match.
   */
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean = {
    val normA = normalize(a)
    val normB = normalize(b)

    if (normA.isOpaque && normB.isOpaque) {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams &&
      typeArgsEqual(normA.typeArgs, normB.typeArgs)
    } else if (normA.isOpaque || normB.isOpaque) {
      false
    } else {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams &&
      typeArgsEqual(normA.typeArgs, normB.typeArgs) &&
      aliasedToEqual(normA.aliasedTo, normB.aliasedTo)
    }
  }

  private def aliasedToEqual(a: Option[TypeRepr], b: Option[TypeRepr]): Boolean = (a, b) match {
    case (Some(reprA), Some(reprB)) => typeReprEqual(reprA, reprB)
    case (None, None)               => true
    case _                          => false
  }

  private def typeArgsEqual(argsA: List[TypeRepr], argsB: List[TypeRepr]): Boolean =
    if (argsA.size != argsB.size) false
    else argsA.zip(argsB).forall { case (a, b) => typeReprEqual(a, b) }

  private def typeReprEqual(a: TypeRepr, b: TypeRepr): Boolean = (a, b) match {
    case (TypeRepr.Ref(idA), TypeRepr.Ref(idB)) =>
      structurallyEqual(idA, idB)
    case (TypeRepr.Applied(tyconA, argsA), TypeRepr.Applied(tyconB, argsB)) =>
      typeReprEqual(tyconA, tyconB) && typeArgsEqual(argsA, argsB)
    case (TypeRepr.ParamRef(paramA, depthA), TypeRepr.ParamRef(paramB, depthB)) =>
      paramA.name == paramB.name && paramA.index == paramB.index && depthA == depthB
    case (TypeRepr.Union(typesA), TypeRepr.Union(typesB)) =>
      typeReprsEqualAsSet(typesA, typesB)
    case (TypeRepr.Intersection(typesA), TypeRepr.Intersection(typesB)) =>
      typeReprsEqualAsSet(typesA, typesB)
    case _ =>
      a == b
  }

  private def typeReprsEqualAsSet(as: List[TypeRepr], bs: List[TypeRepr]): Boolean =
    as.size == bs.size && as.forall(a => bs.exists(b => typeReprEqual(a, b)))

  /**
   * Computes a hash code for a TypeId that is consistent with structural
   * equality. Includes typeArgs for applied types and aliasedTo for aliases.
   */
  def structuralHash(id: TypeId[?]): Int = {
    val norm = normalize(id)
    if (norm.isOpaque) {
      ("opaque", norm.fullName, norm.typeParams, typeArgsHash(norm.typeArgs)).hashCode()
    } else {
      (norm.fullName, norm.typeParams, typeArgsHash(norm.typeArgs), aliasedToHash(norm.aliasedTo)).hashCode()
    }
  }

  private def aliasedToHash(aliasedTo: Option[TypeRepr]): Int =
    aliasedTo.map(typeReprHash).getOrElse(0)

  private def typeArgsHash(args: List[TypeRepr]): Int =
    args.map(typeReprHash).hashCode()

  private def typeReprHash(repr: TypeRepr): Int = repr match {
    case TypeRepr.Ref(id)              => structuralHash(id)
    case TypeRepr.Applied(tycon, args) => (typeReprHash(tycon), typeArgsHash(args)).hashCode()
    case TypeRepr.Union(types)         => ("union", types.map(typeReprHash).toSet).hashCode()
    case TypeRepr.Intersection(types)  => ("intersection", types.map(typeReprHash).toSet).hashCode()
    case _                             => repr.hashCode()
  }

  // ========== Subtyping Helpers (private) ==========

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

  /**
   * Checks variance-aware subtyping for applied types. For
   * List[Dog].isSubtypeOf(List[Animal]) to be true, requires covariance.
   */
  private def checkAppliedSubtyping(sub: TypeId[?], sup: TypeId[?]): Boolean = {
    if (sub.fullName != sup.fullName) return false
    if (sub.typeArgs.size != sup.typeArgs.size) return false
    if (sub.typeParams.size != sub.typeArgs.size) return false

    sub.typeParams.zip(sub.typeArgs.zip(sup.typeArgs)).forall { case (param, (subArg, supArg)) =>
      param.variance match {
        case Variance.Covariant =>
          isTypeReprSubtypeOf(subArg, supArg)
        case Variance.Contravariant =>
          isTypeReprSubtypeOf(supArg, subArg)
        case Variance.Invariant =>
          subArg == supArg
      }
    }
  }

  private def isTypeReprSubtypeOf(sub: TypeRepr, sup: TypeRepr): Boolean = (sub, sup) match {
    case (TypeRepr.Ref(subId), TypeRepr.Ref(supId)) =>
      subId.isSubtypeOf(supId)
    case (TypeRepr.Applied(TypeRepr.Ref(subTycon), subArgs), TypeRepr.Applied(TypeRepr.Ref(supTycon), supArgs)) =>
      if (subTycon.fullName != supTycon.fullName) false
      else if (subArgs.size != supArgs.size) false
      else if (subTycon.typeParams.size != subArgs.size) false
      else {
        subTycon.typeParams.zip(subArgs.zip(supArgs)).forall { case (param, (subArg, supArg)) =>
          param.variance match {
            case Variance.Covariant     => isTypeReprSubtypeOf(subArg, supArg)
            case Variance.Contravariant => isTypeReprSubtypeOf(supArg, subArg)
            case Variance.Invariant     => subArg == supArg
          }
        }
      }
    case _ => sub == sup
  }

  private def typeReprContains(repr: TypeRepr, target: TypeId[?]): Boolean = repr match {
    case TypeRepr.Ref(id)                      => structurallyEqual(id, target)
    case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.fullName == target.fullName
    case TypeRepr.Intersection(members)        => members.exists(m => typeReprContains(m, target))
    case TypeRepr.Union(members)               => members.exists(m => typeReprContains(m, target))
    case _                                     => false
  }

  // ========== Predefined TypeIds for Common Types ==========

  // Java interfaces
  given charSequence: TypeId[CharSequence] =
    nominal[CharSequence]("CharSequence", Owner.javaLang, defKind = TypeDefKind.Trait(isSealed = false))
  given comparable: TypeId[Comparable[?]] =
    nominal[Comparable[?]]("Comparable", Owner.javaLang, List(TypeParam("T", 0, Variance.Invariant)))
  given serializable: TypeId[java.io.Serializable] =
    nominal[java.io.Serializable]("Serializable", Owner.javaIo, defKind = TypeDefKind.Trait(isSealed = false))

  // Primitives
  given unit: TypeId[Unit]       = nominal[Unit]("Unit", Owner.scala)
  given boolean: TypeId[Boolean] = nominal[Boolean]("Boolean", Owner.scala)
  given byte: TypeId[Byte]       = nominal[Byte]("Byte", Owner.scala)
  given short: TypeId[Short]     = nominal[Short]("Short", Owner.scala)
  given int: TypeId[Int]         = nominal[Int]("Int", Owner.scala)
  given long: TypeId[Long]       = nominal[Long]("Long", Owner.scala)
  given float: TypeId[Float]     = nominal[Float]("Float", Owner.scala)
  given double: TypeId[Double]   = nominal[Double]("Double", Owner.scala)
  given char: TypeId[Char]       = nominal[Char]("Char", Owner.scala)
  given string: TypeId[String]   = nominal[String](
    "String",
    Owner.javaLang,
    defKind = TypeDefKind.Class(
      isFinal = true,
      isAbstract = false,
      isCase = false,
      isValue = false,
      bases = List(
        TypeRepr.Ref(charSequence),
        TypeRepr.Ref(comparable.asInstanceOf[TypeId[?]]),
        TypeRepr.Ref(serializable)
      )
    )
  )
  given bigInt: TypeId[BigInt]         = nominal[BigInt]("BigInt", Owner.scala)
  given bigDecimal: TypeId[BigDecimal] = nominal[BigDecimal]("BigDecimal", Owner.scala)

  // Collections
  given option: TypeId[Option]         = nominal[Option]("Option", Owner.scala, List(TypeParam.A))
  given some: TypeId[Some]             = nominal[Some]("Some", Owner.scala, List(TypeParam.A))
  given none: TypeId[None.type]        = nominal[None.type]("None", Owner.scala)
  given list: TypeId[List]             = nominal[List]("List", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given vector: TypeId[Vector]         = nominal[Vector]("Vector", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given set: TypeId[Set]               = nominal[Set]("Set", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given seq: TypeId[Seq]               = nominal[Seq]("Seq", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given indexedSeq: TypeId[IndexedSeq] =
    nominal[IndexedSeq]("IndexedSeq", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given map: TypeId[Map]       = nominal[Map]("Map", Owner.scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  given either: TypeId[Either] = nominal[Either]("Either", Owner.scalaUtil, List(TypeParam.A, TypeParam.B))

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
    val scalaUtil: zio.blocks.typeid.Owner                = zio.blocks.typeid.Owner.fromPackagePath("scala.util")
    val scalaCollectionImmutable: zio.blocks.typeid.Owner =
      zio.blocks.typeid.Owner.fromPackagePath("scala.collection.immutable")
    val javaLang: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.lang")
    val javaIo: zio.blocks.typeid.Owner   = zio.blocks.typeid.Owner.fromPackagePath("java.io")
    val javaTime: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.time")
    val javaUtil: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("java.util")
  }
}
