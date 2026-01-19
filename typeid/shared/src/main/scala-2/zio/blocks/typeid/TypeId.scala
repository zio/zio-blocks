package zio.blocks.typeid

import scala.language.experimental.macros

/**
 * Represents the identity of a type or type constructor.
 *
 * TypeId provides rich type identity information including:
 *   - The type's name
 *   - The owner (package/class/object where it's defined)
 *   - Type parameters (for type constructors)
 *   - Classification (nominal, alias, or opaque)
 *
 * The phantom type parameter `A` ensures type safety when working with TypeId
 * values. In Scala 2, use existential types like `List[_]` or `Map[_, _]` for
 * type constructors.
 *
 * TypeId wraps a TypeRef which contains the actual type identity information.
 * Two TypeIds are considered equal if they refer to the same underlying type,
 * with type aliases being transparent (an alias equals its underlying type),
 * while opaque types maintain nominal identity.
 *
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
final case class TypeId[A](ref: TypeRef) {

  /** The simple name of the type. */
  def name: String = ref.name

  /** The owner of this type (package, object, class, etc.). */
  def owner: Owner = ref.owner

  /**
   * The type parameters of this type (empty for proper types, non-empty for
   * type constructors).
   */
  def typeParams: List[TypeParam] = ref.typeParams

  /** The arity of this type (number of type parameters). */
  def arity: Int = ref.arity

  /** The fully qualified name of this type. */
  def fullName: String = ref.fullName

  /** The kind of type definition (class, trait, object, enum, etc.). */
  def defKind: TypeDefKind = ref.defKind

  /** Returns true if this TypeId represents a nominal type. */
  def isNominal: Boolean = ref.isInstanceOf[TypeRef.Nominal]

  /** Returns true if this TypeId represents a type alias. */
  def isAlias: Boolean = ref.isInstanceOf[TypeRef.Alias]

  /** Returns true if this TypeId represents an opaque type. */
  def isOpaque: Boolean = ref.isInstanceOf[TypeRef.Opaque]

  /** Returns true if this TypeId represents a class (case or regular). */
  def isClass: Boolean = defKind.isInstanceOf[TypeDefKind.Class]

  /** Returns true if this TypeId represents a case class. */
  def isCaseClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, isCase, _, _) => isCase
    case _                                     => false
  }

  /** Returns true if this TypeId represents a trait. */
  def isTrait: Boolean = defKind.isInstanceOf[TypeDefKind.Trait]

  /** Returns true if this TypeId represents a sealed trait. */
  def isSealedTrait: Boolean = defKind match {
    case TypeDefKind.Trait(isSealed, _, _) => isSealed
    case _                                 => false
  }

  /** Returns true if this TypeId represents an object. */
  def isObject: Boolean = defKind.isInstanceOf[TypeDefKind.Object]

  /** Returns true if this TypeId represents a Scala 3 enum. */
  def isEnum: Boolean = defKind.isInstanceOf[TypeDefKind.Enum]

  /** If this is an enum, returns the enum cases; otherwise empty list. */
  def enumCases: List[EnumCaseInfo] = defKind match {
    case TypeDefKind.Enum(cases, _) => cases
    case _                          => Nil
  }

  /**
   * If this is a sealed trait, returns known subtypes; otherwise empty list.
   */
  def knownSubtypes: List[TypeRepr] = defKind match {
    case TypeDefKind.Trait(_, subtypes, _) => subtypes
    case _                                 => Nil
  }

  /** Returns the base types (parent classes/traits) of this type. */
  def baseTypes: List[TypeRepr] = defKind.baseTypes

  /**
   * Checks if this type is a subtype of another type.
   *
   * This method checks the type hierarchy using the extracted base types. It
   * handles:
   *   - Direct subtype relationships (class extends trait)
   *   - Sealed trait subtypes
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
  def isSubtypeOf(other: TypeId[_]): Boolean = {
    // A type is a subtype of itself
    if (TypeId.structurallyEqual(this, other)) return true

    // Check if other appears in our base types
    def checkBaseTypes(bases: List[TypeRepr], visited: Set[String]): Boolean =
      bases.exists {
        case TypeRepr.Ref(id) =>
          if (visited.contains(id.fullName)) false
          else if (TypeId.structurallyEqual(id, other)) true
          else checkBaseTypes(id.baseTypes, visited + id.fullName)
        case TypeRepr.Applied(TypeRepr.Ref(id), _) =>
          if (visited.contains(id.fullName)) false
          else if (id.fullName == other.fullName) true // Match by name for applied types
          else checkBaseTypes(id.baseTypes, visited + id.fullName)
        case _ => false
      }

    checkBaseTypes(baseTypes, Set(this.fullName))
  }

  /** If this is a type alias, returns the aliased type; otherwise None. */
  def aliasedType: Option[TypeRepr] = ref match {
    case a: TypeRef.Alias => Some(a.aliased)
    case _                => None
  }

  /**
   * If this is an opaque type, returns the underlying representation; otherwise
   * None.
   */
  def opaqueRepresentation: Option[TypeRepr] = ref match {
    case o: TypeRef.Opaque => Some(o.representation)
    case _                 => None
  }

  override def equals(other: Any): Boolean = other match {
    case that: TypeId[_] => TypeId.structurallyEqual(this, that)
    case _               => false
  }

  override def hashCode(): Int = TypeId.structuralHash(this)

  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr  = ref match {
      case _: TypeRef.Nominal => "nominal"
      case _: TypeRef.Alias   => "alias"
      case _: TypeRef.Opaque  => "opaque"
    }
    s"TypeId.$kindStr($fullName$paramStr)"
  }
}

object TypeId {

  // Macro derivation

  /** Derives a TypeId for any type or type constructor using macros. */
  def derived[A]: TypeId[A] = macro TypeIdMacros.derivedImpl[A]

  // Factory methods

  /** Creates a TypeId for a nominal type (class, trait, object). */
  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown
  ): TypeId[A] =
    TypeId(TypeRef.Nominal(name, owner, typeParams, defKind))

  /** Creates a TypeId for a type alias. */
  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    defKind: TypeDefKind = TypeDefKind.TypeAlias
  ): TypeId[A] =
    TypeId(TypeRef.Alias(name, owner, typeParams, aliased, defKind))

  /** Creates a TypeId for an opaque type. */
  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    defKind: TypeDefKind = TypeDefKind.OpaqueType()
  ): TypeId[A] =
    TypeId(TypeRef.Opaque(name, owner, typeParams, representation, defKind))

  // Pattern matching extractors

  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam])] = id.ref match {
      case TypeRef.Nominal(name, owner, typeParams, _) => Some((name, owner, typeParams))
      case _                                           => None
    }
  }

  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id.ref match {
      case TypeRef.Alias(name, owner, typeParams, aliased, _) => Some((name, owner, typeParams, aliased))
      case _                                                  => None
    }
  }

  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id.ref match {
      case TypeRef.Opaque(name, owner, typeParams, representation, _) => Some((name, owner, typeParams, representation))
      case _                                                          => None
    }
  }

  // Normalization and structural equality

  /**
   * Normalizes a TypeRef by following type alias chains. Aliases are resolved
   * to their underlying type, while nominal and opaque types remain unchanged.
   */
  def normalize(ref: TypeRef): TypeRef = ref match {
    case TypeRef.Alias(_, _, _, TypeRepr.Ref(id), _) => normalize(id.ref)
    case other                                       => other
  }

  /**
   * Compares two TypeIds for structural equality. Type aliases are transparent:
   * TypeId.of[Age] == TypeId.of[Int] where type Age = Int. Opaque types
   * maintain nominal identity.
   */
  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = {
    val normA = normalize(a.ref)
    val normB = normalize(b.ref)

    (normA, normB) match {
      // Opaque types only equal if they have the same fullName (nominal identity)
      case (oa: TypeRef.Opaque, ob: TypeRef.Opaque) =>
        oa.fullName == ob.fullName && oa.typeParams == ob.typeParams
      // Opaque type is never equal to non-opaque
      case (_: TypeRef.Opaque, _) => false
      case (_, _: TypeRef.Opaque) => false
      // For nominal types and resolved aliases, compare by fullName and typeParams
      case _ =>
        normA.fullName == normB.fullName && normA.typeParams == normB.typeParams
    }
  }

  /**
   * Computes a hash code for a TypeId that is consistent with structural
   * equality.
   */
  def structuralHash(id: TypeId[_]): Int = {
    val norm = normalize(id.ref)
    norm match {
      case o: TypeRef.Opaque =>
        // Include "opaque" marker to distinguish from nominal with same name
        ("opaque", o.fullName, o.typeParams).hashCode()
      case _ =>
        (norm.fullName, norm.typeParams).hashCode()
    }
  }

  // Predefined implicit TypeIds for common types

  // Primitives
  implicit val unit: TypeId[Unit]             = nominal[Unit]("Unit", Owner.scala)
  implicit val boolean: TypeId[Boolean]       = nominal[Boolean]("Boolean", Owner.scala)
  implicit val byte: TypeId[Byte]             = nominal[Byte]("Byte", Owner.scala)
  implicit val short: TypeId[Short]           = nominal[Short]("Short", Owner.scala)
  implicit val int: TypeId[Int]               = nominal[Int]("Int", Owner.scala)
  implicit val long: TypeId[Long]             = nominal[Long]("Long", Owner.scala)
  implicit val float: TypeId[Float]           = nominal[Float]("Float", Owner.scala)
  implicit val double: TypeId[Double]         = nominal[Double]("Double", Owner.scala)
  implicit val char: TypeId[Char]             = nominal[Char]("Char", Owner.scala)
  implicit val string: TypeId[String]         = nominal[String]("String", Owner.javaLang)
  implicit val bigInt: TypeId[BigInt]         = nominal[BigInt]("BigInt", Owner.scala)
  implicit val bigDecimal: TypeId[BigDecimal] = nominal[BigDecimal]("BigDecimal", Owner.scala)

  // Type constructors - use existential types for Scala 2 compatibility
  implicit val option: TypeId[Option[_]] = nominal[Option[_]]("Option", Owner.scala, List(TypeParam.A))
  implicit val some: TypeId[Some[_]]     = nominal[Some[_]]("Some", Owner.scala, List(TypeParam.A))
  implicit val none: TypeId[None.type]   = nominal[None.type]("None", Owner.scala)
  implicit val list: TypeId[List[_]]     = nominal[List[_]]("List", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val vector: TypeId[Vector[_]] =
    nominal[Vector[_]]("Vector", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val set: TypeId[Set[_]]    = nominal[Set[_]]("Set", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val map: TypeId[Map[_, _]] =
    nominal[Map[_, _]]("Map", Owner.scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  implicit val either: TypeId[Either[_, _]] =
    nominal[Either[_, _]]("Either", Owner.scala, List(TypeParam.A, TypeParam.B))

  // Java time types
  implicit val dayOfWeek: TypeId[java.time.DayOfWeek]         = nominal[java.time.DayOfWeek]("DayOfWeek", Owner.javaTime)
  implicit val duration: TypeId[java.time.Duration]           = nominal[java.time.Duration]("Duration", Owner.javaTime)
  implicit val instant: TypeId[java.time.Instant]             = nominal[java.time.Instant]("Instant", Owner.javaTime)
  implicit val localDate: TypeId[java.time.LocalDate]         = nominal[java.time.LocalDate]("LocalDate", Owner.javaTime)
  implicit val localDateTime: TypeId[java.time.LocalDateTime] =
    nominal[java.time.LocalDateTime]("LocalDateTime", Owner.javaTime)
  implicit val localTime: TypeId[java.time.LocalTime]           = nominal[java.time.LocalTime]("LocalTime", Owner.javaTime)
  implicit val month: TypeId[java.time.Month]                   = nominal[java.time.Month]("Month", Owner.javaTime)
  implicit val monthDay: TypeId[java.time.MonthDay]             = nominal[java.time.MonthDay]("MonthDay", Owner.javaTime)
  implicit val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    nominal[java.time.OffsetDateTime]("OffsetDateTime", Owner.javaTime)
  implicit val offsetTime: TypeId[java.time.OffsetTime]       = nominal[java.time.OffsetTime]("OffsetTime", Owner.javaTime)
  implicit val period: TypeId[java.time.Period]               = nominal[java.time.Period]("Period", Owner.javaTime)
  implicit val year: TypeId[java.time.Year]                   = nominal[java.time.Year]("Year", Owner.javaTime)
  implicit val yearMonth: TypeId[java.time.YearMonth]         = nominal[java.time.YearMonth]("YearMonth", Owner.javaTime)
  implicit val zoneId: TypeId[java.time.ZoneId]               = nominal[java.time.ZoneId]("ZoneId", Owner.javaTime)
  implicit val zoneOffset: TypeId[java.time.ZoneOffset]       = nominal[java.time.ZoneOffset]("ZoneOffset", Owner.javaTime)
  implicit val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    nominal[java.time.ZonedDateTime]("ZonedDateTime", Owner.javaTime)

  // Java util types
  implicit val currency: TypeId[java.util.Currency] = nominal[java.util.Currency]("Currency", Owner.javaUtil)
  implicit val uuid: TypeId[java.util.UUID]         = nominal[java.util.UUID]("UUID", Owner.javaUtil)
}
