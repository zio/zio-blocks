package zio.blocks.typeid

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
 * values. In Scala 3, `A` is bounded by `AnyKind` to support higher-kinded
 * types.
 *
 * TypeId wraps a TypeRef which contains the actual type identity information.
 * Two TypeIds are considered equal if they refer to the same underlying type,
 * with type aliases being transparent (an alias equals its underlying type),
 * while opaque types maintain nominal identity.
 *
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
final case class TypeId[A <: AnyKind](ref: TypeRef) {

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

  /** Returns true if this TypeId represents a nominal type. */
  def isNominal: Boolean = ref.isInstanceOf[TypeRef.Nominal]

  /** Returns true if this TypeId represents a type alias. */
  def isAlias: Boolean = ref.isInstanceOf[TypeRef.Alias]

  /** Returns true if this TypeId represents an opaque type. */
  def isOpaque: Boolean = ref.isInstanceOf[TypeRef.Opaque]

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
    case that: TypeId[?] => TypeId.structurallyEqual(this, that)
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
  inline def derived[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  // Factory methods

  /** Creates a TypeId for a nominal type (class, trait, object). */
  def nominal[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil
  ): TypeId[A] =
    TypeId(TypeRef.Nominal(name, owner, typeParams))

  /** Creates a TypeId for a type alias. */
  def alias[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr
  ): TypeId[A] =
    TypeId(TypeRef.Alias(name, owner, typeParams, aliased))

  /** Creates a TypeId for an opaque type. */
  def opaque[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr
  ): TypeId[A] =
    TypeId(TypeRef.Opaque(name, owner, typeParams, representation))

  // Pattern matching extractors

  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam])] = id.ref match {
      case TypeRef.Nominal(name, owner, typeParams) => Some((name, owner, typeParams))
      case _                                        => None
    }
  }

  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id.ref match {
      case TypeRef.Alias(name, owner, typeParams, aliased) => Some((name, owner, typeParams, aliased))
      case _                                               => None
    }
  }

  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id.ref match {
      case TypeRef.Opaque(name, owner, typeParams, representation) => Some((name, owner, typeParams, representation))
      case _                                                       => None
    }
  }

  // Normalization and structural equality

  /**
   * Normalizes a TypeRef by following type alias chains. Aliases are resolved
   * to their underlying type, while nominal and opaque types remain unchanged.
   */
  def normalize(ref: TypeRef): TypeRef = ref match {
    case TypeRef.Alias(_, _, _, TypeRepr.Ref(id)) => normalize(id.ref)
    case other                                    => other
  }

  /**
   * Compares two TypeIds for structural equality. Type aliases are transparent:
   * TypeId.of[Age] == TypeId.of[Int] where type Age = Int. Opaque types
   * maintain nominal identity.
   */
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean = {
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
  def structuralHash(id: TypeId[?]): Int = {
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

  // Type constructors - using AnyKind allows raw type constructors as phantom types
  given option: TypeId[Option]  = nominal[Option]("Option", Owner.scala, List(TypeParam.A))
  given some: TypeId[Some]      = nominal[Some]("Some", Owner.scala, List(TypeParam.A))
  given none: TypeId[None.type] = nominal[None.type]("None", Owner.scala)
  given list: TypeId[List]      = nominal[List]("List", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given vector: TypeId[Vector]  = nominal[Vector]("Vector", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given set: TypeId[Set]        = nominal[Set]("Set", Owner.scalaCollectionImmutable, List(TypeParam.A))
  given map: TypeId[Map]        = nominal[Map]("Map", Owner.scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  given either: TypeId[Either]  = nominal[Either]("Either", Owner.scala, List(TypeParam.A, TypeParam.B))

  // Java time types
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

  // Java util types
  given currency: TypeId[java.util.Currency] = nominal[java.util.Currency]("Currency", Owner.javaUtil)
  given uuid: TypeId[java.util.UUID]         = nominal[java.util.UUID]("UUID", Owner.javaUtil)
}
