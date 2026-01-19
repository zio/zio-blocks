package zio.blocks.typeid

sealed trait TypeId[A] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]

  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.segments.isEmpty) name
    else owner.asString + "." + name
}

object TypeId extends TypeIdCompanionVersionSpecific {
  private final case class NominalImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[Nothing]

  private final case class AliasImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[Nothing]

  private final case class OpaqueImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[Nothing]

  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ): TypeId[A] =
    NominalImpl(name, owner, typeParams).asInstanceOf[TypeId[A]]

  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased).asInstanceOf[TypeId[A]]

  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ): TypeId[A] =
    OpaqueImpl(name, owner, typeParams, representation).asInstanceOf[TypeId[A]]

  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam])] = id match {
      case impl: NominalImpl => Some((impl.name, impl.owner, impl.typeParams))
      case _                 => None
    }
  }

  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: AliasImpl => Some((impl.name, impl.owner, impl.typeParams, impl.aliased))
      case _               => None
    }
  }

  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: OpaqueImpl => Some((impl.name, impl.owner, impl.typeParams, impl.representation))
      case _                => None
    }
  }

  private val scalaOwner: Owner                    = Owner(List(Owner.Package("scala")))
  private val javaTimeOwner: Owner                 = Owner(List(Owner.Package("java"), Owner.Package("time")))
  private val javaUtilOwner: Owner                 = Owner(List(Owner.Package("java"), Owner.Package("util")))
  private val javaLangOwner: Owner                 = Owner(List(Owner.Package("java"), Owner.Package("lang")))
  private val scalaCollectionImmutableOwner: Owner =
    Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable")))

  val unit: TypeId[Unit]               = nominal[Unit]("Unit", scalaOwner, Nil)
  val boolean: TypeId[Boolean]         = nominal[Boolean]("Boolean", scalaOwner, Nil)
  val byte: TypeId[Byte]               = nominal[Byte]("Byte", scalaOwner, Nil)
  val short: TypeId[Short]             = nominal[Short]("Short", scalaOwner, Nil)
  val int: TypeId[Int]                 = nominal[Int]("Int", scalaOwner, Nil)
  val long: TypeId[Long]               = nominal[Long]("Long", scalaOwner, Nil)
  val float: TypeId[Float]             = nominal[Float]("Float", scalaOwner, Nil)
  val double: TypeId[Double]           = nominal[Double]("Double", scalaOwner, Nil)
  val char: TypeId[Char]               = nominal[Char]("Char", scalaOwner, Nil)
  val string: TypeId[String]           = nominal[String]("String", javaLangOwner, Nil)
  val bigInt: TypeId[BigInt]           = nominal[BigInt]("BigInt", scalaOwner, Nil)
  val bigDecimal: TypeId[BigDecimal]   = nominal[BigDecimal]("BigDecimal", scalaOwner, Nil)

  val dayOfWeek: TypeId[java.time.DayOfWeek]           = nominal[java.time.DayOfWeek]("DayOfWeek", javaTimeOwner, Nil)
  val duration: TypeId[java.time.Duration]             = nominal[java.time.Duration]("Duration", javaTimeOwner, Nil)
  val instant: TypeId[java.time.Instant]               = nominal[java.time.Instant]("Instant", javaTimeOwner, Nil)
  val localDate: TypeId[java.time.LocalDate]           = nominal[java.time.LocalDate]("LocalDate", javaTimeOwner, Nil)
  val localDateTime: TypeId[java.time.LocalDateTime]   = nominal[java.time.LocalDateTime]("LocalDateTime", javaTimeOwner, Nil)
  val localTime: TypeId[java.time.LocalTime]           = nominal[java.time.LocalTime]("LocalTime", javaTimeOwner, Nil)
  val month: TypeId[java.time.Month]                   = nominal[java.time.Month]("Month", javaTimeOwner, Nil)
  val monthDay: TypeId[java.time.MonthDay]             = nominal[java.time.MonthDay]("MonthDay", javaTimeOwner, Nil)
  val offsetDateTime: TypeId[java.time.OffsetDateTime] = nominal[java.time.OffsetDateTime]("OffsetDateTime", javaTimeOwner, Nil)
  val offsetTime: TypeId[java.time.OffsetTime]         = nominal[java.time.OffsetTime]("OffsetTime", javaTimeOwner, Nil)
  val period: TypeId[java.time.Period]                 = nominal[java.time.Period]("Period", javaTimeOwner, Nil)
  val year: TypeId[java.time.Year]                     = nominal[java.time.Year]("Year", javaTimeOwner, Nil)
  val yearMonth: TypeId[java.time.YearMonth]           = nominal[java.time.YearMonth]("YearMonth", javaTimeOwner, Nil)
  val zoneId: TypeId[java.time.ZoneId]                 = nominal[java.time.ZoneId]("ZoneId", javaTimeOwner, Nil)
  val zoneOffset: TypeId[java.time.ZoneOffset]         = nominal[java.time.ZoneOffset]("ZoneOffset", javaTimeOwner, Nil)
  val zonedDateTime: TypeId[java.time.ZonedDateTime]   = nominal[java.time.ZonedDateTime]("ZonedDateTime", javaTimeOwner, Nil)

  val currency: TypeId[java.util.Currency] = nominal[java.util.Currency]("Currency", javaUtilOwner, Nil)
  val uuid: TypeId[java.util.UUID]         = nominal[java.util.UUID]("UUID", javaUtilOwner, Nil)

  val none: TypeId[None.type]    = nominal[None.type]("None", scalaOwner, Nil)
  val some: TypeId[Some[_]]      = nominal[Some[_]]("Some", scalaOwner, List(TypeParam("A", 0)))
  val option: TypeId[Option[_]]  = nominal[Option[_]]("Option", scalaOwner, List(TypeParam("A", 0)))

  val list: TypeId[List[_]]                                     = nominal[List[_]]("List", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
  val map: TypeId[Map[_, _]]                                    = nominal[Map[_, _]]("Map", scalaCollectionImmutableOwner, List(TypeParam("K", 0), TypeParam("V", 1)))
  val set: TypeId[Set[_]]                                       = nominal[Set[_]]("Set", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
  val vector: TypeId[Vector[_]]                                 = nominal[Vector[_]]("Vector", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
  val arraySeq: TypeId[scala.collection.immutable.ArraySeq[_]]  = nominal[scala.collection.immutable.ArraySeq[_]]("ArraySeq", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
  val indexedSeq: TypeId[IndexedSeq[_]]                         = nominal[IndexedSeq[_]]("IndexedSeq", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
  val seq: TypeId[Seq[_]]                                       = nominal[Seq[_]]("Seq", scalaCollectionImmutableOwner, List(TypeParam("A", 0)))
}
