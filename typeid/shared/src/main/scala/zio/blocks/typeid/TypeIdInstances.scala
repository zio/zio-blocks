package zio.blocks.typeid

/**
 * Provides predefined TypeId instances for common types. These instances are
 * available implicitly when importing TypeId.
 */
trait TypeIdInstances {

  import TypeIdOps.Owners._

  implicit val charSequence: TypeId[CharSequence] =
    TypeId.nominal[CharSequence]("CharSequence", javaLang, defKind = TypeDefKind.Trait(isSealed = false))
  implicit val comparable: TypeId[Comparable[_]] =
    TypeId.nominal[Comparable[_]]("Comparable", javaLang, List(TypeParam("T", 0, Variance.Invariant)))
  implicit val serializable: TypeId[java.io.Serializable] =
    TypeId.nominal[java.io.Serializable]("Serializable", javaIo, defKind = TypeDefKind.Trait(isSealed = false))

  implicit val unit: TypeId[Unit]       = TypeId.nominal[Unit]("Unit", TypeIdOps.Owners.scala)
  implicit val boolean: TypeId[Boolean] = TypeId.nominal[Boolean]("Boolean", TypeIdOps.Owners.scala)
  implicit val byte: TypeId[Byte]       = TypeId.nominal[Byte]("Byte", TypeIdOps.Owners.scala)
  implicit val short: TypeId[Short]     = TypeId.nominal[Short]("Short", TypeIdOps.Owners.scala)
  implicit val int: TypeId[Int]         = TypeId.nominal[Int]("Int", TypeIdOps.Owners.scala)
  implicit val long: TypeId[Long]       = TypeId.nominal[Long]("Long", TypeIdOps.Owners.scala)
  implicit val float: TypeId[Float]     = TypeId.nominal[Float]("Float", TypeIdOps.Owners.scala)
  implicit val double: TypeId[Double]   = TypeId.nominal[Double]("Double", TypeIdOps.Owners.scala)
  implicit val char: TypeId[Char]       = TypeId.nominal[Char]("Char", TypeIdOps.Owners.scala)
  implicit val string: TypeId[String]   = TypeId.nominal[String](
    "String",
    javaLang,
    defKind = TypeDefKind.Class(
      isFinal = true,
      isAbstract = false,
      isCase = false,
      isValue = false,
      bases = List(
        TypeRepr.Ref(charSequence),
        TypeRepr.Ref(comparable.asInstanceOf[TypeId[_]]),
        TypeRepr.Ref(serializable)
      )
    )
  )
  implicit val bigInt: TypeId[BigInt]         = TypeId.nominal[BigInt]("BigInt", TypeIdOps.Owners.scala)
  implicit val bigDecimal: TypeId[BigDecimal] = TypeId.nominal[BigDecimal]("BigDecimal", TypeIdOps.Owners.scala)

  implicit val option: TypeId[Option[_]] =
    TypeId.nominal[Option[_]]("Option", TypeIdOps.Owners.scala, List(TypeParam.A))
  implicit val some: TypeId[Some[_]]     = TypeId.nominal[Some[_]]("Some", TypeIdOps.Owners.scala, List(TypeParam.A))
  implicit val none: TypeId[None.type]   = TypeId.nominal[None.type]("None", TypeIdOps.Owners.scala)
  implicit val list: TypeId[List[_]]     = TypeId.nominal[List[_]]("List", scalaCollectionImmutable, List(TypeParam.A))
  implicit val vector: TypeId[Vector[_]] =
    TypeId.nominal[Vector[_]]("Vector", scalaCollectionImmutable, List(TypeParam.A))
  implicit val set: TypeId[Set[_]] =
    TypeId.nominal[Set[_]]("Set", scalaCollectionImmutable, List(TypeParam("A", 0, Variance.Invariant)))
  implicit val seq: TypeId[Seq[_]]               = TypeId.nominal[Seq[_]]("Seq", scalaCollectionImmutable, List(TypeParam.A))
  implicit val indexedSeq: TypeId[IndexedSeq[_]] =
    TypeId.nominal[IndexedSeq[_]]("IndexedSeq", scalaCollectionImmutable, List(TypeParam.A))
  implicit val map: TypeId[Map[_, _]] =
    TypeId.nominal[Map[_, _]]("Map", scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  implicit val either: TypeId[Either[_, _]] =
    TypeId.nominal[Either[_, _]]("Either", scalaUtil, List(TypeParam.A, TypeParam.B))
  implicit val array: TypeId[Array[_]] =
    TypeId.nominal[Array[_]]("Array", TypeIdOps.Owners.scala, List(TypeParam("T", 0, Variance.Invariant)))
  implicit val arraySeq: TypeId[_root_.scala.collection.immutable.ArraySeq[_]] =
    TypeId
      .nominal[_root_.scala.collection.immutable.ArraySeq[_]]("ArraySeq", scalaCollectionImmutable, List(TypeParam.A))

  implicit val chunk: TypeId[zio.blocks.chunk.Chunk[_]] =
    TypeId.nominal[zio.blocks.chunk.Chunk[_]]("Chunk", zioBlocksChunk, List(TypeParam.A))

  implicit val dayOfWeek: TypeId[java.time.DayOfWeek]         = TypeId.nominal[java.time.DayOfWeek]("DayOfWeek", javaTime)
  implicit val duration: TypeId[java.time.Duration]           = TypeId.nominal[java.time.Duration]("Duration", javaTime)
  implicit val instant: TypeId[java.time.Instant]             = TypeId.nominal[java.time.Instant]("Instant", javaTime)
  implicit val localDate: TypeId[java.time.LocalDate]         = TypeId.nominal[java.time.LocalDate]("LocalDate", javaTime)
  implicit val localDateTime: TypeId[java.time.LocalDateTime] =
    TypeId.nominal[java.time.LocalDateTime]("LocalDateTime", javaTime)
  implicit val localTime: TypeId[java.time.LocalTime]           = TypeId.nominal[java.time.LocalTime]("LocalTime", javaTime)
  implicit val month: TypeId[java.time.Month]                   = TypeId.nominal[java.time.Month]("Month", javaTime)
  implicit val monthDay: TypeId[java.time.MonthDay]             = TypeId.nominal[java.time.MonthDay]("MonthDay", javaTime)
  implicit val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    TypeId.nominal[java.time.OffsetDateTime]("OffsetDateTime", javaTime)
  implicit val offsetTime: TypeId[java.time.OffsetTime]       = TypeId.nominal[java.time.OffsetTime]("OffsetTime", javaTime)
  implicit val period: TypeId[java.time.Period]               = TypeId.nominal[java.time.Period]("Period", javaTime)
  implicit val year: TypeId[java.time.Year]                   = TypeId.nominal[java.time.Year]("Year", javaTime)
  implicit val yearMonth: TypeId[java.time.YearMonth]         = TypeId.nominal[java.time.YearMonth]("YearMonth", javaTime)
  implicit val zoneId: TypeId[java.time.ZoneId]               = TypeId.nominal[java.time.ZoneId]("ZoneId", javaTime)
  implicit val zoneOffset: TypeId[java.time.ZoneOffset]       = TypeId.nominal[java.time.ZoneOffset]("ZoneOffset", javaTime)
  implicit val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    TypeId.nominal[java.time.ZonedDateTime]("ZonedDateTime", javaTime)

  implicit val currency: TypeId[java.util.Currency] = TypeId.nominal[java.util.Currency]("Currency", javaUtil)
  implicit val uuid: TypeId[java.util.UUID]         = TypeId.nominal[java.util.UUID]("UUID", javaUtil)
}

object TypeIdInstances extends TypeIdInstances
