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
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
sealed trait TypeId[A <: AnyKind] {

  /** The simple name of the type. */
  def name: String

  /** The owner of this type (package, object, class, etc.). */
  def owner: Owner

  /**
   * The type parameters of this type (empty for proper types, non-empty for
   * type constructors).
   */
  def typeParams: List[TypeParam]

  /** The arity of this type (number of type parameters). */
  final def arity: Int = typeParams.size

  /** The fully qualified name of this type. */
  final def fullName: String =
    if (owner.isRoot) name
    else owner.asString + "." + name

  /** Returns true if this TypeId represents a nominal type. */
  final def isNominal: Boolean = this match {
    case _: TypeId.NominalImpl => true
    case _                     => false
  }

  /** Returns true if this TypeId represents a type alias. */
  final def isAlias: Boolean = this match {
    case _: TypeId.AliasImpl => true
    case _                   => false
  }

  /** Returns true if this TypeId represents an opaque type. */
  final def isOpaque: Boolean = this match {
    case _: TypeId.OpaqueImpl => true
    case _                    => false
  }

  /** If this is a type alias, returns the aliased type; otherwise None. */
  final def aliasedType: Option[TypeRepr] = this match {
    case impl: TypeId.AliasImpl => Some(impl.aliased)
    case _                      => None
  }

  /**
   * If this is an opaque type, returns the underlying representation; otherwise
   * None.
   */
  final def opaqueRepresentation: Option[TypeRepr] = this match {
    case impl: TypeId.OpaqueImpl => Some(impl.representation)
    case _                       => None
  }

  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr  = this match {
      case _: TypeId.NominalImpl => "nominal"
      case _: TypeId.AliasImpl   => "alias"
      case _: TypeId.OpaqueImpl  => "opaque"
    }
    s"TypeId.$kindStr($fullName$paramStr)"
  }
}

object TypeId {

  // Private implementations

  private[typeid] final case class NominalImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[Nothing]

  private[typeid] final case class AliasImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[Nothing]

  private[typeid] final case class OpaqueImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[Nothing]

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
    NominalImpl(name, owner, typeParams).asInstanceOf[TypeId[A]]

  /** Creates a TypeId for a type alias. */
  def alias[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr
  ): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased).asInstanceOf[TypeId[A]]

  /** Creates a TypeId for an opaque type. */
  def opaque[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr
  ): TypeId[A] =
    OpaqueImpl(name, owner, typeParams, representation).asInstanceOf[TypeId[A]]

  // Pattern matching extractors

  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam])] = id match {
      case impl: NominalImpl => Some((impl.name, impl.owner, impl.typeParams))
      case _                 => None
    }
  }

  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: AliasImpl => Some((impl.name, impl.owner, impl.typeParams, impl.aliased))
      case _               => None
    }
  }

  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: OpaqueImpl => Some((impl.name, impl.owner, impl.typeParams, impl.representation))
      case _                => None
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
