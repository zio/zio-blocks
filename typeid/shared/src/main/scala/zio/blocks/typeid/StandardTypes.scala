package zio.blocks.typeid

import zio.blocks.typeid.TypeDefKind._

private[blocks] object StandardTypes {
  private val scalaOwner  = Owner.pkg("scala")
  private val javaLang    = Owner.pkgs("java", "lang")
  private val javaTime    = Owner.pkgs("java", "time")
  private val javaUtil    = Owner.pkgs("java", "util")
  private val scalaColImm = Owner.pkgs("scala", "collection", "immutable")

  private def make[A](
    owner: Owner,
    name: String,
    params: List[TypeParam],
    kind: TypeDefKind,
    parents: List[TypeRepr]
  ): TypeId[A] =
    TypeId[A](DynamicTypeId(owner, name, params, kind, parents, Nil, Nil))

  val unit: TypeId[Unit]       = make(scalaOwner, "Unit", Nil, Class(isFinal = true, isValue = true), Nil)
  val boolean: TypeId[Boolean] = make(scalaOwner, "Boolean", Nil, Class(isFinal = true, isValue = true), Nil)
  val byte: TypeId[Byte]       = make(scalaOwner, "Byte", Nil, Class(isFinal = true, isValue = true), Nil)
  val short: TypeId[Short]     = make(scalaOwner, "Short", Nil, Class(isFinal = true, isValue = true), Nil)
  val int: TypeId[Int]         = make(scalaOwner, "Int", Nil, Class(isFinal = true, isValue = true), Nil)
  val long: TypeId[Long]       = make(scalaOwner, "Long", Nil, Class(isFinal = true, isValue = true), Nil)
  val float: TypeId[Float]     = make(scalaOwner, "Float", Nil, Class(isFinal = true, isValue = true), Nil)
  val double: TypeId[Double]   = make(scalaOwner, "Double", Nil, Class(isFinal = true, isValue = true), Nil)
  val char: TypeId[Char]       = make(scalaOwner, "Char", Nil, Class(isFinal = true, isValue = true), Nil)
  val string: TypeId[String]   = make(javaLang, "String", Nil, Class(isFinal = true), Nil)

  // Big numbers
  val bigInt: TypeId[BigInt] =
    make(scalaOwner, "math.BigInt", Nil, Class(), Nil) // simplified name, actually scala.math.BigInt
  val bigDecimal: TypeId[BigDecimal] = make(scalaOwner, "math.BigDecimal", Nil, Class(), Nil)

  // Java Time
  val dayOfWeek: TypeId[java.time.DayOfWeek]         = make(javaTime, "DayOfWeek", Nil, Enum(Nil), Nil)
  val duration: TypeId[java.time.Duration]           = make(javaTime, "Duration", Nil, Class(isFinal = true), Nil)
  val instant: TypeId[java.time.Instant]             = make(javaTime, "Instant", Nil, Class(isFinal = true), Nil)
  val localDate: TypeId[java.time.LocalDate]         = make(javaTime, "LocalDate", Nil, Class(isFinal = true), Nil)
  val localDateTime: TypeId[java.time.LocalDateTime] =
    make(javaTime, "LocalDateTime", Nil, Class(isFinal = true), Nil)
  val localTime: TypeId[java.time.LocalTime]           = make(javaTime, "LocalTime", Nil, Class(isFinal = true), Nil)
  val month: TypeId[java.time.Month]                   = make(javaTime, "Month", Nil, Enum(Nil), Nil)
  val monthDay: TypeId[java.time.MonthDay]             = make(javaTime, "MonthDay", Nil, Class(isFinal = true), Nil)
  val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    make(javaTime, "OffsetDateTime", Nil, Class(isFinal = true), Nil)
  val offsetTime: TypeId[java.time.OffsetTime]       = make(javaTime, "OffsetTime", Nil, Class(isFinal = true), Nil)
  val period: TypeId[java.time.Period]               = make(javaTime, "Period", Nil, Class(isFinal = true), Nil)
  val year: TypeId[java.time.Year]                   = make(javaTime, "Year", Nil, Class(isFinal = true), Nil)
  val yearMonth: TypeId[java.time.YearMonth]         = make(javaTime, "YearMonth", Nil, Class(isFinal = true), Nil)
  val zoneId: TypeId[java.time.ZoneId]               = make(javaTime, "ZoneId", Nil, Class(isAbstract = true), Nil)
  val zoneOffset: TypeId[java.time.ZoneOffset]       = make(javaTime, "ZoneOffset", Nil, Class(isFinal = true), Nil)
  val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    make(javaTime, "ZonedDateTime", Nil, Class(isFinal = true), Nil)

  // Java Util
  val currency: TypeId[java.util.Currency] = make(javaUtil, "Currency", Nil, Class(isFinal = true), Nil)
  val uuid: TypeId[java.util.UUID]         = make(javaUtil, "UUID", Nil, Class(), Nil)

  // Collections (Type Constructors)
  // These are TypeIds of the *constructors*, e.g. List, not List[Int].
  // The type params are definition params.

  private def tparam(name: String, idx: Int, v: Variance) = TypeParam(name, idx, v)

  val ListId: TypeId[List[_]] =
    make(scalaColImm, "List", List(tparam("A", 0, Variance.Covariant)), Trait(isSealed = true), Nil)
  val VectorId: TypeId[Vector[_]] =
    make(scalaColImm, "Vector", List(tparam("A", 0, Variance.Covariant)), Class(isFinal = true), Nil)
  val SetId: TypeId[Set[_]]    = make(scalaColImm, "Set", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)
  val MapId: TypeId[Map[_, _]] = make(
    scalaColImm,
    "Map",
    List(tparam("K", 0, Variance.Invariant), tparam("V", 1, Variance.Covariant)),
    Trait(),
    Nil
  )
  val OptionId: TypeId[Option[_]] =
    make(scalaOwner, "Option", List(tparam("A", 0, Variance.Covariant)), Trait(isSealed = true), Nil)
  val EitherId: TypeId[Either[_, _]] = make(
    scalaOwner,
    "Either",
    List(tparam("A", 0, Variance.Covariant), tparam("B", 1, Variance.Covariant)),
    Trait(isSealed = true),
    Nil
  )
  val IndexedSeqId: TypeId[IndexedSeq[_]] =
    make(scalaColImm, "IndexedSeq", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)
  val SeqId: TypeId[Seq[_]] = make(scalaColImm, "Seq", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)

}
