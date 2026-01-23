package zio.blocks.typeid

import zio.blocks.typeid.TypeDefKind._

private[blocks] object StandardTypes {
  private val scalaOwner  = Owner.pkg("scala")
  private val javaLang    = Owner.pkgs("java", "lang")
  private val javaTime    = Owner.pkgs("java", "time")
  private val javaUtil    = Owner.pkgs("java", "util")
  private val scalaColImm = Owner.pkgs("scala", "collection", "immutable")

  val unit: TypeId[Unit]       = TypeId(scalaOwner, "Unit", Nil, Class(isFinal = true, isValue = true), Nil)
  val boolean: TypeId[Boolean] = TypeId(scalaOwner, "Boolean", Nil, Class(isFinal = true, isValue = true), Nil)
  val byte: TypeId[Byte]       = TypeId(scalaOwner, "Byte", Nil, Class(isFinal = true, isValue = true), Nil)
  val short: TypeId[Short]     = TypeId(scalaOwner, "Short", Nil, Class(isFinal = true, isValue = true), Nil)
  val int: TypeId[Int]         = TypeId(scalaOwner, "Int", Nil, Class(isFinal = true, isValue = true), Nil)
  val long: TypeId[Long]       = TypeId(scalaOwner, "Long", Nil, Class(isFinal = true, isValue = true), Nil)
  val float: TypeId[Float]     = TypeId(scalaOwner, "Float", Nil, Class(isFinal = true, isValue = true), Nil)
  val double: TypeId[Double]   = TypeId(scalaOwner, "Double", Nil, Class(isFinal = true, isValue = true), Nil)
  val char: TypeId[Char]       = TypeId(scalaOwner, "Char", Nil, Class(isFinal = true, isValue = true), Nil)
  val string: TypeId[String]   = TypeId(javaLang, "String", Nil, Class(isFinal = true), Nil)

  // Big numbers
  val bigInt: TypeId[BigInt] =
    TypeId(scalaOwner, "math.BigInt", Nil, Class(), Nil) // simplified name, actually scala.math.BigInt
  val bigDecimal: TypeId[BigDecimal] = TypeId(scalaOwner, "math.BigDecimal", Nil, Class(), Nil)

  // Java Time
  val dayOfWeek: TypeId[java.time.DayOfWeek]         = TypeId(javaTime, "DayOfWeek", Nil, Enum(Nil), Nil)
  val duration: TypeId[java.time.Duration]           = TypeId(javaTime, "Duration", Nil, Class(isFinal = true), Nil)
  val instant: TypeId[java.time.Instant]             = TypeId(javaTime, "Instant", Nil, Class(isFinal = true), Nil)
  val localDate: TypeId[java.time.LocalDate]         = TypeId(javaTime, "LocalDate", Nil, Class(isFinal = true), Nil)
  val localDateTime: TypeId[java.time.LocalDateTime] =
    TypeId(javaTime, "LocalDateTime", Nil, Class(isFinal = true), Nil)
  val localTime: TypeId[java.time.LocalTime]           = TypeId(javaTime, "LocalTime", Nil, Class(isFinal = true), Nil)
  val month: TypeId[java.time.Month]                   = TypeId(javaTime, "Month", Nil, Enum(Nil), Nil)
  val monthDay: TypeId[java.time.MonthDay]             = TypeId(javaTime, "MonthDay", Nil, Class(isFinal = true), Nil)
  val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    TypeId(javaTime, "OffsetDateTime", Nil, Class(isFinal = true), Nil)
  val offsetTime: TypeId[java.time.OffsetTime]       = TypeId(javaTime, "OffsetTime", Nil, Class(isFinal = true), Nil)
  val period: TypeId[java.time.Period]               = TypeId(javaTime, "Period", Nil, Class(isFinal = true), Nil)
  val year: TypeId[java.time.Year]                   = TypeId(javaTime, "Year", Nil, Class(isFinal = true), Nil)
  val yearMonth: TypeId[java.time.YearMonth]         = TypeId(javaTime, "YearMonth", Nil, Class(isFinal = true), Nil)
  val zoneId: TypeId[java.time.ZoneId]               = TypeId(javaTime, "ZoneId", Nil, Class(isAbstract = true), Nil)
  val zoneOffset: TypeId[java.time.ZoneOffset]       = TypeId(javaTime, "ZoneOffset", Nil, Class(isFinal = true), Nil)
  val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    TypeId(javaTime, "ZonedDateTime", Nil, Class(isFinal = true), Nil)

  // Java Util
  val currency: TypeId[java.util.Currency] = TypeId(javaUtil, "Currency", Nil, Class(isFinal = true), Nil)
  val uuid: TypeId[java.util.UUID]         = TypeId(javaUtil, "UUID", Nil, Class(), Nil)

  // Collections (Type Constructors)
  // These are TypeIds of the *constructors*, e.g. List, not List[Int].
  // The type params are definition params.

  private def tparam(name: String, idx: Int, v: Variance) = TypeParam(name, idx, v)

  val ListId: TypeId[List[_]] =
    TypeId(scalaColImm, "List", List(tparam("A", 0, Variance.Covariant)), Trait(isSealed = true), Nil)
  val VectorId: TypeId[Vector[_]] =
    TypeId(scalaColImm, "Vector", List(tparam("A", 0, Variance.Covariant)), Class(isFinal = true), Nil)
  val SetId: TypeId[Set[_]]    = TypeId(scalaColImm, "Set", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)
  val MapId: TypeId[Map[_, _]] = TypeId(
    scalaColImm,
    "Map",
    List(tparam("K", 0, Variance.Invariant), tparam("V", 1, Variance.Covariant)),
    Trait(),
    Nil
  )
  val OptionId: TypeId[Option[_]] =
    TypeId(scalaOwner, "Option", List(tparam("A", 0, Variance.Covariant)), Trait(isSealed = true), Nil)
  val EitherId: TypeId[Either[_, _]] = TypeId(
    scalaOwner,
    "Either",
    List(tparam("A", 0, Variance.Covariant), tparam("B", 1, Variance.Covariant)),
    Trait(isSealed = true),
    Nil
  )
  val IndexedSeqId: TypeId[IndexedSeq[_]] =
    TypeId(scalaColImm, "IndexedSeq", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)
  val SeqId: TypeId[Seq[_]] = TypeId(scalaColImm, "Seq", List(tparam("A", 0, Variance.Covariant)), Trait(), Nil)

}
